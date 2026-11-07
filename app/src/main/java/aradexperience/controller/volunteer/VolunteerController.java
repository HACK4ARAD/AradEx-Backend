package aradexperience.controller.volunteer;

import aradexperience.api.VolunteerApi;
import aradexperience.dto.auth.AuthResponse;
import aradexperience.dto.community.ConfirmResultDto;
import aradexperience.dto.community.RegistrationDto;
import aradexperience.dto.volunteer.VolunteerActionDto;
import aradexperience.dto.volunteer.VolunteerLoginRequest;
import aradexperience.dto.volunteer.VolunteerMeDto;
import aradexperience.dto.volunteer.VolunteerScanRequest;
import aradexperience.mapper.community.CommunityMapper;
import aradexperience.mapper.users.UserMapper;
import aradexperience.mapper.volunteer.VolunteerMapper;
import aradexperience.security.AuthContext;
import aradexperience.service.volunteer.VolunteerService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class VolunteerController implements VolunteerApi {

    private final VolunteerService service;
    private final HttpServletRequest request;

    @Override
    public AuthResponse login(VolunteerLoginRequest body) {
        VolunteerService.Session s = service.login(body.code(), clientKey());
        return new AuthResponse(s.token(), UserMapper.toDto(s.user(), null));
    }

    @Override
    public VolunteerMeDto me() {
        return VolunteerMapper.toMe(service.me(AuthContext.require()));
    }

    @Override
    public List<VolunteerActionDto> actions() {
        return service.actions(AuthContext.require()).stream().map(VolunteerMapper::toAction).toList();
    }

    @Override
    public ConfirmResultDto scan(VolunteerScanRequest body) {
        return CommunityMapper.toConfirmResult(service.scan(body, AuthContext.require()));
    }

    @Override
    public List<RegistrationDto> scans() {
        return service.recentScans(AuthContext.require()).stream().map(VolunteerMapper::toScan).toList();
    }

    /**
     * Adresa clientului pentru limita de coduri greșite. În spatele ALB-ului, ALB adaugă adresa văzută de el la FINALUL lui
     * X-Forwarded-For (primele valori le poate trimite oricine, deci nu contează); fără antet, adresa conexiunii.
     */
    private String clientKey() {
        String fwd = request.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) {
            String[] parts = fwd.split(",");
            return parts[parts.length - 1].trim();
        }
        return request.getRemoteAddr();
    }
}
