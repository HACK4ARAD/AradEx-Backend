package aradexperience.service.google;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Perechi reale (numele nostru din seed ↔ numele de pe Google), văzute la căutarea automată pe 7 nov 2026. */
class NameMatchTest {

    @Test
    void sameThing_differentWording_matches() {
        assertThat(NameMatch.similar("Cetatea Aradului", "Unitatea Militară Cetatea Aradului")).isTrue();
        assertThat(NameMatch.similar("Palatul Băncii Naționale", "Palatul Bancii Nationale - Arad")).isTrue();
        assertThat(NameMatch.similar("Muzeul Memorial „Ioan Slavici și Emil Monția”", "Muzeul Memorial ”Ioan Slavici și Emil Monția”")).isTrue();
        assertThat(NameMatch.similar("Pony Club", "PONY CLUB ARAD")).isTrue();
        assertThat(NameMatch.similar("Michelangelo", "Pizzeria Michelangelo")).isTrue();
        assertThat(NameMatch.similar("Catedrala Ortodoxă „Sfânta Treime”", "Catedrala Arhiepiscopală Sfânta Treime")).isTrue();
    }

    @Test
    void anotherPlaceNearby_doesNotMatch() {
        assertThat(NameMatch.similar("Casa Comitatului", "Hotel Continental Forum Arad")).isFalse();
        assertThat(NameMatch.similar("Palatul Justiției", "Tribunalul Arad")).isFalse();   // același loc, dar fără cuvânt comun: îl leagă Primăria de mână
        assertThat(NameMatch.similar("Biserica Roșie", "Biserica Romano-Catolică")).isFalse();
    }

    @Test
    void missingNames_doNotMatch() {
        assertThat(NameMatch.similar("Gara", null)).isFalse();
        assertThat(NameMatch.similar(null, "Gara Arad")).isFalse();
    }
}
