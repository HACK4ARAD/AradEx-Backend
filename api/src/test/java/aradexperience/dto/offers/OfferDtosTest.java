package aradexperience.dto.offers;

import aradexperience.dto.DtoJson;
import aradexperience.enums.ContentStatus;
import aradexperience.enums.OfferCategory;
import aradexperience.enums.RedemptionStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Serializarea DTO-urilor din docs/API.md §Oferte (T10): JSON-ul din `dto/offers/` e adevărul. */
class OfferDtosTest {

    /** Ca ObjectMapper-ul din Spring Boot: date ca text ISO-8601. */
    private static final ObjectMapper MAPPER = DtoJson.MAPPER.copy().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void offerDto_readsDetailsAndMyRedemption() throws Exception {
        OfferDto dto = MAPPER.readValue(DtoJson.read("dto/offers/offer-dto.json"), OfferDto.class);
        assertThat(dto.category()).isEqualTo(OfferCategory.CAFE);
        assertThat(dto.validUntil()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(dto.status()).isEqualTo(ContentStatus.PUBLISHED);
        assertThat(dto.myRedemption().status()).isEqualTo(RedemptionStatus.ISSUED);
        assertThat(dto.myRedemption().code()).isEqualTo("AX-O1-K8D2QF");
    }

    @Test
    void offerSummary_permanentOfferHasNoValidUntil() throws Exception {
        OfferSummary s = MAPPER.readValue(DtoJson.read("dto/offers/offer-summary.json"), OfferSummary.class);
        assertThat(s.validUntil()).isNull();
        assertThat(s.localProduct()).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "dto/offers/offer-dto.json, aradexperience.dto.offers.OfferDto",
            "dto/offers/offer-summary.json, aradexperience.dto.offers.OfferSummary",
            "dto/offers/redemption-dto.json, aradexperience.dto.offers.RedemptionDto",
            "dto/offers/offer-create.json, aradexperience.dto.offers.OfferCreate",
    })
    void serialize_roundTrip_matchesJson(String path, Class<?> type) throws Exception {
        String json = DtoJson.read(path);
        JSONAssert.assertEquals(json, MAPPER.writeValueAsString(MAPPER.readValue(json, type)), JSONCompareMode.STRICT);
    }
}
