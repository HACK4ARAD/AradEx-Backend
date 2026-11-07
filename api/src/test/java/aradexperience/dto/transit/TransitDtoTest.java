package aradexperience.dto.transit;

import aradexperience.dto.DtoJson;
import aradexperience.enums.TransitMode;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import static org.assertj.core.api.Assertions.assertThat;

/** JSON-ul din resources = docs/API.md §Transport public (T22); DTO-urile îl citesc și îl scriu identic (fără null-uri, date ISO). */
class TransitDtoTest {

    private static final String ROUTE = "dto/transit/transit-route.json";
    private static final String VEHICLES = "dto/transit/transit-vehicles.json";
    private static final String ARRIVALS = "dto/transit/stop-arrivals.json";

    @Test
    void route_deserialize_readsPatternWithPath() {
        TransitRouteDto r = DtoJson.fromResource(ROUTE, TransitRouteDto.class);
        assertThat(r.mode()).isEqualTo(TransitMode.TRAM);
        assertThat(r.patterns().get(0).index()).isEqualTo(-12);
        assertThat(r.patterns().get(0).stopIds()).containsExactly(97, 68, 23);
        assertThat(r.patterns().get(0).path().get(1)).containsExactly(46.18384, 21.32568);
    }

    @Test
    void route_roundTrip_matchesJson() throws Exception {
        TransitRouteDto r = DtoJson.fromResource(ROUTE, TransitRouteDto.class);
        JSONAssert.assertEquals(DtoJson.read(ROUTE), DtoJson.APP.writeValueAsString(r), JSONCompareMode.STRICT);
    }

    @Test
    void vehicles_deserialize_unknownRouteHasNoName() {
        TransitVehiclesDto v = DtoJson.fromResource(VEHICLES, TransitVehiclesDto.class);
        assertThat(v.live()).isTrue();
        assertThat(v.vehicles()).hasSize(2);
        assertThat(v.vehicles().get(0).routeShortName()).isEqualTo("3");
        assertThat(v.vehicles().get(1).routeShortName()).isNull();
        assertThat(v.vehicles().get(1).stale()).isTrue();
    }

    @Test
    void vehicles_roundTrip_matchesJson() throws Exception {
        TransitVehiclesDto v = DtoJson.fromResource(VEHICLES, TransitVehiclesDto.class);
        JSONAssert.assertEquals(DtoJson.read(VEHICLES), DtoJson.APP.writeValueAsString(v), JSONCompareMode.STRICT);
    }

    @Test
    void arrivals_roundTrip_matchesJson() throws Exception {
        StopArrivalsDto a = DtoJson.fromResource(ARRIVALS, StopArrivalsDto.class);
        assertThat(a.stop().routeIds()).containsExactly(11, 13, 38);
        assertThat(a.arrivals().get(0).minutes()).isEqualTo(4);
        JSONAssert.assertEquals(DtoJson.read(ARRIVALS), DtoJson.APP.writeValueAsString(a), JSONCompareMode.STRICT);
    }
}
