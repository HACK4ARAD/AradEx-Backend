package aradexperience.controller.points;

import aradexperience.api.PointsApi;
import aradexperience.dto.points.PointsDto;
import aradexperience.security.AuthContext;
import aradexperience.service.points.PointsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import static aradexperience.mapper.points.PointsMapper.toDto;

@RestController
@RequiredArgsConstructor
public class PointsController implements PointsApi {

    private final PointsService service;

    @Override
    public PointsDto myPoints() {
        return toDto(service.summary(AuthContext.require()));
    }
}
