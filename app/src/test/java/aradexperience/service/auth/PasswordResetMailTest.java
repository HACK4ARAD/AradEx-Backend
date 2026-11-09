package aradexperience.service.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordResetMailTest {

    @Test
    void everyLanguage_hasTheCodeAndTheName_andUnknownFallsBackToRomanian() {
        assertThat(PasswordResetMail.body("ro", "Ana", "482913")).contains("Bună, Ana!").contains("482913").contains("15 minute");
        assertThat(PasswordResetMail.body("en", "Ana", "482913")).contains("Hi Ana,").contains("482913").contains("15 minutes");
        assertThat(PasswordResetMail.body("hu", "Ana", "482913")).contains("Szia, Ana!").contains("482913").contains("15 percig");
        assertThat(PasswordResetMail.subject("de")).isEqualTo(PasswordResetMail.subject("ro"));
        assertThat(PasswordResetMail.subject(null)).isEqualTo(PasswordResetMail.subject("ro"));
    }
}
