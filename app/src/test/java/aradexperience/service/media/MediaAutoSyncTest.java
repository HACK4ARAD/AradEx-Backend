package aradexperience.service.media;

import aradexperience.dto.media.MediaImportDto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaAutoSyncTest {

    MediaImportService importService = mock(MediaImportService.class);

    private static MediaProps props(String bucket, String publicUrl, boolean autoSync) {
        return new MediaProps(bucket, "eu-north-1", publicUrl, "uploads", 1024, 2048, autoSync);
    }

    @Test
    void runsOnlyWithS3_aPublicUrl_andTheSwitchOn() {
        new MediaAutoSync(importService, props("", "https://x/api/v1/media", true)).run();           // disc local
        new MediaAutoSync(importService, props("aradex-media", "", true)).run();                      // fără URL public
        new MediaAutoSync(importService, props("aradex-media", "https://x/api/v1/media", false)).run(); // oprit
        verify(importService, never()).importPlaces(anyInt());
    }

    @Test
    void copiesBatchesUntilNothingIsLeft_andStopsWhenABatchCopiesNothing() {
        when(importService.importPlaces(MediaImportService.MAX_LIMIT))
                .thenReturn(new MediaImportDto(25, 0, 30), new MediaImportDto(25, 0, 5), new MediaImportDto(4, 1, 0));
        var sync = new MediaAutoSync(importService, props("aradex-media", "https://x/api/v1/media", true));
        assertThat(sync.syncAll()).isEqualTo(54);
        verify(importService, times(3)).importPlaces(MediaImportService.MAX_LIMIT);

        MediaImportService stuck = mock(MediaImportService.class);
        when(stuck.importPlaces(anyInt())).thenReturn(new MediaImportDto(0, 2, 3));
        assertThat(new MediaAutoSync(stuck, props("b", "https://x/api/v1/media", true)).syncAll()).isZero();
        verify(stuck, times(1)).importPlaces(anyInt());
    }
}
