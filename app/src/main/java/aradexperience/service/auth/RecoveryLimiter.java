package aradexperience.service.auth;

import aradexperience.exceptions.TooManyRequestsException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Anti-ghicire pentru „Am uitat parola” cu codul de recuperare: se numără doar încercările greșite, 5 pe oră pe adresa de
 * email (orice adresă, cu sau fără cont, ca un 429 să nu arate ce conturi există) și 30 pe oră pe IP. O recuperare reușită
 * șterge încercările adresei. În memorie: API-ul rulează într-un singur task.
 */
@Component
public class RecoveryLimiter {

    static final int EMAIL_FAILURES = 5;
    static final int IP_FAILURES = 30;
    static final Duration WINDOW = Duration.ofHours(1);

    private final Map<String, Deque<Instant>> byEmail = new ConcurrentHashMap<>();
    private final Map<String, Deque<Instant>> byIp = new ConcurrentHashMap<>();

    /** 429 dacă adresa sau IP-ul au atins limita de încercări greșite. `ip` null = fără limita pe IP. */
    public void check(String email, String ip, Instant now) {
        over(byEmail.get(email), EMAIL_FAILURES, now, "Too many wrong attempts for this address");
        if (ip != null) over(byIp.get(ip), IP_FAILURES, now, "Too many wrong attempts from this network");
    }

    public void failed(String email, String ip, Instant now) {
        add(byEmail.computeIfAbsent(email, k -> new ArrayDeque<>()), now);
        if (ip != null) add(byIp.computeIfAbsent(ip, k -> new ArrayDeque<>()), now);
    }

    public void succeeded(String email) {
        byEmail.remove(email);
    }

    private static void over(Deque<Instant> q, int max, Instant now, String message) {
        if (q == null) return;
        synchronized (q) {
            prune(q, now);
            if (q.size() >= max) {
                throw new TooManyRequestsException(message, Math.max(1, Duration.between(now, q.peekFirst().plus(WINDOW)).toSeconds()));
            }
        }
    }

    private static void add(Deque<Instant> q, Instant now) {
        synchronized (q) {
            prune(q, now);
            q.addLast(now);
        }
    }

    private static void prune(Deque<Instant> q, Instant now) {
        while (!q.isEmpty() && !q.peekFirst().isAfter(now.minus(WINDOW))) q.pollFirst();
    }
}
