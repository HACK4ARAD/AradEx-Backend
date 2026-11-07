package aradexperience.service.transit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Formatul observat al API-ului Telelink (7 nov 2026), cu câmpuri în plus pe care le ignorăm. */
class TelelinkClientTest {

    static final String BASE = "https://telelink.test/planner";

    MockRestServiceServer server;
    TelelinkClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new TelelinkClient(builder.baseUrl(BASE).build());
    }

    @Test
    void vehicles_parsesSourceFormat() {
        server.expect(requestTo(BASE + "/vehicles")).andRespond(withSuccess("""
                [{"routeId":38,"patternIndex":1,"tripId":"5.10852","vehicleId":"334","label":"9993","headsign":"G Ar Nou - UTA",
                  "delaySeconds":-120,"nextStopArrival":"2026-11-07T15:13:00Z","latitude":46.1849594,"longitude":21.3097095,
                  "heading":3.200000047683716,"speed":7.1,"lastUpdated":"2026-11-07T15:12:05Z","stopId":120,"stopStatus":"IN_TRANSIT_TO"}]
                """, MediaType.APPLICATION_JSON));

        List<Telelink.Vehicle> v = client.vehicles();

        assertThat(v).hasSize(1);
        assertThat(v.get(0).vehicleId()).isEqualTo("334");
        assertThat(v.get(0).routeId()).isEqualTo(38);
        assertThat(v.get(0).latitude()).isEqualTo(46.1849594);
        assertThat(v.get(0).lastUpdated()).isEqualTo(OffsetDateTime.parse("2026-11-07T15:12:05Z"));
        assertThat(v.get(0).stopId()).isEqualTo(120);
    }

    @Test
    void stopTimes_parsesGroups() {
        server.expect(requestTo(BASE + "/stops/120/times")).andRespond(withSuccess("""
                [{"route":{"routeId":38,"index":1},"times":[{"stopId":120,"scheduledArrival":"2026-11-07T15:13:00Z",
                  "scheduledDeparture":"2026-11-07T15:13:00Z","arrivalDelay":-120,"departureDelay":-120,"realtime":true,
                  "tripId":"5.10852","headsign":"G Ar Nou - UTA","wheelchairAccessible":1,"bikesAllowed":2}]}]
                """, MediaType.APPLICATION_JSON));

        List<Telelink.StopTimes> t = client.stopTimes(120);

        assertThat(t.get(0).route().routeId()).isEqualTo(38);
        assertThat(t.get(0).times().get(0).arrivalDelay()).isEqualTo(-120);
        assertThat(t.get(0).times().get(0).realtime()).isTrue();
    }

    @Test
    void serverError_throwsRestClientException() {
        server.expect(requestTo(BASE + "/vehicles")).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(client::vehicles).isInstanceOf(RestClientException.class);
    }

    @Test
    void brokenJson_throwsRestClientException() {
        server.expect(requestTo(BASE + "/vehicles")).andRespond(withSuccess("<html>maintenance</html>", MediaType.TEXT_HTML));

        assertThatThrownBy(client::vehicles).isInstanceOf(RestClientException.class);
    }
}
