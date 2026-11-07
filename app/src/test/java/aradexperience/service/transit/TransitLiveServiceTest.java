package aradexperience.service.transit;

import aradexperience.mock.TransitMock;
import aradexperience.mock.TransitMock.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Regulile de folosire Telelink: cache comun 25 s, fără cereri suprapuse, backoff 60 → 120 s, niciodată excepție. */
class TransitLiveServiceTest {

    TelelinkClient client;
    MutableClock clock;
    TransitLiveService live;

    @BeforeEach
    void setUp() {
        client = mock(TelelinkClient.class);
        clock = new MutableClock(TransitMock.NOW);
        live = new TransitLiveService(client, clock);
    }

    @Test
    void vehicles_cachedFor25s_thenRefetched() {
        when(client.vehicles()).thenReturn(List.of(TransitMock.vehicle("1", 11, TransitMock.NOW)));

        assertThat(live.vehicles().live()).isTrue();
        clock.advanceSeconds(24);
        assertThat(live.vehicles().data()).hasSize(1);
        verify(client, times(1)).vehicles();

        clock.advanceSeconds(1);
        live.vehicles();
        verify(client, times(2)).vehicles();
    }

    @Test
    void error_withoutCache_isNone_notException() {
        when(client.vehicles()).thenThrow(new ResourceAccessException("timeout"));

        TransitLiveService.Live<?> l = live.vehicles();

        assertThat(l.live()).isFalse();
        assertThat(l.data()).isNull();
        assertThat(l.fetchedAt()).isNull();
    }

    @Test
    void errors_backOff60_then120_andServeCache() {
        when(client.vehicles())
                .thenReturn(List.of(TransitMock.vehicle("1", 11, TransitMock.NOW)))
                .thenThrow(new ResourceAccessException("down"))
                .thenThrow(new ResourceAccessException("down"))
                .thenReturn(List.of());

        live.vehicles();                         // t=0 ok
        clock.advanceSeconds(30);
        assertThat(live.vehicles().data()).hasSize(1); // t=30 eroare → cache-ul vechi, pauză până la t=90
        verify(client, times(2)).vehicles();

        clock.advanceSeconds(59);                // t=89: încă în pauză, nicio cerere
        assertThat(live.vehicles().live()).isTrue();
        verify(client, times(2)).vehicles();

        clock.advanceSeconds(1);                 // t=90: a doua eroare → pauză 120 s (până la t=210)
        live.vehicles();
        verify(client, times(3)).vehicles();

        clock.advanceSeconds(31);                // t=121: datele au peste 120 s → nu mai sunt live
        TransitLiveService.Live<?> stale = live.vehicles();
        assertThat(stale.live()).isFalse();
        assertThat(stale.fetchedAt()).isEqualTo(TransitMock.NOW);
        clock.advanceSeconds(88);                // t=209: încă în pauză
        live.vehicles();
        verify(client, times(3)).vehicles();

        clock.advanceSeconds(1);                 // t=210: sursa a revenit
        assertThat(live.vehicles().live()).isTrue();
        verify(client, times(4)).vehicles();
    }

    @Test
    void backoff_isShared_acrossStops() {
        when(client.stopTimes(120)).thenThrow(new ResourceAccessException("down"));

        live.stopTimes(120);
        live.stopTimes(121);
        live.vehicles();

        verify(client, times(1)).stopTimes(120);
        verify(client, times(0)).stopTimes(121);
        verify(client, times(0)).vehicles();
    }

    @Test
    void stopTimes_cachedPerStop() {
        when(client.stopTimes(120)).thenReturn(List.of(TransitMock.times(11, 120, 4)));
        when(client.stopTimes(130)).thenReturn(List.of());

        live.stopTimes(120);
        live.stopTimes(120);
        live.stopTimes(130);

        verify(client, times(1)).stopTimes(120);
        verify(client, times(1)).stopTimes(130);
    }

    @Test
    void concurrentRequests_neverOverlap_andShareOneCall() throws Exception {
        CountDownLatch inCall = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger running = new AtomicInteger();
        AtomicInteger maxRunning = new AtomicInteger();
        when(client.vehicles()).thenAnswer(inv -> {
            maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
            inCall.countDown();
            release.await(2, TimeUnit.SECONDS);
            running.decrementAndGet();
            return List.of(TransitMock.vehicle("1", 11, TransitMock.NOW));
        });
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            Future<TransitLiveService.Live<List<Telelink.Vehicle>>> first = pool.submit(live::vehicles);
            assertThat(inCall.await(2, TimeUnit.SECONDS)).isTrue();
            List<Future<TransitLiveService.Live<List<Telelink.Vehicle>>>> others = List.of(
                    pool.submit(live::vehicles), pool.submit(live::vehicles), pool.submit(live::vehicles));
            Thread.sleep(100);
            release.countDown();
            assertThat(first.get(3, TimeUnit.SECONDS).data()).hasSize(1);
            for (var f : others) assertThat(f.get(3, TimeUnit.SECONDS).data()).hasSize(1); // au primit rezultatul primei cereri
        } finally {
            pool.shutdownNow();
        }
        assertThat(maxRunning.get()).isEqualTo(1);
        verify(client, times(1)).vehicles();
    }
}
