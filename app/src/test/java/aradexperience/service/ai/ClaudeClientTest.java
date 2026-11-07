package aradexperience.service.ai;

import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudeClientTest {

    final ClaudeClient client = new ClaudeClient("", "claude-haiku-5-5", "");

    @Test
    void requests_turnThinkingOff_soTheWholeTokenBudgetGoesToTheJsonAnswer() {
        // Haiku 5.5 gândește implicit: cu 1500 de tokenuri, planificatorul primea doar thinking și niciun JSON
        MessageCreateParams p = client.params("sistem", ClaudeClient.SCHEMA, 1500).addUserMessage("Salut").build();

        assertThat(p.thinking()).isPresent();
        assertThat(p.thinking().get().isDisabled()).isTrue();
        assertThat(p.maxTokens()).isEqualTo(1500);
    }

    @Test
    void anAnswerCutAtMaxTokens_orRefused_isNotUsed() {
        assertThat(ClaudeClient.usable(StopReason.END_TURN)).isTrue();
        assertThat(ClaudeClient.usable(StopReason.MAX_TOKENS)).isFalse();
        assertThat(ClaudeClient.usable(StopReason.REFUSAL)).isFalse();
    }
}
