package aradexperience.service.offers;

import aradexperience.entity.offers.Offer;
import aradexperience.entity.offers.OfferRedemption;
import aradexperience.enums.ContentStatus;
import aradexperience.enums.RedemptionStatus;
import aradexperience.exceptions.ConflictException;
import aradexperience.exceptions.NotFoundException;
import aradexperience.mock.BusinessMock;
import aradexperience.mock.OfferMock;
import aradexperience.repository.business.BusinessRepository;
import aradexperience.repository.offers.OfferRedemptionRepository;
import aradexperience.repository.offers.OfferRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PLAN.md T10 „Verificare”: revendic → cod → firma îl validează → `used_count` (= `codesUsed`) crește; a doua validare dă 409.
 * Plus: un singur cod activ per ofertă, codul altei firme → 409, cod necunoscut → 404.
 */
@ExtendWith(MockitoExtension.class)
class OfferRedemptionServiceTest {

    @Mock OfferRepository offers;
    @Mock OfferRedemptionRepository redemptions;
    @Mock BusinessRepository businesses;
    @Mock aradexperience.service.points.PointsService points;
    @InjectMocks OfferRedemptionService service;

    @Test
    void redeem_issuesACodeForTheOffer_andCountsIt() {
        when(offers.findById(1L)).thenReturn(Optional.of(OfferMock.coffee()));
        when(redemptions.existsByOfferIdAndUserIdAndStatus(1L, 2L, RedemptionStatus.ISSUED)).thenReturn(false);
        when(redemptions.saveAndFlush(any(OfferRedemption.class))).thenAnswer(inv -> { OfferRedemption r = inv.getArgument(0); r.setId(9L); return r; });
        when(businesses.findById(1L)).thenReturn(Optional.of(BusinessMock.pasaj()));

        OfferRedemptionService.RedemptionView v = service.redeem(1L, OfferMock.local());

        assertThat(v.redemption().getCode()).matches("^AX-O1-[A-HJ-NP-Z2-9]{6}$");
        assertThat(v.redemption().getStatus()).isEqualTo(RedemptionStatus.ISSUED);
        assertThat(v.redemption().getUserId()).isEqualTo(2L);
        assertThat(v.redemption().getPointsSpent()).as("fără withPoints, oferta normală se revendică gratuit").isZero();
        assertThat(v.business().getName()).isEqualTo("Cafeneaua din Pasaj");
        verify(offers).incrementRedemptions(1L);
    }

    @Test
    void redeem_secondActiveCode_is409() {
        when(offers.findById(1L)).thenReturn(Optional.of(OfferMock.coffee()));
        when(redemptions.existsByOfferIdAndUserIdAndStatus(1L, 2L, RedemptionStatus.ISSUED)).thenReturn(true);

        assertThatThrownBy(() -> service.redeem(1L, OfferMock.local()))
                .isInstanceOf(ConflictException.class).hasMessage(OfferRedemptionService.ALREADY_ACTIVE);
        verify(redemptions, never()).saveAndFlush(any());
    }

    @Test
    void redeem_raceOnTheUniqueIndex_is409NotA500() {
        when(offers.findById(1L)).thenReturn(Optional.of(OfferMock.coffee()));
        when(redemptions.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq_redemptions_active"));

        assertThatThrownBy(() -> service.redeem(1L, OfferMock.local())).isInstanceOf(ConflictException.class);
        verify(offers, never()).incrementRedemptions(anyLong());
    }

    @Test
    void redeem_draftOrUnknownOffer_is404_expiredOffer_is409() {
        Offer draft = OfferMock.coffee();
        draft.setStatus(ContentStatus.DRAFT);
        when(offers.findById(1L)).thenReturn(Optional.of(draft));
        assertThatThrownBy(() -> service.redeem(1L, OfferMock.local())).isInstanceOf(NotFoundException.class);

        Offer expired = OfferMock.coffee();
        expired.setValidUntil(OfferMock.TODAY.minusDays(1));
        expired.setValidFrom(OfferMock.TODAY.minusDays(10));
        when(offers.findById(1L)).thenReturn(Optional.of(expired));
        assertThatThrownBy(() -> service.redeem(1L, OfferMock.local()))
                .isInstanceOf(ConflictException.class).hasMessage(OfferRedemptionService.NOT_AVAILABLE);
    }

    @Test
    void use_byTheOwnerBusiness_marksUsed_andCountsIt() {
        OfferRedemption issued = OfferMock.issued(9L, "AX-O1-K8D2QF");
        when(redemptions.findByCode("AX-O1-K8D2QF")).thenReturn(Optional.of(issued));
        when(offers.findById(1L)).thenReturn(Optional.of(OfferMock.coffee()));
        when(businesses.findByOwnerUserId(BusinessMock.OWNER_ID)).thenReturn(Optional.of(BusinessMock.pasaj()));
        when(redemptions.markUsed(eq(9L), any(), eq(BusinessMock.OWNER_ID))).thenReturn(1);
        when(redemptions.findById(9L)).thenReturn(Optional.of(OfferMock.used(9L, "AX-O1-K8D2QF")));

        OfferRedemptionService.RedemptionView v = service.use(BusinessMock.owner(), " ax-o1-k8d2qf ");

        assertThat(v.redemption().getStatus()).isEqualTo(RedemptionStatus.USED);
        assertThat(v.redemption().getUsedAt()).isNotNull();
        verify(offers).incrementUsed(1L);
    }

    @Test
    void use_secondTime_is409() {
        when(redemptions.findByCode("AX-O1-K8D2QF")).thenReturn(Optional.of(OfferMock.used(9L, "AX-O1-K8D2QF")));
        when(offers.findById(1L)).thenReturn(Optional.of(OfferMock.coffee()));
        when(businesses.findByOwnerUserId(BusinessMock.OWNER_ID)).thenReturn(Optional.of(BusinessMock.pasaj()));

        assertThatThrownBy(() -> service.use(BusinessMock.owner(), "AX-O1-K8D2QF"))
                .isInstanceOf(ConflictException.class).hasMessage(OfferRedemptionService.ALREADY_USED);
        verify(offers, never()).incrementUsed(anyLong());
    }

    @Test
    void use_simultaneousScan_lostTheRace_is409() {
        when(redemptions.findByCode("AX-O1-K8D2QF")).thenReturn(Optional.of(OfferMock.issued(9L, "AX-O1-K8D2QF")));
        when(offers.findById(1L)).thenReturn(Optional.of(OfferMock.coffee()));
        when(businesses.findByOwnerUserId(BusinessMock.OWNER_ID)).thenReturn(Optional.of(BusinessMock.pasaj()));
        when(redemptions.markUsed(eq(9L), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.use(BusinessMock.owner(), "AX-O1-K8D2QF")).isInstanceOf(ConflictException.class);
        verify(offers, never()).incrementUsed(anyLong());
    }

    @Test
    void use_codeOfAnotherBusiness_is409_unknownCode_is404() {
        when(redemptions.findByCode("AX-O1-K8D2QF")).thenReturn(Optional.of(OfferMock.issued(9L, "AX-O1-K8D2QF")));
        when(offers.findById(1L)).thenReturn(Optional.of(OfferMock.coffee()));
        when(businesses.findByOwnerUserId(BusinessMock.OTHER_OWNER_ID)).thenReturn(Optional.of(BusinessMock.crama()));
        assertThatThrownBy(() -> service.use(BusinessMock.otherOwner(), "AX-O1-K8D2QF"))
                .isInstanceOf(ConflictException.class).hasMessage(OfferRedemptionService.OTHER_BUSINESS);

        when(redemptions.findByCode("AX-O1-ZZZZZZ")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.use(BusinessMock.owner(), "AX-O1-ZZZZZZ")).isInstanceOf(NotFoundException.class);
        verify(redemptions, never()).markUsed(anyLong(), any(), any());
    }

    @Test
    void redeemThenUse_endToEnd_countsOneUse() {
        // revendic
        when(offers.findById(1L)).thenReturn(Optional.of(OfferMock.coffee()));
        ArgumentCaptor<OfferRedemption> saved = ArgumentCaptor.forClass(OfferRedemption.class);
        when(redemptions.saveAndFlush(saved.capture())).thenAnswer(inv -> { OfferRedemption r = inv.getArgument(0); r.setId(9L); return r; });
        String code = service.redeem(1L, OfferMock.local()).redemption().getCode();

        // firma validează
        when(redemptions.findByCode(code)).thenReturn(Optional.of(saved.getValue()));
        when(businesses.findByOwnerUserId(BusinessMock.OWNER_ID)).thenReturn(Optional.of(BusinessMock.pasaj()));
        when(redemptions.markUsed(eq(9L), any(), any())).thenReturn(1);
        when(redemptions.findById(9L)).thenReturn(Optional.of(OfferMock.used(9L, code)));
        service.use(BusinessMock.owner(), code);
        verify(offers).incrementUsed(1L);

        // a doua validare
        when(redemptions.findByCode(code)).thenReturn(Optional.of(OfferMock.used(9L, code)));
        assertThatThrownBy(() -> service.use(BusinessMock.owner(), code)).isInstanceOf(ConflictException.class);
    }
}
