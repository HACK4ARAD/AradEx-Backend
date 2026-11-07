package aradexperience.util;

import aradexperience.exceptions.BadRequestException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhoneNumbersTest {

    @ParameterizedTest
    @CsvSource({
            "'0799 000 001', +40799000001",
            "'+40 799-000-001', +40799000001",
            "0040799000001, +40799000001",
            "40799000001, +40799000001",
            "799000001, +40799000001",
            "'(0744) 123.456', +40744123456",
            "+4915112345678, +4915112345678",
    })
    void normalize_romanianAndInternational(String raw, String e164) {
        assertThat(PhoneNumbers.normalize(raw)).isEqualTo(e164);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "07", "", "   ", "+0123456789", "0799 000 0011 22"})
    void normalize_invalid_throws400(String raw) {
        assertThatThrownBy(() -> PhoneNumbers.normalize(raw)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void normalize_null_throws400() {
        assertThatThrownBy(() -> PhoneNumbers.normalize(null)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void isDemo_only_0799000xxx() {
        assertThat(PhoneNumbers.isDemo("+40799000123")).isTrue();
        assertThat(PhoneNumbers.isDemo("+40799001123")).isFalse();
        assertThat(PhoneNumbers.isDemo("+4079900012")).isFalse();
    }
}
