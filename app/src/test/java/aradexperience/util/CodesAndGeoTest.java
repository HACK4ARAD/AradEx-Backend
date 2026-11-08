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

    /**
     * 32^6 ≈ 1,07 mld. de coduri: la 2000 de coduri o coliziune apare în ~0,2% din rulări (paradoxul zilelor de naștere),
     * așa că testul cere „practic unice”, nu „toate unice” — altfel pică aleator CI-ul (8 nov). Un generator stricat
     * (ex. sufix constant sau alfabet mic) tot pică.
     */
    @Test
    void generate_isPracticallyUnique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            String code = Codes.generate("AX-C1");
            assertThat(code).matches("AX-C1-[A-HJ-NP-Z2-9]{6}");
            seen.add(code);
        }
        assertThat(seen).hasSizeGreaterThanOrEqualTo(1990);
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
