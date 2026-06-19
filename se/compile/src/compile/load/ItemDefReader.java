package compile.load;

import java.util.List;
import java.util.Set;
import schema.diag.Diagnostics;
import schema.diag.Source;

/**
 * Reads one authored carrier-item file (a composed {@link YamlNode} mapping) into an {@link ItemDef}
 * (ADR-0016 §4). Produces NO abilities and never reaches the compiler — it is inert metadata for the
 * forthcoming carrier-application feature. {@code kind:} drives the allowed shape (a book grants an
 * enchant/crystal/set; a scroll carries a role; a dust carries a success modifier), so the sub-schemas
 * don't overload one ambiguous block. Every fault is a {@code file:line:col} diagnostic, never thrown.
 */
final class ItemDefReader {

    private static final Set<String> KINDS = Set.of("book", "tome", "scroll", "dust", "gem");
    private static final Set<String> ROOT_KEYS = Set.of(
            "display", "description", "tier", "kind", "material", "glow", "grants", "apply");

    private ItemDefReader() {
    }

    /** Parse one carrier item. {@code baseKey} is the path-derived key, e.g. {@code items/book/x}. */
    static ItemDef read(String baseKey, String folderTier, YamlNode root, Diagnostics diags) {
        Source fileSource = root.source();
        if (!root.isMapping()) {
            diags.error("load.item", "item file '" + baseKey + "' must be a YAML mapping", fileSource);
            return null;
        }
        ContentParse.warnUnknownKeys(root, ROOT_KEYS, diags);

        String display = ContentParse.blankToNull(root.string("display"));
        if (display == null) {
            display = baseKey;
        }
        String description = ContentParse.descriptionOf(root);
        String tier = ContentParse.resolveTier(folderTier, root, diags);

        String kind = ContentParse.blankToNull(root.string("kind"));
        if (kind == null) {
            diags.error("load.item.kind", "item '" + baseKey + "' declares no kind (book|tome|scroll|dust|gem)",
                    root.sourceOf("kind"));
        } else if (!KINDS.contains(kind.toLowerCase(java.util.Locale.ROOT))) {
            diags.error("load.item.kind", "item '" + baseKey + "' has unknown kind '" + kind
                    + "' (book|tome|scroll|dust|gem)", root.sourceOf("kind"));
        }
        String normalizedKind = kind == null ? null : kind.toLowerCase(java.util.Locale.ROOT);

        String material = ContentParse.blankToNull(root.string("material"));
        if (material == null) {
            material = defaultMaterial(normalizedKind);
        }
        boolean glow = root.has("glow")
                ? "true".equalsIgnoreCase(root.string("glow"))
                : defaultGlow(normalizedKind);

        ItemDef.Grant grant = readGrant(root.child("grants"));
        ItemDef.Apply apply = readApply(root.child("apply"), diags);
        validateShape(baseKey, normalizedKind, grant, root, diags);

        return new ItemDef(baseKey, display, description == null ? "" : description, tier,
                normalizedKind, material, glow, grant, apply, fileSource);
    }

    private static ItemDef.Grant readGrant(YamlNode grants) {
        if (!grants.isMapping()) {
            return null;
        }
        String enchant = ContentParse.blankToNull(grants.string("enchant"));
        String crystal = ContentParse.blankToNull(grants.string("crystal"));
        String set = ContentParse.blankToNull(grants.string("set"));
        int level = ContentParse.parseIntOr(grants.string("level"), 1);
        Integer successBonus = ContentParse.parseInt(grants.string("success-bonus") == null
                ? "" : grants.string("success-bonus"));
        String role = ContentParse.blankToNull(grants.string("role"));
        if (enchant == null && crystal == null && set == null && successBonus == null && role == null) {
            return null;
        }
        String sound = ContentParse.blankToNull(grants.string("sound"));
        java.util.List<String> particles = grants.has("particles") ? grants.stringList("particles") : java.util.List.of();
        return new ItemDef.Grant(enchant, crystal, set, level, successBonus, role, sound, particles);
    }

    private static ItemDef.Apply readApply(YamlNode apply, Diagnostics diags) {
        int successChance = ContentParse.optInt(apply, "success-chance", 100, diags);
        boolean destroyOnFail = "true".equalsIgnoreCase(apply.string("destroy-on-fail"));
        boolean protectable = !"false".equalsIgnoreCase(apply.string("protectable")); // default true
        List<String> appliesTo = apply.stringList("applies-to");
        return new ItemDef.Apply(successChance, destroyOnFail, protectable, appliesTo);
    }

    /** Light kind-driven shape check (ADR-0016 §4): warn when a carrier's grant doesn't fit its kind. */
    private static void validateShape(String baseKey, String kind, ItemDef.Grant grant, YamlNode root,
                                      Diagnostics diags) {
        if (kind == null) {
            return; // already errored on the missing kind
        }
        switch (kind) {
            case "book", "tome", "gem" -> {
                boolean grantsContent = grant != null
                        && (grant.enchant() != null || grant.crystal() != null || grant.set() != null);
                if (!grantsContent) {
                    diags.warning("load.item.grant", "a " + kind + " ('" + baseKey
                            + "') should grant an enchant/crystal/set under grants:", root.sourceOf("grants"));
                }
            }
            case "dust" -> {
                if (grant == null || grant.successBonus() == null) {
                    diags.warning("load.item.grant", "a dust ('" + baseKey
                            + "') should carry a grants.success-bonus", root.sourceOf("grants"));
                }
            }
            case "scroll" -> {
                if (grant == null || grant.role() == null) {
                    diags.warning("load.item.grant", "a scroll ('" + baseKey
                            + "') should carry a grants.role (e.g. PROTECT)", root.sourceOf("grants"));
                }
            }
            default -> { /* unknown kind already errored */ }
        }
    }

    private static String defaultMaterial(String kind) {
        if (kind == null) {
            return "PAPER";
        }
        return switch (kind) {
            case "book", "tome" -> "ENCHANTED_BOOK";
            case "dust" -> "GLOWSTONE_DUST";
            case "gem" -> "EMERALD";
            default -> "PAPER"; // scroll
        };
    }

    private static boolean defaultGlow(String kind) {
        return "book".equals(kind) || "tome".equals(kind) || "gem".equals(kind);
    }
}
