package compile.load;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import schema.diag.Diagnostics;

/**
 * The rarity-tier registry (ADR-0016 §2), read from {@code content/tiers.yml} — the single source of tier
 * ordering, presentation (lore colour, glint, GUI sort weight), and the default tier; it also answers
 * "is this subfolder a tier?" so the loader can derive a file's tier from its folder. Tiers are data,
 * never hard-coded in the compiler; absent {@code tiers.yml} falls back to {@link #BUILTIN} (common →
 * mythic) so the catalog still loads. Immutable.
 */
public final class TierRegistry {

    /** One tier's presentation metadata. */
    public record Tier(String name, String color, int weight, boolean glint) {
    }

    /** The fallback registry used when {@code content/tiers.yml} is absent or empty. */
    static final TierRegistry BUILTIN = builtin();

    private final Map<String, Tier> tiers; // insertion order = display/sort order
    private final String defaultTier;

    private TierRegistry(Map<String, Tier> tiers, String defaultTier) {
        this.tiers = Map.copyOf(tiers);
        this.defaultTier = defaultTier;
    }

    /** Whether {@code name} is a registered tier (so a subfolder of that name carries that tier). */
    public boolean isTier(String name) {
        return name != null && tiers.containsKey(name);
    }

    /** The default tier applied to a file with no tier folder and no in-file {@code tier:}. */
    public String defaultTier() {
        return defaultTier;
    }

    /** The tier's presentation metadata, or {@code null} if unregistered. */
    public Tier tier(String name) {
        return tiers.get(name);
    }

    /** Every registered tier, in declared order. */
    public Collection<Tier> tiers() {
        return tiers.values();
    }

    /** Read a composed {@code tiers.yml} node into a registry; a non-mapping/empty doc → {@link #BUILTIN}. */
    static TierRegistry read(YamlNode root, Diagnostics diags) {
        if (root == null || !root.isMapping()) {
            return BUILTIN;
        }
        Map<String, Tier> parsed = new LinkedHashMap<>();
        for (YamlNode.Entry entry : root.entries("tiers")) {
            String name = entry.key();
            YamlNode body = entry.value();
            String color = body.string("color");
            int weight = ContentParse.optInt(body, "weight", 0, diags);
            boolean glint = "true".equalsIgnoreCase(body.string("glint"));
            parsed.put(name, new Tier(name, color == null ? "" : color, weight, glint));
        }
        if (parsed.isEmpty()) {
            return BUILTIN;
        }
        String declared = ContentParse.blankToNull(root.string("default-tier"));
        String defaultTier = declared != null && parsed.containsKey(declared)
                ? declared
                : parsed.keySet().iterator().next();
        return new TierRegistry(parsed, defaultTier);
    }

    private static TierRegistry builtin() {
        Map<String, Tier> m = new LinkedHashMap<>();
        m.put("common", new Tier("common", "&7", 10, false));
        m.put("uncommon", new Tier("uncommon", "&a", 20, false));
        m.put("rare", new Tier("rare", "&b", 30, false));
        m.put("epic", new Tier("epic", "&d", 40, true));
        m.put("legendary", new Tier("legendary", "&6", 50, true));
        m.put("mythic", new Tier("mythic", "&c&l", 60, true));
        return new TierRegistry(m, "common");
    }
}
