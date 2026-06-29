package compile.load;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import schema.diag.DiagCode;
import schema.diag.Diagnostics;

/**
 * One configured sound in our unified bracket form {@code { sound: NAME, volume: V, pitch: P }} — the same
 * shape the {@code SOUND} effect uses, so operators read one convention everywhere ({@code volume}/{@code pitch}
 * optional, default {@code 1.0}). A list of these lets one action play several sounds at once. Pure data +
 * loader parsing (no Bukkit); the runtime plays each via {@code feature.compat.Sounds.play}.
 */
public record SoundCue(String name, float volume, float pitch) {

    public SoundCue {
        Objects.requireNonNull(name, "name");
    }

    /**
     * The Bukkit {@code Sound} CONSTANT name for a sound token — uppercased, any {@code namespace:} stripped,
     * and dots → underscores — so BOTH an enum-form token ({@code BLOCK_BEACON_POWER_SELECT}) and a
     * resource-key token ({@code block.beacon.power_select} / {@code entity.player.levelup}) map to the same
     * constant. The runtime ({@code feature.compat.Sounds}) resolves this against {@code org.bukkit.Sound}
     * cross-version, so a token never reaches the namespaced-key parser (which rejects uppercase/underscores).
     */
    public static String canonical(String name) {
        if (name == null) {
            return "";
        }
        String token = name.trim();
        int colon = token.indexOf(':');
        if (colon >= 0) {
            token = token.substring(colon + 1);
        }
        return token.toUpperCase(Locale.ROOT).replace('.', '_');
    }

    /** Read one {@code { sound: NAME, volume: V, pitch: P }} mapping; absent/blank {@code sound} → {@code null}. */
    static SoundCue from(YamlNode node, Diagnostics diags) {
        if (node == null || !node.isMapping()) {
            return null;
        }
        String name = node.string("sound");
        if (name == null || name.isBlank()) {
            return null;
        }
        return new SoundCue(name.trim(), readFloat(node, "volume", 1.0f, diags), readFloat(node, "pitch", 1.0f, diags));
    }

    /** Read the sequence of sound mappings under {@code key} (each a bracket map), skipping any with no name. */
    static List<SoundCue> list(YamlNode parent, String key, Diagnostics diags) {
        List<SoundCue> out = new ArrayList<>();
        for (YamlNode node : parent.items(key)) {
            SoundCue cue = from(node, diags);
            if (cue != null) {
                out.add(cue);
            }
        }
        return List.copyOf(out);
    }

    private static float readFloat(YamlNode node, String key, float fallback, Diagnostics diags) {
        String raw = node.string(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Float.parseFloat(raw.trim());
        } catch (NumberFormatException bad) {
            diags.warning(DiagCode.W_ITEM_NUM, "invalid number '" + raw + "', using " + fallback, node.sourceOf(key));
            return fallback;
        }
    }
}
