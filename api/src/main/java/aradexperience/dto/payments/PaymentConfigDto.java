package aradexperience.dto.payments;

/**
 * docs/API.md §Plăți (T25): `GET /payments/config`. `enabled` = plata cu cardul prin Stripe în mod test e pornită;
 * `publishableKey` (cheia publică `pk_test_…`, poate sta în aplicație) e null când e oprită; `currency` = `ron`.
 */
public record PaymentConfigDto(boolean enabled, String publishableKey, String currency, boolean testMode) {
}
