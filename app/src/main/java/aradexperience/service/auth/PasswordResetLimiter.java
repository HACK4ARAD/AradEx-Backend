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
 * Anti-abuz pentru „Am uitat parola”: pe adresa de email (un cod la 60 s, 5 pe zi) și pe adresa IP (20 la 10 minute, 60 pe
 * zi). Se numără orice adresă de email, cu sau fără cont, ca un 429 să nu arate ce conturi există. În memorie: API-ul
 * rulează într-un singur task.
 */
@Component
public class PasswordResetLimiter {

    static final Duration EMAIL_COOLDOWN = Duration.ofSeconds(60);
    static final int EMAIL_PER_DAY = 5;
    static final int IP_SHORT = 20;
    static final Duration IP_WINDOW = Duration.ofMinutes(10);
    static final int IP_PER_DAY = 60;
    static final Duration DAY = Duration.ofDays(1);

    private final Map<String, Deque<Instant>> byEmail = new ConcurrentHashMap<>();
    private final Map<String, Deque<Instant>> byIp = new ConcurrentHashMap<>();

    /** 429 dacă adresa de email sau IP-ul a atins o limită; altfel cererea se numără. `ip` null = apel intern, fără limita pe IP. */
    public void acquire(String email, String ip, Instant now) {
        Deque<Instant> e = byEmail.computeIfAbsent(email, k -> new ArrayDeque<>());
        Deque<Instant> i = ip == null ? null : byIp.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (e) {
            prune(e, now);
            if (!e.isEmpty() && e.peekLast().plus(EMAIL_COOLDOWN).isAfter(now)) {
                throw new TooManyRequestsException("Wait before requesting a new code", wait(e.peekLast().plus(EMAIL_COOLDOWN), now));
            }
            if (e.size() >= EMAIL_PER_DAY) {
                throw new TooManyRequestsException("Too many codes requested today", wait(e.peekFirst().plus(DAY), now));
            }
            if (i != null) {
                synchronized (i) {
                    prune(i, now);
                    long recent = i.stream().filter(t -> t.isAfter(now.minus(IP_WINDOW))).count();
                    if (recent >= IP_SHORT) {
                        Instant oldest = i.stream().filter(t -> t.isAfter(now.minus(IP_WINDOW))).findFirst().orElse(now);
                        throw new TooManyRequestsException("Too many requests from this network", wait(oldest.plus(IP_WINDOW), now));
                    }
                    if (i.size() >= IP_PER_DAY) {
                        throw new TooManyRequestsException("Too many requests from this network today", wait(i.peekFirst().plus(DAY), now));
                    }
                    i.addLast(now);
                }
            }
            e.addLast(now);
        }
    }

    private static void prune(Deque<Instant> q, Instant now) {
        while (!q.isEmpty() && !q.peekFirst().isAfter(now.minus(DAY))) q.pollFirst();
    }

    private static long wait(Instant until, Instant now) {
        return Math.max(1, Duration.between(now, until).toSeconds());
    }
}
