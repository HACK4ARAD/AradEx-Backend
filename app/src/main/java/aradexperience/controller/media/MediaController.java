package aradexperience.controller.media;

import aradexperience.api.MediaApi;
import aradexperience.dto.media.MediaDto;
import aradexperience.dto.media.MediaImportDto;
import aradexperience.exceptions.BadRequestException;
import aradexperience.service.media.MediaImportService;
import aradexperience.service.media.MediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;

@RestController
@RequiredArgsConstructor
public class MediaController implements MediaApi {

    private final MediaService service;
    private final MediaImportService importService;

    @Override
    public MediaDto upload(MultipartFile file) {
        if (file == null) throw new BadRequestException("Missing file");
        try {
            return service.upload(file.getBytes());
        } catch (IOException e) {
            throw new BadRequestException("Could not read the file");
        }
    }

    @Override
    public MediaImportDto importPlaces(int limit) {
        return importService.importPlaces(limit);
    }

    @Override
    public ResponseEntity<Resource> get(String folder, String name) {
        var stored = service.load(folder, name);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .contentType(MediaType.parseMediaType(stored.contentType()))
                .contentLength(stored.bytes().length)
                .body(new ByteArrayResource(stored.bytes()));
    }
}
