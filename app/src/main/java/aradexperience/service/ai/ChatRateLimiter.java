package aradexperience.service.ai;

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
 * T12 · cel mult 20 de mesaje pe minut de la aceeași adresă (docs/API.md §AI), în memorie: o singură instanță de API.
 * Ține costul Claude sub control și nu lasă un script să golească bugetul.
 */
@Component
public class ChatRateLimiter {

    static final int MAX_PER_MINUTE = 20;
    static final Duration WINDOW = Duration.ofMinutes(1);

    private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();
    private Clock clock = Clock.systemUTC();

    void setClock(Clock clock) {
        this.clock = clock;
    }

    public void check(String clientKey) {
        Instant now = clock.instant();
        Deque<Instant> q = hits.computeIfAbsent(clientKey == null ? "?" : clientKey, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && !q.peekFirst().isAfter(now.minus(WINDOW))) q.pollFirst();
            if (q.size() >= MAX_PER_MINUTE) {
                long wait = Math.max(1, Duration.between(now, q.peekFirst().plus(WINDOW)).toSeconds());
                throw new TooManyRequestsException("Too many chat messages", wait);
            }
            q.addLast(now);
        }
        if (hits.size() > 10_000) hits.entrySet().removeIf(e -> { synchronized (e.getValue()) { return e.getValue().isEmpty(); } });
    }
}
