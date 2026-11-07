package aradexperience.controller.volunteer;

import aradexperience.controller.ControllerTest;
import aradexperience.enums.Role;
import aradexperience.exceptions.ForbiddenException;
import aradexperience.mock.AuthMock;
import aradexperience.mock.CommunityMock;
import aradexperience.mock.JsonFixtures;
import aradexperience.mock.MediaMock;
import aradexperience.mock.VolunteerMock;
import aradexperience.service.volunteer.VolunteerChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ControllerTest(VolunteerChatController.class)
class VolunteerChatControllerTest {

    @Autowired MockMvc mvc;
    @MockBean VolunteerChatService service;

    @Test
    void chat_onlyForVolunteers_withMineAndPhotos() throws Exception {
        var a = VolunteerMock.actionAt(1L, CommunityMock.NOW.plusHours(1));
        when(service.chat(eq(1L), any())).thenReturn(new VolunteerChatService.Chat(a, List.of(VolunteerMock.volunteerUser(), VolunteerMock.otherVolunteerUser()), List.of(
                new VolunteerChatService.MessageView(VolunteerMock.chat(1, 1, VolunteerMock.OTHER_VOL_ID, null, "/img/actiune-plantare-copaci.jpg"), "Maria Ionescu"),
                new VolunteerChatService.MessageView(VolunteerMock.chat(2, 1, VolunteerMock.VOL_ID, "Super.", null), "Andrei Popa"))));
        mvc.perform(get("/api/v1/vol/actions/1/chat")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/vol/actions/1/chat").with(AuthMock.as(4L, Role.CITY_HALL))).andExpect(status().isForbidden());   // Primăria nu e în chat
        mvc.perform(get("/api/v1/vol/actions/1/chat").with(AuthMock.as(VolunteerMock.VOL_ID, Role.VOLUNTEER)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.members.length()").value(2))
           .andExpect(jsonPath("$.messages[0].mine").value(false))
           .andExpect(jsonPath("$.messages[0].imageUrl").value("/img/actiune-plantare-copaci.jpg"))
           .andExpect(jsonPath("$.messages[1].mine").value(true));
    }

    @Test
    void post_text201_notAssigned403_photo201_missingFile400() throws Exception {
        when(service.post(eq(1L), any(), any())).thenReturn(new VolunteerChatService.MessageView(VolunteerMock.chat(5, 1, VolunteerMock.VOL_ID, "Am ajuns la foișor.", null), "Andrei Popa"));
        mvc.perform(post("/api/v1/vol/actions/1/chat").with(AuthMock.as(VolunteerMock.VOL_ID, Role.VOLUNTEER))
                .contentType(MediaType.APPLICATION_JSON).content(JsonFixtures.read("requests/volunteer/message.json")))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.mine").value(true))
           .andExpect(jsonPath("$.actionId").value(1));
        when(service.post(eq(2L), any(), any())).thenThrow(new ForbiddenException("You are not assigned to this action"));
        mvc.perform(post("/api/v1/vol/actions/2/chat").with(AuthMock.as(VolunteerMock.VOL_ID, Role.VOLUNTEER))
                .contentType(MediaType.APPLICATION_JSON).content(JsonFixtures.read("requests/volunteer/message.json")))
           .andExpect(status().isForbidden());

        when(service.postPhoto(anyLong(), any(), any(), any())).thenReturn(new VolunteerChatService.MessageView(
                VolunteerMock.chat(6, 1, VolunteerMock.VOL_ID, "Gata!", "https://api.test/api/v1/media/chat/ab.jpg"), "Andrei Popa"));
        mvc.perform(multipart("/api/v1/vol/actions/1/chat/photo").file(new MockMultipartFile("file", "p.jpg", "image/jpeg", MediaMock.JPEG))
                .param("text", "Gata!").with(AuthMock.as(VolunteerMock.VOL_ID, Role.VOLUNTEER)))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.imageUrl").value("https://api.test/api/v1/media/chat/ab.jpg"));
        mvc.perform(multipart("/api/v1/vol/actions/1/chat/photo").with(AuthMock.as(VolunteerMock.VOL_ID, Role.VOLUNTEER)))
           .andExpect(status().isBadRequest());
    }
}
