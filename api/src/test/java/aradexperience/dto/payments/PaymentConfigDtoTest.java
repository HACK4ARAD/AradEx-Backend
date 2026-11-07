package aradexperience.dto.payments;

import aradexperience.dto.DtoJson;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import static org.assertj.core.api.Assertions.assertThat;

/** docs/API.md §Plăți (T25): `GET /payments/config`. */
class PaymentConfigDtoTest {

    @Test
    void roundTrip() throws Exception {
        String json = DtoJson.read("dto/payments/payment-config.json");
        PaymentConfigDto dto = DtoJson.APP.readValue(json, PaymentConfigDto.class);
        assertThat(dto.cardPayments()).isTrue();
        JSONAssert.assertEquals(json, DtoJson.APP.writeValueAsString(dto), JSONCompareMode.STRICT);
    }
}
