package platform.resolve;

import schema.spec.HandleCategory;
import java.util.Locale;
import java.util.Map;

/**
 * The single home for cross-version rename knowledge (docs/architecture.md §9): per-category
 * legacy&rarr;modern aliases spanning 1.17.1 &rarr; 26.1.x. Upper-cased canonical names; {@link HandleResolver}
 * consults this both ways and the migrator reuses it (§10). Append more as the matrix surfaces them.
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
            Map.entry("MUSHROOM_COW", "MOOSHROOM"),
            // PRIMED_TNT (floor) → TNT (modern); one entry resolves both spellings on both eras
            // (HandleResolver also matches a modern token against an older server).
            Map.entry("PRIMED_TNT", "TNT"));

    private static final Map<String, String> MATERIAL = Map.ofEntries(
            Map.entry("SULPHUR", "GUNPOWDER"),
            Map.entry("GRASS", "SHORT_GRASS"),
            Map.entry("WOOL", "WHITE_WOOL"),
            Map.entry("BOOK_AND_QUILL", "WRITABLE_BOOK"), // 1.13 rename; the resolver interns it both ways
            // ADR-0052 pet structures — the 1.8-era spellings of the cage/web materials.
            Map.entry("IRON_FENCE", "IRON_BARS"),
            Map.entry("WEB", "COBWEB"),
            Map.entry("SMOOTH_BRICK", "STONE_BRICKS"));

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
            Map.entry("HORSE_JUMP_STRENGTH", "JUMP_STRENGTH"),
            Map.entry("GENERIC_WATER_MOVEMENT_EFFICIENCY", "WATER_MOVEMENT_EFFICIENCY"));

    // The 1.13 "sound flattening" renamed nearly every constant; these are the ones the shipped content
    // names. Key = the 1.8-era enum constant, value = the modern flattened name — so HandleResolver interns
    // the 1.8 spelling when a modern token is loaded on a 1.8 server (the legacy fork), and the migrator
    // normalises a 1.8 config the other way. Append as the matrix surfaces more.
    private static final Map<String, String> SOUND = Map.ofEntries(
            Map.entry("EXPLODE", "ENTITY_GENERIC_EXPLODE"),
            Map.entry("LEVEL_UP", "ENTITY_PLAYER_LEVELUP"),
            Map.entry("ANVIL_LAND", "BLOCK_ANVIL_LAND"),
            Map.entry("ENDERDRAGON_GROWL", "ENTITY_ENDER_DRAGON_GROWL"),
            Map.entry("WITHER_SPAWN", "ENTITY_WITHER_SPAWN"),
            Map.entry("DIG_GRASS", "BLOCK_GRASS_BREAK"),
            Map.entry("ITEM_BREAK", "ENTITY_ITEM_BREAK"),
            // The cosmic-port legacy sweep; each verified present in BOTH the 1.8 enum and the modern
            // constant lists before landing. FIREWORK_TWINKLE2 is the "far" twinkle (plain FIREWORK_TWINKLE
            // is the near one), and MAGMACUBE_WALK2 is deliberately the only squish alias — a second key onto
            // the same target would make the resolver's reverse scan pick non-deterministically.
            Map.entry("DIG_STONE", "BLOCK_STONE_BREAK"),
            Map.entry("FIREWORK_LAUNCH", "ENTITY_FIREWORK_ROCKET_LAUNCH"),
            Map.entry("FIREWORK_TWINKLE2", "ENTITY_FIREWORK_ROCKET_TWINKLE_FAR"),
            Map.entry("FIREWORK_BLAST", "ENTITY_FIREWORK_ROCKET_BLAST"),
            Map.entry("ANVIL_BREAK", "BLOCK_ANVIL_BREAK"),
            Map.entry("PISTON_EXTEND", "BLOCK_PISTON_EXTEND"),
            Map.entry("DRINK", "ENTITY_GENERIC_DRINK"),
            Map.entry("ZOMBIE_PIG_ANGRY", "ENTITY_ZOMBIFIED_PIGLIN_ANGRY"),
            Map.entry("CHICKEN_HURT", "ENTITY_CHICKEN_HURT"),
            Map.entry("MAGMACUBE_WALK2", "ENTITY_MAGMA_CUBE_SQUISH"),
            Map.entry("GHAST_SCREAM", "ENTITY_GHAST_SCREAM"),
            Map.entry("CREEPER_HISS", "ENTITY_CREEPER_PRIMED"),
            Map.entry("WITHER_HURT", "ENTITY_WITHER_HURT"));

    /**
     * The COMPLETE 1.20.5 particle rename wave (Spigot flattened the enum), floor name → modern name,
     * derived by diffing the real 1.17.1 and 1.21.11 Particle enums (reference cache, javap). The resolver's
     * reverse scan makes each entry bidirectional, so either era's spelling loads on either era. LIGHT and
     * the LEGACY_* constants died without a successor and are deliberately absent.
     */
    private static final Map<String, String> PARTICLE = Map.ofEntries(
            Map.entry("SPELL_WITCH", "WITCH"),
            Map.entry("VILLAGER_HAPPY", "HAPPY_VILLAGER"),
            Map.entry("VILLAGER_ANGRY", "ANGRY_VILLAGER"),
            Map.entry("SMOKE_NORMAL", "SMOKE"),
            Map.entry("SMOKE_LARGE", "LARGE_SMOKE"),
            Map.entry("REDSTONE", "DUST"),
            Map.entry("BLOCK_CRACK", "BLOCK"),
            Map.entry("BLOCK_DUST", "BLOCK"),
            Map.entry("BARRIER", "BLOCK_MARKER"),
            Map.entry("CRIT_MAGIC", "ENCHANTED_HIT"),
            Map.entry("DRIP_LAVA", "DRIPPING_LAVA"),
            Map.entry("DRIP_WATER", "DRIPPING_WATER"),
            Map.entry("ENCHANTMENT_TABLE", "ENCHANT"),
            Map.entry("EXPLOSION_NORMAL", "POOF"),
            Map.entry("EXPLOSION_LARGE", "EXPLOSION"),
            Map.entry("EXPLOSION_HUGE", "EXPLOSION_EMITTER"),
            Map.entry("FIREWORKS_SPARK", "FIREWORK"),
            Map.entry("ITEM_CRACK", "ITEM"),
            Map.entry("MOB_APPEARANCE", "ELDER_GUARDIAN"),
            Map.entry("SLIME", "ITEM_SLIME"),
            Map.entry("SNOWBALL", "ITEM_SNOWBALL"),
            Map.entry("SNOW_SHOVEL", "ITEM_SNOWBALL"),
            Map.entry("SPELL", "EFFECT"),
            Map.entry("SPELL_INSTANT", "INSTANT_EFFECT"),
            Map.entry("SPELL_MOB", "ENTITY_EFFECT"),
            Map.entry("SPELL_MOB_AMBIENT", "ENTITY_EFFECT"),
            Map.entry("SUSPENDED", "UNDERWATER"),
            Map.entry("SUSPENDED_DEPTH", "UNDERWATER"),
            Map.entry("TOTEM", "TOTEM_OF_UNDYING"),
            Map.entry("TOWN_AURA", "MYCELIUM"),
            Map.entry("WATER_BUBBLE", "BUBBLE"),
            Map.entry("WATER_DROP", "RAIN"),
            Map.entry("WATER_SPLASH", "SPLASH"),
            Map.entry("WATER_WAKE", "FISHING"));

    /** The alias map for a category (empty if none registered). */
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

    public static String normalize(String token) {
        return token == null ? "" : token.trim().toUpperCase(Locale.ROOT);
    }
}
