package aradexperience.service.media;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Setările pentru poze (`app.media.*`). Cu `bucket` gol pozele se scriu pe disc în `localDir` (local, teste);
 * în producție `MEDIA_BUCKET` e bucket-ul S3 (acces prin rolul task-ului ECS). `publicUrl` = adresa publică a
 * `GET /api/v1/media` (gol = adresa cererii curente).
 */
@ConfigurationProperties(prefix = "app.media")
public record MediaProps(String bucket, String region, String publicUrl, String localDir, long maxUploadBytes, long maxImportBytes,
                         boolean autoSync) {

    public boolean s3() {
        return bucket != null && !bucket.isBlank();
    }

    /**
     * Copierea automată a pozelor externe rulează doar cu S3 și cu URL public fix: pe disc (container) pozele s-ar pierde la
     * redeploy, iar fără cerere HTTP nu putem afla adresa publică a API-ului.
     */
    public boolean autoSyncEnabled() {
        return autoSync && s3() && publicUrl != null && !publicUrl.isBlank();
    }
}
