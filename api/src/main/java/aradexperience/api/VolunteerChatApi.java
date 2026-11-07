package aradexperience.api;

import aradexperience.dto.volunteer.ChatDto;
import aradexperience.dto.volunteer.ChatMessageDto;
import aradexperience.dto.volunteer.ChatSummaryDto;
import aradexperience.dto.volunteer.MessageRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * docs/API.md §Voluntari (T18) · chatul de grup: fiecare acțiune are un chat al voluntarilor alocați ei (text și poze).
 * Doar voluntarii acțiunii îl văd și scriu în el (403 altfel); Primăria nu e în chat.
 */
@Tag(name = "Volunteer · Chat")
@RequestMapping("/api/v1/vol")
public interface VolunteerChatApi {

    @Operation(summary = "Chaturile voluntarului (unul pe acțiune alocată): ultimul mesaj, mesaje noi")
    @GetMapping("/chats")
    List<ChatSummaryDto> chats();

    @Operation(summary = "Chatul unei acțiuni (ultimele 200 de mesaje); devine citit. 403 dacă acțiunea nu e a lui")
    @GetMapping("/actions/{id}/chat")
    ChatDto chat(@PathVariable long id);

    @Operation(summary = "Mesaj text în chatul acțiunii")
    @PostMapping("/actions/{id}/chat")
    @ResponseStatus(HttpStatus.CREATED)
    ChatMessageDto post(@PathVariable long id, @Valid @RequestBody MessageRequest request);

    @Operation(summary = "Poză (JPEG, PNG, WebP, GIF; max 10 MB) în chatul acțiunii, cu un text opțional")
    @PostMapping(path = "/actions/{id}/chat/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    ChatMessageDto postPhoto(@PathVariable long id, @RequestPart(name = "file", required = false) MultipartFile file,
                             @RequestParam(required = false) String text);
}
