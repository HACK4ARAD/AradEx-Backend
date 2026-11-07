package aradexperience.controller.events;

import aradexperience.api.EventsApi;
import aradexperience.dto.common.PageResponse;
import aradexperience.dto.events.EventCreate;
import aradexperience.dto.events.EventDto;
import aradexperience.dto.events.EventSummary;
import aradexperience.mapper.events.EventMapper;
import aradexperience.security.AuthContext;
import aradexperience.service.events.EventService;
import aradexperience.util.Pages;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static aradexperience.mapper.events.EventMapper.toDto;

@RestController
@RequiredArgsConstructor
public class EventController implements EventsApi {

    private final EventService service;

    @Override
    public PageResponse<EventSummary> list(String from, String to, String category, String near, Double radiusKm, Long organizerBusinessId,
                                           String organizerType, String ticketType, Boolean hot, String q, String sort, String status,
                                           int page, int size) {
        var user = AuthContext.current();
        var query = service.query(from, to, category, near, radiusKm, organizerBusinessId, organizerType, ticketType, hot, q, sort, status, page, size, user);
        return Pages.of(service.list(query, user), EventMapper::toSummary);
    }

    @Override
    public EventDto get(long id, String near) {
        return toDto(service.get(id, near, AuthContext.current()));
    }

    @Override
    public EventDto create(EventCreate request) {
        return toDto(service.create(request, AuthContext.require()));
    }

    @Override
    public EventDto update(long id, EventCreate request) {
        return toDto(service.update(id, request, AuthContext.require()));
    }

    @Override
    public void delete(long id) {
        service.delete(id, AuthContext.require());
    }

    @Override
    public void save(long id) {
        service.save(id, AuthContext.require());
    }

    @Override
    public void unsave(long id) {
        service.unsave(id, AuthContext.require());
    }

    @Override
    public List<EventSummary> saved(String near) {
        return service.savedBy(AuthContext.require(), near).stream().map(EventMapper::toSummary).toList();
    }
}
