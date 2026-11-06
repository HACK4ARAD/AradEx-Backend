package ro.aradexperience.api.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Toate setările aplicației (prefix `app.` în application.yml). Citite din variabile de mediu. */
@ConfigurationProperties(prefix = "app")
public record AppProps(Jwt jwt, Cors cors, OpenAi openai, Events events) {

    public record Jwt(String secret, int expirationHours) {}

    public record Cors(List<String> origins) {}

    public record OpenAi(String apiKey, String model) {
        public boolean configured() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    public record Events(int hotParticipants, int hotSaves) {}
}
