package aradexperience.controller.community;

import aradexperience.api.CommunityApi;
import aradexperience.dto.community.ActionDto;
import aradexperience.dto.community.ActionSummary;
import aradexperience.dto.community.LeaderboardDto;
import aradexperience.dto.community.RegistrationDto;
import aradexperience.security.AuthContext;
import aradexperience.security.CurrentUser;
import aradexperience.service.community.CommunityService;
import aradexperience.service.points.PointsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static aradexperience.mapper.community.CommunityMapper.toDto;
import static aradexperience.mapper.community.CommunityMapper.toRegistration;
import static aradexperience.mapper.community.CommunityMapper.toSummary;
import static aradexperience.mapper.points.PointsMapper.toLeaderboard;

@RestController
@RequiredArgsConstructor
public class CommunityController implements CommunityApi {

    private final CommunityService service;
    private final PointsService points;

    @Override
    public List<ActionSummary> actions(boolean upcoming) {
        return service.list(upcoming, AuthContext.current()).stream().map(v -> toSummary(v)).toList();
    }

    @Override
    public ActionDto action(long id) {
        return toDto(service.get(id, AuthContext.current()));
    }

    @Override
    public RegistrationDto register(long id) {
        return toRegistration(service.register(id, AuthContext.require()));
    }

    @Override
    public void cancel(long id) {
        service.cancel(id, AuthContext.require());
    }

    @Override
    public List<RegistrationDto> myRegistrations() {
        return service.myRegistrations(AuthContext.require()).stream().map(v -> toRegistration(v)).toList();
    }

    @Override
    public LeaderboardDto leaderboard(String period) {
        return toLeaderboard(points.board(PointsService.Period.parse(period), AuthContext.current().map(CurrentUser::id).orElse(null)));
    }
}
