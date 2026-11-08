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
 * T24 · anti-abuz: câte SMS-uri reale cu cod poate declanșa o singură adresă IP (20 la 10 minute, 60 pe zi: încape o sală întreagă pe același Wi-Fi la un eveniment), ca un script
 * să nu poată trimite coduri spre sute de numere diferite de pe telefonul gateway-ului. Se numără doar SMS-urile care
 * pleacă (nu codurile demo și nici codurile refolosite). În memorie: API-ul rulează într-un singur task.
 */
@Component
public class OtpIpLimiter {

    static final int MAX_SHORT = 20;
    static final Duration SHORT_WINDOW = Duration.ofMinutes(10);
    static final int MAX_DAY = 60;
    static final Duration DAY = Duration.ofDays(1);

    private final Map<String, Deque<Instant>> sent = new ConcurrentHashMap<>();

    /** 429 dacă adresa a atins una din limite; `key` null = adresă necunoscută (apel intern), nu se limitează aici. */
    public void check(String key, Instant now) {
        if (key == null) return;
        Deque<Instant> q = sent.get(key);
        if (q == null) return;
        synchronized (q) {
            prune(q, now);
            long recent = q.stream().filter(t -> t.isAfter(now.minus(SHORT_WINDOW))).count();
            if (recent >= MAX_SHORT) {
                Instant oldestRecent = q.stream().filter(t -> t.isAfter(now.minus(SHORT_WINDOW))).findFirst().orElse(now);
                throw new TooManyRequestsException("Too many codes from this network", wait(oldestRecent.plus(SHORT_WINDOW), now));
            }
            if (q.size() >= MAX_DAY) {
                throw new TooManyRequestsException("Too many codes from this network today", wait(q.peekFirst().plus(DAY), now));
            }
        }
    }

    public void record(String key, Instant now) {
        if (key == null) return;
        Deque<Instant> q = sent.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            prune(q, now);
            q.addLast(now);
        }
    }

    private static void prune(Deque<Instant> q, Instant now) {
        while (!q.isEmpty() && !q.peekFirst().isAfter(now.minus(DAY))) q.pollFirst();
    }

    private static long wait(Instant until, Instant now) {
        return Math.max(1, Duration.between(now, until).toSeconds());
    }
}
