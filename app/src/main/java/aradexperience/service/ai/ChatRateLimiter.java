package aradexperience.service.ai;

import org.springframework.stereotype.Component;

/** T12 · cel mult 20 de mesaje pe minut de la aceeași adresă (docs/API.md §AI). */
@Component
public class ChatRateLimiter extends RateLimiter {

    static final int MAX_PER_MINUTE = 20;

    public ChatRateLimiter() {
        super(MAX_PER_MINUTE, "Too many chat messages");
    }
}
