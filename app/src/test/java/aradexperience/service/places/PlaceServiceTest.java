package aradexperience.service.places;

import aradexperience.enums.PlaceType;
import aradexperience.exceptions.BadRequestException;
import aradexperience.exceptions.NotFoundException;
import aradexperience.mock.PlaceMock;
import aradexperience.repository.places.GuidePointRepository;
import aradexperience.repository.places.PlaceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaceServiceTest {

    @Mock PlaceRepository places;
    @Mock GuidePointRepository guidePoints;
    @InjectMocks PlaceService service;

    private void givenAll() {
        when(places.findAll(any(Sort.class))).thenReturn(PlaceMock.all());
        when(guidePoints.stats()).thenReturn(List.of(PlaceMock.stats(1L, 3, 250), PlaceMock.stats(2L, 4, 0)));
    }

    @Test
    void get_unknownPlace_is404() {
        when(places.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(99L, null)).isInstanceOf(NotFoundException.class).hasMessageContaining("Place 99");
    }

    @Test
    void get_returnsGuidePointsOrderedByPosition() {
        when(places.findById(1L)).thenReturn(Optional.of(PlaceMock.cetatea()));
        when(guidePoints.findByPlaceIdOrderByPositionAsc(1L))
                .thenReturn(List.of(PlaceMock.point(11, 1, 1, 60), PlaceMock.point(12, 1, 2, 90), PlaceMock.point(13, 1, 3, 100)));
        var d = service.get(1L, null);
        assertThat(d.points()).extracting(g -> g.getPosition()).containsExactly(1, 2, 3);
        assertThat(d.distanceKm()).isNull();
    }

    @Test
    void get_withNear_addsDistance() {
        when(places.findById(1L)).thenReturn(Optional.of(PlaceMock.cetatea()));
        when(guidePoints.findByPlaceIdOrderByPositionAsc(1L)).thenReturn(List.of());
        assertThat(service.get(1L, "46.1711,21.3319").distanceKm()).isZero();
    }

    @Test
    void list_withoutFilters_keepsIdOrder_andStats() {
        givenAll();
        var items = service.list(null, null, null, null);
        assertThat(items).extracting(i -> i.place().getId()).containsExactly(1L, 2L, 10L);
        assertThat(items.get(0).pointsCount()).isEqualTo(3);
        assertThat(items.get(0).seconds()).isEqualTo(250);
        assertThat(items.get(2).pointsCount()).isZero();
        assertThat(items).allMatch(i -> i.distanceKm() == null);
    }

    @Test
    void list_filtersByType_tagsAndTextWithoutDiacritics() {
        givenAll();
        assertThat(service.list(PlaceType.MUSEUM, null, null, null)).extracting(i -> i.place().getId()).containsExactly(2L);
        assertThat(service.list(null, null, null, "natura,arta")).extracting(i -> i.place().getId()).containsExactly(2L, 10L);
        assertThat(service.list(null, "padurea", null, null)).extracting(i -> i.place().getId()).containsExactly(10L);
        assertThat(service.list(null, "CETATEA", null, null)).extracting(i -> i.place().getId()).containsExactly(1L);
    }

    @Test
    void list_withNear_sortsByDistance() {
        givenAll();
        // lângă Palatul Cultural
        var items = service.list(null, null, "46.1757,21.3171", null);
        assertThat(items).extracting(i -> i.place().getId()).containsExactly(2L, 1L, 10L);
        assertThat(items.get(0).distanceKm()).isZero();
    }

    @Test
    void list_badNear_is400() {
        assertThatThrownBy(() -> service.list(null, null, "abc", null)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.list(null, null, "100,21", null)).isInstanceOf(BadRequestException.class);
    }
}
