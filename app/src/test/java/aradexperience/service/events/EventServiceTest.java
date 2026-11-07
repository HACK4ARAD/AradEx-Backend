package aradexperience.service.events;

import aradexperience.dto.events.EventCreate;
import aradexperience.entity.events.Event;
import aradexperience.entity.events.SavedEvent;
import aradexperience.enums.EventCategory;
import aradexperience.enums.ContentStatus;
import aradexperience.enums.OrganizerType;
import aradexperience.enums.Role;
import aradexperience.enums.TicketType;
import aradexperience.exceptions.BadRequestException;
import aradexperience.exceptions.ForbiddenException;
import aradexperience.exceptions.NotFoundException;
import aradexperience.mock.EventMock;
import aradexperience.repository.events.EventRepository;
import aradexperience.repository.events.SavedEventRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** T06 · Verificare: salvare / ștergere idempotente, vizibilitatea ciornelor, regulile de la creare. */
class EventServiceTest {

    EventRepository events = mock(EventRepository.class);
    SavedEventRepository saved = mock(SavedEventRepository.class);
    EventService service = new EventService(events, saved, EventMock.PROPS);

    private Event concert() {
        return EventMock.agenda().get(0);
    }

    @Test
    void save_isIdempotent() {
        Event e = concert();
        when(events.findById(1L)).thenReturn(Optional.of(e));
        when(saved.existsByUserIdAndEventId(7L, 1L)).thenReturn(false, true);

        service.save(1L, EventMock.user(7, Role.LOCAL));
        service.save(1L, EventMock.user(7, Role.LOCAL));

        assertThat(e.getSavesCount()).isEqualTo(1);
        verify(saved).saveAndFlush(any(SavedEvent.class));
    }

    @Test
    void unsave_isIdempotent() {
        Event e = concert();
        e.setSavesCount(3);
        when(events.findById(1L)).thenReturn(Optional.of(e));
        when(saved.deleteOne(7L, 1L)).thenReturn(1, 0);

        service.unsave(1L, EventMock.user(7, Role.LOCAL));
        service.unsave(1L, EventMock.user(7, Role.LOCAL));

        assertThat(e.getSavesCount()).isEqualTo(2);
    }

    @Test
    void save_draftEvent_is404() {
        Event e = concert();
        e.setStatus(ContentStatus.DRAFT);
        when(events.findById(1L)).thenReturn(Optional.of(e));
        assertThatThrownBy(() -> service.save(1L, EventMock.user(7, Role.LOCAL))).isInstanceOf(NotFoundException.class);
        verify(saved, never()).saveAndFlush(any());
    }

    @Test
    void get_draft_hiddenFromVisitors_visibleToCityHall() {
        Event e = concert();
        e.setStatus(ContentStatus.DRAFT);
        when(events.findById(1L)).thenReturn(Optional.of(e));
        assertThatThrownBy(() -> service.get(1L, null, Optional.empty())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.get(1L, null, Optional.of(EventMock.user(7, Role.LOCAL)))).isInstanceOf(NotFoundException.class);
        assertThat(service.get(1L, null, Optional.of(EventMock.user(5, Role.CITY_HALL))).event()).isSameAs(e);
    }

    @Test
    void get_hotAndSaved() {
        when(events.findById(1L)).thenReturn(Optional.of(concert()));
        when(saved.existsByUserIdAndEventId(7L, 1L)).thenReturn(true);
        EventView v = service.get(1L, "46.1866,21.3123", Optional.of(EventMock.user(7, Role.LOCAL)));
        assertThat(v.hot()).isTrue();
        assertThat(v.saved()).isTrue();
        assertThat(v.distanceKm()).isBetween(1.0, 2.0);
    }

    private static EventCreate request(TicketType type, BigDecimal adult, BigDecimal child, Long placeId) {
        return new EventCreate("Concert de muzică de cameră", "Program: Mozart.", EventCategory.CONCERT, placeId,
                placeId == null ? "Palatul Cultural" : null, null, placeId == null ? 46.1757 : null, placeId == null ? 21.3171 : null,
                OffsetDateTime.parse("2026-11-20T19:00:00+02:00"), null, type, adult, child, null, 100, null, null, null);
    }

    @Test
    void create_byCityHall_isOrganizedByCity_withPriceFrom() {
        when(events.save(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));
        EventView v = service.create(request(TicketType.IN_APP, new BigDecimal("40"), new BigDecimal("25"), null), EventMock.user(5, Role.CITY_HALL));
        assertThat(v.event().getOrganizerType()).isEqualTo(OrganizerType.CITY);
        assertThat(v.event().getOrganizerName()).isEqualTo("Primăria Arad");
        assertThat(v.event().getPriceFrom()).isEqualByComparingTo("25");
        assertThat(v.event().getStatus()).isEqualTo(ContentStatus.PUBLISHED);
        assertThat(v.event().getCreatedBy()).isEqualTo(5L);
    }

    @Test
    void create_fromPlace_copiesLocationAndImage() {
        when(events.save(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));
        when(events.findPlace(2L)).thenReturn(Optional.of(new EventRepository.PlaceRef() {
            public String getName() { return "Palatul Cultural"; }
            public String getAddress() { return "Piața George Enescu 1"; }
            public Double getLat() { return 46.1757; }
            public Double getLng() { return 21.3171; }
            public String getImageUrl() { return "/img/loc-pc.jpg"; }
            public String getImageCredit() { return "Foto: Roland Meier"; }
        }));
        EventView v = service.create(request(TicketType.FREE, null, null, 2L), EventMock.user(5, Role.CITY_HALL));
        assertThat(v.event().getLocationName()).isEqualTo("Palatul Cultural");
        assertThat(v.event().getImageUrl()).isEqualTo("/img/loc-pc.jpg");
        assertThat(v.imageCredit()).isEqualTo("Foto: Roland Meier");
        assertThat(v.event().getPriceFrom()).isNull();
    }

    @Test
    void create_inAppWithoutPrice_is400() {
        assertThatThrownBy(() -> service.create(request(TicketType.IN_APP, null, null, null), EventMock.user(5, Role.CITY_HALL)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void update_byOtherUser_is403_byAdmin_ok() {
        Event e = EventMock.agenda().get(1);
        e.setCreatedBy(5L);
        when(events.findById(1L)).thenReturn(Optional.of(e));
        EventCreate req = request(TicketType.FREE, null, null, null);
        assertThatThrownBy(() -> service.update(1L, req, EventMock.user(6, Role.CITY_HALL))).isInstanceOf(ForbiddenException.class);
        assertThat(service.update(1L, req, EventMock.user(1, Role.ADMIN)).event().getTitle()).isEqualTo("Concert de muzică de cameră");
    }

    @Test
    void update_capacityBelowTicketsSold_is400() {
        Event e = concert(); // 140 de participanți
        e.setCreatedBy(5L);
        when(events.findById(1L)).thenReturn(Optional.of(e));
        assertThatThrownBy(() -> service.update(1L, request(TicketType.FREE, null, null, null), EventMock.user(5, Role.CITY_HALL)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void delete_hidesEvent() {
        Event e = concert();
        e.setCreatedBy(5L);
        when(events.findById(1L)).thenReturn(Optional.of(e));
        service.delete(1L, EventMock.user(5, Role.CITY_HALL));
        assertThat(e.getStatus()).isEqualTo(ContentStatus.HIDDEN);
    }

    @Test
    void updateForOrganizer_otherBusiness_is403() {
        Event e = concert();
        e.setOrganizerBusinessId(3L);
        when(events.findById(1L)).thenReturn(Optional.of(e));
        assertThatThrownBy(() -> service.updateForOrganizer(1L, request(TicketType.FREE, null, null, null), 4L)).isInstanceOf(ForbiddenException.class);
    }
}
