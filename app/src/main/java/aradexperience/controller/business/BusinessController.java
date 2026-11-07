package aradexperience.controller.business;

import aradexperience.api.BusinessApi;
import aradexperience.config.AppProps;
import aradexperience.dto.business.AnnouncementCreate;
import aradexperience.dto.business.AnnouncementDto;
import aradexperience.dto.business.BusinessActivityDto;
import aradexperience.dto.business.BusinessDto;
import aradexperience.dto.business.BusinessMeDto;
import aradexperience.dto.business.BusinessPageDto;
import aradexperience.dto.business.BusinessUpdate;
import aradexperience.dto.events.EventCreate;
import aradexperience.dto.events.EventDto;
import aradexperience.mapper.offers.OfferMapper;
import aradexperience.security.AuthContext;
import aradexperience.service.business.BusinessService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static aradexperience.mapper.business.BusinessMapper.toAnnouncementDto;
import static aradexperience.mapper.business.BusinessMapper.toDto;
import static aradexperience.mapper.events.EventMapper.toDto;
import static aradexperience.mapper.events.EventMapper.toSummary;

@RestController
@RequiredArgsConstructor
public class BusinessController implements BusinessApi {

    private final BusinessService service;
    private final AppProps props;

    @Override
    public BusinessPageDto page(long id) {
        BusinessService.Page p = service.page(id);
        String name = p.business().getName();
        return new BusinessPageDto(toDto(p.business()), p.offers().stream().map(o -> OfferMapper.toSummary(o, name, null)).toList(),
                p.events().stream().map(e -> toSummary(e, props.events())).toList(),
                p.announcements().stream().map(a -> toAnnouncementDto(a)).toList());
    }

    @Override
    public BusinessMeDto me() {
        BusinessService.Dashboard d = service.dashboard(AuthContext.require());
        return new BusinessMeDto(toDto(d.business()),
                new BusinessMeDto.Stats(d.business().getViewsCount(), d.codesUsed(), d.pointsPayments(), d.upcomingEvents()));
    }

    @Override
    public BusinessDto updateMe(BusinessUpdate request) {
        return toDto(service.updateMe(AuthContext.require(), request));
    }

    @Override
    public List<EventDto> events() {
        return service.myEvents(AuthContext.require()).stream().map(e -> toDto(e, props.events())).toList();
    }

    @Override
    public EventDto createEvent(EventCreate request) {
        return toDto(service.createEvent(AuthContext.require(), request), props.events());
    }

    @Override
    public EventDto updateEvent(long id, EventCreate request) {
        return toDto(service.updateEvent(AuthContext.require(), id, request), props.events());
    }

    @Override
    public void deleteEvent(long id) {
        service.deleteEvent(AuthContext.require(), id);
    }

    @Override
    public List<AnnouncementDto> announcements() {
        return service.myAnnouncements(AuthContext.require()).stream().map(a -> toAnnouncementDto(a)).toList();
    }

    @Override
    public AnnouncementDto createAnnouncement(AnnouncementCreate request) {
        return toAnnouncementDto(service.createAnnouncement(AuthContext.require(), request));
    }

    @Override
    public void deleteAnnouncement(long id) {
        service.deleteAnnouncement(AuthContext.require(), id);
    }

    @Override
    public BusinessActivityDto activity() {
        return service.activity(AuthContext.require());
    }
}
