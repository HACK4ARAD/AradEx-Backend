package aradexperience.service.offers;

import aradexperience.entity.business.Business;
import aradexperience.entity.offers.Offer;
import aradexperience.enums.ContentStatus;
import aradexperience.enums.OfferCategory;
import aradexperience.exceptions.BadRequestException;
import aradexperience.exceptions.ForbiddenException;
import aradexperience.exceptions.NotFoundException;
import aradexperience.mock.BusinessMock;
import aradexperience.mock.OfferMock;
import aradexperience.repository.business.BusinessRepository;
import aradexperience.repository.offers.OfferRedemptionRepository;
import aradexperience.repository.offers.OfferRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Ofertele (T10): filtrele publice, crearea (DRAFT la firma neverificată), proprietarul (403), ștergerea care păstrează istoricul. */
@ExtendWith(MockitoExtension.class)
class OfferServiceTest {

    @Mock OfferRepository offers;
    @Mock OfferRedemptionRepository redemptions;
    @Mock BusinessRepository businesses;
    @InjectMocks OfferService service;

    private void available(List<Offer> list, List<Business> owners) {
        when(offers.findAvailable(any())).thenReturn(list);
        when(businesses.findAllById(anyList())).thenReturn(owners);
    }

    @Test
    void list_filtersByCategoryPointsAndLocal() {
        available(List.of(OfferMock.offer(1, 1, OfferCategory.CAFE, 30, false), OfferMock.offer(5, 3, OfferCategory.EVENT, null, true),
                OfferMock.offer(6, 3, OfferCategory.LOCAL, null, true)), List.of(BusinessMock.pasaj(), OfferMock.crama()));

        assertThat(service.list(new OfferService.Filter(OfferCategory.CAFE, null, null, null, null))).extracting(v -> v.offer().getId()).containsExactly(1L);
        assertThat(service.list(new OfferService.Filter(null, null, true, null, null))).extracting(v -> v.offer().getId()).containsExactly(1L);
        assertThat(service.list(new OfferService.Filter(null, true, null, null, null))).extracting(v -> v.offer().getId()).containsExactly(5L, 6L);
        assertThat(service.list(new OfferService.Filter(null, null, null, null, "crama"))).extracting(v -> v.offer().getId()).containsExactly(5L, 6L);
    }

    @Test
    void list_near_sortsByDistanceAndFillsIt() {
        available(List.of(OfferMock.offer(5, 3, OfferCategory.EVENT, null, true), OfferMock.offer(1, 1, OfferCategory.CAFE, null, false)),
                List.of(BusinessMock.pasaj(), OfferMock.crama()));

        List<OfferService.OfferView> out = service.list(new OfferService.Filter(null, null, null, "46.1730,21.3148", null));

        assertThat(out).extracting(v -> v.offer().getId()).containsExactly(1L, 5L);
        assertThat(out.get(0).distanceKm()).isEqualTo(0.0);
        assertThat(out.get(1).distanceKm()).isGreaterThan(20);
    }

    @Test
    void list_badNear_is400() {
        assertThatThrownBy(() -> service.list(new OfferService.Filter(null, null, null, "arad", null))).isInstanceOf(BadRequestException.class);
    }

    @Test
    void get_draft_is404_published_countsAView() {
        Offer draft = OfferMock.coffee();
        draft.setStatus(ContentStatus.DRAFT);
        when(offers.findById(1L)).thenReturn(Optional.of(draft));
        assertThatThrownBy(() -> service.get(1L, null)).isInstanceOf(NotFoundException.class);
        verify(offers, never()).incrementViews(1L);

        when(offers.findById(1L)).thenReturn(Optional.of(OfferMock.coffee()));
        when(redemptions.findFirstByOfferIdAndUserIdOrderByIssuedAtDesc(1L, 2L)).thenReturn(Optional.of(OfferMock.issued(9L, "AX-O1-K8D2QF")));
        OfferService.OfferView v = service.get(1L, OfferMock.local());
        verify(offers).incrementViews(1L);
        assertThat(v.mine().getCode()).isEqualTo("AX-O1-K8D2QF");
    }

    @Test
    void create_verified_isPublished_withCategoryFromBusiness_unverified_isDraft() {
        when(offers.save(any(Offer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(businesses.findByOwnerUserId(BusinessMock.OWNER_ID)).thenReturn(Optional.of(BusinessMock.pasaj()));

        Offer o = service.create(BusinessMock.owner(), OfferMock.create(null, null, null)).offer();
        assertThat(o.getStatus()).isEqualTo(ContentStatus.PUBLISHED);
        assertThat(o.getCategory()).isEqualTo(OfferCategory.CAFE);
        assertThat(o.getValidFrom()).isEqualTo(OfferMock.TODAY);
        assertThat(o.getValidUntil()).as("permanentă").isNull();
        assertThat(o.getTitle()).isEqualTo("Meniu de Crăciun -15%");

        when(businesses.findByOwnerUserId(BusinessMock.OWNER_ID)).thenReturn(Optional.of(BusinessMock.unverified()));
        assertThat(service.create(BusinessMock.owner(), OfferMock.create(null, null, ContentStatus.PUBLISHED)).offer().getStatus())
                .isEqualTo(ContentStatus.DRAFT);
    }

    @Test
    void create_untilBeforeFrom_is400_hiddenStatus_is400() {
        when(businesses.findByOwnerUserId(BusinessMock.OWNER_ID)).thenReturn(Optional.of(BusinessMock.pasaj()));
        assertThatThrownBy(() -> service.create(BusinessMock.owner(), OfferMock.create(OfferMock.TODAY, OfferMock.TODAY.minusDays(1), null)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.create(BusinessMock.owner(), OfferMock.create(null, null, ContentStatus.HIDDEN)))
                .isInstanceOf(BadRequestException.class);
        verify(offers, never()).save(any());
    }

    @Test
    void update_pause_setsDraft_otherBusiness_is403() {
        when(offers.findById(1L)).thenReturn(Optional.of(OfferMock.coffee()));
        when(businesses.findByOwnerUserId(BusinessMock.OWNER_ID)).thenReturn(Optional.of(BusinessMock.pasaj()));
        when(businesses.findById(1L)).thenReturn(Optional.of(BusinessMock.pasaj()));
        assertThat(service.update(BusinessMock.owner(), 1L, OfferMock.create(null, null, ContentStatus.DRAFT)).offer().getStatus())
                .isEqualTo(ContentStatus.DRAFT);

        when(businesses.findByOwnerUserId(BusinessMock.OTHER_OWNER_ID)).thenReturn(Optional.of(BusinessMock.crama()));
        assertThatThrownBy(() -> service.update(BusinessMock.otherOwner(), 1L, OfferMock.create(null, null, null)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void delete_withCodes_hidesIt_withoutCodes_deletesIt() {
        Offer withCodes = OfferMock.coffee();
        when(offers.findById(1L)).thenReturn(Optional.of(withCodes));
        when(businesses.findByOwnerUserId(BusinessMock.OWNER_ID)).thenReturn(Optional.of(BusinessMock.pasaj()));
        when(redemptions.existsByOfferId(1L)).thenReturn(true);
        service.delete(BusinessMock.owner(), 1L);
        assertThat(withCodes.getStatus()).isEqualTo(ContentStatus.HIDDEN);
        verify(offers, never()).delete(any());

        Offer fresh = OfferMock.offer(2, 1, OfferCategory.CAFE, null, false);
        when(offers.findById(2L)).thenReturn(Optional.of(fresh));
        when(redemptions.existsByOfferId(2L)).thenReturn(false);
        service.delete(BusinessMock.owner(), 2L);
        verify(offers).delete(fresh);
    }
}
