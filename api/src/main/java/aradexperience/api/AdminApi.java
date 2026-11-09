package aradexperience.api;

import aradexperience.dto.admin.AdminContentDto;
import aradexperience.dto.admin.AdminStatsDto;
import aradexperience.dto.admin.AdminUserDto;
import aradexperience.dto.admin.AdminUserUpdate;
import aradexperience.dto.admin.StatusUpdate;
import aradexperience.dto.admin.TemporaryPasswordResponse;
import aradexperience.dto.common.PageResponse;
import aradexperience.enums.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * docs/API.md §Admin (T14): statistici, conținut & moderare, utilizatori (`/admin/**`, doar ADMIN).
 * Implementat de `controller/admin/AdminController` în `app`.
 */
@Tag(name = "Admin")
@RequestMapping("/api/v1/admin")
public interface AdminApi {

    @Operation(summary = "Statistici: utilizatori pe roluri, evenimente, bilete, oferte revendicate, coduri folosite, puncte, acțiuni")
    @GetMapping("/stats")
    AdminStatsDto stats();

    @Operation(summary = "Conținut & moderare: evenimente/oferte/acțiuni (kind), filtrate după stare și titlu; all=false → doar cele care nu s-au încheiat")
    @GetMapping("/{kind:events|offers|actions}")
    PageResponse<AdminContentDto> content(@PathVariable("kind") String kind,
                                          @RequestParam(name = "status", required = false) String status,
                                          @RequestParam(name = "q", required = false) String q,
                                          @RequestParam(name = "all", defaultValue = "false") boolean all,
                                          @RequestParam(name = "page", defaultValue = "0") int page,
                                          @RequestParam(name = "size", defaultValue = "50") int size);

    @Operation(summary = "Publică / ascunde un eveniment, o ofertă sau o acțiune (kind = events|offers|actions)")
    @PatchMapping("/{kind:events|offers|actions}/{id}/status")
    AdminContentDto setStatus(@PathVariable("kind") String kind, @PathVariable("id") long id, @Valid @RequestBody StatusUpdate request);

    @Operation(summary = "Utilizatorii, după rol și căutare (nume, e-mail, telefon)")
    @GetMapping("/users")
    PageResponse<AdminUserDto> users(@RequestParam(name = "role", required = false) Role role,
                                     @RequestParam(name = "q", required = false) String q,
                                     @RequestParam(name = "page", defaultValue = "0") int page,
                                     @RequestParam(name = "size", defaultValue = "20") int size);

    @Operation(summary = "Schimbă rolul, ajustează punctele (ADJUST, cu notă) sau dezactivează / reactivează contul")
    @PatchMapping("/users/{id}")
    AdminUserDto updateUser(@PathVariable("id") long id, @Valid @RequestBody AdminUserUpdate request);

    @Operation(summary = "Parolă temporară pentru un cont, arătată o singură dată (Adminul i-o dă omului); la intrare omul e obligat să-și aleagă alta, iar codul lui de recuperare vechi nu mai merge; 400 voluntar / fără email / cont demo, 403 alt Admin, 404")
    @PostMapping("/users/{id}/temporary-password")
    TemporaryPasswordResponse temporaryPassword(@PathVariable("id") long id);
}
