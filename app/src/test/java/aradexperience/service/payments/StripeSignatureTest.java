package aradexperience.service.payments;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class StripeSignatureTest {

    static final String SECRET = "whsec_test_secret";
    static final String BODY = "{\"type\":\"payment_intent.succeeded\"}";
    static final Instant NOW = Instant.parse("2026-11-07T10:00:00Z");

    @Test
    void acceptsAFreshSignatureMadeWithTheSecret() {
        String header = StripeSignature.sign(BODY, SECRET, NOW.getEpochSecond() - 10);
        assertThat(StripeSignature.valid(BODY, header, SECRET, NOW)).isTrue();
        // Stripe poate trimite mai multe v1 (la schimbarea secretului): ajunge una bună
        assertThat(StripeSignature.valid(BODY, header + ",v1=deadbeef", SECRET, NOW)).isTrue();
    }

    @Test
    void rejectsAnotherSecret_aChangedBody_anOldTimestamp_orAMalformedHeader() {
        long t = NOW.getEpochSecond();
        assertThat(StripeSignature.valid(BODY, StripeSignature.sign(BODY, "whsec_other", t), SECRET, NOW)).isFalse();
        assertThat(StripeSignature.valid(BODY + " ", StripeSignature.sign(BODY, SECRET, t), SECRET, NOW)).isFalse();
        assertThat(StripeSignature.valid(BODY, StripeSignature.sign(BODY, SECRET, t - StripeSignature.TOLERANCE_SECONDS - 1), SECRET, NOW)).isFalse();
        assertThat(StripeSignature.valid(BODY, "v1=abc", SECRET, NOW)).isFalse();
        assertThat(StripeSignature.valid(BODY, null, SECRET, NOW)).isFalse();
        assertThat(StripeSignature.valid(BODY, "t=x,v1=abc", SECRET, NOW)).isFalse();
    }
}
