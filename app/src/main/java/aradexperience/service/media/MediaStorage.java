package aradexperience.service.media;

import java.util.Optional;

/** Unde stau pozele: S3 în producție, disc local altfel. Cheile sunt de forma `places/<nume>.jpg`. */
public interface MediaStorage {

    void put(String key, byte[] bytes, String contentType);

    Optional<Stored> get(String key);

    record Stored(byte[] bytes, String contentType) {}
}
