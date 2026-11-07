package aradexperience.service.payments;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Stripe în modul de test (`app.stripe.*`). Doar chei de test: `sk_test_…` / `pk_test_…`. Cu chei lipsă sau cu chei live
 * (`sk_live_…`) plățile Stripe sunt oprite și biletele merg pe plata demonstrativă de până acum — aplicația nu încasează bani reali.
 */
@ConfigurationProperties(prefix = "app.stripe")
public record StripeProps(String secretKey, String publishableKey, String currency, String baseUrl) {

    public boolean configured() {
        return testKey(secretKey, "sk_test_") && testKey(publishableKey, "pk_test_");
    }

    /** Cheile sunt puse, dar nu sunt de test (ex. `sk_live_…`): le refuzăm. */
    public boolean liveKeysRefused() {
        return !configured() && (present(secretKey) || present(publishableKey));
    }

    public String currencyOrDefault() {
        return present(currency) ? currency.trim().toLowerCase() : "ron";
    }

    public String baseUrlOrDefault() {
        return present(baseUrl) ? baseUrl.trim() : "https://api.stripe.com";
    }

    private static boolean testKey(String key, String prefix) {
        return key != null && key.trim().startsWith(prefix);
    }

    private static boolean present(String s) {
        return s != null && !s.isBlank();
    }
}
