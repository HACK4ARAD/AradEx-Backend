package aradexperience.service.google;

import aradexperience.dto.google.GoogleInfoDto;
import aradexperience.dto.google.GoogleLinkDto;
import aradexperience.dto.google.GooglePlaceIdRequest;
import aradexperience.exceptions.ApiException;
import aradexperience.exceptions.NotFoundException;
import aradexperience.mock.GoogleMock;
import aradexperience.repository.google.GoogleLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static aradexperience.mock.GoogleMock.CETATE_GOOGLE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleServiceTest {

    @Mock GoogleLinkRepository links;
    @Mock GooglePlacesClient google;

    MutableClock clock;
    GoogleService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-11-07T15:00:00Z"));
        service = new GoogleService(links, google, clock);
    }

    @Test
    void placeInfo_linked_mapsAndCachesFor24h() {
        when(links.place(1L)).thenReturn(Optional.of(GoogleMock.cetate(CETATE_GOOGLE_ID)));
        when(google.configured()).thenReturn(true);
        when(google.details(CETATE_GOOGLE_ID, "ro")).thenReturn(GoogleMock.place(7));

        GoogleInfoDto first = service.placeInfo(1L, null);
        clock.advance(Duration.ofHours(23));
        GoogleInfoDto second = service.placeInfo(1L, "ro");

        assertThat(first.rating()).isEqualTo(4.6);
        assertThat(first.userRatingCount()).isEqualTo(1240);
        assertThat(first.reviews()).hasSize(5);                    // cel mult 5
        assertThat(first.reviews().get(0).author()).isEqualTo("Autor 1");
        assertThat(first.attribution()).isEqualTo("Powered by Google");
        assertThat(second).isSameAs(first);
        verify(google, times(1)).details(CETATE_GOOGLE_ID, "ro");

        clock.advance(Duration.ofHours(2));                        // a trecut de 24 h → se cere din nou
        service.placeInfo(1L, "ro");
        verify(google, times(2)).details(CETATE_GOOGLE_ID, "ro");
    }

    @Test
    void placeInfo_languagesAreCachedSeparately() {
        when(links.place(1L)).thenReturn(Optional.of(GoogleMock.cetate(CETATE_GOOGLE_ID)));
        when(google.configured()).thenReturn(true);
        when(google.details(anyString(), anyString())).thenReturn(GoogleMock.place(1));

        service.placeInfo(1L, "en");
        service.placeInfo(1L, "EN");
        service.placeInfo(1L, "hu");                               // maghiara are cache-ul ei
        service.placeInfo(1L, "de");                               // limbă necunoscută → ro

        verify(google, times(1)).details(CETATE_GOOGLE_ID, "en");
        verify(google, times(1)).details(CETATE_GOOGLE_ID, "hu");
        verify(google, times(1)).details(CETATE_GOOGLE_ID, "ro");
    }

    @Test
    void placeInfo_notLinked_findsTheIdOnceAndSavesIt() {
        when(links.place(1L)).thenReturn(Optional.of(GoogleMock.cetate(null)));
        when(google.configured()).thenReturn(true);
        when(google.findId("Cetatea Aradului, Cartier Subcetate, Arad", 46.1709, 21.3326)).thenReturn(Optional.of(CETATE_GOOGLE_ID));
        when(google.details(CETATE_GOOGLE_ID, "ro")).thenReturn(GoogleMock.place(2));

        assertThat(service.placeInfo(1L, "ro").reviews()).hasSize(2);
        verify(links).setPlaceGoogleId(1L, CETATE_GOOGLE_ID);
        verify(google, times(1)).details(CETATE_GOOGLE_ID, "ro");   // detaliile de la verificare intră direct în cache
    }

    @Test
    void placeInfo_resultWithoutRatings_isNotLinked_andALinkedPlaceWithoutRatingsIs404() {
        when(links.place(11L)).thenReturn(Optional.of(new GoogleLinkRepository.PlaceRow(11L, "Podgoria Miniș–Măderat",
                "Miniș, comuna Ghioroc", 46.1378, 21.6048, null)));
        when(google.configured()).thenReturn(true);
        when(google.findId(anyString(), anyDouble(), anyDouble())).thenReturn(Optional.of("ChIJvillage"));
        GooglePlaces.Place village = new GooglePlaces.Place("ChIJvillage", new GooglePlaces.LocalizedText("Miniș", "ro"),
                null, null, "https://maps.google.com/?cid=3", null);
        when(google.details("ChIJvillage", "ro")).thenReturn(village);

        assertThatThrownBy(() -> service.placeInfo(11L, "ro")).isInstanceOf(NotFoundException.class);
        verify(links, never()).setPlaceGoogleId(11L, "ChIJvillage");

        when(links.place(12L)).thenReturn(Optional.of(new GoogleLinkRepository.PlaceRow(12L, "X", null, 46.1, 21.3, "ChIJvillage")));
        assertThatThrownBy(() -> service.placeInfo(12L, "ro")).isInstanceOf(NotFoundException.class)
                .hasMessageContaining("No Google ratings");
    }

    @Test
    void placeInfo_resultWithAnotherName_isNotLinked() {
        when(links.place(20L)).thenReturn(Optional.of(new GoogleLinkRepository.PlaceRow(20L, "Casa Comitatului",
                "Bulevardul Revoluției 79, Arad", 46.1746, 21.318, null)));
        when(google.configured()).thenReturn(true);
        when(google.findId(anyString(), anyDouble(), anyDouble())).thenReturn(Optional.of("ChIJhotel"));
        GooglePlaces.Place hotel = new GooglePlaces.Place("ChIJhotel", new GooglePlaces.LocalizedText("Hotel Continental Forum Arad", "ro"),
                4.3, 3265, "https://maps.google.com/?cid=2", java.util.List.of());
        when(google.details("ChIJhotel", "ro")).thenReturn(hotel);

        assertThatThrownBy(() -> service.placeInfo(20L, "ro")).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.placeInfo(20L, "ro")).isInstanceOf(NotFoundException.class);
        verify(links, never()).setPlaceGoogleId(20L, "ChIJhotel");
        verify(google, times(1)).findId(anyString(), anyDouble(), anyDouble());   // „alt nume” se ține minte 24 h
    }

    @Test
    void placeInfo_notOnGoogle_is404_andIsNotSearchedAgainFor24h() {
        when(links.place(1L)).thenReturn(Optional.of(GoogleMock.cetate(null)));
        when(google.configured()).thenReturn(true);
        when(google.findId(anyString(), anyDouble(), anyDouble())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.placeInfo(1L, "ro")).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.placeInfo(1L, "ro")).isInstanceOf(NotFoundException.class);
        verify(google, times(1)).findId(anyString(), anyDouble(), anyDouble());
        verify(links, never()).setPlaceGoogleId(1L, CETATE_GOOGLE_ID);
    }

    @Test
    void withoutKey_is404_andGoogleIsNeverCalled() {
        when(links.place(1L)).thenReturn(Optional.of(GoogleMock.cetate(CETATE_GOOGLE_ID)));
        when(google.configured()).thenReturn(false);

        assertThatThrownBy(() -> service.placeInfo(1L, "ro")).isInstanceOf(NotFoundException.class)
                .hasMessageContaining("not configured");
        verify(google, never()).details(anyString(), anyString());
    }

    @Test
    void unknownPlace_is404() {
        when(links.place(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.placeInfo(99L, "ro")).isInstanceOf(NotFoundException.class).hasMessageContaining("Place 99");
    }

    @Test
    void googleDown_noCopy_is503_thenWaits5MinutesBeforeTryingAgain() {
        when(links.place(1L)).thenReturn(Optional.of(GoogleMock.cetate(CETATE_GOOGLE_ID)));
        when(google.configured()).thenReturn(true);
        when(google.details(CETATE_GOOGLE_ID, "ro")).thenThrow(new ResourceAccessException("timeout"));

        assertThatThrownBy(() -> service.placeInfo(1L, "ro")).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        assertThatThrownBy(() -> service.placeInfo(1L, "ro")).isInstanceOf(ApiException.class);
        verify(google, times(1)).details(CETATE_GOOGLE_ID, "ro");

        clock.advance(Duration.ofMinutes(6));
        assertThatThrownBy(() -> service.placeInfo(1L, "ro")).isInstanceOf(ApiException.class);
        verify(google, times(2)).details(CETATE_GOOGLE_ID, "ro");
    }

    @Test
    void googleDown_withAnExpiredCopy_servesTheCopy() {
        when(links.place(1L)).thenReturn(Optional.of(GoogleMock.cetate(CETATE_GOOGLE_ID)));
        when(google.configured()).thenReturn(true);
        when(google.details(CETATE_GOOGLE_ID, "ro")).thenReturn(GoogleMock.place(3)).thenThrow(new ResourceAccessException("down"));

        GoogleInfoDto fresh = service.placeInfo(1L, "ro");
        clock.advance(Duration.ofHours(25));

        assertThat(service.placeInfo(1L, "ro")).isSameAs(fresh);
    }

    @Test
    void businessInfo_onlyWhenLinked_neverSearches() {
        when(links.business(1L)).thenReturn(Optional.of(GoogleMock.cafenea(null)));
        when(google.configured()).thenReturn(true);

        assertThatThrownBy(() -> service.businessInfo(1L, "ro")).isInstanceOf(NotFoundException.class)
                .hasMessageContaining("not linked");
        verify(google, never()).findId(anyString(), anyDouble(), anyDouble());
    }

    @Test
    void businessInfo_linked_returnsGoogleData() {
        when(links.business(1L)).thenReturn(Optional.of(GoogleMock.cafenea("ChIJcafe")));
        when(google.configured()).thenReturn(true);
        when(google.details("ChIJcafe", "ro")).thenReturn(GoogleMock.place(1));

        assertThat(service.businessInfo(1L, "ro").userRatingCount()).isEqualTo(1240);
    }

    @Test
    void setPlaceLink_savesTrimmedId_blankUnlinks() {
        when(links.place(1L)).thenReturn(Optional.of(GoogleMock.cetate(null)));
        when(google.configured()).thenReturn(false);

        GoogleLinkDto linked = service.setPlaceLink(1L, new GooglePlaceIdRequest(" ChIJnew "));
        GoogleLinkDto unlinked = service.setPlaceLink(1L, new GooglePlaceIdRequest(""));

        verify(links).setPlaceGoogleId(1L, "ChIJnew");
        verify(links).setPlaceGoogleId(1L, null);
        assertThat(linked.googlePlaceId()).isEqualTo("ChIJnew");
        assertThat(linked.configured()).isFalse();
        assertThat(linked.info()).isNull();
        assertThat(unlinked.googlePlaceId()).isNull();
    }

    @Test
    void setPlaceLink_clearsTheNotFoundMemory() {
        when(links.place(1L)).thenReturn(Optional.of(GoogleMock.cetate(null)));
        when(google.configured()).thenReturn(true);
        when(google.findId(anyString(), anyDouble(), anyDouble())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.placeInfo(1L, "ro")).isInstanceOf(NotFoundException.class);

        service.setPlaceLink(1L, new GooglePlaceIdRequest(""));
        assertThatThrownBy(() -> service.placeInfo(1L, "ro")).isInstanceOf(NotFoundException.class);

        verify(google, times(2)).findId(anyString(), anyDouble(), anyDouble());
    }

    @Test
    void wrongLinkedId_is404_panelSaysInvalid_andGoogleIsNotAskedAgainFor24h() {
        when(links.place(1L)).thenReturn(Optional.of(GoogleMock.cetate("ChIJwrong")));
        when(google.configured()).thenReturn(true);
        when(google.details("ChIJwrong", "ro")).thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", null, null, null));

        assertThatThrownBy(() -> service.placeInfo(1L, "ro")).isInstanceOf(NotFoundException.class).hasMessageContaining("ChIJwrong");
        GoogleLinkDto link = service.placeLink(1L);

        assertThat(link.info()).isNull();
        assertThat(link.problem()).isEqualTo(GoogleLinkDto.INVALID_ID);
        verify(google, times(1)).details("ChIJwrong", "ro");
    }

    @Test
    void keyRejectedByGoogle_isUnavailable_notInvalid() {
        when(links.place(1L)).thenReturn(Optional.of(GoogleMock.cetate(CETATE_GOOGLE_ID)));
        when(google.configured()).thenReturn(true);
        when(google.details(CETATE_GOOGLE_ID, "ro")).thenThrow(HttpClientErrorException.create(HttpStatus.FORBIDDEN, "Forbidden", null, null, null));

        assertThat(service.placeLink(1L).problem()).isEqualTo(GoogleLinkDto.UNAVAILABLE);
    }

    @Test
    void links_googleErrorsNeverFailThePanel() {
        when(links.place(1L)).thenReturn(Optional.of(GoogleMock.cetate(CETATE_GOOGLE_ID)));
        when(google.configured()).thenReturn(true);
        when(google.details(CETATE_GOOGLE_ID, "ro")).thenThrow(new ResourceAccessException("down"));

        GoogleLinkDto link = service.placeLink(1L);

        assertThat(link.googlePlaceId()).isEqualTo(CETATE_GOOGLE_ID);
        assertThat(link.configured()).isTrue();
        assertThat(link.info()).isNull();
    }

    @Test
    void myLink_withoutBusiness_is404_setMyLink_savesOnMyBusiness() {
        when(links.businessOfOwner(3L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.myLink(GoogleMock.firma())).isInstanceOf(NotFoundException.class);

        when(links.businessOfOwner(3L)).thenReturn(Optional.of(GoogleMock.cafenea(null)));
        when(google.configured()).thenReturn(true);
        when(google.details("ChIJcafe", "ro")).thenReturn(GoogleMock.place(1));

        GoogleLinkDto link = service.setMyLink(GoogleMock.firma(), new GooglePlaceIdRequest("ChIJcafe"));

        verify(links).setBusinessGoogleId(1L, "ChIJcafe");
        assertThat(link.info().rating()).isEqualTo(4.6);
    }

    @Test
    void query_usesTheAddressUnlessItRepeatsTheName() {
        assertThat(GoogleService.query(GoogleMock.cetate(null))).isEqualTo("Cetatea Aradului, Cartier Subcetate, Arad");
        assertThat(GoogleService.query(new GoogleLinkRepository.PlaceRow(2L, "Gara", null, 46.19, 21.32, null))).isEqualTo("Gara, Arad");
    }

    /** Ceas de test care poate fi mutat înainte. */
    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
