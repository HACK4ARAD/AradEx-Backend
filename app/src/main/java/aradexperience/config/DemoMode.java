package aradexperience.config;

import aradexperience.exceptions.ForbiddenException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * „Doar demo” (`app.demo-only`, `DEMO_ONLY`): până la lansare pot intra doar conturile demo (`@demo.ro`); conturile noi și
 * recuperarea parolei sunt oprite, ca aplicația să nu colecteze date personale reale. Pornit în producție, oprit local.
 */
@Component
@RequiredArgsConstructor
public class DemoMode {

    public static final String NOT_LIVE = "Not live yet: only the demo accounts can sign in";
    public static final String DEMO_DOMAIN = "@demo.ro";

    private final AppProps props;

    public boolean on() {
        return props.demoOnly();
    }

    /** 403 dacă modul e pornit (conturi noi, recuperarea parolei). */
    public void requireLive() {
        if (on()) throw new ForbiddenException(NOT_LIVE);
    }

    /** 403 dacă modul e pornit și adresa (deja normalizată) nu e a unui cont demo. */
    public void requireDemoAccount(String email) {
        if (on() && (email == null || !email.endsWith(DEMO_DOMAIN))) throw new ForbiddenException(NOT_LIVE);
    }
}
