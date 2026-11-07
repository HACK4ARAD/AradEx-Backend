package aradexperience.service.volunteer;

import aradexperience.exceptions.TooManyRequestsException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limita pentru codurile de voluntar (6 cifre = 1.000.000 de variante): după 10 coduri greșite în 15 minute de la aceeași adresă,
 * intrarea se blochează până iese din fereastră cea mai veche încercare (429 cu `retryAfterSeconds`). În memorie: un singur task API.
 */
@Component
public class LoginAttempts {

    static final int MAX_FAILURES = 10;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();
    Clock clock = Clock.systemUTC();

    public void check(String key) {
        Deque<Instant> q = failures.get(key);
        if (q == null) return;
        synchronized (q) {
            prune(q);
            if (q.size() >= MAX_FAILURES) {
                long wait = Math.max(1, Duration.between(clock.instant(), q.peekFirst().plus(WINDOW)).toSeconds());
                throw new TooManyRequestsException("Too many wrong codes, try again later", wait);
            }
        }
    }

    public void failed(String key) {
        if (failures.size() > 10_000) failures.values().removeIf(d -> { synchronized (d) { prune(d); return d.isEmpty(); } });
        Deque<Instant> q = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            prune(q);
            q.addLast(clock.instant());
        }
    }

    public void succeeded(String key) {
        failures.remove(key);
    }

    private void prune(Deque<Instant> q) {
        Instant cutoff = clock.instant().minus(WINDOW);
        while (!q.isEmpty() && q.peekFirst().isBefore(cutoff)) q.pollFirst();
    }
}
