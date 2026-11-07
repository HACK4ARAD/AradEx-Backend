package aradexperience.service.transit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Cererile HTTP spre API-ul public Telelink (cel folosit de portalul CTP), fără cheie, prin RestClient. Orice eroare
 * (rețea, timeout, HTTP ≠ 2xx, JSON stricat) iese ca `RestClientException`; cache-ul, ritmul și rezerva sunt în
 * `TransitLiveService` — nu chemați clientul direct.
 */
@Component
public class TelelinkClient {

    static final String DEFAULT_BASE =
            "https://www.telelink.city/api/v1/e39aa927-11c1-435f-92b9-57804f7bc1b4/transport/planner";

    private final RestClient http;

    @Autowired
    public TelelinkClient(@Value("${app.transit.base-url:" + DEFAULT_BASE + "}") String baseUrl, RestClient.Builder builder) {
        this(builder.clone().requestFactory(timeouts()).baseUrl(baseUrl).build());
    }

    /** Pentru teste: clientul vine legat de `MockRestServiceServer`. */
    TelelinkClient(RestClient http) {
        this.http = http;
    }

    /** Toate vehiculele, o singură cerere. */
    public List<Telelink.Vehicle> vehicles() {
        List<Telelink.Vehicle> body = http.get().uri("/vehicles").retrieve().body(new ParameterizedTypeReference<>() {});
        return body == null ? List.of() : body;
    }

    /** Sosirile următoare într-o stație (id Telelink). */
    public List<Telelink.StopTimes> stopTimes(int stopId) {
        List<Telelink.StopTimes> body = http.get().uri("/stops/{id}/times", stopId).retrieve()
                .body(new ParameterizedTypeReference<>() {});
        return body == null ? List.of() : body;
    }

    /** Răspunsul vine în ~0,3 s; un utilizator nu așteaptă mai mult de câteva secunde după o sursă căzută. */
    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(3_000);
        f.setReadTimeout(6_000);
        return f;
    }
}
