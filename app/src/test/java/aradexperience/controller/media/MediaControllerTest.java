package aradexperience.controller.media;

import aradexperience.controller.ControllerTest;
import aradexperience.dto.media.MediaDto;
import aradexperience.dto.media.MediaImportDto;
import aradexperience.enums.Role;
import aradexperience.exceptions.BadRequestException;
import aradexperience.mock.AuthMock;
import aradexperience.mock.MediaMock;
import aradexperience.service.media.MediaImportService;
import aradexperience.service.media.MediaService;
import aradexperience.service.media.MediaStorage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ControllerTest(MediaController.class)
class MediaControllerTest {

    @Autowired MockMvc mvc;
    @MockBean MediaService service;
    @MockBean MediaImportService importService;

    private final MockMultipartFile file = new MockMultipartFile("file", "poza.jpg", "image/jpeg", MediaMock.JPEG);

    @Test
    void upload_asCityHallOrAdmin_is201() throws Exception {
        when(service.upload(any())).thenReturn(new MediaDto("https://api.test/api/v1/media/uploads/ab.jpg", "uploads/ab.jpg", "image/jpeg", 13));
        mvc.perform(multipart("/api/v1/city/media").file(file).with(AuthMock.as(4L, Role.CITY_HALL)))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.url").value("https://api.test/api/v1/media/uploads/ab.jpg"))
           .andExpect(jsonPath("$.contentType").value("image/jpeg"));
        mvc.perform(multipart("/api/v1/city/media").file(file).with(AuthMock.as(5L, Role.ADMIN))).andExpect(status().isCreated());
    }

    @Test
    void upload_withoutToken_is401_wrongRole_is403_noFile_or_badImage_is400() throws Exception {
        mvc.perform(multipart("/api/v1/city/media").file(file)).andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/v1/city/media").file(file).with(AuthMock.as(2L, Role.LOCAL))).andExpect(status().isForbidden());
        mvc.perform(multipart("/api/v1/city/media").with(AuthMock.as(4L, Role.CITY_HALL))).andExpect(status().isBadRequest());
        when(service.upload(any())).thenThrow(new BadRequestException("Only JPEG, PNG, WebP or GIF images"));
        mvc.perform(multipart("/api/v1/city/media").file(file).with(AuthMock.as(4L, Role.CITY_HALL)))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.detail").value("Only JPEG, PNG, WebP or GIF images"));
    }

    @Test
    void get_isPublic_withLongCache() throws Exception {
        when(service.load("places", "cetatea-ab12cd34.jpg")).thenReturn(new MediaStorage.Stored(MediaMock.JPEG, "image/jpeg"));
        mvc.perform(get("/api/v1/media/places/cetatea-ab12cd34.jpg"))
           .andExpect(status().isOk())
           .andExpect(header().string("Content-Type", "image/jpeg"))
           .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("immutable")))
           .andExpect(content().bytes(MediaMock.JPEG));
    }

    @Test
    void importPlaces_cityHallOnly() throws Exception {
        when(importService.importPlaces(5)).thenReturn(new MediaImportDto(5, 0, 12));
        mvc.perform(post("/api/v1/city/media/import-places?limit=5").with(AuthMock.as(4L, Role.CITY_HALL)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.imported").value(5))
           .andExpect(jsonPath("$.remaining").value(12));
        mvc.perform(post("/api/v1/city/media/import-places")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/city/media/import-places").with(AuthMock.as(3L, Role.BUSINESS))).andExpect(status().isForbidden());
    }
}
