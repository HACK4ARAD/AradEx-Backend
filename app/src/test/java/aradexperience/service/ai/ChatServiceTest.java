package aradexperience.service.ai;

import aradexperience.dto.ai.ChatMessageDto;
import aradexperience.dto.ai.ChatRequest;
import aradexperience.dto.ai.ChatResponse;
import aradexperience.entity.events.Event;
import aradexperience.entity.places.Place;
import aradexperience.enums.EventCategory;
import aradexperience.enums.TicketType;
import aradexperience.exceptions.TooManyRequestsException;
import aradexperience.repository.events.EventRepository;
import aradexperience.repository.places.GuidePointRepository;
import aradexperience.repository.places.PlaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatServiceTest {

    // sâmbătă, 7 nov. 2026, 12:00 la Arad
    static final Instant NOW = Instant.parse("2026-11-07T10:00:00Z");

    ClaudeClient claude;
    EventRepository events;
    PlaceRepository places;
    GuidePointRepository guide;
    ChatService service;
    final List<Event> catalog = new ArrayList<>();

    @BeforeEach
    void setUp() {
        claude = mock(ClaudeClient.class);
        events = mock(EventRepository.class);
        places = mock(PlaceRepository.class);
        guide = mock(GuidePointRepository.class);
        when(events.findCandidates(any(), any())).thenAnswer(inv -> catalog);
        when(guide.stats()).thenReturn(List.of());
        when(places.findAllById(any())).thenReturn(List.of());
        catalog.add(event(4L, "Teatru de păpuși: Scufița Roșie", EventCategory.FAMILY, "2026-11-07T11:00:00+02:00", TicketType.IN_APP, 25));
        catalog.add(event(7L, "Plantare pe Faleză", EventCategory.OUTDOOR, "2026-11-08T10:00:00+02:00", TicketType.FREE, 0));
        catalog.add(event(9L, "Concert Filarmonica", EventCategory.CONCERT, "2026-11-12T19:00:00+02:00", TicketType.EXTERNAL, 40));
        catalog.add(event(99L, "Târg peste o lună", EventCategory.FAIR, "2026-12-20T10:00:00+02:00", TicketType.FREE, 0));
        service = new ChatService(claude, events, places, guide, new ChatFallback(), new ChatRateLimiter());
        service.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    static Event event(long id, String title, EventCategory cat, String start, TicketType ticket, int price) {
        Event e = new Event();
        e.setId(id);
        e.setTitle(title);
        e.setCategory(cat);
        e.setStartAt(OffsetDateTime.parse(start));
        e.setLocationName("Arad");
        e.setTicketType(ticket);
        e.setPriceFrom(BigDecimal.valueOf(price));
        return e;
    }

    static ChatRequest ask(String... userThenAssistant) {
        List<ChatMessageDto> msgs = new ArrayList<>();
        for (int i = 0; i < userThenAssistant.length; i++) msgs.add(new ChatMessageDto(i % 2 == 0 ? "user" : "assistant", userThenAssistant[i]));
        return new ChatRequest(msgs, "ro");
    }

    @Test
    void claudeAnswer_keepsOnlyEventIdsFromTheCatalog() {
        when(claude.complete(anyString(), anyList())).thenReturn(Optional.of(
                "{\"reply\":\"Sâmbătă: teatrul de păpuși.\",\"suggestedEventIds\":[4,123,4,99,7],\"quickReplies\":[\"Altceva\",\" \"]}"));

        ChatResponse r = service.chat(ask("Ce pot face cu copiii sâmbătă?"), "1.2.3.4");

        assertThat(r.offline()).isFalse();
        assertThat(r.reply()).isEqualTo("Sâmbătă: teatrul de păpuși.");
        // 123 nu există, 99 e peste 14 zile (nu e în catalog), dublura se scoate
        assertThat(r.suggestedEventIds()).containsExactly(4L, 7L);
        assertThat(r.quickReplies()).containsExactly("Altceva");
    }

    @Test
    void systemPrompt_hasTodayTheLanguageAndOnlyTheNext14Days() {
        when(claude.complete(anyString(), anyList())).thenReturn(Optional.of("{\"reply\":\"ok\",\"suggestedEventIds\":[],\"quickReplies\":[]}"));
        service.chat(ask("Ce e azi?"), "k");

        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(claude).complete(system.capture(), anyList());
        assertThat(system.getValue()).contains("2026-11-07").contains("română").contains("Teatru de păpuși").contains("Concert Filarmonica")
                .doesNotContain("Târg peste o lună");
    }

    @Test
    void history_isSentWithoutALeadingAssistantMessage() {
        when(claude.complete(anyString(), anyList())).thenReturn(Optional.of("{\"reply\":\"ok\",\"suggestedEventIds\":[],\"quickReplies\":[]}"));
        List<ChatMessageDto> msgs = List.of(new ChatMessageDto("assistant", "Bună!"), new ChatMessageDto("user", "Ce e azi?"));
        service.chat(new ChatRequest(msgs, "ro"), "k");

        @SuppressWarnings("unchecked") ArgumentCaptor<List<ChatMessageDto>> sent = ArgumentCaptor.forClass(List.class);
        verify(claude).complete(anyString(), sent.capture());
        assertThat(sent.getValue()).extracting(ChatMessageDto::role).containsExactly("user");
    }

    @Test
    void claudeDown_givesTheOfflineAnswer_kidsWithoutADay_asksWhichDay() {
        when(claude.complete(anyString(), anyList())).thenReturn(Optional.empty());

        ChatResponse r = service.chat(ask("Ce pot face cu copiii în weekend?"), "k");

        assertThat(r.offline()).isTrue();
        assertThat(r.reply()).isNotBlank();
        assertThat(r.quickReplies()).isNotEmpty();
    }

    @Test
    void claudeDown_concerts_listsTheConcertsFromTheCatalog() {
        when(claude.complete(anyString(), anyList())).thenReturn(Optional.empty());

        ChatResponse r = service.chat(ask("Ce concerte sunt săptămâna asta?"), "k");

        assertThat(r.offline()).isTrue();
        assertThat(r.suggestedEventIds()).containsExactly(9L);
    }

    @Test
    void claudeDown_kidsAndADay_listsFamilyEvents() {
        when(claude.complete(anyString(), anyList())).thenReturn(Optional.empty());

        ChatResponse r = service.chat(ask("Ce pot face cu copiii?", "Pentru ce zi cauți?", "Sâmbătă"), "k");

        assertThat(r.offline()).isTrue();
        assertThat(r.suggestedEventIds()).contains(4L);
    }

    @Test
    void brokenJsonFromClaude_isTreatedAsOffline() {
        when(claude.complete(anyString(), anyList())).thenReturn(Optional.of("nu e json"));

        assertThat(service.chat(ask("Ce e azi?"), "k").offline()).isTrue();
    }

    @Test
    void moreThan20MessagesAMinuteFromOneAddress_is429() {
        when(claude.complete(anyString(), anyList())).thenReturn(Optional.empty());
        for (int i = 0; i < 20; i++) service.chat(ask("Ce e azi?"), "1.1.1.1");

        assertThatThrownBy(() -> service.chat(ask("Ce e azi?"), "1.1.1.1")).isInstanceOf(TooManyRequestsException.class);
        assertThat(service.chat(ask("Ce e azi?"), "2.2.2.2")).isNotNull();
    }

    @Test
    void englishLocale_isPassedToTheModelAndToTheFallback() {
        when(claude.complete(anyString(), anyList())).thenReturn(Optional.empty());
        ChatResponse r = service.chat(new ChatRequest(List.of(new ChatMessageDto("user", "What can I do with kids?")), "en"), "k");
        assertThat(r.reply()).doesNotContain("ă").doesNotContain("ț");
    }

    @Test
    void guidedPlaces_areInThePrompt() {
        when(guide.stats()).thenReturn(List.of(new GuidePointRepository.GuideStats() {
            public Long getPlaceId() { return 1L; }
            public Long getCount() { return 6L; }
            public Long getSeconds() { return 600L; }
        }));
        Place p = new Place();
        p.setId(1L);
        p.setName("Cetatea Aradului");
        p.setAddress("Str. Cetății");
        when(places.findAllById(any())).thenReturn(List.of(p));
        when(claude.complete(anyString(), anyList())).thenReturn(Optional.of("{\"reply\":\"ok\",\"suggestedEventIds\":[],\"quickReplies\":[]}"));
        service.chat(ask("Cum ajung la Cetate?"), "k");

        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(claude).complete(system.capture(), anyList());
        assertThat(system.getValue()).contains("Cetatea Aradului");
    }

    @Test
    void systemPrompt_spellsOutTodayTheWeekendAndThisWeek_inRomanian() {
        String p = service.systemPrompt("ro", NOW.atZone(ChatService.ARAD), catalog, List.of());

        assertThat(p).contains("Azi este sâmbătă, 7 noiembrie 2026, ora 12:00")
                .contains("mâine: duminică, 8 noiembrie")
                .contains("weekendul: sâmbătă 7 și duminică 8 noiembrie")
                .contains("săptămâna asta: de azi până duminică, 8 noiembrie")
                // în catalog, ziua evenimentului e scrisă în română, nu „Sat”
                .contains("sâmbătă 2026-11-07 11:00").doesNotContain("Sat ");
    }

    @Test
    void systemPrompt_asksForTheDayOnlyWhenNoTimeWasGiven() {
        String p = service.systemPrompt("ro", NOW.atZone(ChatService.ARAD), catalog, List.of());
        assertThat(p).contains("săptămâna asta").contains("răspunde direct");
    }
}

