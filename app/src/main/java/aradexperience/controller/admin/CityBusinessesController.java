package aradexperience.controller.admin;

import aradexperience.api.CityBusinessesApi;
import aradexperience.dto.admin.BusinessReject;
import aradexperience.dto.admin.CityBusinessCreate;
import aradexperience.dto.admin.CityBusinessCreated;
import aradexperience.dto.admin.CityBusinessDto;
import aradexperience.mapper.admin.AdminMapper;
import aradexperience.security.AuthContext;
import aradexperience.service.admin.PartnerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CityBusinessesController implements CityBusinessesApi {

    private final PartnerService service;

    @Override
    public List<CityBusinessDto> list(Boolean verified) {
        return service.list(verified).stream().map(AdminMapper::toDto).toList();
    }

    @Override
    public CityBusinessCreated create(CityBusinessCreate request) {
        PartnerService.Created c = service.create(request, AuthContext.require());
        return new CityBusinessCreated(AdminMapper.toDto(c.row()), c.invitePassword());
    }

    @Override
    public CityBusinessDto verify(long id) {
        return AdminMapper.toDto(service.verify(id, AuthContext.require()));
    }

    @Override
    public void reject(long id, BusinessReject request) {
        service.reject(id, request == null ? null : request.reason(), AuthContext.require());
    }
}
