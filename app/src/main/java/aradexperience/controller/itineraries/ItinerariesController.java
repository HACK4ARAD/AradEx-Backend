package aradexperience.controller.itineraries;

import aradexperience.api.ItinerariesApi;
import aradexperience.dto.itineraries.ItineraryDto;
import aradexperience.security.AuthContext;
import aradexperience.security.CurrentUser;
import aradexperience.service.itineraries.ItinerariesService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ItinerariesController implements ItinerariesApi {

    private final ItinerariesService service;

    @Override
    public ItineraryDto save(ItineraryDto itinerary) {
        return service.save(itinerary, AuthContext.current().map(CurrentUser::id));
    }

    @Override
    public ItineraryDto get(String idOrShareCode) {
        return service.get(idOrShareCode);
    }

    @Override
    public List<ItineraryDto> mine() {
        return service.mine(AuthContext.require().id());
    }
}
