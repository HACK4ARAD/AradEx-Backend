package aradexperience.controller.itineraries;

import aradexperience.api.ItinerariesApi;
import aradexperience.dto.itineraries.ItineraryDto;
import aradexperience.security.AuthContext;
import aradexperience.security.CurrentUser;
import aradexperience.service.itineraries.ItinerariesService;
import aradexperience.util.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ItinerariesController implements ItinerariesApi {

    private final ItinerariesService service;
    private final HttpServletRequest request;

    @Override
    public ItineraryDto save(ItineraryDto itinerary) {
        return service.save(itinerary, AuthContext.current().map(CurrentUser::id), ClientIp.of(request));
    }

    @Override
    public ItineraryDto get(String idOrShareCode) {
        return service.get(idOrShareCode, AuthContext.current().map(CurrentUser::id));
    }

    @Override
    public ItineraryDto saveToMine(String shareCode) {
        return service.saveToMine(shareCode, AuthContext.require().id());
    }

    @Override
    public List<ItineraryDto> mine() {
        return service.mine(AuthContext.require().id());
    }
}
