package aradexperience.service.points;

import aradexperience.dto.community.LeaderboardDto;
import aradexperience.entity.points.PointsTransaction;
import aradexperience.entity.users.User;
import aradexperience.enums.PointsType;
import aradexperience.exceptions.BadRequestException;
import aradexperience.exceptions.ConflictException;
import aradexperience.exceptions.NotFoundException;
import aradexperience.exceptions.UnauthorizedException;
import aradexperience.repository.community.CommunityRegistrationRepository;
import aradexperience.repository.points.PointsBalanceRepository;
import aradexperience.repository.points.PointsTransactionRepository;
import aradexperience.repository.users.UserRepository;
import aradexperience.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Punctele: SINGURUL loc care scrie `points_transactions` și `users.points_balance` (CLAUDE.md). Soldul = suma tranzacțiilor,
 * actualizat atomic în aceeași tranzacție DB cu rândul nou. Clasamentul = doar puncte câștigate (amount > 0) în luna / anul curent,
 * ora Aradului (Europe/Bucharest).
 * Folosit de: T11 (confirmarea la acțiuni → {@link #award}), T10 (plata cu puncte la oferte → {@link #spend}), T14 (ajustări → {@link #adjust}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PointsService {

    public static final ZoneId ZONE = ZoneId.of("Europe/Bucharest");
    /** Mesajul 409 din API.md când soldul nu ajunge (FE îl traduce). */
    public static final String INSUFFICIENT_POINTS = "insufficient_points";
    static final int BOARD_SIZE = 50;

    private final PointsTransactionRepository transactions;
    private final PointsBalanceRepository balances;
    private final CommunityRegistrationRepository registrations;
    private final UserRepository users;

    /** Ceasul aplicației; în teste se înlocuiește ca „luna curentă” să fie fixă. */
    Clock clock = Clock.system(ZONE);

    public enum Period {
        MONTH, YEAR;

        public static Period parse(String raw) {
            if (raw == null || raw.isBlank() || raw.equalsIgnoreCase("month")) return MONTH;
            if (raw.equalsIgnoreCase("year")) return YEAR;
            throw new BadRequestException("period must be month or year");
        }

        public String key() {
            return name().toLowerCase();
        }
    }

    /** Soldul, punctele lunii, locul în clasamentul lunii, seria de luni active și ultimele 50 de tranzacții. */
    public record Summary(int balance, int monthEarned, int rank, int streakMonths, List<PointsTransaction> transactions) {}

    /** Clasamentul calculat (rândurile + numele + participările), mapat în LeaderboardDto de mapper. */
    public record Board(Period period, List<LeaderboardDto.Entry> entries, LeaderboardDto.Me me) {}

    // ---------------------------------------------------------------- scriere

    /** Acordă puncte (ex. confirmarea la o acțiune). `amount` > 0. */
    @Transactional
    public PointsTransaction award(long userId, int amount, PointsType type, String refType, Long refId, String note) {
        if (amount <= 0) throw new IllegalArgumentException("award amount must be positive");
        if (balances.add(userId, amount) == 0) throw NotFoundException.of("User", userId);
        log.info("Points +{} for user {} ({} {} {})", amount, userId, type, refType, refId);
        return transactions.save(tx(userId, amount, type, refType, refId, note));
    }

    /** Cheltuie puncte (ex. plata unei oferte, T10). 409 `insufficient_points` dacă soldul nu ajunge; nimic nu se scrie în acest caz. */
    @Transactional
    public PointsTransaction spend(long userId, int amount, String refType, Long refId, String note) {
        if (amount <= 0) throw new IllegalArgumentException("spend amount must be positive");
        if (balances.subtract(userId, amount) == 0) {
            if (!users.existsById(userId)) throw NotFoundException.of("User", userId);
            throw new ConflictException(INSUFFICIENT_POINTS);
        }
        log.info("Points -{} for user {} ({} {})", amount, userId, refType, refId);
        return transactions.save(tx(userId, -amount, PointsType.SPEND_OFFER, refType, refId, note));
    }

    /** Ajustare manuală de Admin (T14): `delta` pozitiv sau negativ; soldul nu poate deveni negativ (409). */
    @Transactional
    public PointsTransaction adjust(long userId, int delta, String note) {
        if (delta == 0) throw new BadRequestException("pointsAdjust cannot be 0");
        int updated = delta > 0 ? balances.add(userId, delta) : balances.subtract(userId, -delta);
        if (updated == 0) {
            if (!users.existsById(userId)) throw NotFoundException.of("User", userId);
            throw new ConflictException(INSUFFICIENT_POINTS);
        }
        log.info("Points adjust {} for user {}", delta, userId);
        return transactions.save(tx(userId, delta, PointsType.ADJUST, PointsTransaction.REF_ADMIN, null, note));
    }

    // ---------------------------------------------------------------- citire

    public Summary summary(CurrentUser current) {
        User u = users.findById(current.id()).orElseThrow(() -> new UnauthorizedException("Account no longer exists"));
        Board month = board(Period.MONTH, current.id());
        return new Summary(u.getPointsBalance(), month.me().points(), month.me().rank(), streakMonths(u.getId()),
                transactions.findTop50ByUserIdOrderByCreatedAtDescIdDesc(u.getId()));
    }

    /** @param meId utilizatorul logat (sau null pentru vizitatori fără cont) — pentru `me` */
    public Board board(Period period, Long meId) {
        OffsetDateTime[] range = range(period);
        List<PointsTransactionRepository.BoardRow> rows = transactions.board(range[0], range[1]);

        Map<Long, String> names = users.findAllById(rows.stream().limit(BOARD_SIZE).map(PointsTransactionRepository.BoardRow::getUserId).toList())
                .stream().collect(Collectors.toMap(User::getId, User::getDisplayName));
        Map<Long, CommunityRegistrationRepository.ParticipationRow> part = registrations.participation(range[0], range[1]).stream()
                .collect(Collectors.toMap(CommunityRegistrationRepository.ParticipationRow::getUserId, Function.identity()));
        Map<Long, String> lastTitle = new HashMap<>();
        registrations.lastActions(range[0], range[1]).forEach(r -> lastTitle.putIfAbsent(r.getUserId(), r.getTitle()));

        List<LeaderboardDto.Entry> entries = new ArrayList<>();
        LeaderboardDto.Me me = null;
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i);
            int rank = i + 1;
            int points = row.getPoints().intValue();
            if (rank <= BOARD_SIZE) {
                var p = part.get(row.getUserId());
                entries.add(new LeaderboardDto.Entry(rank, row.getUserId(), names.getOrDefault(row.getUserId(), "—"), points,
                        p == null ? 0 : p.getActionsCount().intValue(), p == null ? null : p.getLastAt(), lastTitle.get(row.getUserId())));
            }
            if (meId != null && meId.equals(row.getUserId())) me = new LeaderboardDto.Me(rank, points);
        }
        if (meId != null && me == null) me = new LeaderboardDto.Me(rows.size() + 1, 0);   // fără puncte în perioadă: primul loc după cei cu puncte
        return new Board(period, entries, me);
    }

    /** Luni consecutive cu puncte câștigate, numărate înapoi de la luna curentă (sau de la cea trecută, dacă luna asta încă n-a câștigat). */
    int streakMonths(long userId) {
        Set<YearMonth> months = new TreeSet<>();
        transactions.earnedAt(userId).forEach(t -> months.add(YearMonth.from(t.atZoneSameInstant(ZONE))));
        YearMonth m = YearMonth.now(clock);
        if (!months.contains(m)) m = m.minusMonths(1);
        int streak = 0;
        while (months.contains(m)) {
            streak++;
            m = m.minusMonths(1);
        }
        return streak;
    }

    /** [început, sfârșit) al lunii / anului curent, ora Aradului. */
    OffsetDateTime[] range(Period period) {
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(ZONE);
        ZonedDateTime from = period == Period.MONTH
                ? now.withDayOfMonth(1).toLocalDate().atStartOfDay(ZONE)
                : now.withDayOfYear(1).toLocalDate().atStartOfDay(ZONE);
        ZonedDateTime to = period == Period.MONTH ? from.plusMonths(1) : from.plusYears(1);
        return new OffsetDateTime[]{from.toOffsetDateTime(), to.toOffsetDateTime()};
    }

    public OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    private PointsTransaction tx(long userId, int amount, PointsType type, String refType, Long refId, String note) {
        PointsTransaction t = new PointsTransaction();
        t.setUserId(userId);
        t.setAmount(amount);
        t.setType(type);
        t.setRefType(refType);
        t.setRefId(refId);
        t.setNote(Optional.ofNullable(note).map(n -> n.length() > 250 ? n.substring(0, 250) : n).orElse(null));
        t.setCreatedAt(OffsetDateTime.now(clock));
        return t;
    }
}
