package aradexperience.service.transit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Cache-ul comun pentru datele live Telelink (T22), după regulile de folosire din `docs/ARAD-DATA.md`:
 * <ul>
 *   <li>o singură citire `/vehicles` (și câte una per stație) la cel mult {@link #TTL}, pentru toți utilizatorii;</li>
 *   <li>niciodată două cereri simultane spre Telelink ({@link #lock}); cine nu prinde lacătul în
 *       {@link #LOCK_WAIT} primește ce e în cache;</li>
 *   <li>la eroare: pauză 60 s, apoi 120 s la fiecare eroare următoare; între timp doar cache-ul;</li>
 *   <li>niciodată excepție spre apelant: fără date → {@link Live#none}.</li>
 * </ul>
 * „Live” = ultima citire reușită e mai nouă de {@link #STALE}.
 */
@Slf4j
@Service
public class TransitLiveService {

    static final Duration TTL = Duration.ofSeconds(25);
    static final Duration STALE = Duration.ofSeconds(120);
    static final Duration BACKOFF_FIRST = Duration.ofSeconds(60);
    static final Duration BACKOFF_MAX = Duration.ofSeconds(120);
    static final Duration LOCK_WAIT = Duration.ofSeconds(4);

    /** Ce primește apelantul: datele (null dacă n-a fost nicio citire reușită), momentul citirii și dacă sunt „live”. */
    public record Live<T>(T data, Instant fetchedAt, boolean live) {
        static <T> Live<T> none() {
            return new Live<>(null, null, false);
        }
    }

    private record Snapshot<T>(T data, Instant fetchedAt) {}

    private final TelelinkClient client;
    private final Clock clock;
    private final ReentrantLock lock = new ReentrantLock();
    private volatile Snapshot<List<Telelink.Vehicle>> vehicles;
    private final Map<Integer, Snapshot<List<Telelink.StopTimes>>> stopTimes = new ConcurrentHashMap<>();
    private volatile Instant retryAt = Instant.MIN;
    private int failures;

    @Autowired
    public TransitLiveService(TelelinkClient client) {
        this(client, Clock.systemUTC());
    }

    TransitLiveService(TelelinkClient client, Clock clock) {
        this.client = client;
        this.clock = clock;
    }

    public Live<List<Telelink.Vehicle>> vehicles() {
        return read(() -> vehicles, s -> vehicles = s, client::vehicles, "vehicles", null);
    }

    /** Stațiile sunt cele din seed (≤ 299), deci harta de cache e mărginită. */
    public Live<List<Telelink.StopTimes>> stopTimes(int stopId) {
        // 4xx (ex. 404: stație scoasă din rețea, încă în seed) = „fără sosiri” doar pentru stația asta, fără pauza comună
        return read(() -> stopTimes.get(stopId), s -> stopTimes.put(stopId, s), () -> client.stopTimes(stopId), "stop " + stopId, List.of());
    }

    /** `onClientError` ≠ null: un 4xx de la sursă e un răspuns valid (acesta), nu o pană a sursei. */
    private <T> Live<T> read(Supplier<Snapshot<T>> get, java.util.function.Consumer<Snapshot<T>> put, Supplier<T> fetch, String what,
                             T onClientError) {
        Snapshot<T> cached = get.get();
        if (!needsFetch(cached)) return view(cached);
        boolean locked;
        try {
            locked = lock.tryLock(LOCK_WAIT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return view(cached);
        }
        if (!locked) return view(cached); // altă cerere spre Telelink e în curs: nu suprapunem
        try {
            cached = get.get(); // poate a adus-o chiar cererea după care am așteptat
            if (!needsFetch(cached)) return view(cached);
            Instant now = clock.instant();
            try {
                Snapshot<T> fresh = new Snapshot<>(fetch.get(), now);
                put.accept(fresh);
                if (failures > 0) log.info("Telelink back after {} failure(s)", failures);
                failures = 0;
                retryAt = Instant.MIN;
                return view(fresh);
            } catch (HttpClientErrorException e) {
                if (onClientError == null) return fail(e, what, now, cached);
                log.info("Telelink {}: HTTP {}, treated as no data", what, e.getStatusCode().value());
                Snapshot<T> empty = new Snapshot<>(onClientError, now);
                put.accept(empty);
                return view(empty);
            } catch (RuntimeException e) {
                return fail(e, what, now, cached);
            }
        } finally {
            lock.unlock();
        }
    }

    /** Pană a sursei (rețea, timeout, 5xx, JSON stricat): pauză comună 60 s, apoi 120 s; apelantul primește cache-ul. */
    private <T> Live<T> fail(RuntimeException e, String what, Instant now, Snapshot<T> cached) {
        failures++; // doar sub lacăt
        Duration pause = failures == 1 ? BACKOFF_FIRST : BACKOFF_MAX;
        retryAt = now.plus(pause);
        log.warn("Telelink {} failed ({}x, next try in {} s): {}", what, failures, pause.toSeconds(), e.getMessage());
        return view(cached);
    }

    private boolean needsFetch(Snapshot<?> s) {
        Instant now = clock.instant();
        if (now.isBefore(retryAt)) return false; // pauză după o eroare
        return s == null || !now.isBefore(s.fetchedAt().plus(TTL));
    }

    private <T> Live<T> view(Snapshot<T> s) {
        if (s == null) return Live.none();
        boolean live = !clock.instant().isAfter(s.fetchedAt().plus(STALE));
        return new Live<>(s.data(), s.fetchedAt(), live);
    }
}
