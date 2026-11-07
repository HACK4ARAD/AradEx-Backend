package aradexperience.controller.offers;

import aradexperience.api.OffersApi;
import aradexperience.dto.offers.OfferCreate;
import aradexperience.dto.offers.OfferDto;
import aradexperience.dto.offers.OfferSummary;
import aradexperience.dto.offers.RedemptionDto;
import aradexperience.dto.offers.UseCodeRequest;
import aradexperience.enums.OfferCategory;
import aradexperience.enums.RedemptionStatus;
import aradexperience.security.AuthContext;
import aradexperience.service.offers.OfferRedemptionService;
import aradexperience.service.offers.OfferRedemptionService.RedemptionView;
import aradexperience.service.offers.OfferService;
import aradexperience.service.offers.OfferService.OfferView;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static aradexperience.mapper.offers.OfferMapper.toDto;
import static aradexperience.mapper.offers.OfferMapper.toRedemptionDto;
import static aradexperience.mapper.offers.OfferMapper.toSummary;

@RestController
@RequiredArgsConstructor
public class OffersController implements OffersApi {

    private final OfferService offers;
    private final OfferRedemptionService redemptions;

    @Override
    public List<OfferSummary> list(OfferCategory category, Boolean localProduct, Boolean points, String near, String q) {
        return offers.list(new OfferService.Filter(category, localProduct, points, near, q)).stream()
                .map(v -> toSummary(v.offer(), name(v), v.distanceKm())).toList();
    }

    @Override
    public OfferDto get(long id) {
        OfferView v = offers.get(id, AuthContext.current().orElse(null));
        return dto(v);
    }

    @Override
    public RedemptionDto redeem(long id) {
        return redemption(redemptions.redeem(id, AuthContext.require()));
    }

    @Override
    public List<RedemptionDto> myRedemptions() {
        return redemptions.mine(AuthContext.require()).stream().map(OffersController::redemption).toList();
    }

    @Override
    public List<OfferDto> businessOffers() {
        return offers.businessOffers(AuthContext.require()).stream().map(OffersController::dto).toList();
    }

    @Override
    public OfferDto createOffer(OfferCreate request) {
        return dto(offers.create(AuthContext.require(), request));
    }

    @Override
    public OfferDto updateOffer(long id, OfferCreate request) {
        return dto(offers.update(AuthContext.require(), id, request));
    }

    @Override
    public void deleteOffer(long id) {
        offers.delete(AuthContext.require(), id);
    }

    @Override
    public RedemptionDto useCode(UseCodeRequest request) {
        return redemption(redemptions.use(AuthContext.require(), request.code()));
    }

    @Override
    public List<RedemptionDto> businessRedemptions(RedemptionStatus status) {
        return redemptions.businessRedemptions(AuthContext.require(), status).stream().map(OffersController::redemption).toList();
    }

    private static OfferDto dto(OfferView v) {
        String business = name(v);
        return toDto(v.offer(), business, v.distanceKm(),
                v.mine() == null ? null : toRedemptionDto(v.mine(), v.offer().getTitle(), business));
    }

    private static RedemptionDto redemption(RedemptionView v) {
        return toRedemptionDto(v.redemption(), v.offer() == null ? null : v.offer().getTitle(),
                v.business() == null ? null : v.business().getName());
    }

    private static String name(OfferView v) {
        return v.business() == null ? null : v.business().getName();
    }
}
