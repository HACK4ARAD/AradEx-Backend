package aradexperience.service.media;

import aradexperience.entity.places.GuidePoint;
import aradexperience.entity.places.Place;
import aradexperience.mock.MediaMock;
import aradexperience.mock.PlaceMock;
import aradexperience.repository.places.GuidePointRepository;
import aradexperience.repository.places.PlaceRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.domain.Sort;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaImportServiceTest {

    @TempDir Path dir;
    HttpServer server;
    String base;
    PlaceRepository places = mock(PlaceRepository.class);
    GuidePointRepository points = mock(GuidePointRepository.class);
    MediaImportService service;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ok.jpg", ex -> { ex.sendResponseHeaders(200, MediaMock.JPEG.length); ex.getResponseBody().write(MediaMock.JPEG); ex.close(); });
        server.createContext("/page.html", ex -> { ex.sendResponseHeaders(200, MediaMock.TEXT.length); ex.getResponseBody().write(MediaMock.TEXT); ex.close(); });
        server.createContext("/gone.jpg", ex -> { ex.sendResponseHeaders(404, -1); ex.close(); });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        var props = MediaMock.props(dir);
        service = new MediaImportService(places, points, new MediaService(new LocalMediaStorage(dir), props), props);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void copiesExternalPhotos_rewritesImageUrl_andSkipsOursAndFailures() {
        Place cetatea = PlaceMock.cetatea();
        cetatea.setImageUrl(base + "/ok.jpg");
        Place palat = PlaceMock.palatulCultural();
        palat.setImageUrl(base + "/gone.jpg");
        Place local = PlaceMock.place(3L, "Teatrul", aradexperience.enums.PlaceType.ATTRACTION, 46.17, 21.31, "");
        local.setImageUrl("/img/loc-tc.jpg");
        GuidePoint gp = new GuidePoint();
        gp.setId(9L);
        gp.setPlaceId(1L);
        gp.setPosition(2);
        gp.setImageUrl(base + "/page.html");
        when(places.findAll(any(Sort.class))).thenReturn(List.of(cetatea, palat, local));
        when(points.findAll(any(Sort.class))).thenReturn(List.of(gp));

        var first = service.importPlaces(10);

        assertThat(first.imported()).isEqualTo(1);
        assertThat(first.failed()).isEqualTo(2);       // 404 + o pagină HTML în loc de poză
        assertThat(first.remaining()).isZero();        // cele eșuate nu se mai încearcă (panoul s-ar învârti la nesfârșit)
        assertThat(cetatea.getImageUrl()).matches("https://api\\.test/api/v1/media/places/cetatea-aradului-[0-9a-f]{8}\\.jpg");
        assertThat(Files.exists(dir.resolve(cetatea.getImageUrl().substring("https://api.test/api/v1/media/".length())))).isTrue();
        assertThat(palat.getImageUrl()).isEqualTo(base + "/gone.jpg");
        assertThat(local.getImageUrl()).isEqualTo("/img/loc-tc.jpg");
        verify(places).save(cetatea);
        verify(places, never()).save(palat);
        verify(points, never()).save(any());
    }

    @Test
    void limit_isBatched_andRemainingCountsTheRest() {
        Place a = PlaceMock.cetatea();
        a.setImageUrl(base + "/ok.jpg?a");
        Place b = PlaceMock.palatulCultural();
        b.setImageUrl(base + "/ok.jpg?b");
        when(places.findAll(any(Sort.class))).thenReturn(List.of(a, b));
        when(points.findAll(any(Sort.class))).thenReturn(List.of());

        var res = service.importPlaces(1);

        assertThat(res.imported()).isEqualTo(1);
        assertThat(res.remaining()).isEqualTo(1);
        assertThat(MediaService.isExternal(a.getImageUrl())).isFalse();
        assertThat(MediaService.isExternal(b.getImageUrl())).isTrue();
    }

    @Test
    void slugAndHash() {
        assertThat(MediaImportService.slug("Liceul Pedagogic „D. Țichindeal”", "x")).isEqualTo("liceul-pedagogic-d-tichindeal");
        assertThat(MediaImportService.slug(null, "place-4")).isEqualTo("place-4");
        assertThat(MediaImportService.shortHash("a")).hasSize(8);
    }
}
