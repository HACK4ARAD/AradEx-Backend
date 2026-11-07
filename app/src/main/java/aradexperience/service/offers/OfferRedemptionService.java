package aradexperience.service.offers;

import aradexperience.entity.business.Business;
import aradexperience.entity.offers.Offer;
import aradexperience.entity.offers.OfferRedemption;
import aradexperience.enums.ContentStatus;
import aradexperience.enums.RedemptionStatus;
import aradexperience.exceptions.ConflictException;
import aradexperience.exceptions.NotFoundException;
import aradexperience.repository.business.BusinessRepository;
import aradexperience.repository.offers.OfferRedemptionRepository;
import aradexperience.repository.offers.OfferRepository;
import aradexperience.security.CurrentUser;
import aradexperience.util.Codes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Codurile de ofertă (T10): revendicare → `AX-O{offerId}-XXXXXX` (`ISSUED`), validare la firmă → `USED` o singură dată.
 * Reguli: un singur cod activ per utilizator per ofertă (409; garantat și de indexul unic parțial din V7); oferta trebuie să fie
 * publicată și valabilă azi; la casă, codul trebuie să fie al firmei care îl validează (409 altfel) și se folosește atomic
 * (două scanări simultane → una singură reușește). Plata cu puncte (`pointsPrice`) se debitează din T11 prin `PointsService`;
 * până atunci `pointsSpent` = 0.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OfferRedemptionService {

    static final String ALREADY_ACTIVE = "You already have an active code for this offer";
    static final String NOT_AVAILABLE = "This offer is not valid today";
    static final String ALREADY_USED = "Code already used";
    static final String OTHER_BUSINESS = "This code belongs to another business";
    static final String CANCELLED = "Code cancelled";
    static final String UNKNOWN = "Unknown code";

    private final OfferRepository offers;
    private final OfferRedemptionRepository redemptions;
    private final BusinessRepository businesses;

    /** Un cod cu oferta și firma lui (pentru titlu și nume în DTO). */
    public record RedemptionView(OfferRedemption redemption, Offer offer, Business business) {}

    @Transactional
    public RedemptionView redeem(long offerId, CurrentUser user) {
        Offer o = offers.findById(offerId).filter(x -> x.getStatus() == ContentStatus.PUBLISHED)
                .orElseThrow(() -> NotFoundException.of("Offer", offerId));
        if (!o.isAvailableOn(OfferService.today())) {
            throw new ConflictException(NOT_AVAILABLE);
        }
        if (redemptions.existsByOfferIdAndUserIdAndStatus(offerId, user.id(), RedemptionStatus.ISSUED)) {
            throw new ConflictException(ALREADY_ACTIVE);
        }
        OfferRedemption r = new OfferRedemption();
        r.setOfferId(offerId);
        r.setUserId(user.id());
        r.setCode(Codes.generate("AX-O" + offerId));
        r.setStatus(RedemptionStatus.ISSUED);
        r.setPointsSpent(0);   // T11: debitează o.getPointsPrice() prin PointsService (409 insufficient_points)
        r.setIssuedAt(OffsetDateTime.now());
        try {
            r = redemptions.saveAndFlush(r);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(ALREADY_ACTIVE);   // două cereri simultane: indexul unic parțial a oprit-o pe a doua
        }
        offers.incrementRedemptions(offerId);
        log.info("Offer {} redeemed by user {} (redemption {})", offerId, user.id(), r.getId());
        return new RedemptionView(r, o, businesses.findById(o.getBusinessId()).orElse(null));
    }

    public List<RedemptionView> mine(CurrentUser user) {
        return withOffers(redemptions.findByUserIdOrderByIssuedAtDesc(user.id()));
    }

    /** Validarea la casă: 404 cod necunoscut, 409 al altei firme / deja folosit / anulat; ADMIN poate valida orice cod. */
    @Transactional
    public RedemptionView use(CurrentUser user, String rawCode) {
        String code = rawCode.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
        OfferRedemption r = redemptions.findByCode(code).orElseThrow(() -> new NotFoundException(UNKNOWN));
        Offer o = offers.findById(r.getOfferId()).orElseThrow(() -> new NotFoundException(UNKNOWN));
        if (!user.isAdmin()) {
            Business mine = businesses.findByOwnerUserId(user.id()).orElseThrow(() -> new NotFoundException(OfferService.NO_BUSINESS));
            if (!Objects.equals(o.getBusinessId(), mine.getId())) throw new ConflictException(OTHER_BUSINESS);
        }
        if (r.getStatus() == RedemptionStatus.USED) throw new ConflictException(ALREADY_USED);
        if (r.getStatus() == RedemptionStatus.CANCELLED) throw new ConflictException(CANCELLED);
        if (redemptions.markUsed(r.getId(), OffsetDateTime.now(), user.id()) == 0) {
            throw new ConflictException(ALREADY_USED);   // altă scanare a câștigat între timp
        }
        offers.incrementUsed(o.getId());
        log.info("Redemption {} used for offer {} by user {}", r.getId(), o.getId(), user.id());
        OfferRedemption used = redemptions.findById(r.getId()).orElseThrow();
        return new RedemptionView(used, o, businesses.findById(o.getBusinessId()).orElse(null));
    }

    /** Codurile firmei mele (ultimele 100), opțional pe stare. */
    public List<RedemptionView> businessRedemptions(CurrentUser user, RedemptionStatus status) {
        Business b = businesses.findByOwnerUserId(user.id()).orElseThrow(() -> new NotFoundException(OfferService.NO_BUSINESS));
        return withOffers(redemptions.findByBusiness(b.getId(), status, PageRequest.of(0, 100)));
    }

    private List<RedemptionView> withOffers(List<OfferRedemption> list) {
        Map<Long, Offer> byOffer = offers.findAllById(list.stream().map(OfferRedemption::getOfferId).distinct().toList()).stream()
                .collect(Collectors.toMap(Offer::getId, Function.identity()));
        Map<Long, Business> byBusiness = businesses.findAllById(byOffer.values().stream().map(Offer::getBusinessId).distinct().toList())
                .stream().collect(Collectors.toMap(Business::getId, Function.identity()));
        return list.stream().map(r -> {
            Offer o = byOffer.get(r.getOfferId());
            return new RedemptionView(r, o, o == null ? null : byBusiness.get(o.getBusinessId()));
        }).toList();
    }
}
