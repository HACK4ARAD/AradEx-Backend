package aradexperience.service.media;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.Optional;

/** Pozele în S3 (bucket privat; backend-ul le servește prin `GET /api/v1/media/**`). Credențialele vin din rolul task-ului ECS. */
@Slf4j
public class S3MediaStorage implements MediaStorage {

    private final S3Client s3;
    private final String bucket;

    public S3MediaStorage(S3Client s3, String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
        log.info("Media: S3 bucket {}", bucket);
    }

    @Override
    public void put(String key, byte[] bytes, String contentType) {
        s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType)
                .cacheControl("public, max-age=31536000, immutable").build(), RequestBody.fromBytes(bytes));
    }

    @Override
    public Optional<Stored> get(String key) {
        try {
            var res = s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build());
            String type = res.response().contentType();
            return Optional.of(new Stored(res.asByteArray(), type == null || type.isBlank() ? MediaService.contentTypeOf(key) : type));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }
}
