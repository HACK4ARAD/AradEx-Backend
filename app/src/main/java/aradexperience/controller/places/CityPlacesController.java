package aradexperience.controller.places;

import aradexperience.api.CityPlacesApi;
import aradexperience.dto.places.GuidePointDto;
import aradexperience.dto.places.GuidePointRequest;
import aradexperience.dto.places.PlaceDto;
import aradexperience.dto.places.PlaceRequest;
import aradexperience.service.places.CityPlaceService;
import aradexperience.service.places.PlaceService.PlaceDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import static aradexperience.mapper.places.PlaceMapper.toDto;
import static aradexperience.mapper.places.PlaceMapper.toGuidePointDto;

@RestController
@RequiredArgsConstructor
public class CityPlacesController implements CityPlacesApi {

    private final CityPlaceService service;

    @Override
    public PlaceDto create(PlaceRequest request) {
        return dto(service.create(request));
    }

    @Override
    public PlaceDto update(long id, PlaceRequest request) {
        return dto(service.update(id, request));
    }

    @Override
    public void delete(long id) {
        service.delete(id);
    }

    @Override
    public GuidePointDto addGuidePoint(long id, GuidePointRequest request) {
        return toGuidePointDto(service.addGuidePoint(id, request));
    }

    @Override
    public GuidePointDto updateGuidePoint(long id, long gpId, GuidePointRequest request) {
        return toGuidePointDto(service.updateGuidePoint(id, gpId, request));
    }

    @Override
    public void deleteGuidePoint(long id, long gpId) {
        service.deleteGuidePoint(id, gpId);
    }

    private static PlaceDto dto(PlaceDetail d) {
        return toDto(d.place(), d.points(), d.distanceKm());
    }
}
