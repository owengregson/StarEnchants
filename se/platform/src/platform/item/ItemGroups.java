package platform.item;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;

/**
 * The built-in named item groups for {@code applies-to} (docs/architecture.md §4.2; cross-version-item-api).
 * Resolves each token to the {@link Material}s present <em>on this version</em> by NAME via
 * {@link Material#getMaterial(String)}, so a material absent on an older/newer server is skipped rather
 * than a compile-time constant that would fail to link. Built once at boot, immutable; a cold-path check.
 */
public final class ItemGroups {

    public static final String ALL = "ALL";

    private final Map<String, Set<Material>> groups;

    private ItemGroups(Map<String, Set<Material>> groups) {
        this.groups = groups;
    }

    /** Whether {@code material} belongs to any of the named {@code tokens} (case-insensitive; {@code ALL} matches all). */
    public boolean matches(Material material, Collection<String> tokens) {
        if (material == null || material == Material.AIR) {
            return false;
        }
        for (String token : tokens) {
            String upper = token == null ? "" : token.toUpperCase(java.util.Locale.ROOT);
            if (ALL.equals(upper)) {
                return true;
            }
            Set<Material> set = groups.get(upper);
            if (set != null && set.contains(material)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A human-readable, grammatically-joined label for {@code applies-to} tokens — each token title-cased
     * ({@code SWORD}→Sword, {@code FISHING_ROD}→Fishing Rod) and serial-joined with an Oxford comma:
     * 1→{@code "Sword"}, 2→{@code "Sword & Axe"}, 3+→{@code "Boots, Leggings, & Helmet"}. Empty for no
     * tokens. Callers append their own suffix (e.g. {@code " Enchantment"}). A cold-path display helper.
     */
    public static String kindsLabel(Collection<String> tokens) {
        // Collapse common enumerated slot/type sets to one friendly category (order-independent), so an
        // armour enchant reads "Armor" rather than "Boots, Leggings, Chestplate, & Helmet".
        String grouped = groupedLabel(tokens);
        if (grouped != null) {
            return grouped;
        }
        List<String> words = new ArrayList<>();
        if (tokens != null) {
            for (String token : tokens) {
                String word = titleCase(token);
                if (!word.isEmpty()) {
                    words.add(word);
                }
            }
        }
        int n = words.size();
        if (n == 0) {
            return "";
        }
        if (n == 1) {
            return words.get(0);
        }
        if (n == 2) {
            return words.get(0) + " & " + words.get(1);
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < n - 1; i++) {
            out.append(words.get(i)).append(", ");
        }
        return out.append("& ").append(words.get(n - 1)).toString(); // Oxford comma before the final item
    }

    /** The four armour slots — when {@code applies-to} is exactly these, the label collapses to "Armor". */
    private static final Set<String> ARMOR_SLOTS = Set.of("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS");
    /** Melee weapons — when exactly these, the label collapses to "Weapon". */
    private static final Set<String> MELEE = Set.of("SWORD", "AXE");

    /**
     * A collapsed category label for a recognised enumerated token set, or {@code null} to fall back to the
     * per-token serial join. Matches as a SET (order-independent): the four armour slots → "Armor",
     * sword+axe → "Weapon", a lone fishing rod → "Rod".
     */
    private static String groupedLabel(Collection<String> tokens) {
        if (tokens == null) {
            return null;
        }
        Set<String> upper = new java.util.HashSet<>();
        for (String token : tokens) {
            if (token != null && !token.isBlank()) {
                upper.add(token.trim().toUpperCase(java.util.Locale.ROOT));
            }
        }
        if (upper.equals(Set.of(ALL))) {
            return "Any Item"; // the wildcard reads as a phrase, not the bare token "All"
        }
        if (upper.equals(ARMOR_SLOTS)) {
            return "Armor";
        }
        if (upper.equals(MELEE)) {
            return "Weapon";
        }
        if (upper.equals(Set.of("FISHING_ROD"))) {
            return "Rod";
        }
        return null;
    }

    /** Title-case a token, turning {@code _} into spaces: {@code FISHING_ROD} → {@code "Fishing Rod"}. */
    private static String titleCase(String token) {
        if (token == null || token.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (String part : token.toLowerCase(java.util.Locale.ROOT).split("_")) {
            if (part.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

    /** The resolved materials of a single group token (empty if the token is unknown on this version). */
    public Set<Material> materials(String token) {
        Set<Material> set = groups.get(token == null ? "" : token.toUpperCase(java.util.Locale.ROOT));
        return set == null ? Set.of() : set;
    }

    /** Build the standard groups, resolving each material by name and dropping those absent on this version. */
    public static ItemGroups standard() {
        Map<String, Set<Material>> table = new LinkedHashMap<>();
        String[] tiers = {"WOODEN", "STONE", "IRON", "GOLDEN", "DIAMOND", "NETHERITE"};
        define(table, "SWORD", tiered(tiers, "SWORD"));
        define(table, "AXE", tiered(tiers, "AXE"));
        define(table, "PICKAXE", tiered(tiers, "PICKAXE"));
        define(table, "SHOVEL", tiered(tiers, "SHOVEL"));
        define(table, "HOE", tiered(tiers, "HOE"));
        define(table, "BOW", "BOW");
        define(table, "CROSSBOW", "CROSSBOW");
        define(table, "TRIDENT", "TRIDENT");
        define(table, "SHIELD", "SHIELD");
        define(table, "FISHING_ROD", "FISHING_ROD");
        define(table, "MACE", "MACE"); // added in 1.21; absent earlier → skipped
        define(table, "HELMET", armor("HELMET", "TURTLE_HELMET"));
        define(table, "CHESTPLATE", armor("CHESTPLATE"));
        define(table, "LEGGINGS", armor("LEGGINGS"));
        define(table, "BOOTS", armor("BOOTS"));
        define(table, "ELYTRA", "ELYTRA");

        // Composites: unions of the primitives resolved above.
        define(table, "ARMOR", union(table, "HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"));
        define(table, "WEAPON", union(table, "SWORD", "AXE", "BOW", "CROSSBOW", "TRIDENT", "MACE"));
        define(table, "TOOL", union(table, "PICKAXE", "AXE", "SHOVEL", "HOE"));
        return new ItemGroups(table);
    }

    private static void define(Map<String, Set<Material>> table, String token, String... names) {
        define(table, token, resolve(names));
    }

    private static void define(Map<String, Set<Material>> table, String token, Set<Material> materials) {
        table.put(token, materials);
    }

    /** Resolve material names to the set present on this version (absent → skipped). */
    private static Set<Material> resolve(String... names) {
        EnumSet<Material> set = EnumSet.noneOf(Material.class);
        for (String name : names) {
            Material material = Material.getMaterial(name);
            if (material != null) {
                set.add(material);
            }
        }
        return set;
    }

    /** {@code <TIER>_<suffix>} for every tier, e.g. WOODEN_SWORD … NETHERITE_SWORD. */
    private static String[] tiered(String[] tiers, String suffix) {
        String[] names = new String[tiers.length];
        for (int i = 0; i < tiers.length; i++) {
            names[i] = tiers[i] + "_" + suffix;
        }
        return names;
    }

    /** Armour tiers (leather/chainmail/iron/gold/diamond/netherite) for a slot suffix, plus any extras. */
    private static String[] armor(String suffix, String... extras) {
        String[] tiers = {"LEATHER", "CHAINMAIL", "IRON", "GOLDEN", "DIAMOND", "NETHERITE"};
        String[] names = new String[tiers.length + extras.length];
        for (int i = 0; i < tiers.length; i++) {
            names[i] = tiers[i] + "_" + suffix;
        }
        System.arraycopy(extras, 0, names, tiers.length, extras.length);
        return names;
    }

    private static Set<Material> union(Map<String, Set<Material>> table, String... tokens) {
        EnumSet<Material> set = EnumSet.noneOf(Material.class);
        for (String token : tokens) {
            set.addAll(table.getOrDefault(token, Set.of()));
        }
        return set;
    }
}
