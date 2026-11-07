package aradexperience.api;

import aradexperience.dto.business.AnnouncementCreate;
import aradexperience.dto.business.AnnouncementDto;
import aradexperience.dto.business.BusinessActivityDto;
import aradexperience.dto.business.BusinessDto;
import aradexperience.dto.business.BusinessMeDto;
import aradexperience.dto.business.BusinessPageDto;
import aradexperience.dto.business.BusinessUpdate;
import aradexperience.dto.events.EventCreate;
import aradexperience.dto.events.EventDto;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;

/**
 * docs/API.md §Firme (T09): pagina publică a firmei și panoul firmei (`/business/**`, rol BUSINESS; ADMIN trece peste tot).
 * Implementat de `controller/business/BusinessController` în `app`.
 */
@Tag(name = "Businesses")
@RequestMapping("/api/v1")
public interface BusinessApi {

    @Operation(summary = "Pagina publică a firmei: profil, oferte active, evenimente viitoare, anunțuri; incrementează viewsCount")
    @GetMapping("/businesses/{id}")
    BusinessPageDto page(@PathVariable("id") long id);

    @Operation(summary = "Firma mea + statistici (views, codesUsed, pointsPayments, upcomingEvents)")
    @GetMapping("/business/me")
    BusinessMeDto me();

    @Operation(summary = "Actualizează profilul firmei mele (fără verified)")
    @PutMapping("/business/me")
    BusinessDto updateMe(@Valid @RequestBody BusinessUpdate request);

    @Operation(summary = "Evenimentele firmei mele (inclusiv ciorne și trecute)")
    @GetMapping("/business/events")
    List<EventDto> events();

    @Operation(summary = "Eveniment nou al firmei; status DRAFT dacă firma nu e verificată")
    @PostMapping("/business/events")
    @ResponseStatus(HttpStatus.CREATED)
    EventDto createEvent(@Valid @RequestBody EventCreate request);

    @Operation(summary = "Editează un eveniment al firmei mele; 403 dacă e al altei firme")
    @PutMapping("/business/events/{id}")
    EventDto updateEvent(@PathVariable("id") long id, @Valid @RequestBody EventCreate request);

    @Operation(summary = "Șterge un eveniment al firmei mele; 403 dacă e al altei firme")
    @DeleteMapping("/business/events/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteEvent(@PathVariable("id") long id);

    @Operation(summary = "Anunțurile firmei mele")
    @GetMapping("/business/announcements")
    List<AnnouncementDto> announcements();

    @Operation(summary = "Anunț nou; status DRAFT dacă firma nu e verificată")
    @PostMapping("/business/announcements")
    @ResponseStatus(HttpStatus.CREATED)
    AnnouncementDto createAnnouncement(@Valid @RequestBody AnnouncementCreate request);

    @Operation(summary = "Șterge un anunț al firmei mele; 403 dacă e al altei firme")
    @DeleteMapping("/business/announcements/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteAnnouncement(@PathVariable("id") long id);

    @Operation(summary = "Activitatea recentă a firmei (ultimele 20)")
    @GetMapping("/business/activity")
    BusinessActivityDto activity();
}
