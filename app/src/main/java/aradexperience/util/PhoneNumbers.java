package aradexperience.util;

import aradexperience.exceptions.BadRequestException;
import lombok.experimental.UtilityClass;

import java.util.regex.Pattern;

/**
 * Numere de telefon (T21): totul se stochează și se compară în E.164 (`+40…`). Numerele românești se pot scrie oricum
 * (`0799 000 001`, `40799000001`, `0040…`, `799000001`); cele cu `+` rămân cum sunt (spații și separatori scoși).
 */
@UtilityClass
public class PhoneNumbers {

    private static final Pattern E164 = Pattern.compile("^\\+[1-9]\\d{7,14}$");
    /** Doar mobile românești (07xx): fără fixe, numere cu tarif special (09xx) sau gratuite (0800) — T24, anti-abuz SMS. */
    private static final Pattern ROMANIAN = Pattern.compile("^\\+407\\d{8}$");
    /** Numerele de demo: fără SMS, codul se întoarce în răspuns (T21). */
    private static final Pattern DEMO = Pattern.compile("^\\+40799000\\d{3}$");

    public static String normalize(String raw) {
        if (raw == null) throw invalid();
        String s = raw.replaceAll("[\\s\\-.()/]", "");
        if (s.startsWith("00")) s = "+" + s.substring(2);
        else if (!s.startsWith("+")) {
            if (s.startsWith("40") && s.length() == 11) s = "+" + s;
            else if (s.startsWith("0") && s.length() == 10) s = "+40" + s.substring(1);
            else if (s.startsWith("7") && s.length() == 9) s = "+40" + s;
        }
        if (!E164.matcher(s).matches() || (s.startsWith("+40") && !ROMANIAN.matcher(s).matches())) throw invalid();
        return s;
    }

    public static boolean isDemo(String e164) {
        return e164 != null && DEMO.matcher(e164).matches();
    }

    private static BadRequestException invalid() {
        return new BadRequestException("Invalid phone number");
    }
}
