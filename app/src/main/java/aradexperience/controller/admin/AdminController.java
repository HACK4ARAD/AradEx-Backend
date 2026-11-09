package aradexperience.controller.admin;

import aradexperience.api.AdminApi;
import aradexperience.dto.admin.AdminContentDto;
import aradexperience.dto.admin.AdminStatsDto;
import aradexperience.dto.admin.AdminUserDto;
import aradexperience.dto.admin.AdminUserUpdate;
import aradexperience.dto.admin.StatusUpdate;
import aradexperience.dto.admin.TemporaryPasswordResponse;
import aradexperience.dto.common.PageResponse;
import aradexperience.enums.Role;
import aradexperience.mapper.admin.AdminMapper;
import aradexperience.security.AuthContext;
import aradexperience.service.admin.AdminService;
import aradexperience.service.auth.PasswordService;
import aradexperience.util.Pages;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AdminController implements AdminApi {

    private final AdminService service;
    private final PasswordService passwords;

    @Override
    public AdminStatsDto stats() {
        return AdminMapper.toDto(service.stats());
    }

    @Override
    public PageResponse<AdminContentDto> content(String kind, String status, String q, boolean all, int page, int size) {
        AdminService.Content c = service.content(AdminService.Kind.parse(kind), status, q, all, page, size);
        return Pages.of(c.page(), item -> AdminMapper.toContent(item, c.businesses()));
    }

    @Override
    public AdminContentDto setStatus(String kind, long id, StatusUpdate request) {
        AdminService.Content c = service.setStatus(AdminService.Kind.parse(kind), id, request.status(), AuthContext.require());
        return AdminMapper.toContent(c.page().getContent().get(0), c.businesses());
    }

    @Override
    public PageResponse<AdminUserDto> users(Role role, String q, int page, int size) {
        return Pages.of(service.users(role, q, page, size), AdminMapper::toDto);
    }

    @Override
    public AdminUserDto updateUser(long id, AdminUserUpdate request) {
        return AdminMapper.toDto(service.updateUser(id, request, AuthContext.require()));
    }

    @Override
    public TemporaryPasswordResponse temporaryPassword(long id) {
        return new TemporaryPasswordResponse(passwords.temporaryPassword(id, AuthContext.require()));
    }
}
