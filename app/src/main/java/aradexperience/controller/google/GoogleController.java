package aradexperience.controller.google;

import aradexperience.api.GoogleApi;
import aradexperience.dto.google.GoogleInfoDto;
import aradexperience.dto.google.GoogleLinkDto;
import aradexperience.dto.google.GooglePlaceIdRequest;
import aradexperience.security.AuthContext;
import aradexperience.service.google.GoogleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class GoogleController implements GoogleApi {

    private final GoogleService service;

    @Override
    public GoogleInfoDto place(long id, String lang) {
        return service.placeInfo(id, lang);
    }

    @Override
    public GoogleInfoDto business(long id, String lang) {
        return service.businessInfo(id, lang);
    }

    @Override
    public GoogleLinkDto placeLink(long id) {
        return service.placeLink(id);
    }

    @Override
    public GoogleLinkDto setPlaceLink(long id, GooglePlaceIdRequest request) {
        return service.setPlaceLink(id, request);
    }

    @Override
    public GoogleLinkDto myLink() {
        return service.myLink(AuthContext.require());
    }

    @Override
    public GoogleLinkDto setMyLink(GooglePlaceIdRequest request) {
        return service.setMyLink(AuthContext.require(), request);
    }
}
