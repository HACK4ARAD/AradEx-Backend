package aradexperience.dto.admin;

import aradexperience.dto.DtoJson;
import aradexperience.dto.common.PageResponse;
import aradexperience.enums.BusinessCategory;
import aradexperience.enums.Role;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Serializarea DTO-urilor din docs/API.md §Admin (T14): JSON-ul din `dto/admin/` e adevărul (fără câmpuri null, ca în aplicație). */
class AdminDtosTest {

    static final ObjectMapper MAPPER = DtoJson.APP.copy().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void stats_readsCountsPerRole() throws Exception {
        AdminStatsDto dto = MAPPER.readValue(DtoJson.read("dto/admin/admin-stats.json"), AdminStatsDto.class);
        assertThat(dto.usersByRole()).containsEntry(Role.LOCAL, 391L).containsEntry(Role.VOLUNTEER, 24L).hasSize(6);
        assertThat(dto.codesUsed()).isEqualTo(187);
    }

    @Test
    void contentPage_readsEventsOffersAndActionsInTheSameShape() throws Exception {
        PageResponse<AdminContentDto> page = MAPPER.readValue(DtoJson.read("dto/admin/admin-content-page.json"), new TypeReference<>() {});
        assertThat(page.content()).hasSize(3);
        assertThat(page.content().get(0).participants()).isEqualTo(120);
        assertThat(page.content().get(1).organizerVerified()).isFalse();
        assertThat(page.content().get(1).validUntil()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(page.content().get(2).businessId()).isNull();
    }

    @Test
    void requestsAndRows_read() throws Exception {
        AdminUserUpdate up = MAPPER.readValue(DtoJson.read("dto/admin/admin-user-update.json"), AdminUserUpdate.class);
        assertThat(up.role()).isEqualTo(Role.CITY_HALL);
        assertThat(up.pointsAdjust()).isEqualTo(-20);
        AdminUserDto user = MAPPER.readValue(DtoJson.read("dto/admin/admin-user.json"), AdminUserDto.class);
        assertThat(user.business().verified()).isTrue();
        CityBusinessCreate create = MAPPER.readValue(DtoJson.read("dto/admin/city-business-create.json"), CityBusinessCreate.class);
        assertThat(create.category()).isEqualTo(BusinessCategory.CAFE);
        CityBusinessCreated created = MAPPER.readValue(DtoJson.read("dto/admin/city-business-created.json"), CityBusinessCreated.class);
        assertThat(created.business().verified()).isTrue();
        assertThat(created.invitePassword()).hasSize(12);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "dto/admin/admin-stats.json, aradexperience.dto.admin.AdminStatsDto",
            "dto/admin/status-update.json, aradexperience.dto.admin.StatusUpdate",
            "dto/admin/admin-user.json, aradexperience.dto.admin.AdminUserDto",
            "dto/admin/admin-user-update.json, aradexperience.dto.admin.AdminUserUpdate",
            "dto/admin/city-business.json, aradexperience.dto.admin.CityBusinessDto",
            "dto/admin/city-business-create.json, aradexperience.dto.admin.CityBusinessCreate",
            "dto/admin/city-business-created.json, aradexperience.dto.admin.CityBusinessCreated",
            "dto/admin/business-reject.json, aradexperience.dto.admin.BusinessReject",
    })
    void serialize_roundTrip_matchesJson(String path, Class<?> type) throws Exception {
        String json = DtoJson.read(path);
        JSONAssert.assertEquals(json, MAPPER.writeValueAsString(MAPPER.readValue(json, type)), JSONCompareMode.STRICT);
    }

    @Test
    void contentPage_roundTrip() throws Exception {
        String json = DtoJson.read("dto/admin/admin-content-page.json");
        PageResponse<AdminContentDto> page = MAPPER.readValue(json, new TypeReference<>() {});
        JSONAssert.assertEquals(json, MAPPER.writeValueAsString(page), JSONCompareMode.STRICT);
    }
}
