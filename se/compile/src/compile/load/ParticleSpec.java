package compile.load;

import java.util.Objects;
import schema.diag.DiagCode;
import schema.diag.Diagnostics;

/**
 * A configured particle effect in our unified bracket form
 * {@code { particle: NAME, count: N, color: { r, g, b }, spread: S, y-offset: Y }} — extending the {@code PARTICLE}
 * effect's {@code particle}/{@code count} shape with an RGB {@code color} (applied only to the dust/redstone
 * particle), a {@code spread} radius, and a {@code y-offset} above the player. Pure data (no Bukkit); the runtime
 * spawns it via {@code feature.fx.ParticleFx}, which builds a cross-version {@code DustOptions} from the colour
 * (and degrades on 1.8.9, which has no coloured-dust API).
 *
 * @param type    the particle token (e.g. {@code REDSTONE}/{@code DUST}, {@code ENCHANTMENT_TABLE}); blank = none
 * @param colorR  red 0-255 (dust only)
 * @param colorG  green 0-255 (dust only)
 * @param colorB  blue 0-255 (dust only)
 * @param amount  particle count (>= 0; 0 = none)
 * @param spread  the x/y/z offset radius the particles scatter over (>= 0)
 * @param yOffset spawn height above the player's feet
 */
public record ParticleSpec(String type, int colorR, int colorG, int colorB, int amount, double spread, double yOffset) {

    public ParticleSpec {
        Objects.requireNonNull(type, "type");
        colorR = clampColor(colorR);
        colorG = clampColor(colorG);
        colorB = clampColor(colorB);
        amount = Math.max(0, amount);
        spread = Math.max(0.0, spread);
    }

    /** Nothing to spawn (no particle token, or a zero count). */
    public boolean isEmpty() {
        return type.isBlank() || amount <= 0;
    }

    public static ParticleSpec none() {
        return new ParticleSpec("", 0, 0, 0, 0, 0.0, 0.0);
    }

    /** Read one {@code { particle: NAME, count: N, color: { r, g, b }, spread: S, y-offset: Y }} mapping. */
    static ParticleSpec from(YamlNode node, Diagnostics diags) {
        if (node == null || !node.isMapping()) {
            return none();
        }
        String type = node.string("particle");
        if (type == null || type.isBlank()) {
            return none();
        }
        YamlNode color = node.child("color");
        return new ParticleSpec(
                type.trim(),
                readInt(color, "r", 0, diags),
                readInt(color, "g", 0, diags),
                readInt(color, "b", 0, diags),
                readInt(node, "count", 1, diags),
                readDouble(node, "spread", 0.0, diags),
                readDouble(node, "y-offset", 0.0, diags));
    }

    private static int clampColor(int component) {
        return Math.max(0, Math.min(255, component));
    }

    private static int readInt(YamlNode node, String key, int fallback, Diagnostics diags) {
        String raw = node.string(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException bad) {
            diags.warning(DiagCode.W_ITEM_NUM, "invalid number '" + raw + "', using " + fallback, node.sourceOf(key));
            return fallback;
        }
    }

    private static double readDouble(YamlNode node, String key, double fallback, Diagnostics diags) {
        String raw = node.string(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException bad) {
            diags.warning(DiagCode.W_ITEM_NUM, "invalid number '" + raw + "', using " + fallback, node.sourceOf(key));
            return fallback;
        }
    }
}
