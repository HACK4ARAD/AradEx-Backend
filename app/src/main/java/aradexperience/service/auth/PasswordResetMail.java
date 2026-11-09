package aradexperience.service.auth;

import lombok.experimental.UtilityClass;

/** Emailul cu codul de resetare, în limba contului (ro, en, hu). Text simplu, fără linkuri de acțiune. */
@UtilityClass
public class PasswordResetMail {

    public static String subject(String locale) {
        return switch (locale == null ? "ro" : locale) {
            case "en" -> "AradExperience: your code for a new password";
            case "hu" -> "AradExperience: kód az új jelszóhoz";
            default -> "AradExperience: codul pentru parola nouă";
        };
    }

    public static String body(String locale, String name, String code) {
        return switch (locale == null ? "ro" : locale) {
            case "en" -> """
                    Hi %s,

                    Your code for a new AradExperience password is:

                        %s

                    It is valid for 15 minutes. Enter it in the app or on the website, together with your new password.

                    If you didn't ask to change your password, ignore this email: your password stays the same.

                    The AradExperience team
                    https://aradexperience.site
                    """.formatted(name, code);
            case "hu" -> """
                    Szia, %s!

                    Az új AradExperience-jelszavadhoz tartozó kód:

                        %s

                    15 percig érvényes. Írd be az alkalmazásban vagy a weboldalon, az új jelszavaddal együtt.

                    Ha nem te kérted a jelszó módosítását, hagyd figyelmen kívül ezt az e-mailt: a jelszavad nem változik.

                    Az AradExperience csapata
                    https://aradexperience.site
                    """.formatted(name, code);
            default -> """
                    Bună, %s!

                    Codul pentru parola nouă AradExperience este:

                        %s

                    E valabil 15 minute. Introdu-l în aplicație sau pe site, împreună cu parola nouă.

                    Dacă nu ai cerut tu schimbarea parolei, ignoră acest e-mail: parola ta rămâne aceeași.

                    Echipa AradExperience
                    https://aradexperience.site
                    """.formatted(name, code);
        };
    }
}
