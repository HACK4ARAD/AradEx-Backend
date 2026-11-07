package aradexperience.controller.volunteer;

import aradexperience.api.CityVolunteersApi;
import aradexperience.dto.volunteer.CoordinatorDto;
import aradexperience.dto.volunteer.VolunteerCreate;
import aradexperience.dto.volunteer.VolunteerDto;
import aradexperience.dto.volunteer.VolunteerUpdate;
import aradexperience.mapper.volunteer.VolunteerMapper;
import aradexperience.security.AuthContext;
import aradexperience.service.volunteer.CityVolunteerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CityVolunteersController implements CityVolunteersApi {

    private final CityVolunteerService service;

    @Override
    public List<VolunteerDto> list() {
        return service.list().stream().map(VolunteerMapper::toDto).toList();
    }

    @Override
    public VolunteerDto create(VolunteerCreate request) {
        return VolunteerMapper.toDto(service.create(request, AuthContext.require()));
    }

    @Override
    public VolunteerDto update(long id, VolunteerUpdate request) {
        return VolunteerMapper.toDto(service.update(id, request));
    }

    @Override
    public List<CoordinatorDto> coordinators() {
        return service.coordinators().stream().map(VolunteerMapper::toCoordinator).toList();
    }
}
