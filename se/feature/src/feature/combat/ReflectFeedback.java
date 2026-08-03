package feature.combat;

import platform.text.Numbers;
import platform.text.Tokens;

/**
 * The {@code REFLECT} per-hit feedback line: fills {@code {damage}} with the health the reflect actually
 * returned, rendered as a chat number ({@link Numbers#chat} — the same convention as the DAMAGE_CAP arming
 * line and the MESSAGE token bindings).
 */
final class ReflectFeedback {

    private ReflectFeedback() {
    }

    static String fill(String template, double damage) {
        return Tokens.sub(template, "damage", Numbers.chat(damage));
    }
}
