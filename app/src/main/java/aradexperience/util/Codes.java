package aradexperience.util;

import java.security.SecureRandom;

/**
 * Coduri scurte pentru bilete / oferte / înscrieri: `AX-T12-7K3Q9Z`.
 * Alfabet fără 0/O și 1/I ca să poată fi dictate și scrise de mână.
 */
public final class Codes {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private Codes() {}

    /** @param prefix ex. "AX-T12" → "AX-T12-XXXXXX" */
    public static String generate(String prefix) {
        return prefix + "-" + random(6);
    }

    public static String random(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
