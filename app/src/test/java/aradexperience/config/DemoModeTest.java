package aradexperience.config;

import aradexperience.exceptions.ForbiddenException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DemoModeTest {

    static DemoMode mode(boolean on) {
        return new DemoMode(new AppProps(null, null, null, null, on));
    }

    @Test
    void on_onlyDemoAddressesPass_andNewAccountsAreRefused() {
        DemoMode d = mode(true);
        assertThat(d.on()).isTrue();
        assertThatCode(() -> d.requireDemoAccount("local@demo.ro")).doesNotThrowAnyException();
        assertThatThrownBy(() -> d.requireDemoAccount("ana@exemplu.ro")).isInstanceOf(ForbiddenException.class).hasMessage(DemoMode.NOT_LIVE);
        assertThatThrownBy(() -> d.requireDemoAccount("cineva@demo.ro.evil.com")).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> d.requireDemoAccount(null)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(d::requireLive).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void off_changesNothing() {
        DemoMode d = mode(false);
        assertThatCode(() -> d.requireDemoAccount("ana@exemplu.ro")).doesNotThrowAnyException();
        assertThatCode(d::requireLive).doesNotThrowAnyException();
    }
}
