package aradexperience.service.google;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Formatul Google Places API (New) observat pe 7 nov 2026 (Place Details + Text Search), cu câmpuri în plus ignorate. */
class GooglePlacesClientTest {

    static final String BASE = "https://places.test/v1";

    MockRestServiceServer server;
    GooglePlacesClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GooglePlacesClient(builder.baseUrl(BASE).build(), "test-key");
    }

    @Test
    void details_sendsKeyAndFieldMask_parsesRatingAndReviews() {
        server.expect(requestTo(BASE + "/places/ChIJabc?languageCode=ro"))
              .andExpect(method(HttpMethod.GET))
              .andExpect(header("X-Goog-Api-Key", "test-key"))
              .andExpect(header("X-Goog-FieldMask", GooglePlacesClient.DETAILS_FIELDS))
              .andRespond(withSuccess("""
                      {"id":"ChIJabc","rating":4.6,"userRatingCount":1240,"googleMapsUri":"https://maps.google.com/?cid=1",
                       "reviews":[{"name":"places/ChIJabc/reviews/x","relativePublishTimeDescription":"acum o lună","rating":5,
                         "text":{"text":"Superb","languageCode":"ro"},"originalText":{"text":"Superb","languageCode":"ro"},
                         "authorAttribution":{"displayName":"Ana P.","uri":"https://www.google.com/maps/contrib/1/reviews",
                           "photoUri":"https://lh3.googleusercontent.com/a/x"},"publishTime":"2026-10-01T10:00:00Z"}]}
                      """, MediaType.APPLICATION_JSON));

        GooglePlaces.Place p = client.details("ChIJabc", "ro");

        assertThat(p.rating()).isEqualTo(4.6);
        assertThat(p.userRatingCount()).isEqualTo(1240);
        assertThat(p.googleMapsUri()).isEqualTo("https://maps.google.com/?cid=1");
        assertThat(p.reviews()).hasSize(1);
        assertThat(p.reviews().get(0).authorAttribution().displayName()).isEqualTo("Ana P.");
        assertThat(p.reviews().get(0).text().text()).isEqualTo("Superb");
        server.verify();
    }

    @Test
    void findId_asksOnlyForIds_restrictedAroundTheCoordinates() {
        server.expect(requestTo(BASE + "/places:searchText"))
              .andExpect(method(HttpMethod.POST))
              .andExpect(header("X-Goog-FieldMask", "places.id"))
              .andExpect(jsonPath("$.textQuery").value("Cetatea Aradului, Arad"))
              .andExpect(jsonPath("$.pageSize").value(1))
              .andExpect(jsonPath("$.locationRestriction.rectangle.low.latitude").value(46.1709 - GooglePlacesClient.BOX_LAT))
              .andExpect(jsonPath("$.locationRestriction.rectangle.high.longitude").value(21.3326 + GooglePlacesClient.BOX_LNG))
              .andRespond(withSuccess("{\"places\":[{\"id\":\"ChIJ7VFfQACZRUcRXFuEhkS_zss\"}]}", MediaType.APPLICATION_JSON));

        assertThat(client.findId("Cetatea Aradului, Arad", 46.1709, 21.3326)).contains("ChIJ7VFfQACZRUcRXFuEhkS_zss");
    }

    @Test
    void findId_noResults_isEmpty() {
        server.expect(requestTo(BASE + "/places:searchText")).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        assertThat(client.findId("Statuia X, Arad", 46.17, 21.31)).isEqualTo(Optional.empty());
    }

    @Test
    void errors_surfaceAsRestClientException() {
        server.expect(requestTo(BASE + "/places/ChIJbad?languageCode=en")).andRespond(withStatus(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON).body("{\"error\":{\"code\":403,\"status\":\"PERMISSION_DENIED\"}}"));
        assertThatThrownBy(() -> client.details("ChIJbad", "en")).isInstanceOf(RestClientException.class);
    }

    @Test
    void configured_onlyWithAKey() {
        assertThat(client.configured()).isTrue();
        assertThat(new GooglePlacesClient(RestClient.create(), " ").configured()).isFalse();
        assertThat(new GooglePlacesClient(RestClient.create(), null).configured()).isFalse();
    }
}
