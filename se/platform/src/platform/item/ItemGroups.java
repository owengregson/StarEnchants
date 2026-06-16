package platform.item;

import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;

/**
 * The built-in named item groups (docs/architecture.md §4.2 {@code applies-to}; cross-version-item-api
 * skill) — the table that answers "may this enchant sit on this item?". An enchant/crystal declares
 * {@code applies-to: [SWORD, AXE]}; this resolves each token to the set of {@link Material}s present
 * <em>on this version</em> and tests membership. Materials are resolved by NAME through
 * {@link Material#getMaterial(String)} (stable across 1.17.1 → 26.1.x) so a material absent on an
 * older/newer server is simply skipped — never a compile-time constant that would fail to link.
 *
 * <p>Groups compose: {@code ARMOR}/{@code WEAPON}/{@code TOOL} are unions of the primitive groups, and
 * the wildcard {@code ALL} matches any item. The table is built once at boot and is immutable; the
 * eligibility check is a cold apply-path concern, never the combat hot path.
 */
public final class ItemGroups {

    /** The wildcard token that matches any (non-air) item. */
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

        // Composite groups: unions of the primitives already resolved above.
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
