package aradexperience.service.payments;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * T25 · Stripe în **mod test** (`app.stripe.*`, din STRIPE_SECRET_KEY, STRIPE_PUBLISHABLE_KEY, STRIPE_WEBHOOK_SECRET).
 * Plata cu cardul pornește doar cu chei de test (`sk_test_…` + `pk_test_…`): cheile live sunt refuzate, ca aplicația să nu
 * poată încasa bani reali. Fără chei, biletele plătite se emit cu plata demonstrativă din T07.
 */
@ConfigurationProperties(prefix = "app.stripe")
public record StripeProps(String secretKey, String publishableKey, String webhookSecret, String baseUrl, String currency) {

    public boolean enabled() {
        return startsWith(secretKey, "sk_test_") && startsWith(publishableKey, "pk_test_");
    }

    /** Cineva a pus chei live: le ignorăm (și o spunem în log la pornire). */
    public boolean liveKeyGiven() {
        return startsWith(secretKey, "sk_live_") || startsWith(secretKey, "rk_live_") || startsWith(publishableKey, "pk_live_");
    }

    public boolean webhookConfigured() {
        return enabled() && startsWith(webhookSecret, "whsec_");
    }

    public String currencyOrDefault() {
        return currency == null || currency.isBlank() ? "ron" : currency.trim().toLowerCase();
    }

    public String baseUrlOrDefault() {
        return baseUrl == null || baseUrl.isBlank() ? "https://api.stripe.com" : baseUrl;
    }

    private static boolean startsWith(String s, String prefix) {
        return s != null && s.trim().startsWith(prefix);
    }
}
