package aradexperience.mock;

import aradexperience.service.media.MediaProps;

import java.nio.file.Path;

/** Date de test pentru media: octeții minimi ai fiecărui tip de poză și setările (fără S3). */
public final class MediaMock {

    private MediaMock() {}

    public static final byte[] JPEG = bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 0x10, 'J', 'F', 'I', 'F', 0, 1, 1);
    public static final byte[] PNG = bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I');
    public static final byte[] WEBP = bytes('R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 'V');
    public static final byte[] TEXT = "<html>not an image</html>".getBytes();

    public static MediaProps props(Path dir) {
        return new MediaProps("", "eu-north-1", "https://api.test/api/v1/media", dir.toString(), 1024, 2048);
    }

    private static byte[] bytes(int... v) {
        byte[] b = new byte[v.length];
        for (int i = 0; i < v.length; i++) b[i] = (byte) v[i];
        return b;
    }
}
