package aradexperience.service.payments;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StripePropsTest {

    @Test
    void onlyTestKeysTurnStripeOn_liveKeysAreRefused() {
        assertThat(new StripeProps("sk_test_1", "pk_test_2", null, null).configured()).isTrue();
        assertThat(new StripeProps("sk_live_1", "pk_live_2", null, null).configured()).isFalse();
        assertThat(new StripeProps("sk_live_1", "pk_live_2", null, null).liveKeysRefused()).isTrue();
        assertThat(new StripeProps("sk_test_1", "", null, null).configured()).isFalse();
        assertThat(new StripeProps("", "", null, null).liveKeysRefused()).isFalse();
    }

    @Test
    void defaultsToRonAndTheStripeApi() {
        StripeProps p = new StripeProps("sk_test_1", "pk_test_2", " RON ", " ");
        assertThat(p.currencyOrDefault()).isEqualTo("ron");
        assertThat(p.baseUrlOrDefault()).isEqualTo("https://api.stripe.com");
    }
}
