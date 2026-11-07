package aradexperience.mapper.offers;

import aradexperience.dto.offers.OfferCreate;
import aradexperience.dto.offers.OfferDto;
import aradexperience.dto.offers.OfferSummary;
import aradexperience.dto.offers.RedemptionDto;
import aradexperience.entity.offers.Offer;
import aradexperience.entity.offers.OfferRedemption;
import lombok.experimental.UtilityClass;

/** Offer / OfferRedemption ↔ DTO-urile din `dto/offers`. Numele firmei și distanța vin ca parametri (le știe service-ul). */
@UtilityClass
public class OfferMapper {

    public static OfferSummary toSummary(Offer o, String businessName, Double distanceKm) {
        return new OfferSummary(o.getId(), o.getBusinessId(), businessName, o.getTitle(), o.getDiscountLabel(), o.isLocalProduct(),
                o.getValidUntil(), o.getPointsPrice(), o.getImageUrl(), distanceKm, null, o.getCategory());
    }

    public static OfferDto toDto(Offer o, String businessName, Double distanceKm, RedemptionDto mine) {
        return new OfferDto(o.getId(), o.getBusinessId(), businessName, o.getTitle(), o.getDiscountLabel(), o.isLocalProduct(),
                o.getValidUntil(), o.getPointsPrice(), o.getImageUrl(), distanceKm, null, o.getCategory(), o.getDescription(),
                o.getValidFrom(), o.getStatus(), o.getViewsCount(), o.getRedemptionsCount(), o.getUsedCount(), mine);
    }

    public static RedemptionDto toRedemptionDto(OfferRedemption r, String offerTitle, String businessName) {
        return new RedemptionDto(r.getId(), r.getCode(), r.getOfferId(), offerTitle, businessName, r.getPointsSpent(), r.getStatus(),
                r.getIssuedAt(), r.getUsedAt());
    }

    /** Câmpurile editabile; categoria implicită, `validFrom` implicit și starea le stabilește service-ul. */
    public static void apply(OfferCreate req, Offer o) {
        o.setTitle(req.title().trim());
        o.setDescription(req.description().trim());
        o.setDiscountLabel(req.discountLabel().trim());
        o.setLocalProduct(req.localProduct());
        o.setValidUntil(req.validUntil());
        o.setPointsPrice(req.pointsPrice());
        o.setImageUrl(req.imageUrl() == null || req.imageUrl().isBlank() ? null : req.imageUrl().trim());
    }
}
