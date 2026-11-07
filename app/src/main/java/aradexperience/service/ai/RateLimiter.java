package aradexperience.service.ai;

import aradexperience.exceptions.TooManyRequestsException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fereastră glisantă de un minut pe client, în memorie (o singură instanță de API): ține costul Claude sub control și nu lasă un
 * script să golească bugetul. Fiecare funcție AI are limitatorul ei (`ChatRateLimiter`, `ItineraryRateLimiter`).
 */
public abstract class RateLimiter {

    static final Duration WINDOW = Duration.ofMinutes(1);

    private final int maxPerMinute;
    private final String message;
    private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();
    private Clock clock = Clock.systemUTC();

    protected RateLimiter(int maxPerMinute, String message) {
        this.maxPerMinute = maxPerMinute;
        this.message = message;
    }

    public void setClock(Clock clock) {
        this.clock = clock;
    }

    public void check(String clientKey) {
        Instant now = clock.instant();
        Deque<Instant> q = hits.computeIfAbsent(clientKey == null ? "?" : clientKey, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && !q.peekFirst().isAfter(now.minus(WINDOW))) q.pollFirst();
            if (q.size() >= maxPerMinute) {
                long wait = Math.max(1, Duration.between(now, q.peekFirst().plus(WINDOW)).toSeconds());
                throw new TooManyRequestsException(message, wait);
            }
            q.addLast(now);
        }
        // curățenie: scoatem clienții care n-au mai cerut nimic în ultimul minut (altfel harta crește la nesfârșit)
        if (hits.size() > 10_000) hits.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                Deque<Instant> d = e.getValue();
                while (!d.isEmpty() && !d.peekFirst().isAfter(now.minus(WINDOW))) d.pollFirst();
                return d.isEmpty();
            }
        });
    }
}
