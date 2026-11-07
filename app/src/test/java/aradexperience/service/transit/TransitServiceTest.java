package aradexperience.service.transit;

import aradexperience.dto.transit.StopArrivalsDto;
import aradexperience.dto.transit.TransitRouteDto;
import aradexperience.dto.transit.TransitStopDto;
import aradexperience.dto.transit.TransitVehiclesDto;
import aradexperience.exceptions.BadRequestException;
import aradexperience.exceptions.NotFoundException;
import aradexperience.mock.TransitMock;
import aradexperience.repository.transit.TransitPatternRepository;
import aradexperience.repository.transit.TransitRouteRepository;
import aradexperience.repository.transit.TransitStopRepository;
import aradexperience.service.transit.TransitLiveService.Live;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransitServiceTest {

    TransitRouteRepository routes = mock(TransitRouteRepository.class);
    TransitStopRepository stops = mock(TransitStopRepository.class);
    TransitPatternRepository patterns = mock(TransitPatternRepository.class);
    TransitLiveService live = mock(TransitLiveService.class);
    TransitService service;

    @BeforeEach
    void setUp() {
        when(routes.findAll()).thenReturn(List.of(TransitMock.bus21(), TransitMock.tram1(),
                TransitMock.route(39, "GH - POD", aradexperience.enums.TransitMode.BUS, null),
                TransitMock.route(12, "1b", aradexperience.enums.TransitMode.TRAM, "#db3e00")));
        when(stops.findAll()).thenReturn(TransitMock.stops());
        when(patterns.findAll()).thenReturn(TransitMock.patterns());
        service = new TransitService(routes, stops, patterns, live, Clock.fixed(TransitMock.NOW, ZoneOffset.UTC));
    }

    @Test
    void stops_carryTheirRoutes_andNetworkIsLoadedOnce() {
        List<TransitStopDto> list = service.stops();
        service.routes();

        assertThat(list).extracting(TransitStopDto::id).containsExactly(120, 121, 130);
        assertThat(list.get(0).routeIds()).containsExactly(11);
        assertThat(list.get(2).routeIds()).containsExactly(3, 11);
        assertThat(list.get(2).lines()).containsExactly("1", "21"); // tramvaiele întâi
        verify(stops, times(1)).findAll();
    }

    @Test
    void routes_tramsFirst_inNumberOrder_withDecodedPath() {
        List<TransitRouteDto> list = service.routes();

        assertThat(list).extracting(TransitRouteDto::shortName).containsExactly("1", "1b", "21", "GH - POD");
        TransitRouteDto tram = list.get(0);
        assertThat(tram.patterns()).extracting(p -> p.index()).containsExactly(-1, 1);
        assertThat(tram.patterns().get(1).stopIds()).containsExactly(120, 130);
        assertThat(tram.patterns().get(1).path()).hasSize(2);
    }

    @Test
    void vehicles_live_mapsAndDropsVehiclesWithoutPosition() {
        var noPos = new Telelink.Vehicle("9", "9", 11, 1, null, null, null, null, null, null, null, null, null);
        when(live.vehicles()).thenReturn(new Live<>(List.of(TransitMock.vehicle("1", 11, TransitMock.NOW), noPos), TransitMock.NOW, true));

        TransitVehiclesDto v = service.vehicles();

        assertThat(v.live()).isTrue();
        assertThat(v.attribution()).isEqualTo("Date: CTP Arad / Telelink");
        assertThat(v.vehicles()).hasSize(1);
        assertThat(v.vehicles().get(0).routeShortName()).isEqualTo("1");
        assertThat(v.updatedAt().toInstant()).isEqualTo(TransitMock.NOW);
    }

    @Test
    void vehicles_sourceDown_isEmptyAndFlagged() {
        when(live.vehicles()).thenReturn(Live.none());

        TransitVehiclesDto v = service.vehicles();

        assertThat(v.live()).isFalse();
        assertThat(v.vehicles()).isEmpty();
        assertThat(v.updatedAt()).isNull();
    }

    @Test
    void vehicles_oldData_notLive_isEmpty() {
        when(live.vehicles()).thenReturn(new Live<>(List.of(TransitMock.vehicle("1", 11, TransitMock.NOW)), TransitMock.NOW.minusSeconds(300), false));

        TransitVehiclesDto v = service.vehicles();

        assertThat(v.live()).isFalse();
        assertThat(v.vehicles()).isEmpty();
        assertThat(v.updatedAt()).isNotNull();
    }

    @Test
    void arrivals_sortedLimitedAndWithoutPast() {
        when(live.stopTimes(120)).thenReturn(new Live<>(List.of(
                TransitMock.times(11, 120, 9, 4, -2, 30, 40, 50),
                TransitMock.times(3, 120, 6, 12, 14, 16, 18)), TransitMock.NOW, true));

        StopArrivalsDto a = service.arrivals(120);

        assertThat(a.live()).isTrue();
        assertThat(a.stop().name()).isEqualTo("Podgoria");
        assertThat(a.distanceKm()).isNull();
        assertThat(a.arrivals()).hasSize(8);
        assertThat(a.arrivals()).extracting(x -> x.minutes()).containsExactly(4, 6, 9, 12, 14, 16, 18, 30);
        assertThat(a.arrivals().get(0).routeShortName()).isEqualTo("1");
        assertThat(a.arrivals().get(1).routeShortName()).isEqualTo("21");
    }

    @Test
    void arrivals_unknownStop_is404_withoutCallingSource() {
        assertThatThrownBy(() -> service.arrivals(5)).isInstanceOf(NotFoundException.class);
        verify(live, never()).stopTimes(5);
    }

    @Test
    void arrivals_sourceDown_staticFallback() {
        when(live.stopTimes(130)).thenReturn(Live.none());

        StopArrivalsDto a = service.arrivals(130);

        assertThat(a.live()).isFalse();
        assertThat(a.arrivals()).isEmpty();
        assertThat(a.stop().routeIds()).containsExactly(3, 11); // „liniile din stație” rămân
        assertThat(a.attribution()).isEqualTo("Date: CTP Arad / Telelink");
    }

    @Test
    void nearest_picksClosestStop_andMergesOppositePlatform() {
        when(live.stopTimes(120)).thenReturn(new Live<>(List.of(TransitMock.times(11, 120, 7)), TransitMock.NOW, true));
        when(live.stopTimes(121)).thenReturn(new Live<>(List.of(TransitMock.times(11, 121, 3)), TransitMock.NOW, true));

        StopArrivalsDto a = service.nearest(46.1835, 21.3252);

        assertThat(a.stop().id()).isEqualTo(120);
        assertThat(a.distanceKm()).isEqualTo(0.0);
        assertThat(a.arrivals()).extracting(x -> x.stopId()).containsExactly(121, 120);
        verify(live, never()).stopTimes(130);
    }

    @Test
    void nearest_farAway_stillAnswersWithDistance() {
        when(live.stopTimes(org.mockito.ArgumentMatchers.anyInt())).thenReturn(Live.none());

        StopArrivalsDto a = service.nearest(46.1378, 21.6048); // Miniș, ~25 km la est: cel mai aproape e Podgoria

        assertThat(a.stop().name()).isEqualTo("Podgoria");
        assertThat(a.distanceKm()).isGreaterThan(20);
        assertThat(a.live()).isFalse();
    }

    @Test
    void nearest_missingOrInvalidCoordinates_is400() {
        assertThatThrownBy(() -> service.nearest(null, 21.3)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.nearest(46.1, 200.0)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void sortKey_numbersBeforeNames() {
        assertThat(TransitService.sortKey("1")).isLessThan(TransitService.sortKey("1b"));
        assertThat(TransitService.sortKey("3")).isLessThan(TransitService.sortKey("15"));
        assertThat(TransitService.sortKey("60")).isLessThan(TransitService.sortKey("GH - POD"));
    }
}
