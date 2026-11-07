package aradexperience.service.itineraries;

import aradexperience.dto.itineraries.ItineraryDto;
import aradexperience.dto.itineraries.ItineraryStop;
import aradexperience.entity.itineraries.Itinerary;
import aradexperience.exceptions.NotFoundException;
import aradexperience.exceptions.TooManyRequestsException;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ItinerariesServiceTest {

    ItineraryRepository repo;
    ItineraryStops stops;
    ItinerarySaveLimiter limiter;
    ItinerariesService service;

    @BeforeEach
    void setUp() {
        repo = mock(ItineraryRepository.class);
        when(repo.save(any())).thenAnswer(inv -> {
            Itinerary i = inv.getArgument(0);
            i.setId(12L);
            return i;
        });
        stops = mock(ItineraryStops.class);
        when(stops.canonical(any())).thenAnswer(inv -> inv.getArgument(0));
        limiter = new ItinerarySaveLimiter();
        service = new ItinerariesService(repo, stops, limiter);
        Clock clock = Clock.fixed(Instant.parse("2026-11-07T08:00:00Z"), ZoneOffset.UTC);
        service.setClock(clock);
        limiter.setClock(clock);
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

        ItineraryDto saved = service.save(body, Optional.of(7L), "1.2.3.4");

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
        service.save(ItineraryMock.dto(null, null), Optional.empty(), "1.2.3.4");
        ArgumentCaptor<Itinerary> e = ArgumentCaptor.forClass(Itinerary.class);
        verify(repo).save(e.capture());
        assertThat(e.getValue().getUserId()).isNull();
    }

    @Test
    void get_byShareCode_caseInsensitive_forAnyone_else404() {
        when(repo.findByShareCode("AX-I-7K3Q9Z")).thenReturn(Optional.of(ItineraryMock.entity(12, "AX-I-7K3Q9Z", 7L)));

        ItineraryDto byCode = service.get("ax-i-7k3q9z", Optional.empty());
        assertThat(byCode.id()).isEqualTo(12L);
        assertThat(byCode.stops()).extracting(ItineraryStop::name).containsExactly("Palatul Administrativ (Primăria)", "Cetatea Aradului");
        assertThat(byCode.totalKm()).isEqualTo(1.6);
        assertThat(service.get("AX-I-7K3Q9Z", Optional.of(8L)).id()).isEqualTo(12L);
        assertThatThrownBy(() -> service.get("AX-I-NOPE22", Optional.empty())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void get_byNumericId_onlyForTheOwner_else404() {
        when(repo.findById(12L)).thenReturn(Optional.of(ItineraryMock.entity(12, "AX-I-7K3Q9Z", 7L)));
        when(repo.findById(13L)).thenReturn(Optional.of(ItineraryMock.entity(13, "AX-I-BBBBBB", null)));

        assertThat(service.get("12", Optional.of(7L)).shareCode()).isEqualTo("AX-I-7K3Q9Z");
        assertThatThrownBy(() -> service.get("12", Optional.empty())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.get("12", Optional.of(8L))).isInstanceOf(NotFoundException.class);
        // fără proprietar: nimeni nu-l deschide după id
        assertThatThrownBy(() -> service.get("13", Optional.of(7L))).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.get("99", Optional.of(7L))).isInstanceOf(NotFoundException.class);
    }

    @Test
    void save_rebuildsTheStopsFromTheDatabase() {
        when(repo.existsByShareCode(anyString())).thenReturn(false);
        ItineraryStop real = ItineraryMock.stop(1, 4, "Palatul Administrativ (Primăria)", "2026-11-14T10:00:00+02:00", "2026-11-14T10:15:00+02:00");
        when(stops.canonical(any())).thenReturn(List.of(real));

        ItineraryDto saved = service.save(ItineraryMock.dto(null, null), Optional.empty(), "1.2.3.4");

        assertThat(saved.stops()).containsExactly(real);
    }

    @Test
    void save_moreThan20AMinuteFromOneAddress_is429() {
        when(repo.existsByShareCode(anyString())).thenReturn(false);
        for (int n = 0; n < 20; n++) service.save(ItineraryMock.dto(null, null), Optional.empty(), "1.1.1.1");

        assertThatThrownBy(() -> service.save(ItineraryMock.dto(null, null), Optional.empty(), "1.1.1.1"))
                .isInstanceOf(TooManyRequestsException.class);
        assertThat(service.save(ItineraryMock.dto(null, null), Optional.empty(), "2.2.2.2")).isNotNull();
    }

    @Test
    void saveToMine_withoutOwner_becomesMine() {
        Itinerary anonymous = ItineraryMock.entity(12, "AX-I-7K3Q9Z", null);
        when(repo.findByShareCode("AX-I-7K3Q9Z")).thenReturn(Optional.of(anonymous));

        ItineraryDto d = service.saveToMine("ax-i-7k3q9z", 7L);

        assertThat(anonymous.getUserId()).isEqualTo(7L);
        assertThat(d.id()).isEqualTo(12L);
        assertThat(d.shareCode()).isEqualTo("AX-I-7K3Q9Z");
    }

    @Test
    void saveToMine_alreadyMine_staysTheSame() {
        when(repo.findByShareCode("AX-I-7K3Q9Z")).thenReturn(Optional.of(ItineraryMock.entity(12, "AX-I-7K3Q9Z", 7L)));

        assertThat(service.saveToMine("AX-I-7K3Q9Z", 7L).id()).isEqualTo(12L);
        verify(repo, never()).save(any());
    }

    @Test
    void saveToMine_someoneElses_isCopied_withANewCode() {
        Itinerary theirs = ItineraryMock.entity(12, "AX-I-7K3Q9Z", 8L);
        when(repo.findByShareCode("AX-I-7K3Q9Z")).thenReturn(Optional.of(theirs));
        when(repo.existsByShareCode(anyString())).thenReturn(false);

        ItineraryDto d = service.saveToMine("AX-I-7K3Q9Z", 7L);

        ArgumentCaptor<Itinerary> e = ArgumentCaptor.forClass(Itinerary.class);
        verify(repo).save(e.capture());
        assertThat(e.getValue()).isNotSameAs(theirs);
        assertThat(e.getValue().getUserId()).isEqualTo(7L);
        assertThat(d.shareCode()).isNotEqualTo("AX-I-7K3Q9Z").matches("AX-I-[A-Z2-9]{6}");
        assertThat(d.stops()).hasSize(2);
        assertThat(theirs.getUserId()).isEqualTo(8L);
    }

    @Test
    void saveToMine_unknownCode_is404() {
        when(repo.findByShareCode(anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.saveToMine("AX-I-NOPE22", 7L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void mine_newestFirst_fromTheRepository() {
        when(repo.findByUserIdOrderByCreatedAtDescIdDesc(7L)).thenReturn(List.of(ItineraryMock.entity(13, "AX-I-BBBBBB", 7L), ItineraryMock.entity(12, "AX-I-AAAAAA", 7L)));
        assertThat(service.mine(7L)).extracting(ItineraryDto::id).containsExactly(13L, 12L);
    }
}
