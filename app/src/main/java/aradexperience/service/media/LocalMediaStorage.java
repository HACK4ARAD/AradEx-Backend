package aradexperience.service.media;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Pozele pe disc (`app.media.local-dir`), când nu e configurat un bucket S3. Tipul se deduce din extensie. */
@Slf4j
public class LocalMediaStorage implements MediaStorage {

    private final Path root;

    public LocalMediaStorage(Path root) {
        this.root = root.toAbsolutePath().normalize();
        log.info("Media: local storage in {}", this.root);
    }

    @Override
    public void put(String key, byte[] bytes, String contentType) {
        Path file = resolve(key);
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Optional<Stored> get(String key) {
        Path file = resolve(key);
        if (!Files.isRegularFile(file)) return Optional.empty();
        try {
            return Optional.of(new Stored(Files.readAllBytes(file), MediaService.contentTypeOf(key)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path resolve(String key) {
        Path file = root.resolve(key).normalize();
        if (!file.startsWith(root)) throw new IllegalArgumentException("Bad key");
        return file;
    }
}
