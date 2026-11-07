package aradexperience.service.sms;

import aradexperience.dto.sms.GatewaySmsDto;
import aradexperience.entity.sms.SmsOutbox;
import aradexperience.exceptions.UnauthorizedException;
import aradexperience.repository.sms.SmsOutboxRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * T24 · SMS-urile cu cod OTP trimise de pe telefonul lui Zoltan: API-ul pune SMS-ul în coadă (`sms_outbox`), scriptul de pe
 * Mac (AradEx-Ops/sms-gateway) îl ia cu `claim`, îl trimite prin aplicația Mesaje (iPhone, redirecționare SMS) și confirmă.
 * Activ doar dacă `app.sms-gateway.token` e setat (secretul `aradex/sms-gateway`); altfel numerele reale merg prin Twilio.
 * Textul cu codul stă în DB doar până la trimitere; nu se scrie niciodată în log.
 */
@Slf4j
@Service
public class SmsGatewayService {

    /** După atât fără confirmare, SMS-ul luat se dă din nou (scriptul s-a oprit la jumătate). */
    static final Duration CLAIM_TIMEOUT = Duration.ofSeconds(60);
    /** Un SMS se dă de cel mult atâtea ori, apoi e FAILED (să nu trimitem același cod la nesfârșit). */
    static final int MAX_TRIES = 3;
    /** Fereastra de trimitere: un SMS care pleacă mai târziu ar ajunge cu un cod aproape expirat (TTL 5 min). */
    static final Duration SEND_WINDOW = Duration.ofMinutes(4);
    static final int BATCH = 10;

    private final SmsOutboxRepository outbox;
    private final String token;
    private Clock clock = Clock.systemUTC();

    @Autowired
    public SmsGatewayService(SmsOutboxRepository outbox, @Value("${app.sms-gateway.token:}") String token) {
        this.outbox = outbox;
        this.token = token == null ? "" : token.trim();
    }

    /** Pentru teste. */
    void setClock(Clock clock) {
        this.clock = clock;
    }

    public boolean enabled() {
        return !token.isEmpty();
    }

    /** Pune în coadă SMS-ul cu codul; pleacă doar în fereastra de trimitere și înainte să expire codul. */
    public void enqueue(String phone, String code, Instant expiresAt) {
        Instant now = clock.instant();
        SmsOutbox s = new SmsOutbox();
        s.setPhone(phone);
        s.setBody("Codul tău AradExperience: " + code + ". Expiră în 5 minute. Nu-l da nimănui.");
        Instant window = now.plus(SEND_WINDOW);
        s.setSendBefore(window.isBefore(expiresAt) ? window : expiresAt);
        s.setCreatedAt(now);
        outbox.save(s);
    }

    @Transactional
    public List<GatewaySmsDto> claim(String given) {
        checkToken(given);
        Instant now = clock.instant();
        outbox.expireOld(now);
        List<GatewaySmsDto> out = new ArrayList<>();
        for (SmsOutbox s : outbox.lockClaimable(now, now.minus(CLAIM_TIMEOUT), BATCH)) {
            if (s.getTries() >= MAX_TRIES) {
                finish(s, SmsOutbox.Status.FAILED);
                log.warn("SMS {} not confirmed after {} tries, giving up", s.getId(), MAX_TRIES);
                continue;
            }
            s.setTries(s.getTries() + 1);
            s.setStatus(SmsOutbox.Status.CLAIMED);
            s.setClaimedAt(now);
            outbox.save(s);
            out.add(new GatewaySmsDto(s.getId(), s.getPhone(), s.getBody()));
        }
        return out;
    }

    @Transactional
    public void sent(String given, long id) {
        checkToken(given);
        outbox.findById(id).filter(s -> s.getStatus() == SmsOutbox.Status.CLAIMED).ifPresent(s -> {
            s.setSentAt(clock.instant());
            finish(s, SmsOutbox.Status.SENT);
        });
    }

    @Transactional
    public void failed(String given, long id) {
        checkToken(given);
        outbox.findById(id).filter(s -> s.getStatus() == SmsOutbox.Status.CLAIMED).ifPresent(s -> {
            finish(s, SmsOutbox.Status.FAILED);
            log.warn("SMS {} reported failed by the gateway", s.getId());
        });
    }

    private void finish(SmsOutbox s, SmsOutbox.Status status) {
        s.setStatus(status);
        s.setBody(null);
        outbox.save(s);
    }

    /** Comparație în timp constant; un gateway neconfigurat nu acceptă niciun token. */
    private void checkToken(String given) {
        boolean ok = enabled() && given != null
                && MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), given.trim().getBytes(StandardCharsets.UTF_8));
        if (!ok) throw new UnauthorizedException("Invalid gateway token");
    }
}
