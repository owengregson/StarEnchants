package compile.load;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import schema.diag.Source;

/**
 * Non-runtime metadata of one authored armour set (ADR-0014) — its PHYSICAL members only. A set is tierless;
 * its pieces ({@link #armorMembers}) and optional {@link #weapon} are what get minted, with their shared lore
 * and per-piece enchants. Its BEHAVIOUR is any number of bonus abilities, read separately from the unified
 * {@code bonuses:} list: each is {@code on: armor} (fires once {@link #armorComplete} pieces are worn) or
 * {@code on: weapon} (fires while complete AND its weapon is held). The first armour bonus expands to
 * {@code <key>} (its completion count on {@code setPieces}); further armour bonuses to {@code <key>/aN} and
 * weapon bonuses to {@code <key>/wN}, all resolver-gated (not a piece count).
 *
 * @param tier          always {@code null} for sets (kept for {@link Library} uniformity)
 * @param armorComplete worn-piece count that completes the set ({@code >= 1})
 * @param armorLore     lore SHARED by every armour piece, rendered from state on the worn piece
 * @param weapon        the weapon member, or {@code null} for an armour-only set
 * @param weaponLore    the weapon's own lore (empty when there is no weapon)
 * @param appliesTo     armour slot tokens this set covers, derived from {@link #armorMembers}
 * @param armorEnchants enchants every armour piece is minted with ({@code ref → level}, insertion order):
 *                      a {@code enchants/<id>} ref is a custom plugin enchant (stamped into the piece's
 *                      combat state, validated at compile), any other key is a vanilla enchant NAME applied
 *                      cross-version at mint (§6.6, author-configurable)
 * @param weaponEnchants enchants the set weapon is minted with (same {@code ref → level} model)
 * @param announce      send the player a chat line when the set transitions complete/incomplete (off by default)
 * @param equipMessage  the line sent when the set becomes complete (authored verbatim, no tokens; may be empty)
 * @param removeMessage the line sent when a complete set drops below its threshold (verbatim; may be empty)
 */
public record SetDef(
        String key,
        String display,
        String description,
        String tier,
        int armorComplete,
        List<Member> armorMembers,
        List<String> armorLore,
        Member weapon,
        List<String> weaponLore,
        List<String> appliesTo,
        Map<String, Integer> armorEnchants,
        Map<String, Integer> weaponEnchants,
        boolean announce,
        String equipMessage,
        String removeMessage,
        Source source) {

    public SetDef {
        armorMembers = List.copyOf(armorMembers);
        armorLore = List.copyOf(armorLore);
        weaponLore = List.copyOf(weaponLore);
        appliesTo = List.copyOf(appliesTo);
        equipMessage = equipMessage == null ? "" : equipMessage;
        removeMessage = removeMessage == null ? "" : removeMessage;
        // Unmodifiable LinkedHashMap (not Map.copyOf) so the authored enchant order is preserved — it
        // determines the lore order of custom set-piece enchants.
        armorEnchants = Collections.unmodifiableMap(new LinkedHashMap<>(armorEnchants));
        weaponEnchants = Collections.unmodifiableMap(new LinkedHashMap<>(weaponEnchants));
    }

    public record Member(
            String slot,
            String material,
            String name,
            List<String> lore,
            Integer leatherColor,
            Map<String, Integer> enchants,
            List<EnchantRoll> enchantRolls,
            List<EnchantPool> enchantPools,
            List<EnchantChoice> enchantChoices,
            Heroic heroic) {

        public Member {
            lore = lore == null ? List.of() : List.copyOf(lore);
            enchants = enchants == null ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(enchants));
            enchantRolls = enchantRolls == null ? List.of() : List.copyOf(enchantRolls);
            enchantPools = enchantPools == null ? List.of() : List.copyOf(enchantPools);
            enchantChoices = enchantChoices == null ? List.of() : List.copyOf(enchantChoices);
            heroic = heroic == null ? Heroic.NONE : heroic;
        }

        /** Backward-compatible physical member with no per-piece overrides. */
        public Member(String slot, String material, String name) {
            this(slot, material, name, List.of(), null, Map.of(), List.of(), List.of(), List.of(), Heroic.NONE);
        }
    }

    /** One independently-chanced custom-enchant roll, evaluated in authored order at mint time. */
    public record EnchantRoll(String enchant, double chance, RollMode mode, int level, int maxLevel) {
        public EnchantRoll {
            chance = Math.max(0.0, Math.min(100.0, chance));
            mode = mode == null ? RollMode.FIXED : mode;
            level = Math.max(1, level);
            maxLevel = Math.max(level, maxLevel);
        }
    }

    /** A uniform sample without replacement from {@code enchants}, then one level roll per selected enchant. */
    public record EnchantPool(List<String> enchants, int count, RollMode mode) {
        public EnchantPool {
            enchants = enchants == null ? List.of() : List.copyOf(enchants);
            count = Math.max(0, Math.min(count, enchants.size()));
            mode = mode == null ? RollMode.ABILITY_NEAR_MAX : mode;
        }
    }

    /** One weighted, mutually-exclusive branch; all rolls in the selected branch are then evaluated in order. */
    public record EnchantChoice(List<EnchantBranch> options) {
        public EnchantChoice {
            options = options == null ? List.of() : List.copyOf(options);
        }
    }

    public record EnchantBranch(double weight, List<EnchantRoll> rolls) {
        public EnchantBranch {
            weight = Math.max(0.0, weight);
            rolls = rolls == null ? List.of() : List.copyOf(rolls);
        }
    }

    public enum RollMode {
        FIXED,
        MAX,
        UNIFORM,
        RANGE,
        PLAIN_NEAR_MAX,
        ABILITY_NEAR_MAX
    }

    /** Exact on-item Heroic stats for pieces minted heroic by their source set. */
    public record Heroic(double percentDamage, double percentReduction, double durability,
                         double flatDamage, double flatReduction) {
        public static final Heroic NONE = new Heroic(0.0, 0.0, 0.0, 0.0, 0.0);

        public boolean isZero() {
            return percentDamage == 0.0 && percentReduction == 0.0 && durability == 0.0
                    && flatDamage == 0.0 && flatReduction == 0.0;
        }
    }

    public boolean hasWeapon() {
        return weapon != null;
    }
}
