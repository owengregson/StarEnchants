package compile.load;

import compile.def.AbilityDef;
import compile.model.SourceKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.IntSupplier;
import schema.diag.DiagCode;
import schema.diag.Diagnostics;
import schema.diag.Source;
import schema.grammar.EffectLine;

/**
 * Reads one authored armour-set file into its {@link SetDef} plus its bonus abilities (ADR-0014). The
 * {@code armor:}/{@code weapon:} blocks are PHYSICAL only (pieces, names, lore, minted enchants); every
 * BEHAVIOUR lives in the unified {@code bonuses:} list, where each block is {@code on: armor} (fires while the
 * set is complete) or {@code on: weapon} (fires while complete AND its weapon is held) and carries its own
 * trigger / chance / cooldown / condition / effects — so a set holds ANY NUMBER of independent effects, exactly
 * like an enchant's abilities. The first {@code on: armor} bonus is the completion ability ({@code <key>}, its
 * {@code complete} count on {@code setPieces)); further armour bonuses get {@code <key>/aN} and weapon bonuses
 * {@code <key>/wN} (all {@code setPieces} 0), gated by the resolver, not a piece count. A fault is a diagnostic;
 * a missing trigger, no armour bonus, or a non-positive completion count blocks, but the rest still parses.
 */
final class SetDefReader {

    private static final Set<String> ROOT_KEYS = Set.of("display", "description", "complete", "armor", "weapon",
            "bonuses", "announce", "equip-message", "remove-message");
    private static final Set<String> ARMOR_KEYS = Set.of("lore", "enchants", "pieces");
    private static final Set<String> WEAPON_KEYS = Set.of("material", "name", "lore", "enchants",
            "color", "enchant-rolls", "enchant-pools", "enchant-choices", "heroic");
    private static final Set<String> BONUS_KEYS = Set.of(
            "on", "trigger", "disabled-worlds", "group", "repeat", "chance", "cooldown", "soul-cost",
            "condition", "effects");
    private static final Set<String> MEMBER_KEYS = Set.of("material", "name", "lore", "enchants",
            "color", "enchant-rolls", "enchant-pools", "enchant-choices", "heroic");
    private static final Set<String> ROLL_KEYS = Set.of(
            "enchant", "chance", "mode", "level", "min-level", "max-level");
    private static final Set<String> POOL_KEYS = Set.of("enchants", "count", "mode");
    private static final Set<String> CHOICE_KEYS = Set.of("options");
    private static final Set<String> BRANCH_KEYS = Set.of("weight", "enchant-rolls");
    private static final Set<String> HEROIC_KEYS = Set.of(
            "outgoing", "incoming", "durability", "flat-damage", "flat-reduction");

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
            diags.error(DiagCode.E_LOAD_SET, "set file '" + baseKey + "' must be a YAML mapping", fileSource);
            return new Parsed(null, List.of());
        }
        ContentParse.warnUnknownKeys(root, ROOT_KEYS, diags);

        String display = ContentParse.blankToNull(root.string("display"));
        if (display == null) {
            display = baseKey;
        }
        String description = ContentParse.descriptionOf(root);

        // Physical armour: the pieces, their shared lore, and minted enchants. Behaviour is in bonuses:.
        YamlNode armor = root.child("armor");
        if (!armor.isMapping()) {
            diags.error(DiagCode.E_LOAD_SET_ARMOR, "set '" + baseKey + "' must declare an 'armor:' block", root.sourceOf("armor"));
        }
        ContentParse.warnUnknownKeys(armor, ARMOR_KEYS, diags);
        List<String> armorLore = armor.stringList("lore");
        java.util.Map<String, Integer> armorEnchants = readEnchants(armor, baseKey, diags);
        List<SetDef.Member> armorMembers = new ArrayList<>();
        List<String> appliesTo = new ArrayList<>();
        for (YamlNode.Entry entry : armor.entries("pieces")) {
            YamlNode memberNode = entry.value();
            ContentParse.warnUnknownKeys(memberNode, MEMBER_KEYS, diags);
            String slot = entry.key();
            String material = ContentParse.blankToNull(memberNode.string("material"));
            String name = ContentParse.blankToNull(memberNode.string("name"));
            if (material == null) {
                diags.error(DiagCode.E_LOAD_SET_MEMBER, "armour piece '" + slot + "' of '" + baseKey
                        + "' must declare a 'material'", memberNode.sourceOf("material"));
            }
            armorMembers.add(readMember(slot, material, name, memberNode, baseKey, diags));
            appliesTo.add(slot.toUpperCase(Locale.ROOT));
        }
        if (armorMembers.isEmpty()) {
            diags.error(DiagCode.E_LOAD_SET_ARMOR, "set '" + baseKey + "' declares no armour pieces (armor.pieces)",
                    armor.sourceOf("pieces"));
        }
        int complete = ContentParse.optInt(root, "complete", armorMembers.size(), diags);
        if (complete < 1) {
            diags.error(DiagCode.E_LOAD_SET_COMPLETE, "set '" + baseKey + "' must complete on a positive piece count, got "
                    + complete, root.sourceOf("complete"));
        }

        // Physical weapon (optional): material, name, lore, minted enchants. Its behaviour is an on:weapon bonus.
        SetDef.Member weapon = null;
        List<String> weaponLore = List.of();
        java.util.Map<String, Integer> weaponEnchants = java.util.Map.of();
        boolean hasWeaponItem = false;
        if (root.has("weapon")) {
            YamlNode weaponNode = root.child("weapon");
            ContentParse.warnUnknownKeys(weaponNode, WEAPON_KEYS, diags);
            String material = ContentParse.blankToNull(weaponNode.string("material"));
            String name = ContentParse.blankToNull(weaponNode.string("name"));
            weaponLore = weaponNode.stringList("lore");
            weaponEnchants = readEnchants(weaponNode, baseKey, diags);
            if (material == null) {
                diags.error(DiagCode.E_LOAD_SET_WEAPON, "the weapon of '" + baseKey + "' must declare a 'material'",
                        weaponNode.sourceOf("material"));
            }
            weapon = readMember("weapon", material, name, weaponNode, baseKey, diags);
            hasWeaponItem = true;
        }

        // Behaviours: the unified bonuses list. The first on:armor bonus is the completion ability
        // (stableKey == baseKey, setPieces = complete); further armour bonuses are baseKey/aN and weapon
        // bonuses baseKey/wN (setPieces 0), gated on set completion (and weapon-held) by the resolver.
        List<AbilityDef> abilities = new ArrayList<>();
        int armorBonuses = 0;
        int weaponBonuses = 0;
        for (YamlNode bonus : root.items("bonuses")) {
            if (!bonus.isMapping()) {
                diags.error(DiagCode.E_LOAD_SET, "set '" + baseKey + "' has a non-mapping bonus entry", bonus.source());
                continue;
            }
            ContentParse.warnUnknownKeys(bonus, BONUS_KEYS, diags);
            if (isWeaponScope(bonus.string("on"))) {
                abilities.add(ability(baseKey + "/w" + (++weaponBonuses), bonus, 0, fileSource, nextDefId, diags));
            } else {
                armorBonuses++;
                String stableKey = armorBonuses == 1 ? baseKey : baseKey + "/a" + (armorBonuses - 1);
                int setPieces = armorBonuses == 1 ? Math.max(0, complete) : 0;
                abilities.add(ability(stableKey, bonus, setPieces, fileSource, nextDefId, diags));
            }
        }
        if (armorBonuses == 0) {
            diags.error(DiagCode.E_LOAD_SET_ARMOR, "set '" + baseKey
                    + "' must declare at least one 'on: armor' bonus (bonuses:)", root.sourceOf("bonuses"));
        }
        if (weaponBonuses > 0 && !hasWeaponItem) {
            diags.warning(DiagCode.W_LOAD_SET_WEAPON_UNREACHABLE, "set '" + baseKey
                    + "' has an on:weapon bonus but no weapon: item to hold — it can never fire",
                    root.sourceOf("bonuses"));
        }

        // Optional equip/remove announcement (§6.6) — authored verbatim per set; the driver substitutes nothing.
        boolean announce = ContentParse.boolOr(root.string("announce"), false, "announce", DiagCode.W_LOAD_BOOL,
                root.sourceOf("announce"), diags);
        String equipMessage = root.string("equip-message");
        String removeMessage = root.string("remove-message");

        SetDef def = new SetDef(baseKey, display, description == null ? "" : description, null,
                Math.max(0, complete), armorMembers, armorLore, weapon, weaponLore, appliesTo,
                armorEnchants, weaponEnchants, announce, equipMessage, removeMessage, fileSource);
        return new Parsed(def, abilities);
    }

    /** A bonus is weapon-scoped when {@code on: weapon} (case-insensitive); anything else (incl. absent) is armour. */
    private static boolean isWeaponScope(String on) {
        return on != null && on.trim().equalsIgnoreCase("weapon");
    }

    private static SetDef.Member readMember(String slot, String material, String name, YamlNode node,
                                            String setKey, Diagnostics diags) {
        return new SetDef.Member(slot, material, name, node.stringList("lore"),
                readColor(node, setKey, diags), readEnchants(node, setKey, diags),
                readEnchantRolls(node, setKey, diags), readEnchantPools(node, setKey, diags),
                readEnchantChoices(node, setKey, diags), readHeroic(node, setKey, diags));
    }

    private static Integer readColor(YamlNode node, String setKey, Diagnostics diags) {
        String raw = ContentParse.blankToNull(node.string("color"));
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        try {
            int color = value.startsWith("#")
                    ? Integer.parseInt(value.substring(1), 16)
                    : Integer.parseInt(value);
            if (color < 0 || color > 0xFFFFFF) {
                throw new NumberFormatException("outside RGB");
            }
            return color;
        } catch (NumberFormatException bad) {
            diags.error(DiagCode.E_LOAD_SET_MEMBER, "set '" + setKey
                    + "' leather color must be #RRGGBB or 0..16777215, got '" + raw + "'", node.sourceOf("color"));
            return null;
        }
    }

    private static List<SetDef.EnchantRoll> readEnchantRolls(YamlNode node, String setKey, Diagnostics diags) {
        List<SetDef.EnchantRoll> out = new ArrayList<>();
        for (YamlNode roll : node.items("enchant-rolls")) {
            if (!roll.isMapping()) {
                diags.error(DiagCode.E_LOAD_SET_MEMBER, "set '" + setKey
                        + "' enchant-roll entry must be a mapping", roll.source());
                continue;
            }
            ContentParse.warnUnknownKeys(roll, ROLL_KEYS, diags);
            String enchant = ContentParse.blankToNull(roll.string("enchant"));
            if (enchant == null) {
                diags.error(DiagCode.E_LOAD_SET_MEMBER, "set '" + setKey
                        + "' enchant-roll must declare an enchant", roll.sourceOf("enchant"));
                continue;
            }
            double chance = ContentParse.optDouble(roll, "chance", 100.0, diags);
            if (!Double.isFinite(chance) || chance < 0.0 || chance > 100.0) {
                diags.error(DiagCode.E_LOAD_SET_MEMBER, "set '" + setKey
                        + "' enchant-roll chance must be within 0..100, got " + chance, roll.sourceOf("chance"));
            }
            int level = ContentParse.optInt(roll, "level",
                    ContentParse.optInt(roll, "min-level", 1, diags), diags);
            int maxLevel = ContentParse.optInt(roll, "max-level", level, diags);
            out.add(new SetDef.EnchantRoll(enchant, chance, readRollMode(roll, setKey, diags), level, maxLevel));
        }
        return out;
    }

    private static List<SetDef.EnchantChoice> readEnchantChoices(
            YamlNode node, String setKey, Diagnostics diags) {
        List<SetDef.EnchantChoice> out = new ArrayList<>();
        for (YamlNode choice : node.items("enchant-choices")) {
            if (!choice.isMapping()) {
                diags.error(DiagCode.E_LOAD_SET_MEMBER, "set '" + setKey
                        + "' enchant-choice entry must be a mapping", choice.source());
                continue;
            }
            ContentParse.warnUnknownKeys(choice, CHOICE_KEYS, diags);
            List<SetDef.EnchantBranch> branches = new ArrayList<>();
            for (YamlNode branch : choice.items("options")) {
                if (!branch.isMapping()) {
                    diags.error(DiagCode.E_LOAD_SET_MEMBER, "set '" + setKey
                            + "' enchant-choice option must be a mapping", branch.source());
                    continue;
                }
                ContentParse.warnUnknownKeys(branch, BRANCH_KEYS, diags);
                double weight = ContentParse.optDouble(branch, "weight", 0.0, diags);
                List<SetDef.EnchantRoll> rolls = readEnchantRolls(branch, setKey, diags);
                if (!(weight > 0.0)) {
                    diags.error(DiagCode.E_LOAD_SET_MEMBER, "set '" + setKey
                            + "' enchant-choice option needs a positive weight", branch.source());
                }
                branches.add(new SetDef.EnchantBranch(weight, rolls));
            }
            if (branches.isEmpty()) {
                diags.error(DiagCode.E_LOAD_SET_MEMBER, "set '" + setKey
                        + "' enchant-choice needs at least one option", choice.source());
            }
            out.add(new SetDef.EnchantChoice(branches));
        }
        return out;
    }

    private static List<SetDef.EnchantPool> readEnchantPools(YamlNode node, String setKey, Diagnostics diags) {
        List<SetDef.EnchantPool> out = new ArrayList<>();
        for (YamlNode pool : node.items("enchant-pools")) {
            if (!pool.isMapping()) {
                diags.error(DiagCode.E_LOAD_SET_MEMBER, "set '" + setKey
                        + "' enchant-pool entry must be a mapping", pool.source());
                continue;
            }
            ContentParse.warnUnknownKeys(pool, POOL_KEYS, diags);
            List<String> enchants = pool.stringList("enchants");
            int count = ContentParse.optInt(pool, "count", 1, diags);
            if (enchants.isEmpty() || count < 0 || count > enchants.size()) {
                diags.error(DiagCode.E_LOAD_SET_MEMBER, "set '" + setKey
                        + "' enchant-pool count must fit its non-empty enchant list", pool.source());
            }
            out.add(new SetDef.EnchantPool(enchants, count, readRollMode(pool, setKey, diags)));
        }
        return out;
    }

    private static SetDef.RollMode readRollMode(YamlNode node, String setKey, Diagnostics diags) {
        String raw = ContentParse.blankToNull(node.string("mode"));
        if (raw == null) {
            return SetDef.RollMode.FIXED;
        }
        try {
            return SetDef.RollMode.valueOf(raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException bad) {
            diags.error(DiagCode.E_LOAD_SET_MEMBER, "set '" + setKey
                    + "' roll mode must be fixed, max, uniform, range, plain-near-max, or ability-near-max, got '" + raw + "'",
                    node.sourceOf("mode"));
            return SetDef.RollMode.FIXED;
        }
    }

    private static SetDef.Heroic readHeroic(YamlNode node, String setKey, Diagnostics diags) {
        if (!node.has("heroic")) {
            return SetDef.Heroic.NONE;
        }
        YamlNode heroic = node.child("heroic");
        if (!heroic.isMapping()) {
            diags.error(DiagCode.E_LOAD_SET_MEMBER, "set '" + setKey + "' heroic must be a mapping",
                    node.sourceOf("heroic"));
            return SetDef.Heroic.NONE;
        }
        ContentParse.warnUnknownKeys(heroic, HEROIC_KEYS, diags);
        return new SetDef.Heroic(
                ContentParse.optDouble(heroic, "outgoing", 0.0, diags),
                ContentParse.optDouble(heroic, "incoming", 0.0, diags),
                ContentParse.optDouble(heroic, "durability", 0.0, diags),
                ContentParse.optDouble(heroic, "flat-damage", 0.0, diags),
                ContentParse.optDouble(heroic, "flat-reduction", 0.0, diags));
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
            Integer level = ContentParse.parseInt(raw);
            if (level == null) {
                diags.warning(DiagCode.W_SET_ENCHANT, "set '" + setKey + "' enchant '" + entry.key()
                        + "' level is not a number: " + raw, entry.value().source());
                continue;
            }
            out.put(entry.key(), level);
        }
        return out;
    }

    private static AbilityDef ability(String stableKey, YamlNode node, int setPieces, Source fileSource,
                                      IntSupplier nextDefId, Diagnostics diags) {
        List<String> triggers = node.stringList("trigger");
        if (triggers.isEmpty()) {
            diags.error(DiagCode.E_LOAD_SET_TRIGGER, "set bonus '" + stableKey + "' declares no trigger",
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
            diags.warning(DiagCode.W_LOAD_EFFECTS, "set bonus '" + stableKey + "' declares no effects",
                    node.sourceOf("effects"));
        }
        return new AbilityDef(
                SourceKind.SET, stableKey, nextDefId.getAsInt(), 0, chance, cooldown, soulCost, triggers,
                disabledWorlds, condition, effects, stableKey, stableKey, group, null, repeatTicks, fileSource,
                Math.max(0, setPieces));
    }
}
