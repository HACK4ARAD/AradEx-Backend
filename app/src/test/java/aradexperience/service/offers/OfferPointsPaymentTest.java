package aradexperience.service.offers;

import aradexperience.entity.offers.Offer;
import aradexperience.entity.offers.OfferRedemption;
import aradexperience.entity.points.PointsTransaction;
import aradexperience.enums.RedemptionStatus;
import aradexperience.exceptions.BadRequestException;
import aradexperience.exceptions.ConflictException;
import aradexperience.mock.BusinessMock;
import aradexperience.mock.OfferMock;
import aradexperience.repository.business.BusinessRepository;
import aradexperience.repository.offers.OfferRedemptionRepository;
import aradexperience.repository.offers.OfferRepository;
import aradexperience.service.points.PointsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T11 „plătește cu puncte” (PLAN.md T11, completează T10): `withPoints` debitează `pointsPrice` prin PointsService în aceeași
 * tranzacție cu codul; ofertele doar pe puncte se plătesc mereu cu puncte; sold insuficient → 409 și niciun cod numărat.
 */
@ExtendWith(MockitoExtension.class)
class OfferPointsPaymentTest {

    @Mock OfferRepository offers;
    @Mock OfferRedemptionRepository redemptions;
    @Mock BusinessRepository businesses;
    @Mock PointsService points;
    @InjectMocks OfferRedemptionService service;

    @BeforeEach
    void setUp() {
        lenient().when(redemptions.existsByOfferIdAndUserIdAndStatus(anyLong(), anyLong(), any())).thenReturn(false);
        lenient().when(redemptions.saveAndFlush(any(OfferRedemption.class))).thenAnswer(inv -> { OfferRedemption r = inv.getArgument(0); r.setId(9L); return r; });
        lenient().when(businesses.findById(1L)).thenReturn(Optional.of(BusinessMock.pasaj()));
    }

    @Test
    void withPoints_debitsThePointsPrice_andRecordsIt() {
        Offer coffee = OfferMock.coffee();                                     // „-20%”, 30 de puncte opțional
        when(offers.findById(1L)).thenReturn(Optional.of(coffee));

        OfferRedemption r = service.redeem(1L, OfferMock.local(), true).redemption();

        assertThat(r.getPointsSpent()).isEqualTo(30);
        assertThat(r.getStatus()).isEqualTo(RedemptionStatus.ISSUED);
        verify(points).spend(2L, 30, PointsTransaction.REF_OFFER, 1L, coffee.getTitle());
        verify(offers).incrementRedemptions(1L);
    }

    @Test
    void optionalPointsOffer_withoutWithPoints_isFree() {
        when(offers.findById(1L)).thenReturn(Optional.of(OfferMock.coffee()));

        assertThat(service.redeem(1L, OfferMock.local(), false).redemption().getPointsSpent()).isZero();
        verify(points, never()).spend(anyLong(), anyInt(), any(), any(), any());
    }

    @Test
    void pointsOnlyOffer_isAlwaysPaidWithPoints() {
        Offer onlyPoints = OfferMock.coffee();
        onlyPoints.setDiscountLabel("40 p");
        onlyPoints.setPointsPrice(40);
        when(offers.findById(1L)).thenReturn(Optional.of(onlyPoints));

        assertThat(service.redeem(1L, OfferMock.local()).redemption().getPointsSpent()).isEqualTo(40);
        verify(points).spend(2L, 40, PointsTransaction.REF_OFFER, 1L, onlyPoints.getTitle());
    }

    @Test
    void insufficientPoints_is409_andTheCodeIsNotCounted() {
        when(offers.findById(1L)).thenReturn(Optional.of(OfferMock.coffee()));
        when(points.spend(anyLong(), anyInt(), any(), any(), any())).thenThrow(new ConflictException(PointsService.INSUFFICIENT_POINTS));

        assertThatThrownBy(() -> service.redeem(1L, OfferMock.local(), true))
                .isInstanceOf(ConflictException.class).hasMessage(PointsService.INSUFFICIENT_POINTS);
        verify(offers, never()).incrementRedemptions(anyLong());
    }

    @Test
    void withPoints_onAnOfferWithoutPointsPrice_is400() {
        Offer free = OfferMock.coffee();
        free.setPointsPrice(null);
        when(offers.findById(1L)).thenReturn(Optional.of(free));

        assertThatThrownBy(() -> service.redeem(1L, OfferMock.local(), true)).isInstanceOf(BadRequestException.class);
        verify(redemptions, never()).saveAndFlush(any());
    }

    @Test
    void pointsOnly_matchesTheFrontendLabelRule() {
        Offer o = OfferMock.coffee();
        o.setDiscountLabel(" 30 P ");
        assertThat(OfferRedemptionService.pointsOnly(o)).isTrue();
        o.setDiscountLabel("-20%");
        assertThat(OfferRedemptionService.pointsOnly(o)).isFalse();
    }
}
