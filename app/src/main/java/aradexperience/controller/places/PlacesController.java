package aradexperience.controller.places;

import aradexperience.api.PlacesApi;
import aradexperience.dto.places.PlaceDto;
import aradexperience.dto.places.PlaceSummary;
import aradexperience.enums.PlaceType;
import aradexperience.service.places.PlaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static aradexperience.mapper.places.PlaceMapper.toDto;
import static aradexperience.mapper.places.PlaceMapper.toSummary;

@RestController
@RequiredArgsConstructor
public class PlacesController implements PlacesApi {

    private final PlaceService service;

    @Override
    public List<PlaceSummary> list(PlaceType type, String q, String near, String tags) {
        return service.list(type, q, near, tags).stream()
                .map(i -> toSummary(i.place(), i.pointsCount(), i.seconds(), i.distanceKm())).toList();
    }

    @Override
    public PlaceDto get(long id, String near) {
        PlaceService.PlaceDetail d = service.get(id, near);
        return toDto(d.place(), d.points(), d.distanceKm());
    }
}
