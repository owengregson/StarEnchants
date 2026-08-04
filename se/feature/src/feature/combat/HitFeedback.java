package feature.combat;

import platform.text.Numbers;
import platform.text.Tokens;

/**
 * A per-hit feedback line: fills {@code {damage}} with the health the hit actually moved, rendered as a chat
 * number ({@link Numbers#chat} — the same convention as the DAMAGE_CAP arming line and the MESSAGE token
 * bindings). Shared by {@code REFLECT}'s return line and {@code VULNERABILITY}'s amplified-hit line, so the
 * two cannot drift on how a damage figure reads.
 */
public final class HitFeedback {

    private HitFeedback() {
    }

    public static String fill(String template, double damage) {
        return Tokens.sub(template, "damage", Numbers.chat(damage));
    }
}
