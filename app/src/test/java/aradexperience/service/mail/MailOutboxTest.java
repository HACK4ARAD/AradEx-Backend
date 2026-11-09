package aradexperience.service.mail;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class MailOutboxTest {

    @Test
    void withoutATransaction_sendsRightAway_onTheMailThread_andAFailureIsOnlyLogged() throws Exception {
        CountDownLatch sent = new CountDownLatch(2);
        StringBuilder seen = new StringBuilder();
        MailOutbox outbox = new MailOutbox((to, subject, text) -> {
            seen.append(to).append('|').append(Thread.currentThread().getName()).append(';');
            sent.countDown();
            if (to.startsWith("boom")) throw new IllegalStateException("SES down");
        });

        outbox.sendAfterCommit("boom@exemplu.ro", "s", "t");
        outbox.sendAfterCommit("ana@exemplu.ro", "s", "t");

        assertThat(sent.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(seen.toString()).isEqualTo("boom@exemplu.ro|mail;ana@exemplu.ro|mail;");
    }

    @Test
    void mask_keepsOnlyTheFirstLetterAndTheDomain() {
        assertThat(MailOutbox.mask("ana@exemplu.ro")).isEqualTo("a***@exemplu.ro");
        assertThat(MailOutbox.mask("fara-aron")).isEqualTo("***");
    }
}
