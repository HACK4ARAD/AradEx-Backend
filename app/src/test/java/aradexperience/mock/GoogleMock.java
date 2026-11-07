package aradexperience.mock;

import aradexperience.dto.google.GoogleInfoDto;
import aradexperience.dto.google.GoogleLinkDto;
import aradexperience.dto.google.GoogleReviewDto;
import aradexperience.enums.Role;
import aradexperience.repository.google.GoogleLinkRepository.BusinessRow;
import aradexperience.repository.google.GoogleLinkRepository.PlaceRow;
import aradexperience.security.CurrentUser;
import aradexperience.service.google.GooglePlaces;

import java.util.List;

/** Date de test T17: un obiectiv real (Cetatea), firma demo și un răspuns Google Places (New). */
public final class GoogleMock {

    public static final String CETATE_GOOGLE_ID = "ChIJ7VFfQACZRUcRXFuEhkS_zss";

    private GoogleMock() {}

    public static PlaceRow cetate(String googlePlaceId) {
        return new PlaceRow(1L, "Cetatea Aradului", "Cartier Subcetate, Arad", 46.1709, 21.3326, googlePlaceId);
    }

    public static BusinessRow cafenea(String googlePlaceId) {
        return new BusinessRow(1L, "Cafeneaua din Pasaj", googlePlaceId);
    }

    public static CurrentUser firma() {
        return new CurrentUser(3L, "firma@demo.ro", Role.BUSINESS);
    }

    public static GooglePlaces.Place place(int reviews) {
        List<GooglePlaces.Review> list = java.util.stream.IntStream.rangeClosed(1, reviews)
                .mapToObj(i -> new GooglePlaces.Review(5, new GooglePlaces.LocalizedText("Recenzia " + i, "ro"), null,
                        "acum " + i + " luni", new GooglePlaces.AuthorAttribution("Autor " + i, "https://g.co/a" + i, null)))
                .toList();
        return new GooglePlaces.Place(CETATE_GOOGLE_ID, new GooglePlaces.LocalizedText("Unitatea Militară Cetatea Aradului", "ro"), 4.6, 1240, "https://maps.google.com/?cid=1", list);
    }

    public static GoogleInfoDto info() {
        return new GoogleInfoDto(4.6, 1240,
                List.of(new GoogleReviewDto("Ana P.", "https://g.co/a1", null, 5, "Superb", "acum o lună")),
                "https://maps.google.com/?cid=1", GoogleInfoDto.ATTRIBUTION);
    }

    public static GoogleLinkDto link() {
        return new GoogleLinkDto(CETATE_GOOGLE_ID, true, info());
    }
}
