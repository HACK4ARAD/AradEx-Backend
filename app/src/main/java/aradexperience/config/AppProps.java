package aradexperience.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Toate setările aplicației (prefix `app.` în application.yml). Citite din variabile de mediu. */
@ConfigurationProperties(prefix = "app")
public record AppProps(Jwt jwt, Cors cors, Anthropic anthropic, Events events, Twilio twilio) {

    public record Jwt(String secret, int expirationHours) {}

    public record Cors(List<String> origins) {}

    /** Claude API (Anthropic): cheia din ANTHROPIC_API_KEY, modelul din ANTHROPIC_MODEL (T12 chat, T13 itinerar). */
    public record Anthropic(String apiKey, String model) {
        public boolean configured() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    public record Events(int hotParticipants, int hotSaves) {}

    /**
     * Twilio Verify (T21): SMS-ul cu codul OTP pentru numerele reale (cele de demo nu primesc SMS). Din TWILIO_ACCOUNT_SID,
     * TWILIO_AUTH_TOKEN, TWILIO_VERIFY_SERVICE_SID; fără ele, cererea de cod pentru un număr real întoarce 503.
     */
    public record Twilio(String accountSid, String authToken, String verifyServiceSid, String baseUrl) {
        public boolean configured() {
            return notBlank(accountSid) && notBlank(authToken) && notBlank(verifyServiceSid);
        }

        private static boolean notBlank(String s) {
            return s != null && !s.isBlank();
        }
    }
}
