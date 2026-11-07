package aradexperience.service.admin;

import aradexperience.dto.admin.CityBusinessCreate;
import aradexperience.entity.business.Business;
import aradexperience.entity.users.User;
import aradexperience.enums.ContentStatus;
import aradexperience.enums.Role;
import aradexperience.exceptions.ConflictException;
import aradexperience.exceptions.NotFoundException;
import aradexperience.repository.admin.AdminContentRepository;
import aradexperience.repository.business.BusinessRepository;
import aradexperience.repository.users.UserRepository;
import aradexperience.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static aradexperience.enums.ContentStatus.DRAFT;
import static aradexperience.enums.ContentStatus.PUBLISHED;

/**
 * T14 · Firme partenere (`/city/businesses`, Primăria și Adminul): lista, contul nou creat de Primărie (verificat din start, cu
 * invitație simulată), verificarea (conținutul în ciornă devine public) și respingerea / suspendarea (conținutul redevine ciornă).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PartnerService {

    static final String EMAIL_TAKEN = "Email already registered";
    /** Centrul Aradului (Piața Avram Iancu): firmele create din panou n-au coordonate până nu și le completează în profil. */
    static final double ARAD_LAT = 46.1866;
    static final double ARAD_LNG = 21.3123;
    private static final String PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AdminContentRepository content;
    private final BusinessRepository businesses;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    /** Un rând din tabel: firma, contul proprietarului și ofertele ei. */
    public record Row(Business business, User owner, long activeOffers, long draftOffers, long codesUsed) {}

    public record Created(Row row, String invitePassword) {}

    public List<Row> list(Boolean verified) {
        List<Business> all = content.allBusinesses().stream()
                .filter(b -> verified == null || b.isVerified() == verified).toList();
        return rows(all);
    }

    @Transactional
    public Created create(CityBusinessCreate req, CurrentUser by) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmail(email)) throw new ConflictException(EMAIL_TAKEN);
        String password = invitePassword();

        User u = new User();
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode(password));
        u.setDisplayName(req.contactName().trim());
        u.setRole(Role.BUSINESS);
        u.setLocale("ro");
        try {
            u = users.saveAndFlush(u);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(EMAIL_TAKEN);
        }

        Business b = new Business();
        b.setOwnerUserId(u.getId());
        b.setName(req.name().trim());
        b.setCategory(req.category());
        b.setAddress(req.address().trim());
        b.setPhone(blankToNull(req.phone()));
        b.setLat(ARAD_LAT);
        b.setLng(ARAD_LNG);
        b.setVerified(true);
        b = businesses.saveAndFlush(b);
        log.info("Business {} created by user {} (invitation e-mail simulated)", b.getId(), by.id());
        return new Created(new Row(b, u, 0, 0, 0), password);
    }

    @Transactional
    public Row verify(long id, CurrentUser by) {
        Business b = find(id);
        b.setVerified(true);
        b.setRejectedAt(null);
        b.setRejectReason(null);
        int moved = move(id, DRAFT, PUBLISHED);
        log.info("Business {} verified by user {}: {} drafts published", id, by.id(), moved);
        return rows(List.of(find(id))).get(0);
    }

    /** Respinge o firmă nouă sau suspendă una verificată; conținutul ei public redevine ciornă. */
    @Transactional
    public void reject(long id, String reason, CurrentUser by) {
        Business b = find(id);
        boolean wasVerified = b.isVerified();
        b.setVerified(false);
        b.setRejectedAt(OffsetDateTime.now());
        b.setRejectReason(blankToNull(reason));
        int moved = wasVerified ? move(id, PUBLISHED, DRAFT) : 0;
        log.info("Business {} {} by user {}: {} items back to draft", id, wasVerified ? "suspended" : "rejected", by.id(), moved);
    }

    private int move(long businessId, ContentStatus from, ContentStatus to) {
        return content.moveEvents(businessId, from, to) + content.moveOffers(businessId, from, to)
                + content.moveAnnouncements(businessId, from, to);
    }

    private Business find(long id) {
        return businesses.findById(id).orElseThrow(() -> NotFoundException.of("Business", id));
    }

    private List<Row> rows(List<Business> list) {
        if (list.isEmpty()) return List.of();
        Map<Long, User> owners = users.findAllById(list.stream().map(Business::getOwnerUserId).toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, AdminContentRepository.OfferCounts> counts = content.offerCounts(PUBLISHED, DRAFT).stream()
                .collect(Collectors.toMap(AdminContentRepository.OfferCounts::getBusinessId, Function.identity()));
        return list.stream().map(b -> {
            AdminContentRepository.OfferCounts c = counts.get(b.getId());
            return new Row(b, owners.get(b.getOwnerUserId()), c == null ? 0 : nz(c.getActive()), c == null ? 0 : nz(c.getDrafts()),
                    c == null ? 0 : nz(c.getUsed()));
        }).toList();
    }

    static String invitePassword() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) sb.append(PASSWORD_CHARS.charAt(RANDOM.nextInt(PASSWORD_CHARS.length())));
        return sb.toString();
    }

    private static long nz(Long v) {
        return v == null ? 0 : v;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
