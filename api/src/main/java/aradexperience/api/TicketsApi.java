package aradexperience.api;

import aradexperience.dto.tickets.TicketDto;
import aradexperience.dto.tickets.TicketRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;

/** docs/API.md §Evenimente (T06) & bilete (T07): bilete, înscrieri gratuite, calendar. Implementat de `controller/tickets/TicketController`. */
@Tag(name = "Tickets")
@RequestMapping("/api/v1")
public interface TicketsApi {

    @Operation(summary = "Cumpără bilete (IN_APP) sau înscrie-te (FREE cu locuri); 409 dacă nu mai sunt locuri, 400 pentru EXTERNAL")
    @PostMapping("/events/{id}/tickets")
    @ResponseStatus(HttpStatus.CREATED)
    TicketDto buy(@PathVariable("id") long id, @Valid @RequestBody TicketRequest request);

    @Operation(summary = "Biletele mele: upcoming=true (viitoare, după dată) / false (trecute, cele recente primele) / lipsă (toate)")
    @GetMapping("/me/tickets")
    List<TicketDto> mine(@Parameter(description = "true | false") @RequestParam(name = "upcoming", required = false) Boolean upcoming);

    @Operation(summary = "Un bilet după cod: proprietarul, organizatorul sau Adminul")
    @GetMapping("/tickets/{code}")
    TicketDto get(@PathVariable("code") String code);

    @Operation(summary = "Marchează biletul folosit la intrare (organizatorul sau Adminul); 409 dacă era deja folosit")
    @PostMapping("/tickets/{code}/use")
    TicketDto use(@PathVariable("code") String code);

    @Operation(summary = "Fișier .ics pentru „Adaugă în calendar”")
    @GetMapping(value = "/events/{id}/ics", produces = "text/calendar")
    ResponseEntity<String> ics(@PathVariable("id") long id);
}
