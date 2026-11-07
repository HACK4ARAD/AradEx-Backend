package aradexperience.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** AI-ul e Claude (Anthropic API): app.anthropic.* vine din ANTHROPIC_API_KEY / ANTHROPIC_MODEL. */
class AppPropsAnthropicTest {

    private static AppProps bind(Map<String, String> props) {
        return new Binder(new MapConfigurationPropertySource(props)).bind("app", AppProps.class).get();
    }

    @Test
    void bindsKeyAndModel() {
        AppProps props = bind(Map.of(
                "app.anthropic.api-key", "test-key",
                "app.anthropic.model", "claude-haiku-5-5"));

        assertThat(props.anthropic().apiKey()).isEqualTo("test-key");
        assertThat(props.anthropic().model()).isEqualTo("claude-haiku-5-5");
        assertThat(props.anthropic().configured()).isTrue();
    }

    @Test
    void blankKeyIsNotConfigured() {
        AppProps props = bind(Map.of("app.anthropic.api-key", " ", "app.anthropic.model", "claude-haiku-5-5"));

        assertThat(props.anthropic().configured()).isFalse();
    }
}
