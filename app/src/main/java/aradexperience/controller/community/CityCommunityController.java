package aradexperience.controller.community;

import aradexperience.api.CityCommunityApi;
import aradexperience.dto.community.ActionCreate;
import aradexperience.dto.community.ActionDto;
import aradexperience.dto.community.CityDashboardDto;
import aradexperience.dto.community.ConfirmCodeRequest;
import aradexperience.dto.community.ConfirmResultDto;
import aradexperience.dto.community.RegistrationDto;
import aradexperience.enums.RegistrationStatus;
import aradexperience.security.AuthContext;
import aradexperience.service.community.CityCommunityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static aradexperience.mapper.community.CommunityMapper.toConfirmResult;
import static aradexperience.mapper.community.CommunityMapper.toDto;
import static aradexperience.mapper.community.CommunityMapper.toRegistration;

@RestController
@RequiredArgsConstructor
public class CityCommunityController implements CityCommunityApi {

    private final CityCommunityService service;

    @Override
    public CityDashboardDto dashboard() {
        return service.dashboard();
    }

    @Override
    public List<ActionDto> actions() {
        return service.actions().stream().map(v -> toDto(v)).toList();
    }

    @Override
    public ActionDto create(ActionCreate request) {
        return toDto(service.create(request, AuthContext.require()));
    }

    @Override
    public ActionDto update(long id, ActionCreate request) {
        return toDto(service.update(id, request));
    }

    @Override
    public List<RegistrationDto> registrations(long id, RegistrationStatus status) {
        return service.registrations(id, status).stream().map(v -> toRegistration(v)).toList();
    }

    @Override
    public ConfirmResultDto confirmCode(ConfirmCodeRequest request) {
        return toConfirmResult(service.confirmByCode(request.code(), AuthContext.require()));
    }

    @Override
    public ConfirmResultDto confirm(long id) {
        return toConfirmResult(service.confirmById(id, AuthContext.require()));
    }

    @Override
    public RegistrationDto reject(long id) {
        return toRegistration(service.reject(id, AuthContext.require()));
    }
}
