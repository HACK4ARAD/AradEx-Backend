package aradexperience.controller.transit;

import aradexperience.api.TransitApi;
import aradexperience.dto.transit.StopArrivalsDto;
import aradexperience.dto.transit.TransitRouteDto;
import aradexperience.dto.transit.TransitStopDto;
import aradexperience.dto.transit.TransitVehiclesDto;
import aradexperience.service.transit.TransitService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class TransitController implements TransitApi {

    private final TransitService service;

    @Override
    public List<TransitStopDto> stops() {
        return service.stops();
    }

    @Override
    public List<TransitRouteDto> routes() {
        return service.routes();
    }

    @Override
    public TransitVehiclesDto vehicles() {
        return service.vehicles();
    }

    @Override
    public StopArrivalsDto arrivals(int id) {
        return service.arrivals(id);
    }

    @Override
    public StopArrivalsDto nearest(Double lat, Double lng) {
        return service.nearest(lat, lng);
    }
}
