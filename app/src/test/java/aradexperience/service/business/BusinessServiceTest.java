package aradexperience.service.business;

import aradexperience.dto.business.BusinessUpdate;
import aradexperience.dto.events.EventCreate;
import aradexperience.entity.business.Announcement;
import aradexperience.entity.business.Business;
import aradexperience.entity.events.Event;
import aradexperience.enums.BusinessCategory;
import aradexperience.enums.ContentStatus;
import aradexperience.enums.EventCategory;
import aradexperience.enums.OrganizerType;
import aradexperience.enums.TicketType;
import aradexperience.exceptions.BadRequestException;
import aradexperience.exceptions.ForbiddenException;
import aradexperience.exceptions.NotFoundException;
import aradexperience.mock.BusinessMock;
import aradexperience.repository.business.AnnouncementRepository;
import aradexperience.repository.business.BusinessRepository;
import aradexperience.repository.events.EventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static aradexperience.mock.BusinessMock.OTHER_OWNER_ID;
import static aradexperience.mock.BusinessMock.OWNER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Regulile din PLAN.md T09 „Verificare”: firma creează evenimente (PUBLISHED / DRAFT), nu atinge conținutul altei firme (403). */
@ExtendWith(MockitoExtension.class)
class BusinessServiceTest {

    @Mock BusinessRepository businesses;
    @Mock EventRepository events;
    @Mock AnnouncementRepository announcements;
    @Mock aradexperience.repository.offers.OfferRepository offers;
    @Mock aradexperience.repository.offers.OfferRedemptionRepository redemptions;
    @Mock aradexperience.repository.points.PointsTransactionRepository pointsTransactions;
    @InjectMocks BusinessService service;

    private void ownerHas(Business b) {
        when(businesses.findByOwnerUserId(b.getOwnerUserId())).thenReturn(Optional.of(b));
    }

    private void savesReturnArgument() {
        when(events.save(any(Event.class))).thenAnswer(inv -> { Event e = inv.getArgument(0); e.setId(31L); return e; });
    }

    // --- evenimente -----------------------------------------------------------------------------------------------

    @Test
    void createEvent_verifiedBusiness_isPublishedAtTheBusinessAddress() {
        ownerHas(BusinessMock.pasaj());
        savesReturnArgument();

        Event e = service.createEvent(BusinessMock.owner(), BusinessMock.inAppEvent());

        assertThat(e.getStatus()).isEqualTo(ContentStatus.PUBLISHED);
        assertThat(e.getOrganizerType()).isEqualTo(OrganizerType.BUSINESS);
        assertThat(e.getOrganizerBusinessId()).isEqualTo(1L);
        assertThat(e.getOrganizerName()).isEqualTo("Cafeneaua din Pasaj");
        assertThat(e.getLocationName()).isEqualTo("Cafeneaua din Pasaj");
        assertThat(e.getAddress()).isEqualTo("Str. Vasile Alecsandri 1, Arad");
        assertThat(e.getLat()).isEqualTo(46.1730);
        assertThat(e.getPriceFrom()).isEqualByComparingTo("40");
        assertThat(e.getCreatedBy()).isEqualTo(OWNER_ID);
        assertThat(e.getCategory()).isEqualTo(EventCategory.FOOD);
    }

    @Test
    void createEvent_unverifiedBusiness_staysDraft() {
        ownerHas(BusinessMock.unverified());
        savesReturnArgument();

        assertThat(service.createEvent(BusinessMock.owner(), BusinessMock.inAppEvent()).getStatus()).isEqualTo(ContentStatus.DRAFT);
    }

    @Test
    void createEvent_withOwnLocation_usesIt() {
        ownerHas(BusinessMock.pasaj());
        savesReturnArgument();
        var req = new EventCreate("Cros", "Alergare", EventCategory.SPORT, null, "Faleza Mureșului", null, 46.1745, 21.3215,
                BusinessMock.START, null, TicketType.FREE, new BigDecimal("10"), null, null, null, null, null, null);

        Event e = service.createEvent(BusinessMock.owner(), req);

        assertThat(e.getLocationName()).isEqualTo("Faleza Mureșului");
        assertThat(e.getLng()).isEqualTo(21.3215);
        assertThat(e.getPriceFrom()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(e.getPriceAdult()).as("FREE fără prețuri").isNull();
    }

    @Test
    void createEvent_endBeforeStart_is400() {
        ownerHas(BusinessMock.pasaj());
        var req = BusinessMock.eventCreate(TicketType.FREE, null, BusinessMock.START.minusHours(1));

        assertThatThrownBy(() -> service.createEvent(BusinessMock.owner(), req)).isInstanceOf(BadRequestException.class);
        verify(events, never()).save(any());
    }

    @Test
    void createEvent_inAppWithoutPrice_is400_externalWithoutUrl_is400() {
        ownerHas(BusinessMock.pasaj());

        assertThatThrownBy(() -> service.createEvent(BusinessMock.owner(), BusinessMock.eventCreate(TicketType.IN_APP, null, null)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("priceAdult");
        assertThatThrownBy(() -> service.createEvent(BusinessMock.owner(), BusinessMock.eventCreate(TicketType.EXTERNAL, null, null)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("externalUrl");
    }

    @Test
    void createEvent_accountWithoutBusiness_is404() {
        when(businesses.findByOwnerUserId(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createEvent(BusinessMock.admin(), BusinessMock.inAppEvent())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateEvent_ofAnotherBusiness_is403() {
        when(events.findById(24L)).thenReturn(Optional.of(BusinessMock.event(24L, 1L)));
        ownerHas(BusinessMock.crama());

        assertThatThrownBy(() -> service.updateEvent(BusinessMock.otherOwner(), 24L, BusinessMock.inAppEvent()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void updateEvent_own_appliesChanges_andKeepsLocationAndStatus() {
        Event existing = BusinessMock.event(24L, 1L);
        when(events.findById(24L)).thenReturn(Optional.of(existing));
        ownerHas(BusinessMock.pasaj());

        Event e = service.updateEvent(BusinessMock.owner(), 24L, BusinessMock.inAppEvent());

        assertThat(e.getTitle()).isEqualTo("Atelier latte art");
        assertThat(e.getTicketType()).isEqualTo(TicketType.IN_APP);
        assertThat(e.getLocationName()).isEqualTo("Cafeneaua din Pasaj");
        assertThat(e.getStatus()).isEqualTo(ContentStatus.PUBLISHED);
    }

    @Test
    void updateEvent_admin_canEditAnyEvent() {
        when(events.findById(24L)).thenReturn(Optional.of(BusinessMock.event(24L, 1L)));

        assertThat(service.updateEvent(BusinessMock.admin(), 24L, BusinessMock.inAppEvent()).getTitle()).isEqualTo("Atelier latte art");
        verify(businesses, never()).findByOwnerUserId(any());
    }

    @Test
    void updateEvent_cityEvent_isForbiddenForABusiness() {
        Event city = BusinessMock.event(1L, 1L);
        city.setOrganizerBusinessId(null);
        city.setOrganizerType(OrganizerType.CITY);
        when(events.findById(1L)).thenReturn(Optional.of(city));
        ownerHas(BusinessMock.pasaj());

        assertThatThrownBy(() -> service.updateEvent(BusinessMock.owner(), 1L, BusinessMock.inAppEvent())).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void deleteEvent_unknown_is404_otherBusiness_is403_own_deletes() {
        when(events.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.deleteEvent(BusinessMock.owner(), 99L)).isInstanceOf(NotFoundException.class);

        Event e = BusinessMock.event(24L, 1L);
        when(events.findById(24L)).thenReturn(Optional.of(e));
        ownerHas(BusinessMock.crama());
        assertThatThrownBy(() -> service.deleteEvent(BusinessMock.otherOwner(), 24L)).isInstanceOf(ForbiddenException.class);
        verify(events, never()).delete(any());

        ownerHas(BusinessMock.pasaj());
        service.deleteEvent(BusinessMock.owner(), 24L);
        verify(events).delete(e);
    }

    // --- anunțuri -------------------------------------------------------------------------------------------------

    @Test
    void createAnnouncement_verified_isPublished_unverified_isDraft() {
        when(announcements.save(any(Announcement.class))).thenAnswer(inv -> inv.getArgument(0));

        ownerHas(BusinessMock.pasaj());
        Announcement a = service.createAnnouncement(BusinessMock.owner(), BusinessMock.announcementCreate());
        assertThat(a.getStatus()).isEqualTo(ContentStatus.PUBLISHED);
        assertThat(a.getBusinessId()).isEqualTo(1L);
        assertThat(a.getCreatedAt()).isNotNull();

        ownerHas(BusinessMock.unverified());
        assertThat(service.createAnnouncement(BusinessMock.owner(), BusinessMock.announcementCreate()).getStatus())
                .isEqualTo(ContentStatus.DRAFT);
    }

    @Test
    void deleteAnnouncement_ofAnotherBusiness_is403() {
        when(announcements.findById(1L)).thenReturn(Optional.of(BusinessMock.announcement(1L, 1L)));
        when(businesses.findByOwnerUserId(OTHER_OWNER_ID)).thenReturn(Optional.of(BusinessMock.crama()));

        assertThatThrownBy(() -> service.deleteAnnouncement(BusinessMock.otherOwner(), 1L)).isInstanceOf(ForbiddenException.class);
        verify(announcements, never()).delete(any());
    }

    // --- pagina publică și panoul ---------------------------------------------------------------------------------

    @Test
    void page_countsAView_andReturnsOnlyPublishedContent() {
        when(businesses.incrementViews(1L)).thenReturn(1);
        when(businesses.findById(1L)).thenReturn(Optional.of(BusinessMock.pasaj()));
        when(events.findUpcomingByBusiness(eq(1L), eq(ContentStatus.PUBLISHED), any())).thenReturn(List.of(BusinessMock.event(24L, 1L)));
        when(announcements.findByBusinessIdAndStatusOrderByCreatedAtDesc(1L, ContentStatus.PUBLISHED))
                .thenReturn(List.of(BusinessMock.announcement(1L, 1L)));

        BusinessService.Page p = service.page(1L);

        verify(businesses).incrementViews(1L);
        assertThat(p.events()).hasSize(1);
        assertThat(p.announcements()).hasSize(1);
    }

    @Test
    void page_unknownBusiness_is404() {
        when(businesses.incrementViews(42L)).thenReturn(0);

        assertThatThrownBy(() -> service.page(42L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void dashboard_countsUpcomingEvents() {
        ownerHas(BusinessMock.pasaj());
        when(events.countUpcomingByBusiness(eq(1L), any())).thenReturn(2L);

        BusinessService.Dashboard d = service.dashboard(BusinessMock.owner());

        assertThat(d.upcomingEvents()).isEqualTo(2);
        assertThat(d.business().getViewsCount()).isEqualTo(1240);
    }

    @Test
    void updateMe_changesProfileButNotVerification() {
        Business b = BusinessMock.unverified();
        ownerHas(b);

        service.updateMe(BusinessMock.owner(), new BusinessUpdate(" Cafeneaua din Pasaj ", BusinessCategory.CAFE, "Jazz vinerea.",
                "Str. Vasile Alecsandri 1, Arad", 46.1731, 21.3149, " ", null, null));

        assertThat(b.getName()).isEqualTo("Cafeneaua din Pasaj");
        assertThat(b.getDescription()).isEqualTo("Jazz vinerea.");
        assertThat(b.getPhone()).as("gol → null").isNull();
        assertThat(b.isVerified()).isFalse();
    }

    @Test
    void activity_listsUsedCodesAndRedemptions_newestFirst() {
        ownerHas(BusinessMock.pasaj());
        when(redemptions.findByBusiness(eq(1L), org.mockito.ArgumentMatchers.isNull(), any()))
                .thenReturn(List.of(aradexperience.mock.OfferMock.used(1L, "AX-O1-B9Q2LW"), aradexperience.mock.OfferMock.issued(2L, "AX-O1-H4K9PD")));
        when(offers.findAllById(org.mockito.ArgumentMatchers.anyList())).thenReturn(List.of(aradexperience.mock.OfferMock.coffee()));

        var items = service.activity(BusinessMock.owner()).items();

        assertThat(items).extracting(i -> i.type()).containsExactly(aradexperience.enums.BusinessActivityType.CODE_USED,
                aradexperience.enums.BusinessActivityType.OFFER_REDEEMED);
        assertThat(items.get(0).text()).isEqualTo("Cod folosit: 20% reducere la orice cafea");
    }

    @Test
    void dashboard_codesUsed_isTheSumOfUsedCounts() {
        ownerHas(BusinessMock.pasaj());
        when(offers.sumUsedByBusiness(1L)).thenReturn(121L);

        assertThat(service.dashboard(BusinessMock.owner()).codesUsed()).isEqualTo(121);
    }
}
