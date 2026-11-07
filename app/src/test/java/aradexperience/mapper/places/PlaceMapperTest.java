package aradexperience.mapper.places;

import aradexperience.mock.PlaceMock;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceMapperTest {

    @Test
    void toDto_countsPointsAndRoundsMinutesUp() {
        var dto = PlaceMapper.toDto(PlaceMock.cetatea(), List.of(PlaceMock.point(1, 1, 1, 70), PlaceMock.point(2, 1, 2, 80)), null);
        assertThat(dto.guidePointsCount()).isEqualTo(2);
        assertThat(dto.guideMinutes()).isEqualTo(3);   // 150 s → 3 min
        assertThat(dto.tags()).containsExactly("istorie", "familie", "foto");
        assertThat(dto.rating()).isEqualTo(4.6);
        assertThat(dto.exteriorOnly()).isTrue();
    }

    @Test
    void guideMinutes_withoutDurations_isOneMinutePerPoint() {
        assertThat(PlaceMapper.guideMinutes(0, 0)).isZero();
        assertThat(PlaceMapper.guideMinutes(4, 0)).isEqualTo(4);
        assertThat(PlaceMapper.guideMinutes(3, 250)).isEqualTo(5);
    }

    @Test
    void readingSeconds_isAbout140WordsPerMinute() {
        String hundredFortyWords = String.join(" ", Arrays.stream(new String[140]).map(s -> "cuvânt").toList());
        assertThat(PlaceMapper.readingSeconds(hundredFortyWords)).isEqualTo(60);
        assertThat(PlaceMapper.readingSeconds("scurt")).isEqualTo(20);
    }

    @Test
    void tags_areSplitAndJoinedNormalized() {
        assertThat(PlaceMapper.splitTags(null)).isEmpty();
        assertThat(PlaceMapper.splitTags("a, b,,c")).containsExactly("a", "b", "c");
        assertThat(PlaceMapper.joinTags(List.of(" Istorie ", "istorie", "vin,gastronomie", ""))).isEqualTo("istorie,vin gastronomie");
        assertThat(PlaceMapper.joinTags(List.of())).isNull();
    }
}
