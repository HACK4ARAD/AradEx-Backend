package aradexperience.service.media;

import aradexperience.exceptions.ApiException;
import aradexperience.exceptions.BadRequestException;
import aradexperience.exceptions.NotFoundException;
import aradexperience.mock.MediaMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaServiceTest {

    @TempDir Path dir;
    MediaService service;

    @BeforeEach
    void setUp() {
        service = new MediaService(new LocalMediaStorage(dir), MediaMock.props(dir));
    }

    @Test
    void upload_detectsTypeFromContent_storesUnderUploads_andReturnsPublicUrl() {
        var dto = service.upload(MediaMock.PNG);
        assertThat(dto.key()).matches("uploads/[0-9a-f]{16}\\.png");
        assertThat(dto.url()).isEqualTo("https://api.test/api/v1/media/" + dto.key());
        assertThat(dto.contentType()).isEqualTo("image/png");
        assertThat(dto.size()).isEqualTo(MediaMock.PNG.length);

        String name = dto.key().substring("uploads/".length());
        var stored = service.load("uploads", name);
        assertThat(stored.bytes()).isEqualTo(MediaMock.PNG);
        assertThat(stored.contentType()).isEqualTo("image/png");
    }

    @Test
    void upload_rejectsEmpty_nonImages_andTooLarge() {
        assertThatThrownBy(() -> service.upload(new byte[0])).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.upload(MediaMock.TEXT)).isInstanceOf(BadRequestException.class).hasMessageContaining("JPEG");
        byte[] big = new byte[1025];
        System.arraycopy(MediaMock.JPEG, 0, big, 0, MediaMock.JPEG.length);
        assertThatThrownBy(() -> service.upload(big)).isInstanceOf(BadRequestException.class).hasMessageContaining("too large");
    }

    @Test
    void load_unknownFolder_badName_orMissing_is404() {
        assertThatThrownBy(() -> service.load("secrets", "a.jpg")).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.load("uploads", "../x.jpg")).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.load("uploads", "abc.exe")).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.load("uploads", "missing.jpg")).isInstanceOf(ApiException.class);
    }

    @Test
    void sniffing_andExternalUrls() {
        assertThat(MediaService.extensionOf(MediaMock.JPEG)).isEqualTo("jpg");
        assertThat(MediaService.extensionOf(MediaMock.WEBP)).isEqualTo("webp");
        assertThat(MediaService.extensionOf(MediaMock.TEXT)).isNull();
        assertThat(MediaService.isExternal("https://www.visitarad.ro/storage/x.jpg")).isTrue();
        assertThat(MediaService.isExternal("https://api.test/api/v1/media/places/x.jpg")).isFalse();
        assertThat(MediaService.isExternal("/img/loc-ca.jpg")).isFalse();
        assertThat(MediaService.isExternal(null)).isFalse();
    }

    @Test
    void localStorage_refusesKeysOutsideItsFolder() {
        var storage = new LocalMediaStorage(dir);
        assertThatThrownBy(() -> storage.put("../../etc/x.jpg", MediaMock.JPEG, "image/jpeg")).isInstanceOf(IllegalArgumentException.class);
    }
}
