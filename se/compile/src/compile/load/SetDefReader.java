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
 * {@code complete} count on {@code setPieces}); further armour bonuses get {@code <key>/aN} and weapon bonuses
 * {@code <key>/wN} (all {@code setPieces} 0), gated by the resolver, not a piece count. A fault is a diagnostic;
 * a missing trigger, no armour bonus, or a non-positive completion count blocks, but the rest still parses.
 */
final class SetDefReader {

    private static final Set<String> ROOT_KEYS = Set.of("display", "description", "complete", "armor", "weapon",
            "bonuses", "announce", "equip-message", "remove-message");
    private static final Set<String> ARMOR_KEYS = Set.of("lore", "enchants", "pieces");
    private static final Set<String> WEAPON_KEYS = Set.of("material", "name", "lore", "enchants");
    private static final Set<String> BONUS_KEYS = ContentParse.withEnvelopeKnobs(
            "on", "trigger", "disabled-worlds", "group", "repeat", "repeat-delay", "chance", "cooldown", "soul-cost",
            "soul-cost-growth", "soul-cost-cap", "soul-cost-decay-period",
            "no-souls-message", "condition", "effects");
    private static final Set<String> MEMBER_KEYS = Set.of("material", "name", "lore", "enchants", "color", "heroic");

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
        java.util.Map<String, EnchantRoll> armorEnchants = readEnchants(armor, baseKey, diags);
        List<SetDef.Member> armorMembers = new ArrayList<>();
        List<String> appliesTo = new ArrayList<>();
        for (YamlNode.Entry entry : armor.entries("pieces")) {
            ContentParse.warnUnknownKeys(entry.value(), MEMBER_KEYS, diags);
            String slot = entry.key();
            YamlNode piece = entry.value();
            String material = ContentParse.blankToNull(piece.string("material"));
            String name = ContentParse.blankToNull(piece.string("name"));
            if (material == null) {
                diags.error(DiagCode.E_LOAD_SET_MEMBER, "armour piece '" + slot + "' of '" + baseKey
                        + "' must declare a 'material'", piece.sourceOf("material"));
            }
            // Per-piece refinements (all optional, all absent on a set authored before this surface): its own
            // flavour lore above the shared block, its own roster entries after the shared ones, a leather dye,
            // and whether the piece mints ALREADY heroic.
            armorMembers.add(new SetDef.Member(slot, material, name, piece.stringList("lore"),
                    readEnchants(piece, baseKey + " piece '" + slot + "'", diags),
                    ContentParse.blankToNull(piece.string("color")),
                    ContentParse.boolOr(piece.string("heroic"), false, "heroic", DiagCode.W_LOAD_BOOL,
                            piece.sourceOf("heroic"), diags)));
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
        java.util.Map<String, EnchantRoll> weaponEnchants = java.util.Map.of();
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
            weapon = new SetDef.Member("weapon", material, name);
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

    /**
     * Parse an {@code enchants:} block — the mint roster a minted piece carries (§6.6). A {@code enchants/<id>}
     * ref is a custom plugin enchant (referential integrity is checked library-wide in {@code LibraryLoader});
     * any other key is a vanilla enchant NAME resolved cross-version at mint. Insertion order is preserved: it
     * is the piece's enchant-lore order.
     *
     * <p>An entry is either a bare level ({@code PROTECTION: 5}) or a roll map — {@code { min: 2, max: 5 }},
     * {@code { nearly-maxed: 4 }}, {@code { chance: 25, min: 1, max: 4 }}. A malformed entry warns by code and
     * is SKIPPED rather than guessed: minting an enchant at a level nobody authored is worse than not minting it.
     */
    private static java.util.Map<String, EnchantRoll> readEnchants(YamlNode block, String setKey, Diagnostics diags) {
        java.util.Map<String, EnchantRoll> out = new java.util.LinkedHashMap<>();
        if (!block.has("enchants")) {
            return out;
        }
        for (YamlNode.Entry entry : block.entries("enchants")) {
            EnchantRoll roll = entry.value().isMapping()
                    ? readRoll(entry.value(), setKey, entry.key(), diags)
                    : readFixed(entry.value(), setKey, entry.key(), diags);
            if (roll != null) {
                out.put(entry.key(), roll);
            }
        }
        return out;
    }

    /** The bare-level form: one integer, always minted, at exactly that level. */
    private static EnchantRoll readFixed(YamlNode value, String setKey, String ref, Diagnostics diags) {
        String raw = value.scalar();
        if (raw == null) {
            return null;
        }
        Integer level = ContentParse.parseInt(raw);
        if (level == null) {
            diags.warning(DiagCode.W_SET_ENCHANT, "set '" + setKey + "' enchant '" + ref
                    + "' level is not a number: " + raw, value.source());
            return null;
        }
        return EnchantRoll.fixed(level);
    }

    /**
     * The roll form. {@code nearly-maxed: M} is the family's measured draw and fixes both bounds; otherwise
     * {@code min}/{@code max} give a uniform band (equal bounds, or a lone {@code max}, degrade to FIXED so a
     * band of one is not a needless draw). {@code chance} gates whether the entry mints at all.
     */
    private static EnchantRoll readRoll(YamlNode value, String setKey, String ref, Diagnostics diags) {
        // Fractional (R-QC51): parsed as a double, so an authored 17.5 rolls at 17.5 instead of failing to
        // parse as an int and falling back to 100 — an entry meant to be rare minting on every single piece.
        double chance = ContentParse.doubleOr(value.string("chance"), 100.0, "chance",
                schema.diag.Severity.WARNING, DiagCode.W_SET_ENCHANT, value.sourceOf("chance"), diags);
        Integer nearlyMaxed = value.has("nearly-maxed")
                ? ContentParse.parseInt(value.string("nearly-maxed")) : null;
        if (value.has("nearly-maxed")) {
            if (nearlyMaxed == null) {
                diags.warning(DiagCode.W_SET_ENCHANT, "set '" + setKey + "' enchant '" + ref
                        + "' nearly-maxed is not a number: " + value.string("nearly-maxed"), value.source());
                return null;
            }
            return new EnchantRoll(Math.max(1, nearlyMaxed - 2), nearlyMaxed, chance, EnchantRoll.Mode.NEARLY_MAXED);
        }
        if (!value.has("max")) {
            diags.warning(DiagCode.W_SET_ENCHANT, "set '" + setKey + "' enchant '" + ref
                    + "' declares no level: a roll needs 'max' or 'nearly-maxed'", value.source());
            return null;
        }
        int max = rollInt(value, "max", 1, diags);
        int min = rollInt(value, "min", max, diags);
        return new EnchantRoll(min, max, chance,
                min >= max ? EnchantRoll.Mode.FIXED : EnchantRoll.Mode.UNIFORM);
    }

    private static int rollInt(YamlNode value, String key, int fallback, Diagnostics diags) {
        return ContentParse.intOr(value.string(key), fallback, key, schema.diag.Severity.WARNING,
                DiagCode.W_SET_ENCHANT, value.sourceOf(key), diags);
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
        // R-QC35b: ticks before the FIRST run; -1 keeps the historical shape (one full period out).
        int repeatDelayTicks = ContentParse.optInt(node, "repeat-delay", -1, diags);
        ContentParse.Chance chance = ContentParse.resolveChanceValue(node, "chance", diags);
        int cooldown = ContentParse.resolveInt(node, "cooldown", 0, diags);
        int soulCost = ContentParse.resolveInt(node, "soul-cost", 0, diags);
        double soulCostGrowth = ContentParse.resolveDouble(node, "soul-cost-growth", 1.0, diags);
        int soulCostCap = ContentParse.resolveInt(node, "soul-cost-cap", 0, diags);
        int soulCostDecayPeriod = ContentParse.resolveInt(node, "soul-cost-decay-period", 0, diags);
        String noSoulsMessage = ContentParse.blankToNull(
                ContentParse.resolveString(node, "no-souls-message", diags));
        ContentParse.SoulKnobs soulKnobs = ContentParse.resolveSoulKnobs(node, diags);
        String condition = ContentParse.blankToNull(node.string("condition"));
        List<EffectLine> effects = ContentParse.effectItems(node, "effects", diags);
        if (effects.isEmpty() && !node.has("effects")) {
            diags.warning(DiagCode.W_LOAD_EFFECTS, "set bonus '" + stableKey + "' declares no effects",
                    node.sourceOf("effects"));
        }
        return new AbilityDef(
                SourceKind.SET, stableKey, nextDefId.getAsInt(), 0, chance.constant(), cooldown, soulCost, triggers,
                disabledWorlds, condition, effects, stableKey,
                ContentParse.resolveCooldownScope(node, stableKey, diags),
                group, null, repeatTicks, fileSource,
                Math.max(0, setPieces), false, chance.expr(), noSoulsMessage, soulKnobs.carried(), soulKnobs.sound(),
                soulKnobs.particle(), soulCostGrowth, soulCostCap, soulCostDecayPeriod,
                ContentParse.resolveCooldownPerVictim(node, diags),
                repeatDelayTicks);
    }
}
