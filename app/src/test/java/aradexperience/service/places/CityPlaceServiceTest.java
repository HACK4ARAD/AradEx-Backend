package aradexperience.service.places;

import aradexperience.entity.places.GuidePoint;
import aradexperience.entity.places.Place;
import aradexperience.exceptions.ConflictException;
import aradexperience.exceptions.NotFoundException;
import aradexperience.mock.PlaceMock;
import aradexperience.repository.places.GuidePointRepository;
import aradexperience.repository.places.PlaceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CityPlaceServiceTest {

    @Mock PlaceRepository places;
    @Mock GuidePointRepository guidePoints;
    @InjectMocks CityPlaceService service;

    @Test
    void create_generatesUniqueSlugFromName_andNormalizesTags() {
        when(places.existsBySlug("muzeul-viei-si-vinului")).thenReturn(true);
        when(places.existsBySlug("muzeul-viei-si-vinului-2")).thenReturn(false);
        when(places.saveAndFlush(any(Place.class))).thenAnswer(inv -> { Place p = inv.getArgument(0); p.setId(13L); return p; });

        var d = service.create(PlaceMock.request("Muzeul Viei și Vinului", null));

        assertThat(d.place().getSlug()).isEqualTo("muzeul-viei-si-vinului-2");
        assertThat(d.place().getTags()).isEqualTo("muzee,istorie");
        assertThat(d.place().getVisitMinutes()).isEqualTo(45);
        assertThat(d.points()).isEmpty();
    }

    @Test
    void create_withTakenExplicitSlug_is409() {
        when(places.existsBySlug("cetatea-aradului")).thenReturn(true);
        assertThatThrownBy(() -> service.create(PlaceMock.request("Cetatea", "cetatea-aradului"))).isInstanceOf(ConflictException.class);
        verify(places, never()).saveAndFlush(any());
    }

    @Test
    void update_unknown_is404_andKeepsSlugWhenMissing() {
        when(places.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(99L, PlaceMock.request("X", null))).isInstanceOf(NotFoundException.class);

        Place cetatea = PlaceMock.cetatea();
        String slug = cetatea.getSlug();
        when(places.findById(1L)).thenReturn(Optional.of(cetatea));
        when(places.saveAndFlush(cetatea)).thenReturn(cetatea);
        var d = service.update(1L, PlaceMock.request("Cetatea Aradului (Vauban)", null));
        assertThat(d.place().getSlug()).isEqualTo(slug);
        assertThat(d.place().getName()).isEqualTo("Cetatea Aradului (Vauban)");
    }

    @Test
    void delete_placeUsedByEvents_is409() {
        Place cetatea = PlaceMock.cetatea();
        when(places.findById(1L)).thenReturn(Optional.of(cetatea));
        doThrow(new DataIntegrityViolationException("fk")).when(places).flush();
        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(ConflictException.class).hasMessageContaining("events");
    }

    @Test
    void addGuidePoint_withoutPosition_goesLast_andEstimatesDuration() {
        when(places.findById(2L)).thenReturn(Optional.of(PlaceMock.palatulCultural()));
        when(guidePoints.maxPosition(2L)).thenReturn(4);
        when(guidePoints.findByPlaceIdAndPosition(2L, 5)).thenReturn(Optional.empty());
        when(guidePoints.saveAndFlush(any(GuidePoint.class))).thenAnswer(inv -> inv.getArgument(0));

        GuidePoint g = service.addGuidePoint(2L, PlaceMock.pointRequest(null));

        assertThat(g.getPosition()).isEqualTo(5);
        assertThat(g.getPlaceId()).isEqualTo(2L);
        assertThat(g.getDurationSec()).isEqualTo(20);   // text scurt → minimum 20 s
    }

    @Test
    void addGuidePoint_takenPosition_is409_unknownPlace_is404() {
        when(places.findById(2L)).thenReturn(Optional.of(PlaceMock.palatulCultural()));
        when(guidePoints.findByPlaceIdAndPosition(2L, 1)).thenReturn(Optional.of(PlaceMock.point(20, 2, 1, 60)));
        assertThatThrownBy(() -> service.addGuidePoint(2L, PlaceMock.pointRequest(1))).isInstanceOf(ConflictException.class);

        when(places.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.addGuidePoint(99L, PlaceMock.pointRequest(null))).isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateGuidePoint_toTakenPosition_swapsWithOtherPoint() {
        GuidePoint first = PlaceMock.point(20, 2, 1, 60);
        GuidePoint second = PlaceMock.point(21, 2, 2, 60);
        when(guidePoints.findByIdAndPlaceId(21L, 2L)).thenReturn(Optional.of(second));
        when(guidePoints.findByPlaceIdAndPosition(2L, 1)).thenReturn(Optional.of(first));
        List<Integer> savedPositions = new ArrayList<>();
        when(guidePoints.saveAndFlush(any(GuidePoint.class))).thenAnswer(inv -> {
            GuidePoint g = inv.getArgument(0);
            savedPositions.add(g.getPosition());
            return g;
        });

        GuidePoint moved = service.updateGuidePoint(2L, 21L, PlaceMock.pointRequest(1));

        assertThat(moved.getPosition()).isEqualTo(1);
        assertThat(first.getPosition()).isEqualTo(2);
        assertThat(savedPositions).containsExactly(-1, 1, 2);   // poziție temporară, ca să nu lovim UNIQUE (place_id, position)
    }

    @Test
    void updateOrDeleteGuidePoint_ofOtherPlace_is404() {
        when(guidePoints.findByIdAndPlaceId(21L, 1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateGuidePoint(1L, 21L, PlaceMock.pointRequest(null))).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.deleteGuidePoint(1L, 21L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteGuidePoint_deletesIt() {
        GuidePoint g = PlaceMock.point(21, 2, 2, 60);
        when(guidePoints.findByIdAndPlaceId(21L, 2L)).thenReturn(Optional.of(g));
        service.deleteGuidePoint(2L, 21L);
        ArgumentCaptor<GuidePoint> captor = ArgumentCaptor.forClass(GuidePoint.class);
        verify(guidePoints).delete(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(21L);
    }

    @Test
    void slugify_stripsDiacriticsAndPunctuation() {
        assertThat(CityPlaceService.slugify("Teatrul Clasic „Ioan Slavici”")).isEqualTo("teatrul-clasic-ioan-slavici");
        assertThat(CityPlaceService.slugify("Ștrandul Neptun – Arad")).isEqualTo("strandul-neptun-arad");
        assertThat(CityPlaceService.slugify("„”")).isEqualTo("obiectiv");
    }
}
