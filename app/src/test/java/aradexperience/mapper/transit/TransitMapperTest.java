package aradexperience.mapper.transit;

import aradexperience.dto.transit.TransitArrivalDto;
import aradexperience.dto.transit.TransitVehicleDto;
import aradexperience.enums.TransitMode;
import aradexperience.mock.TransitMock;
import aradexperience.service.transit.Telelink;
import org.junit.jupiter.api.Test;

import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TransitMapperTest {

    @Test
    void decodePath_matchesGeoJsonOfSource() {
        // începutul variantei 3:-12 din export: routes.geojson are [21.32509, 46.18338], [21.32568, 46.18384] ([lng, lat])
        List<double[]> pts = TransitMapper.decodePath("ce{xGy`daC{AuB");
        assertThat(pts).hasSize(2);
        assertThat(pts.get(0)).containsExactly(46.18338, 21.32509);
        assertThat(pts.get(1)).containsExactly(46.18384, 21.32568);
        assertThat(TransitMapper.decodePath(null)).isEmpty();
        assertThat(TransitMapper.decodePath("")).isEmpty();
    }

    @Test
    void vehicle_staleAfter120s_andNamedFromStaticRoute() {
        TransitVehicleDto fresh = TransitMapper.toVehicleDto(TransitMock.vehicle("1", 11, TransitMock.NOW.minusSeconds(120)),
                TransitMock.tram1(), TransitMock.NOW);
        TransitVehicleDto old = TransitMapper.toVehicleDto(TransitMock.vehicle("2", 99, TransitMock.NOW.minusSeconds(121)),
                null, TransitMock.NOW);
        TransitVehicleDto noTime = TransitMapper.toVehicleDto(TransitMock.vehicle("3", 11, null), TransitMock.tram1(), TransitMock.NOW);

        assertThat(fresh.stale()).isFalse();
        assertThat(fresh.routeShortName()).isEqualTo("1");
        assertThat(fresh.mode()).isEqualTo(TransitMode.TRAM);
        assertThat(fresh.nextStopId()).isEqualTo(120);
        assertThat(old.stale()).isTrue();
        assertThat(old.routeShortName()).isNull();
        assertThat(noTime.stale()).isTrue();
    }

    @Test
    void arrival_expectedTimeIncludesDelay_minutesNeverNegative() {
        Telelink.Time late = new Telelink.Time(120, TransitMock.NOW.plusSeconds(180).atOffset(ZoneOffset.UTC), 90, true, "x", " ");
        Telelink.Time early = new Telelink.Time(null, TransitMock.NOW.plusSeconds(20).atOffset(ZoneOffset.UTC), -60, false, "y", "Gara");

        TransitArrivalDto a = TransitMapper.toArrivalDto(late, 11, TransitMock.tram1(), 120, TransitMock.NOW);
        TransitArrivalDto b = TransitMapper.toArrivalDto(early, 11, null, 121, TransitMock.NOW);

        assertThat(a.at().toInstant()).isEqualTo(TransitMock.NOW.plusSeconds(270));
        assertThat(a.minutes()).isEqualTo(4);
        assertThat(a.delaySeconds()).isEqualTo(90);
        assertThat(a.headsign()).isNull();
        assertThat(a.routeShortName()).isEqualTo("1");
        assertThat(b.minutes()).isZero();
        assertThat(b.stopId()).isEqualTo(121);
        assertThat(b.realtime()).isFalse();
    }
}
