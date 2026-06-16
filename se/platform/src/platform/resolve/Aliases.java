package platform.resolve;

import schema.spec.HandleCategory;
import java.util.Locale;
import java.util.Map;

/**
 * The cross-version rename knowledge (docs/architecture.md §9): per-category
 * legacy&rarr;modern name aliases spanning the 1.17.1 &rarr; 26.1.x range — the
 * 1.20.5 spigot&rarr;mojang flip, the 1.21.x attribute de-prefixing, the 1.13
 * flattening survivors, and the long-standing potion/enchant renames. Keys and values
 * are upper-cased canonical names; {@link HandleResolver} consults this both ways.
 *
 * <p>This is a living table seeded with the well-known renames; more are appended as the
 * matrix surfaces them. It is the single home for this knowledge — the migrator reuses
 * the same maps to modernise legacy config (§10).
 */
public final class Aliases {

    private Aliases() {
    }

    private static final Map<String, String> POTION_EFFECT = Map.ofEntries(
            Map.entry("CONFUSION", "NAUSEA"),
            Map.entry("SLOW", "SLOWNESS"),
            Map.entry("FAST_DIGGING", "HASTE"),
            Map.entry("SLOW_DIGGING", "MINING_FATIGUE"),
            Map.entry("INCREASE_DAMAGE", "STRENGTH"),
            Map.entry("HEAL", "INSTANT_HEALTH"),
            Map.entry("HARM", "INSTANT_DAMAGE"),
            Map.entry("JUMP", "JUMP_BOOST"),
            Map.entry("DAMAGE_RESISTANCE", "RESISTANCE"));

    private static final Map<String, String> ENCHANTMENT = Map.ofEntries(
            Map.entry("DURABILITY", "UNBREAKING"),
            Map.entry("DIG_SPEED", "EFFICIENCY"),
            Map.entry("PROTECTION_ENVIRONMENTAL", "PROTECTION"),
            Map.entry("PROTECTION_FIRE", "FIRE_PROTECTION"),
            Map.entry("PROTECTION_FALL", "FEATHER_FALLING"),
            Map.entry("PROTECTION_EXPLOSIONS", "BLAST_PROTECTION"),
            Map.entry("PROTECTION_PROJECTILE", "PROJECTILE_PROTECTION"),
            Map.entry("LOOT_BONUS_BLOCKS", "FORTUNE"),
            Map.entry("LOOT_BONUS_MOBS", "LOOTING"),
            Map.entry("DAMAGE_ALL", "SHARPNESS"),
            Map.entry("DAMAGE_UNDEAD", "SMITE"),
            Map.entry("DAMAGE_ARTHROPODS", "BANE_OF_ARTHROPODS"),
            Map.entry("ARROW_DAMAGE", "POWER"),
            Map.entry("ARROW_KNOCKBACK", "PUNCH"),
            Map.entry("ARROW_FIRE", "FLAME"),
            Map.entry("ARROW_INFINITE", "INFINITY"),
            Map.entry("WATER_WORKER", "AQUA_AFFINITY"),
            Map.entry("OXYGEN", "RESPIRATION"));

    private static final Map<String, String> ENTITY_TYPE = Map.ofEntries(
            Map.entry("PIG_ZOMBIE", "ZOMBIFIED_PIGLIN"),
            Map.entry("ZOMBIE_PIGMAN", "ZOMBIFIED_PIGLIN"),
            Map.entry("SNOWMAN", "SNOW_GOLEM"),
            Map.entry("MUSHROOM_COW", "MOOSHROOM"));

    private static final Map<String, String> MATERIAL = Map.ofEntries(
            Map.entry("SULPHUR", "GUNPOWDER"),
            Map.entry("GRASS", "SHORT_GRASS"),
            Map.entry("WOOL", "WHITE_WOOL"));

    // 1.21.x dropped the GENERIC_ / HORSE_ / ZOMBIE_ prefixes on attribute keys.
    private static final Map<String, String> ATTRIBUTE = Map.ofEntries(
            Map.entry("GENERIC_MAX_HEALTH", "MAX_HEALTH"),
            Map.entry("GENERIC_ATTACK_DAMAGE", "ATTACK_DAMAGE"),
            Map.entry("GENERIC_ATTACK_SPEED", "ATTACK_SPEED"),
            Map.entry("GENERIC_MOVEMENT_SPEED", "MOVEMENT_SPEED"),
            Map.entry("GENERIC_ARMOR", "ARMOR"),
            Map.entry("GENERIC_ARMOR_TOUGHNESS", "ARMOR_TOUGHNESS"),
            Map.entry("GENERIC_KNOCKBACK_RESISTANCE", "KNOCKBACK_RESISTANCE"),
            Map.entry("GENERIC_LUCK", "LUCK"),
            Map.entry("HORSE_JUMP_STRENGTH", "JUMP_STRENGTH"));

    private static final Map<String, String> SOUND = Map.ofEntries(
            Map.entry("ENTITY_PLAYER_LEVELUP", "ENTITY_PLAYER_LEVELUP"));

    private static final Map<String, String> PARTICLE = Map.ofEntries(
            Map.entry("SPELL_WITCH", "WITCH"),
            Map.entry("VILLAGER_HAPPY", "HAPPY_VILLAGER"),
            Map.entry("REDSTONE", "DUST"));

    /** The legacy&rarr;modern alias map for a category (empty if the category has none registered). */
    public static Map<String, String> forCategory(HandleCategory category) {
        return switch (category) {
            case POTION_EFFECT -> POTION_EFFECT;
            case ENCHANTMENT -> ENCHANTMENT;
            case ENTITY_TYPE -> ENTITY_TYPE;
            case MATERIAL -> MATERIAL;
            case ATTRIBUTE -> ATTRIBUTE;
            case SOUND -> SOUND;
            case PARTICLE -> PARTICLE;
        };
    }

    /** Normalize a token to its canonical upper-case form. */
    public static String normalize(String token) {
        return token == null ? "" : token.trim().toUpperCase(Locale.ROOT);
    }
}
