package aradexperience.service.payments;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * T25 · secretul webhook-ului Stripe (`app.stripe.webhook.secret`, din STRIPE_WEBHOOK_SECRET, `whsec_…`). Opțional: fără el
 * biletul plătit vine doar din aplicație (`POST /events/{id}/tickets` cu `paymentIntentId`); cu el, și când aplicația s-a închis
 * imediat după plată.
 */
@ConfigurationProperties(prefix = "app.stripe.webhook")
public record StripeWebhookProps(String secret) {

    public boolean configured() {
        return secret != null && secret.trim().startsWith("whsec_");
    }
}
