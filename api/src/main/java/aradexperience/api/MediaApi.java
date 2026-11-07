package aradexperience.api;

import aradexperience.dto.media.MediaDto;
import aradexperience.dto.media.MediaImportDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;

/**
 * docs/API.md §Poze (media) — pozele încărcate din panou stau în S3 (local: pe disc). Încărcarea și importul sunt pentru
 * Primărie și Admin (`/city/**`); citirea e publică (`GET /media/**`), cu cache lung (cheile nu se refolosesc).
 */
@Tag(name = "Media")
@RequestMapping("/api/v1")
public interface MediaApi {

    @Operation(summary = "Încarcă o poză (JPEG, PNG, WebP, GIF; max 10 MB); întoarce URL-ul public de pus în imageUrl")
    @PostMapping(path = "/city/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    MediaDto upload(@RequestPart(name = "file", required = false) MultipartFile file);

    @Operation(summary = "Copiază în S3 pozele obiectivelor (și ale punctelor de ghid) care sunt încă pe alte site-uri, câte `limit` pe cerere")
    @PostMapping("/city/media/import-places")
    MediaImportDto importPlaces(@RequestParam(defaultValue = "10") int limit);

    @Operation(summary = "O poză încărcată (public)")
    @GetMapping("/media/{folder}/{name}")
    ResponseEntity<Resource> get(@PathVariable String folder, @PathVariable String name);
}
