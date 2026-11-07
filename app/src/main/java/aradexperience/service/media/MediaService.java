package aradexperience.service.media;

import aradexperience.dto.media.MediaDto;
import aradexperience.exceptions.BadRequestException;
import aradexperience.exceptions.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Pozele: încărcare din panou (validare după conținut, nu după numele fișierului), citire publică și URL-ul public.
 * Cheile sunt aleatoare / derivate din conținutul sursei, deci o cheie nu se rescrie niciodată (cache „immutable”).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaService {

    /** Folderele din bucket: `uploads` (din panou), `places` și `guide` (copiate din alte site-uri de import). */
    static final Set<String> FOLDERS = Set.of("uploads", "places", "guide");
    private static final Pattern NAME = Pattern.compile("[a-z0-9][a-z0-9-]{0,150}\\.(jpg|png|webp|gif)");
    private static final String MEDIA_PATH = "/api/v1/media";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MediaStorage storage;
    private final MediaProps props;

    public MediaDto upload(byte[] bytes) {
        if (bytes == null || bytes.length == 0) throw new BadRequestException("Empty file");
        if (bytes.length > props.maxUploadBytes()) throw new BadRequestException("Image too large (max " + props.maxUploadBytes() / (1024 * 1024) + " MB)");
        String ext = extensionOf(bytes);
        if (ext == null) throw new BadRequestException("Only JPEG, PNG, WebP or GIF images");
        byte[] id = new byte[8];
        RANDOM.nextBytes(id);
        String key = "uploads/" + HexFormat.of().formatHex(id) + "." + ext;
        storage.put(key, bytes, contentTypeOf(key));
        log.info("Media uploaded: {} ({} bytes)", key, bytes.length);
        return new MediaDto(publicUrl(key), key, contentTypeOf(key), bytes.length);
    }

    public MediaStorage.Stored load(String folder, String name) {
        if (!FOLDERS.contains(folder) || name == null || !NAME.matcher(name).matches()) throw NotFoundException.of("Media", folder + "/" + name);
        return storage.get(folder + "/" + name).orElseThrow(() -> NotFoundException.of("Media", folder + "/" + name));
    }

    /** Salvează poze deja verificate (importul). */
    void store(String key, byte[] bytes) {
        storage.put(key, bytes, contentTypeOf(key));
    }

    /** URL-ul public al unei chei: `app.media.public-url` sau, dacă lipsește, adresa cererii curente + `/api/v1/media`. */
    public String publicUrl(String key) {
        String base = props.publicUrl() != null && !props.publicUrl().isBlank()
                ? props.publicUrl()
                : ServletUriComponentsBuilder.fromCurrentContextPath().path(MEDIA_PATH).toUriString();
        return base.replaceAll("/+$", "") + "/" + key;
    }

    /** O poză e „a noastră” dacă e servită de `GET /api/v1/media/**`; restul (alte site-uri) se pot importa. */
    public static boolean isExternal(String url) {
        return url != null && (url.startsWith("https://") || url.startsWith("http://")) && !url.contains(MEDIA_PATH + "/");
    }

    /** Tipul după primii octeți (JPEG, PNG, WebP, GIF) → extensia, sau null dacă nu e o poză acceptată. */
    public static String extensionOf(byte[] b) {
        if (b == null || b.length < 12) return null;
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "jpg";
        if ((b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "png";
        if (b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') return "gif";
        if (b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "webp";
        return null;
    }

    public static String contentTypeOf(String key) {
        String ext = key.substring(key.lastIndexOf('.') + 1);
        return switch (ext) {
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            default -> "image/jpeg";
        };
    }
}
