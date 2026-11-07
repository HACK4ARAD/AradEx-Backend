package aradexperience.service.events;

import aradexperience.enums.EventCategory;
import aradexperience.enums.ContentStatus;
import aradexperience.enums.Role;
import aradexperience.exceptions.BadRequestException;
import aradexperience.mock.EventMock;
import aradexperience.repository.events.EventRepository;
import aradexperience.repository.events.SavedEventRepository;
import aradexperience.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Filtrele din GET /events (T06 · Verificare): dată, categorie, popular, căutare fără diacritice, „lângă mine”. */
class EventQueryTest {

    EventRepository events = mock(EventRepository.class);
    SavedEventRepository saved = mock(SavedEventRepository.class);
    EventService service = new EventService(events, saved, EventMock.PROPS);

    @BeforeEach
    void setUp() {
        service.setClock(Clock.fixed(EventMock.NOW.toInstant(), EventService.ZONE));
        when(events.findCandidates(any(), any())).thenReturn(EventMock.agenda());
    }

    private List<Long> ids(String from, String to, String category, String near, String hot, String q, String sort) {
        EventQuery query = service.query(from, to, category, near, null, null, null, null, hot == null ? null : Boolean.valueOf(hot), q, sort, null, 0, 20, Optional.empty());
        return service.list(query, Optional.empty()).getContent().stream().map(v -> v.event().getId()).toList();
    }

    @Test
    void today_onlyEventsStartingToday_sortedByTime() {
        assertThat(ids("2026-11-07", "2026-11-07", null, null, null, null, null)).containsExactly(2L, 1L);
    }

    @Test
    void weekend_includesSunday() {
        assertThat(ids("2026-11-07", "2026-11-08", null, null, null, null, null)).containsExactly(2L, 1L, 3L);
    }

    @Test
    void categories_csv() {
        assertThat(ids(null, null, "SPORT,fair", null, null, null, null)).containsExactly(3L, 4L);
    }

    @Test
    void hot_byParticipantsOrSaves() {
        assertThat(ids(null, null, null, null, "true", null, null)).containsExactly(1L, 3L);
    }

    @Test
    void search_ignoresDiacritics_andCase() {
        assertThat(ids(null, null, null, null, null, "croitorasul", null)).containsExactly(2L);
        assertThat(ids(null, null, null, null, null, "cetatea", null)).containsExactly(3L);
    }

    @Test
    void near_fillsDistance_andSortsByDistance() {
        EventQuery query = service.query(null, null, null, "46.1711,21.3319", null, null, null, null, null, null, "distance", null, 0, 20, Optional.empty());
        var rows = service.list(query, Optional.empty()).getContent();
        assertThat(rows.get(0).event().getId()).isEqualTo(3L);
        assertThat(rows.get(0).distanceKm()).isZero();
        assertThat(rows).allMatch(v -> v.distanceKm() != null);
    }

    @Test
    void savedFlag_onlyForLoggedInUser() {
        CurrentUser ana = EventMock.user(7, Role.LOCAL);
        when(saved.findEventIdsIn(anyLong(), any())).thenReturn(Set.of(3L));
        EventQuery query = service.query(null, null, null, null, null, null, null, null, null, null, null, null, 0, 20, Optional.of(ana));
        var rows = service.list(query, Optional.of(ana)).getContent();
        assertThat(rows).filteredOn(v -> Boolean.TRUE.equals(v.saved())).extracting(v -> v.event().getId()).containsExactly(3L);
        assertThat(service.list(query, Optional.empty()).getContent()).allMatch(v -> v.saved() == null);
    }

    @Test
    void paging() {
        EventQuery query = service.query(null, null, null, null, null, null, null, null, null, null, null, null, 1, 3, Optional.empty());
        var page = service.list(query, Optional.empty());
        assertThat(page.getTotalElements()).isEqualTo(4);
        assertThat(page.getContent()).extracting(v -> v.event().getId()).containsExactly(4L);
    }

    @Test
    void drafts_onlyForCityHallAndAdmin() {
        assertThat(service.query(null, null, null, null, null, null, null, null, null, null, null, "ALL", 0, 20, Optional.empty()).statuses())
                .containsExactly(ContentStatus.PUBLISHED);
        assertThat(service.query(null, null, null, null, null, null, null, null, null, null, null, "DRAFT", 0, 20, Optional.of(EventMock.user(5, Role.CITY_HALL))).statuses())
                .containsExactly(ContentStatus.DRAFT);
    }

    @Test
    void invalidParams_are400() {
        assertThatThrownBy(() -> ids("7 nov", null, null, null, null, null, null)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> ids(null, null, "OPERA", null, null, null, null)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> ids(null, null, null, "arad", null, null, null)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> ids(null, null, null, null, null, null, "distance")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void category_enumUsedInFilter() {
        assertThat(service.query(null, null, "concert", null, null, null, null, null, null, null, null, null, 0, 20, Optional.empty()).categories())
                .containsExactly(EventCategory.CONCERT);
    }
}
