package aradexperience.service.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Emailurile aplicației (`app.mail.*`). `from` = expeditorul verificat în Amazon SES (ex. „AradExperience
 * <no-reply@aradexperience.site>”); gol = emailurile nu pleacă, se scriu doar în log (local, teste).
 */
@ConfigurationProperties(prefix = "app.mail")
public record MailProps(String from, String region) {

    public boolean ses() {
        return from != null && !from.isBlank();
    }
}
