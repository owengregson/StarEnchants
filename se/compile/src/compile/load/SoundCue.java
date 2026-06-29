package compile.load;

import java.util.ArrayList;
import java.util.List;
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
