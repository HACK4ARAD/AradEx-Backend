package aradexperience.util;

import lombok.experimental.UtilityClass;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * Codurile de recuperare și parolele temporare: 12 caractere din 31 ușor de citit și de dictat (fără 0/O, 1/I/L), afișate ca
 * `XXXX-XXXX-XXXX` (~59 de biți). La citire contează doar literele și cifrele: majusculele, spațiile și liniuțele nu.
 */
@UtilityClass
public class RecoveryCodes {

    static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    static final int LENGTH = 12;

    public static String generate(SecureRandom random) {
        StringBuilder sb = new StringBuilder(LENGTH + 2);
        for (int i = 0; i < LENGTH; i++) {
            if (i > 0 && i % 4 == 0) sb.append('-');
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /** Forma comparată (fără liniuțe și spații, cu majuscule), sau null dacă nu poate fi un cod. */
    public static String normalize(String raw) {
        if (raw == null) return null;
        String s = raw.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        if (s.length() != LENGTH) return null;
        for (int i = 0; i < s.length(); i++) if (ALPHABET.indexOf(s.charAt(i)) < 0) return null;
        return s;
    }
}
