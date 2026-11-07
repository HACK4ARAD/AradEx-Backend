package aradexperience.service.payments;

import lombok.experimental.UtilityClass;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

/**
 * T25 · verificarea semnăturii webhook-ului Stripe (header `Stripe-Signature: t=…,v1=…`): HMAC-SHA256 cu secretul `whsec_…`
 * peste `t + "." + corpul cererii`, comparat în timp constant; cererile mai vechi de `TOLERANCE_SECONDS` se refuză (replay).
 */
@UtilityClass
public class StripeSignature {

    public static final long TOLERANCE_SECONDS = 300;

    public static boolean valid(String payload, String header, String secret, Instant now) {
        if (payload == null || header == null || secret == null || secret.isBlank()) return false;
        long t = -1;
        java.util.List<String> sigs = new java.util.ArrayList<>();
        for (String part : header.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length != 2) continue;
            if (kv[0].equals("t")) {
                try { t = Long.parseLong(kv[1]); } catch (NumberFormatException e) { return false; }
            } else if (kv[0].equals("v1")) {
                sigs.add(kv[1]);
            }
        }
        if (t < 0 || sigs.isEmpty() || Math.abs(now.getEpochSecond() - t) > TOLERANCE_SECONDS) return false;
        byte[] expected = HexFormat.of().formatHex(hmac(secret.trim(), t + "." + payload)).getBytes(StandardCharsets.US_ASCII);
        return sigs.stream().anyMatch(s -> MessageDigest.isEqual(expected, s.getBytes(StandardCharsets.US_ASCII)));
    }

    /** Semnătura, ca în header-ul Stripe (pentru teste și pentru `stripe trigger` local). */
    public static String sign(String payload, String secret, long timestamp) {
        return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(hmac(secret, timestamp + "." + payload));
    }

    private static byte[] hmac(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
