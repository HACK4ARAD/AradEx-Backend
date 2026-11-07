package aradexperience.service.media;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.nio.file.Path;

/** Alege unde stau pozele: S3 dacă `MEDIA_BUCKET` e setat, altfel disc local. */
@Configuration
public class MediaStorageConfig {

    @Bean
    MediaStorage mediaStorage(MediaProps props) {
        if (!props.s3()) return new LocalMediaStorage(Path.of(props.localDir() == null ? "uploads" : props.localDir()));
        S3Client s3 = S3Client.builder().region(Region.of(props.region())).build();
        return new S3MediaStorage(s3, props.bucket());
    }
}
