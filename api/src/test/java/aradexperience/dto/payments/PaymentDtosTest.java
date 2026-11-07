package aradexperience.dto.payments;

import aradexperience.dto.DtoJson;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import static org.assertj.core.api.Assertions.assertThat;

/** Serializarea DTO-urilor din docs/API.md §Plăți (T25): JSON-ul din `dto/payments/` e adevărul. */
class PaymentDtosTest {

    static final ObjectMapper MAPPER = DtoJson.APP;
    static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void reads() throws Exception {
        assertThat(MAPPER.readValue(DtoJson.read("dto/payments/payment-config.json"), PaymentConfigDto.class).publishableKey()).startsWith("pk_test_");
        assertThat(MAPPER.readValue(DtoJson.read("dto/payments/checkout.json"), CheckoutDto.class).amount()).isEqualTo(105.0);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "dto/payments/payment-config.json, aradexperience.dto.payments.PaymentConfigDto",
            "dto/payments/checkout.json, aradexperience.dto.payments.CheckoutDto",
            "dto/payments/payment-confirm.json, aradexperience.dto.payments.PaymentConfirm",
    })
    void serialize_roundTrip_matchesJson(String path, Class<?> type) throws Exception {
        String json = DtoJson.read(path);
        JSONAssert.assertEquals(json, MAPPER.writeValueAsString(MAPPER.readValue(json, type)), JSONCompareMode.STRICT);
    }

    @Test
    void confirm_emailIsOptionalButValid() {
        assertThat(VALIDATOR.validate(new PaymentConfirm(null))).isEmpty();
        assertThat(VALIDATOR.validate(new PaymentConfirm("nu-e-email"))).hasSize(1);
    }
}
