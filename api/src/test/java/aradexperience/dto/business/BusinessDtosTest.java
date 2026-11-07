package aradexperience.dto.business;

import aradexperience.dto.DtoJson;
import aradexperience.enums.BusinessActivityType;
import aradexperience.enums.BusinessCategory;
import aradexperience.enums.ContentStatus;
import aradexperience.enums.EventCategory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Serializarea DTO-urilor din docs/API.md §Firme (T09): JSON-ul din `dto/business/` e adevărul. */
class BusinessDtosTest {

    /** Ca ObjectMapper-ul din Spring Boot: datele ca text ISO-8601, nu ca numere. */
    static final ObjectMapper MAPPER = DtoJson.MAPPER.copy().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void businessPage_readsProfileOffersEventsAnnouncements() throws Exception {
        BusinessPageDto dto = MAPPER.readValue(DtoJson.read("dto/business/business-page.json"), BusinessPageDto.class);

        assertThat(dto.business().name()).isEqualTo("Cafeneaua din Pasaj");
        assertThat(dto.business().viewsCount()).isEqualTo(1241);
        assertThat(dto.offers()).singleElement().satisfies(o -> assertThat(o.pointsPrice()).isEqualTo(30));
        assertThat(dto.events()).singleElement().satisfies(e -> {
            assertThat(e.category()).isEqualTo(EventCategory.CONCERT);
            assertThat(e.isHot()).isTrue();
            assertThat(e.priceFrom()).isEqualByComparingTo(BigDecimal.ZERO);
        });
        assertThat(dto.announcements()).singleElement().satisfies(a -> assertThat(a.status()).isEqualTo(ContentStatus.PUBLISHED));
    }

    @Test
    void businessMe_readsStats() throws Exception {
        BusinessMeDto dto = MAPPER.readValue(DtoJson.read("dto/business/business-me.json"), BusinessMeDto.class);
        assertThat(dto.stats().upcomingEvents()).isEqualTo(2);
        assertThat(dto.business().phone()).isNull();
    }

    @Test
    void requests_read() throws Exception {
        BusinessUpdate up = MAPPER.readValue(DtoJson.read("dto/business/business-update.json"), BusinessUpdate.class);
        assertThat(up.category()).isEqualTo(BusinessCategory.CAFE);
        AnnouncementCreate ac = MAPPER.readValue(DtoJson.read("dto/business/announcement-create.json"), AnnouncementCreate.class);
        assertThat(ac.title()).isEqualTo("Căutăm barista part-time");
        BusinessActivityDto act = MAPPER.readValue(DtoJson.read("dto/business/business-activity.json"), BusinessActivityDto.class);
        assertThat(act.items()).singleElement().satisfies(i -> assertThat(i.type()).isEqualTo(BusinessActivityType.CODE_USED));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "dto/business/business-page.json, aradexperience.dto.business.BusinessPageDto",
            "dto/business/business-me.json, aradexperience.dto.business.BusinessMeDto",
            "dto/business/business-update.json, aradexperience.dto.business.BusinessUpdate",
            "dto/business/announcement-create.json, aradexperience.dto.business.AnnouncementCreate",
            "dto/business/business-activity.json, aradexperience.dto.business.BusinessActivityDto",
            "dto/events/event-create.json, aradexperience.dto.events.EventCreate",
            "dto/events/event-dto.json, aradexperience.dto.events.EventDto",
    })
    void serialize_roundTrip_matchesJson(String path, Class<?> type) throws Exception {
        String json = DtoJson.read(path);
        JSONAssert.assertEquals(json, MAPPER.writeValueAsString(MAPPER.readValue(json, type)), JSONCompareMode.STRICT);
    }
}
