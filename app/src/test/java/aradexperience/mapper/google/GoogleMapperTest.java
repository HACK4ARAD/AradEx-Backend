package aradexperience.mapper.google;

import aradexperience.dto.google.GoogleInfoDto;
import aradexperience.dto.google.GoogleReviewDto;
import aradexperience.service.google.GooglePlaces;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleMapperTest {

    private static GooglePlaces.Review review(int stars, String author, String text) {
        return new GooglePlaces.Review(stars, text == null ? null : new GooglePlaces.LocalizedText(text, "ro"), null,
                "acum o lună", new GooglePlaces.AuthorAttribution(author, null, null));
    }

    private static GooglePlaces.Place place(List<GooglePlaces.Review> reviews) {
        return new GooglePlaces.Place("ChIJx", new GooglePlaces.LocalizedText("Palatul Cultural", "ro"), 4.8, 631,
                "https://maps.google.com/?cid=1", reviews);
    }

    @Test
    void reviews_mostStarsFirst_equalStarsKeepGoogleOrder() {
        GoogleInfoDto info = GoogleMapper.toInfo(place(List.of(
                review(3, "C3", "ok"), review(5, "A5", "superb"), review(1, "E1", "slab"),
                review(5, "B5", "minunat"), review(4, "D4", "frumos"))));

        assertThat(info.reviews()).extracting(GoogleReviewDto::author).containsExactly("A5", "B5", "D4", "C3", "E1");
        assertThat(info.reviews()).extracting(GoogleReviewDto::rating).containsExactly(5, 5, 4, 3, 1);
    }

    @Test
    void reviewsWithoutText_areDropped_andAtMostFiveAreKept() {
        GoogleInfoDto info = GoogleMapper.toInfo(place(List.of(
                review(5, "fără text", null), review(2, "A", "a"), review(4, "B", "b"), review(3, "C", "c"),
                review(1, "D", "d"), review(5, "E", "e"), review(4, "F", "f"))));

        assertThat(info.reviews()).hasSize(5);
        assertThat(info.reviews()).extracting(GoogleReviewDto::author).doesNotContain("fără text");
        assertThat(info.reviews()).extracting(GoogleReviewDto::rating).containsExactly(5, 4, 4, 3, 2);
    }

    @Test
    void noReviews_isAnEmptyList() {
        assertThat(GoogleMapper.toInfo(place(null)).reviews()).isEmpty();
    }
}
