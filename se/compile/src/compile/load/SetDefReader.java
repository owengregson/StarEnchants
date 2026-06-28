package compile.load;

import compile.def.AbilityDef;
import compile.model.SourceKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.IntSupplier;
import schema.diag.DiagCode;
import schema.diag.Diagnostics;
import schema.diag.Source;
import schema.grammar.EffectLine;

/**
 * Reads one authored armour-set file into its {@link SetDef} plus its bonus abilities (ADR-0014): the
 * {@code armor:} bonus compiled to {@code <key>} (its {@code complete} count on {@code setPieces}) and the
 * optional {@code weapon:} bonus to {@code <key>/weapon} ({@code setPieces} 0, resolver-gated). A fault is
 * a diagnostic; a missing trigger or non-positive completion count blocks, but the rest still parses.
 */
final class SetDefReader {

    private static final Set<String> ROOT_KEYS = Set.of("display", "description", "complete", "armor", "weapon");
    private static final Set<String> ARMOR_KEYS = Set.of(
            "lore", "enchants", "pieces", "trigger", "disabled-worlds", "group", "repeat", "chance", "cooldown",
            "soul-cost", "condition", "effects");
    private static final Set<String> WEAPON_KEYS = Set.of(
            "material", "name", "lore", "enchants", "trigger", "disabled-worlds", "group", "repeat", "chance",
            "cooldown", "soul-cost", "condition", "effects");
    private static final Set<String> MEMBER_KEYS = Set.of("material", "name");

    private SetDefReader() {
    }

    record Parsed(SetDef def, List<AbilityDef> abilities) {
    }

    /** Test/convenience entry: no folder-derived tier (sets are tierless). */
    static Parsed read(String baseKey, YamlNode root, IntSupplier nextDefId, Diagnostics diags) {
        return read(baseKey, null, root, nextDefId, diags);
    }

    static Parsed read(String baseKey, String folderTier, YamlNode root, IntSupplier nextDefId, Diagnostics diags) {
        Source fileSource = root.source();
        if (!root.isMapping()) {
            diags.error("load.set", "set file '" + baseKey + "' must be a YAML mapping", fileSource);
            return new Parsed(null, List.of());
        }
        ContentParse.warnUnknownKeys(root, ROOT_KEYS, diags);

        String display = ContentParse.blankToNull(root.string("display"));
        if (display == null) {
            display = baseKey;
        }
        String description = ContentParse.descriptionOf(root);

        YamlNode armor = root.child("armor");
        if (!armor.isMapping()) {
            diags.error("load.set.armor", "set '" + baseKey + "' must declare an 'armor:' block", root.sourceOf("armor"));
        }
        ContentParse.warnUnknownKeys(armor, ARMOR_KEYS, diags);
        List<String> armorLore = armor.stringList("lore");
        java.util.Map<String, Integer> armorEnchants = readEnchants(armor, baseKey, diags);
        List<SetDef.Member> armorMembers = new ArrayList<>();
        List<String> appliesTo = new ArrayList<>();
        for (YamlNode.Entry entry : armor.entries("pieces")) {
            ContentParse.warnUnknownKeys(entry.value(), MEMBER_KEYS, diags);
            String slot = entry.key();
            String material = ContentParse.blankToNull(entry.value().string("material"));
            String name = ContentParse.blankToNull(entry.value().string("name"));
            if (material == null) {
                diags.error("load.set.member", "armour piece '" + slot + "' of '" + baseKey
                        + "' must declare a 'material'", entry.value().sourceOf("material"));
            }
            armorMembers.add(new SetDef.Member(slot, material, name));
            appliesTo.add(slot.toUpperCase(java.util.Locale.ROOT));
        }
        if (armorMembers.isEmpty()) {
            diags.error("load.set.armor", "set '" + baseKey + "' declares no armour pieces (armor.pieces)",
                    armor.sourceOf("pieces"));
        }
        int complete = ContentParse.optInt(root, "complete", armorMembers.size(), diags);
        if (complete < 1) {
            diags.error("load.set.complete", "set '" + baseKey + "' must complete on a positive piece count, got "
                    + complete, root.sourceOf("complete"));
        }

        List<AbilityDef> abilities = new ArrayList<>();
        abilities.add(ability(baseKey, armor, Math.max(0, complete), fileSource, nextDefId, diags));

        SetDef.Member weapon = null;
        List<String> weaponLore = List.of();
        java.util.Map<String, Integer> weaponEnchants = java.util.Map.of();
        if (root.has("weapon")) {
            YamlNode weaponNode = root.child("weapon");
            ContentParse.warnUnknownKeys(weaponNode, WEAPON_KEYS, diags);
            String material = ContentParse.blankToNull(weaponNode.string("material"));
            String name = ContentParse.blankToNull(weaponNode.string("name"));
            weaponLore = weaponNode.stringList("lore");
            weaponEnchants = readEnchants(weaponNode, baseKey, diags);
            if (material == null) {
                diags.error("load.set.weapon", "the weapon of '" + baseKey + "' must declare a 'material'",
                        weaponNode.sourceOf("material"));
            }
            weapon = new SetDef.Member("weapon", material, name);
            abilities.add(ability(baseKey + "/weapon", weaponNode, 0, fileSource, nextDefId, diags));
        }

        SetDef def = new SetDef(baseKey, display, description == null ? "" : description, null,
                Math.max(0, complete), armorMembers, armorLore, weapon, weaponLore, appliesTo,
                armorEnchants, weaponEnchants, fileSource);
        return new Parsed(def, abilities);
    }

    /**
     * Parse an {@code enchants:} block ({@code ref: level}) — the enchants a minted piece carries (§6.6). A
     * {@code enchants/<id>} ref is a custom plugin enchant (referential integrity is checked library-wide in
     * {@code LibraryLoader}); any other key is a vanilla enchant NAME resolved cross-version at mint. A
     * non-numeric level warns and is skipped. Insertion order is preserved.
     */
    private static java.util.Map<String, Integer> readEnchants(YamlNode block, String setKey, Diagnostics diags) {
        java.util.Map<String, Integer> out = new java.util.LinkedHashMap<>();
        if (!block.has("enchants")) {
            return out;
        }
        for (YamlNode.Entry entry : block.entries("enchants")) {
            String raw = entry.value().scalar();
            if (raw == null) {
                continue;
            }
            try {
                out.put(entry.key(), Integer.parseInt(raw.trim()));
            } catch (NumberFormatException bad) {
                diags.warning(DiagCode.W_SET_ENCHANT, "set '" + setKey + "' enchant '" + entry.key()
                        + "' level is not a number: " + raw, entry.value().source());
            }
        }
        return out;
    }

    private static AbilityDef ability(String stableKey, YamlNode node, int setPieces, Source fileSource,
                                      IntSupplier nextDefId, Diagnostics diags) {
        List<String> triggers = node.stringList("trigger");
        if (triggers.isEmpty()) {
            diags.error("load.set.trigger", "set bonus '" + stableKey + "' declares no trigger",
                    node.sourceOf("trigger"));
        }
        List<String> disabledWorlds = node.stringList("disabled-worlds");
        String group = ContentParse.blankToNull(node.string("group"));
        int repeatTicks = ContentParse.optInt(node, "repeat", 0, diags);
        double chance = ContentParse.resolveChance(node, "chance", diags);
        int cooldown = ContentParse.resolveInt(node, "cooldown", 0, diags);
        int soulCost = ContentParse.resolveInt(node, "soul-cost", 0, diags);
        String condition = ContentParse.blankToNull(node.string("condition"));
        List<EffectLine> effects = ContentParse.effectItems(node, "effects", diags);
        if (effects.isEmpty()) {
            diags.warning("load.effects", "set bonus '" + stableKey + "' declares no effects",
                    node.sourceOf("effects"));
        }
        return new AbilityDef(
                SourceKind.SET, stableKey, nextDefId.getAsInt(), 0, chance, cooldown, soulCost, triggers,
                disabledWorlds, condition, effects, stableKey, stableKey, group, null, repeatTicks, fileSource,
                Math.max(0, setPieces));
    }
}
