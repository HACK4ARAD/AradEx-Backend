package aradexperience.api;

import aradexperience.dto.common.PageResponse;
import aradexperience.dto.events.EventCreate;
import aradexperience.dto.events.EventDto;
import aradexperience.dto.events.EventSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;

/** docs/API.md §Evenimente (T06). Implementat de `controller/events/EventController` în `app`. */
@Tag(name = "Events")
@RequestMapping("/api/v1")
public interface EventsApi {

    @Operation(summary = "Evenimente publicate, sortate după dată (sau distanță); fără `from` doar cele care nu s-au terminat")
    @GetMapping("/events")
    PageResponse<EventSummary> list(
            @Parameter(description = "zi de început YYYY-MM-DD (Europe/Bucharest), inclusiv") @RequestParam(name = "from", required = false) String from,
            @Parameter(description = "zi de sfârșit YYYY-MM-DD, inclusiv") @RequestParam(name = "to", required = false) String to,
            @Parameter(description = "una sau mai multe, separate prin virgulă: CONCERT,THEATRE") @RequestParam(name = "category", required = false) String category,
            @Parameter(description = "lat,lng — completează distanceKm") @RequestParam(name = "near", required = false) String near,
            @RequestParam(name = "radiusKm", required = false) Double radiusKm,
            @RequestParam(name = "organizerBusinessId", required = false) Long organizerBusinessId,
            @Parameter(description = "CITY | BUSINESS | ADMIN | EXTERNAL, separate prin virgulă") @RequestParam(name = "organizerType", required = false) String organizerType,
            @Parameter(description = "FREE | IN_APP | EXTERNAL, separate prin virgulă") @RequestParam(name = "ticketType", required = false) String ticketType,
            @RequestParam(name = "hot", required = false) Boolean hot,
            @Parameter(description = "caută în titlu, loc și organizator (fără diacritice)") @RequestParam(name = "q", required = false) String q,
            @Parameter(description = "date (implicit) | distance (cere near)") @RequestParam(name = "sort", required = false) String sort,
            @Parameter(description = "doar Primărie/Admin: DRAFT | HIDDEN | ALL (implicit doar PUBLISHED)") @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size);

    @Operation(summary = "Detaliul unui eveniment; 404 dacă nu există sau nu e publicat")
    @GetMapping("/events/{id}")
    EventDto get(@PathVariable("id") long id, @RequestParam(name = "near", required = false) String near);

    @Operation(summary = "Eveniment nou (Primăria / Adminul)")
    @PostMapping("/events")
    @ResponseStatus(HttpStatus.CREATED)
    EventDto create(@Valid @RequestBody EventCreate request);

    @Operation(summary = "Modifică un eveniment (creatorul sau Adminul)")
    @PutMapping("/events/{id}")
    EventDto update(@PathVariable("id") long id, @Valid @RequestBody EventCreate request);

    @Operation(summary = "Șterge (ascunde) un eveniment (creatorul sau Adminul)")
    @DeleteMapping("/events/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable("id") long id);

    @Operation(summary = "Salvează evenimentul (idempotent)")
    @PostMapping("/events/{id}/save")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void save(@PathVariable("id") long id);

    @Operation(summary = "Scoate evenimentul din Salvate (idempotent)")
    @DeleteMapping("/events/{id}/save")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unsave(@PathVariable("id") long id);

    @Operation(summary = "Evenimentele salvate de utilizatorul curent, după dată")
    @GetMapping("/me/saved-events")
    List<EventSummary> saved(@RequestParam(name = "near", required = false) String near);
}
