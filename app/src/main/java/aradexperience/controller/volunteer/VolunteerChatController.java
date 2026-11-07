package aradexperience.controller.volunteer;

import aradexperience.api.VolunteerChatApi;
import aradexperience.dto.volunteer.ChatDto;
import aradexperience.dto.volunteer.ChatMessageDto;
import aradexperience.dto.volunteer.ChatSummaryDto;
import aradexperience.dto.volunteer.MessageRequest;
import aradexperience.exceptions.BadRequestException;
import aradexperience.mapper.volunteer.VolunteerMapper;
import aradexperience.security.AuthContext;
import aradexperience.security.CurrentUser;
import aradexperience.service.volunteer.VolunteerChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class VolunteerChatController implements VolunteerChatApi {

    private final VolunteerChatService service;

    @Override
    public List<ChatSummaryDto> chats() {
        CurrentUser me = AuthContext.require();
        return service.chats(me).stream().map(s -> VolunteerMapper.toSummary(s, me.id())).toList();
    }

    @Override
    public ChatDto chat(long id) {
        CurrentUser me = AuthContext.require();
        return VolunteerMapper.toChat(service.chat(id, me), me.id());
    }

    @Override
    public ChatMessageDto post(long id, MessageRequest request) {
        CurrentUser me = AuthContext.require();
        return VolunteerMapper.toMessage(service.post(id, request.text(), me), me.id());
    }

    @Override
    public ChatMessageDto postPhoto(long id, MultipartFile file, String text) {
        if (file == null) throw new BadRequestException("Missing file");
        CurrentUser me = AuthContext.require();
        try {
            return VolunteerMapper.toMessage(service.postPhoto(id, file.getBytes(), text, me), me.id());
        } catch (IOException e) {
            throw new BadRequestException("Could not read the file");
        }
    }
}
