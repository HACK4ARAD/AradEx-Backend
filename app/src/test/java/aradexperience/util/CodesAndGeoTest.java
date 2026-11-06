package aradexperience.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CodesAndGeoTest {

    @Test
    void generate_hasPrefixAndSixSafeChars() {
        String code = Codes.generate("AX-T12");
        assertThat(code).matches("AX-T12-[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{6}");
    }

    @Test
    void generate_isPracticallyUnique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            assertThat(seen.add(Codes.generate("AX-C1"))).isTrue();
        }
    }

    @Test
    void distance_cetateToPalatulCultural_isAboutOneAndAHalfKm() {
        double km = Geo.distanceKm(46.1744, 21.3330, 46.1760, 21.3155);
        assertThat(km).isBetween(1.2, 1.6);
    }

    @Test
    void round1_roundsToOneDecimal() {
        assertThat(Geo.round1(0.44)).isEqualTo(0.4);
        assertThat(Geo.round1(1.25)).isEqualTo(1.3);
    }
}
