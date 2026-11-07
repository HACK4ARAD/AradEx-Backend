package aradexperience.service.itineraries;

import aradexperience.dto.itineraries.ItineraryDto;
import aradexperience.dto.itineraries.ItineraryStop;
import aradexperience.entity.itineraries.Itinerary;
import aradexperience.exceptions.NotFoundException;
import aradexperience.mock.ItineraryMock;
import aradexperience.repository.itineraries.ItineraryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ItinerariesServiceTest {

    ItineraryRepository repo;
    ItinerariesService service;

    @BeforeEach
    void setUp() {
        repo = mock(ItineraryRepository.class);
        when(repo.save(any())).thenAnswer(inv -> {
            Itinerary i = inv.getArgument(0);
            i.setId(12L);
            return i;
        });
        service = new ItinerariesService(repo);
        service.setClock(Clock.fixed(Instant.parse("2026-11-07T08:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void save_givesAShareCode_theUser_andCreatedAt_storesStopsInAradTime() {
        when(repo.existsByShareCode(anyString())).thenReturn(true, false);
        // Jackson citește orele din cerere în UTC: la salvare se aduc la ora Aradului
        ItineraryDto body = ItineraryMock.dto(null, null);
        ItineraryStop first = body.stops().get(0);
        ItineraryStop utc = new ItineraryStop(1, 1, first.kind(), first.refId(), first.name(), first.subtitle(), first.lat(), first.lng(),
                OffsetDateTime.parse("2026-11-14T08:00:00Z"), OffsetDateTime.parse("2026-11-14T08:15:00Z"), 15, 0, 0, first.travelMode(),
                null, null, true, null);
        body = new ItineraryDto(null, null, "  Centrul istoric ", body.summary(), body.ask(), body.date(), 1, body.transport(), "10:00",
                95, 1.64, true, List.of(utc, body.stops().get(1)), null);

        ItineraryDto saved = service.save(body, Optional.of(7L));

        ArgumentCaptor<Itinerary> e = ArgumentCaptor.forClass(Itinerary.class);
        verify(repo).save(e.capture());
        assertThat(e.getValue().getUserId()).isEqualTo(7L);
        assertThat(e.getValue().getTotalKm()).isEqualByComparingTo("1.6");
        assertThat(saved.id()).isEqualTo(12L);
        assertThat(saved.shareCode()).matches("AX-I-[A-Z2-9]{6}");
        assertThat(saved.title()).isEqualTo("Centrul istoric");
        assertThat(saved.createdAt()).isEqualTo(OffsetDateTime.parse("2026-11-07T10:00:00+02:00"));
        assertThat(saved.stops()).hasSize(2);
        assertThat(saved.stops().get(0).arriveAt()).isEqualTo(OffsetDateTime.parse("2026-11-14T10:00:00+02:00"));
        assertThat(saved.stops().get(0).arriveAt().getOffset()).isEqualTo(ZoneOffset.ofHours(2));
        assertThat(saved.offline()).isTrue();
    }

    @Test
    void save_withoutAccount_hasNoUser() {
        service.save(ItineraryMock.dto(null, null), Optional.empty());
        ArgumentCaptor<Itinerary> e = ArgumentCaptor.forClass(Itinerary.class);
        verify(repo).save(e.capture());
        assertThat(e.getValue().getUserId()).isNull();
    }

    @Test
    void get_byIdOrByShareCode_caseInsensitive_else404() {
        when(repo.findById(12L)).thenReturn(Optional.of(ItineraryMock.entity(12, "AX-I-7K3Q9Z", null)));
        when(repo.findByShareCode("AX-I-7K3Q9Z")).thenReturn(Optional.of(ItineraryMock.entity(12, "AX-I-7K3Q9Z", null)));

        assertThat(service.get("12").shareCode()).isEqualTo("AX-I-7K3Q9Z");
        ItineraryDto byCode = service.get("ax-i-7k3q9z");
        assertThat(byCode.id()).isEqualTo(12L);
        assertThat(byCode.stops()).extracting(ItineraryStop::name).containsExactly("Palatul Administrativ (Primăria)", "Cetatea Aradului");
        assertThat(byCode.totalKm()).isEqualTo(1.6);
        assertThatThrownBy(() -> service.get("AX-I-NOPE22")).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.get("99")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void mine_newestFirst_fromTheRepository() {
        when(repo.findByUserIdOrderByCreatedAtDescIdDesc(7L)).thenReturn(List.of(ItineraryMock.entity(13, "AX-I-BBBBBB", 7L), ItineraryMock.entity(12, "AX-I-AAAAAA", 7L)));
        assertThat(service.mine(7L)).extracting(ItineraryDto::id).containsExactly(13L, 12L);
    }
}
