package aradexperience.controller.tickets;

import aradexperience.api.TicketsApi;
import aradexperience.dto.tickets.TicketDto;
import aradexperience.dto.tickets.TicketPaymentDto;
import aradexperience.mapper.tickets.TicketMapper;
import aradexperience.dto.tickets.TicketRequest;
import aradexperience.security.AuthContext;
import aradexperience.service.tickets.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static aradexperience.mapper.tickets.TicketMapper.toDto;

@RestController
@RequiredArgsConstructor
public class TicketController implements TicketsApi {

    private final TicketService service;

    @Override
    public TicketDto buy(long id, TicketRequest request) {
        TicketService.TicketView v = service.buy(id, request, AuthContext.require());
        return toDto(v.ticket(), v.event());
    }

    @Override
    public TicketPaymentDto preparePayment(long id, TicketRequest request) {
        return TicketMapper.toPaymentDto(service.preparePayment(id, request, AuthContext.require()));
    }

    @Override
    public List<TicketDto> mine(Boolean upcoming) {
        return service.mine(AuthContext.require(), upcoming).stream().map(v -> toDto(v.ticket(), v.event())).toList();
    }

    @Override
    public TicketDto get(String code) {
        TicketService.TicketView v = service.get(code, AuthContext.require());
        return toDto(v.ticket(), v.event());
    }

    @Override
    public TicketDto use(String code) {
        TicketService.TicketView v = service.use(code, AuthContext.require());
        return toDto(v.ticket(), v.event());
    }

    @Override
    public ResponseEntity<String> ics(long id) {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "calendar", java.nio.charset.StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"aradexperience-" + id + ".ics\"")
                .body(service.ics(id));
    }
}
