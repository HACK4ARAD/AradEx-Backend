package aradexperience.dto.ai;

import java.util.List;

/**
 * Răspunsul asistentului (T12): textul, id-urile evenimentelor recomandate (doar din DB, validate de BE), chips de răspuns rapid
 * și `offline` = true când Claude n-a răspuns și textul vine din răspunsurile pregătite.
 */
public record ChatResponse(String reply, List<Long> suggestedEventIds, List<String> quickReplies, boolean offline) {
}
