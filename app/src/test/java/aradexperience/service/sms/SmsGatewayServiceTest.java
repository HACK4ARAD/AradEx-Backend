package aradexperience.service.sms;

import aradexperience.dto.sms.GatewaySmsDto;
import aradexperience.entity.sms.SmsOutbox;
import aradexperience.exceptions.UnauthorizedException;
import aradexperience.repository.sms.SmsOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SmsGatewayServiceTest {

    static final String TOKEN = "gw-secret-token-0123456789abcdef";
    final List<SmsOutbox> rows = new ArrayList<>();
    SmsOutboxRepository repo;
    SmsGatewayService service;
    Instant now = Instant.parse("2026-11-07T12:00:00Z");

    @BeforeEach
    void setUp() {
        repo = mock(SmsOutboxRepository.class);
        when(repo.save(any(SmsOutbox.class))).thenAnswer(inv -> {
            SmsOutbox s = inv.getArgument(0);
            if (s.getId() == null) s.setId((long) rows.size() + 1);
            if (!rows.contains(s)) rows.add(s);
            return s;
        });
        when(repo.lockClaimable(any(Instant.class), any(Instant.class), anyInt())).thenAnswer(inv -> {
            Instant t = inv.getArgument(0), stale = inv.getArgument(1);
            return rows.stream().filter(s -> s.getSendBefore().isAfter(t)
                    && (s.getStatus() == SmsOutbox.Status.PENDING || (s.getStatus() == SmsOutbox.Status.CLAIMED && s.getClaimedAt().isBefore(stale))))
                    .limit((int) inv.getArgument(2)).toList();
        });
        when(repo.findById(anyLong())).thenAnswer(inv -> rows.stream().filter(s -> s.getId().equals(inv.getArgument(0))).findFirst());
        service = new SmsGatewayService(repo, TOKEN);
        at(now);
    }

    void at(Instant t) {
        now = t;
        service.setClock(Clock.fixed(t, ZoneOffset.UTC));
    }

    @Test
    void enabledOnlyWithAToken() {
        assertThat(service.enabled()).isTrue();
        assertThat(new SmsGatewayService(repo, "").enabled()).isFalse();
    }

    @Test
    void enqueuedCodeIsClaimedOnceWithItsText_thenSentClearsTheText() {
        service.enqueue("+40744123456", "123456", now.plus(Duration.ofMinutes(5)));

        List<GatewaySmsDto> batch = service.claim(TOKEN);
        assertThat(batch).hasSize(1);
        assertThat(batch.get(0).phone()).isEqualTo("+40744123456");
        assertThat(batch.get(0).body()).contains("123456");
        assertThat(service.claim(TOKEN)).isEmpty(); // already claimed

        service.sent(TOKEN, batch.get(0).id());
        assertThat(rows.get(0).getStatus()).isEqualTo(SmsOutbox.Status.SENT);
        assertThat(rows.get(0).getBody()).isNull();
    }

    @Test
    void aClaimNotConfirmedWithin60sIsHandedOutAgain_atMost3Times() {
        service.enqueue("+40744123456", "123456", now.plus(Duration.ofMinutes(5)));
        assertThat(service.claim(TOKEN)).hasSize(1);
        at(now.plusSeconds(61));
        assertThat(service.claim(TOKEN)).hasSize(1);
        at(now.plusSeconds(61));
        assertThat(service.claim(TOKEN)).hasSize(1);
        at(now.plusSeconds(61));
        assertThat(service.claim(TOKEN)).isEmpty();
        assertThat(rows.get(0).getStatus()).isEqualTo(SmsOutbox.Status.FAILED);
        assertThat(rows.get(0).getBody()).isNull();
    }

    @Test
    void nothingIsSentAfterTheSendWindow() {
        service.enqueue("+40744123456", "123456", now.plus(Duration.ofMinutes(5)));
        at(now.plus(Duration.ofMinutes(4)).plusSeconds(1));
        assertThat(service.claim(TOKEN)).isEmpty();
    }

    @Test
    void aFailedSend_goesBackInTheQueue_andIsRetried_atMost3Times_thenFailedClearsTheText() {
        // iPhone-ul a ieșit o clipă din rețea: SMS-ul nu se pierde, se reîncearcă
        service.enqueue("+40744123456", "123456", now.plus(Duration.ofMinutes(5)));
        for (int i = 0; i < SmsGatewayService.MAX_TRIES; i++) {
            GatewaySmsDto m = service.claim(TOKEN).get(0);
            assertThat(m.body()).contains("123456");
            service.failed(TOKEN, m.id());
        }
        assertThat(service.claim(TOKEN)).isEmpty();
        assertThat(rows.get(0).getStatus()).isEqualTo(SmsOutbox.Status.FAILED);
        assertThat(rows.get(0).getBody()).isNull();
    }

    @Test
    void aFailedSendAfterTheSendWindow_isNotRetried() {
        service.enqueue("+40744123456", "123456", now.plus(Duration.ofMinutes(5)));
        GatewaySmsDto m = service.claim(TOKEN).get(0);
        at(now.plus(SmsGatewayService.SEND_WINDOW).plusSeconds(1));
        service.failed(TOKEN, m.id());
        assertThat(rows.get(0).getStatus()).isEqualTo(SmsOutbox.Status.FAILED);
        assertThat(rows.get(0).getBody()).isNull();
    }

    @Test
    void wrongOrMissingTokenIsRejected_andAnUnconfiguredGatewayRejectsEverything() {
        assertThatThrownBy(() -> service.claim("nope")).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> service.claim(null)).isInstanceOf(UnauthorizedException.class);
        SmsGatewayService off = new SmsGatewayService(repo, "");
        assertThatThrownBy(() -> off.claim("")).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> service.sent("nope", 1L)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void sentForAnUnknownOrFinishedMessageIsIgnored() {
        service.sent(TOKEN, 99L);
        service.enqueue("+40744123456", "123456", now.plus(Duration.ofMinutes(5)));
        GatewaySmsDto m = null;
        for (int i = 0; i < SmsGatewayService.MAX_TRIES; i++) { m = service.claim(TOKEN).get(0); service.failed(TOKEN, m.id()); }
        service.sent(TOKEN, m.id());
        assertThat(rows.get(0).getStatus()).isEqualTo(SmsOutbox.Status.FAILED);
        assertThat(repo.findById(m.id())).isEqualTo(Optional.of(rows.get(0)));
    }
}
