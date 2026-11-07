package aradexperience.service.media;

import aradexperience.dto.media.MediaImportDto;
import aradexperience.entity.places.GuidePoint;
import aradexperience.entity.places.Place;
import aradexperience.repository.places.GuidePointRepository;
import aradexperience.repository.places.PlaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Copiază în S3 pozele obiectivelor și ale punctelor de ghid care sunt încă legate de pe alte site-uri (visitarad.ro…),
 * apoi pune URL-ul nostru în `image_url`. Merge pe bucăți (`limit` poze pe cerere), ca o cerere să nu depășească
 * timeout-ul ALB-ului; panoul cheamă până nu mai rămâne nimic. Creditul pozei (`image_credit`) rămâne neschimbat.
 * URL-urile care n-au putut fi descărcate sunt sărite până la repornire (altfel panoul le-ar încerca la nesfârșit).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaImportService {

    static final int MAX_LIMIT = 25;

    private final PlaceRepository places;
    private final GuidePointRepository guidePoints;
    private final MediaService media;
    private final MediaProps props;
    private final Set<String> failedUrls = ConcurrentHashMap.newKeySet();
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(10)).build();

    public MediaImportDto importPlaces(int limit) {
        int n = Math.max(1, Math.min(limit, MAX_LIMIT));
        List<Task> todo = pending();
        int imported = 0, failed = 0;
        for (Task t : todo.subList(0, Math.min(n, todo.size()))) {
            try {
                byte[] bytes = download(t.url());
                String ext = MediaService.extensionOf(bytes);
                if (ext == null) throw new IOException("not an image");
                String key = t.keyPrefix() + "-" + shortHash(t.url()) + "." + ext;
                media.store(key, bytes);
                t.apply().accept(media.publicUrl(key));
                imported++;
            } catch (IOException | RuntimeException e) {
                failedUrls.add(t.url());
                failed++;
                log.info("Media import failed for {}: {}", t.url(), e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        int remaining = pending().size();
        log.info("Media import: {} imported, {} failed, {} remaining", imported, failed, remaining);
        return new MediaImportDto(imported, failed, remaining);
    }

    private record Task(String url, String keyPrefix, java.util.function.Consumer<String> apply) {}

    private List<Task> pending() {
        List<Task> out = new ArrayList<>();
        for (Place p : places.findAll(org.springframework.data.domain.Sort.by("id"))) {
            if (MediaService.isExternal(p.getImageUrl()) && !failedUrls.contains(p.getImageUrl())) {
                out.add(new Task(p.getImageUrl(), "places/" + slug(p.getSlug(), "place-" + p.getId()), url -> {
                    p.setImageUrl(url);
                    places.save(p);
                }));
            }
        }
        for (GuidePoint g : guidePoints.findAll(org.springframework.data.domain.Sort.by("id"))) {
            if (MediaService.isExternal(g.getImageUrl()) && !failedUrls.contains(g.getImageUrl())) {
                out.add(new Task(g.getImageUrl(), "guide/" + g.getPlaceId() + "-" + g.getPosition(), url -> {
                    g.setImageUrl(url);
                    guidePoints.save(g);
                }));
            }
        }
        return out;
    }

    private byte[] download(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                .header("User-Agent", "AradExperience media import").GET().build();
        HttpResponse<InputStream> res = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream in = res.body()) {
            if (res.statusCode() != 200) throw new IOException("HTTP " + res.statusCode());
            byte[] bytes = in.readNBytes((int) props.maxImportBytes() + 1);
            if (bytes.length > props.maxImportBytes()) throw new IOException("too large");
            return bytes;
        }
    }

    static String slug(String s, String fallback) {
        String v = s == null ? "" : java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (v.length() > 80) v = v.substring(0, 80).replaceAll("-+$", "");
        return v.isEmpty() ? fallback : v;
    }

    static String shortHash(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d, 0, 4);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
