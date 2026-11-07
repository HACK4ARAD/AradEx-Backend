package aradexperience.service.media;

import aradexperience.dto.media.MediaImportDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Copierea automată în S3 a pozelor obiectivelor (și ale punctelor de ghid) care sunt pe alte site-uri: la 1 minut după
 * pornire, apoi la fiecare 2 minute, câte 25 de poze pe pas până nu mai rămâne nimic. Așa nu mai e nevoie de buton în panou:
 * o poză lipită ca link în „Obiective & ghiduri” ajunge singură în S3. Doar cu S3 + `MEDIA_PUBLIC_URL` (vezi `MediaProps`).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MediaAutoSync {

    private final MediaImportService importService;
    private final MediaProps props;

    @Scheduled(initialDelay = 60_000, fixedDelayString = "${app.media.sync-interval-ms:120000}")
    public void run() {
        if (!props.autoSyncEnabled()) return;
        syncAll();
    }

    /** Pașii de import până nu mai e nimic de copiat (sau un pas nu mai copiază nimic: doar poze care nu se pot descărca). */
    int syncAll() {
        int total = 0;
        for (int i = 0; i < 100; i++) {
            MediaImportDto r = importService.importPlaces(MediaImportService.MAX_LIMIT);
            total += r.imported();
            if (r.remaining() == 0 || r.imported() == 0) break;
        }
        if (total > 0) log.info("Media auto-sync: {} photos copied to S3", total);
        return total;
    }
}
