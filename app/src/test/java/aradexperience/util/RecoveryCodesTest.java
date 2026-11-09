package aradexperience.util;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RecoveryCodesTest {

    @Test
    void generate_isThreeGroupsOfFour_fromTheReadableAlphabet_andDiffersEachTime() {
        SecureRandom random = new SecureRandom();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String c = RecoveryCodes.generate(random);
            assertThat(c).matches("[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{4}-[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{4}-[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{4}");
            seen.add(c);
        }
        assertThat(seen).hasSize(200);
    }

    @Test
    void normalize_ignoresCaseSpacesAndDashes_andRejectsAnythingElse() {
        assertThat(RecoveryCodes.normalize(" k7qm-4xpt 9dwa ")).isEqualTo("K7QM4XPT9DWA");
        assertThat(RecoveryCodes.normalize("K7QM4XPT9DWA")).isEqualTo("K7QM4XPT9DWA");
        assertThat(RecoveryCodes.normalize("K7QM-4XPT-9DW")).isNull();      // prea scurt
        assertThat(RecoveryCodes.normalize("K7QM-4XPT-9DW0")).isNull();     // 0 nu e în alfabet
        assertThat(RecoveryCodes.normalize(null)).isNull();
    }
}
