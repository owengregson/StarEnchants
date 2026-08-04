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
            // 1.20.5 renamed the constant to match the vanilla id; the floor (and 1.17–1.20.4) still says ENDER_.
            Map.entry("ENDER_CRYSTAL", "END_CRYSTAL"),
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
            Map.entry("SMOOTH_BRICK", "STONE_BRICKS"),
            // The 1.13 flattening renamed the end's floor; PHANTOM_BLOCKS' enemy default names it.
            Map.entry("ENDER_STONE", "END_STONE"),
            // Wave 2e.2, the sweep's two MATERIAL rows (Devour's block-crack burst, Titan Trap's water cue).
            Map.entry("MYCEL", "MYCELIUM"),
            // 1.8 split still water from flowing; the modern WATER is both. This row is FORWARD-only in
            // practice — a 1.8 server resolves the token WATER directly (it has that constant too), so the
            // reverse scan never runs and a modern-authored WATER stays flowing there. It earns its place by
            // reading a 1.8-authored config on a modern server, which is the migrator's direction.
            Map.entry("STATIONARY_WATER", "WATER"));

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
            // 1.8's ANVIL_BREAK is random.anvil_break — the SHATTER, not a block break: 1.8's anvil step
            // sound answers dig.stone for getBreakSound(). Modern block.anvil.break took over that mining
            // break (SoundType.ANVIL's break slot) and block.anvil.destroy the shatter (AnvilBlock fires it
            // after a fall), so DESTROY is the descendant and BREAK a different, lighter cue.
            Map.entry("ANVIL_BREAK", "BLOCK_ANVIL_DESTROY"),
            Map.entry("PISTON_EXTEND", "BLOCK_PISTON_EXTEND"),
            Map.entry("DRINK", "ENTITY_GENERIC_DRINK"),
            Map.entry("ZOMBIE_PIG_ANGRY", "ENTITY_ZOMBIFIED_PIGLIN_ANGRY"),
            Map.entry("CHICKEN_HURT", "ENTITY_CHICKEN_HURT"),
            Map.entry("MAGMACUBE_WALK2", "ENTITY_MAGMA_CUBE_SQUISH"),
            Map.entry("GHAST_SCREAM", "ENTITY_GHAST_SCREAM"),
            Map.entry("CREEPER_HISS", "ENTITY_CREEPER_PRIMED"),
            Map.entry("WITHER_HURT", "ENTITY_WITHER_HURT"),
            // The 1.8 shoot pair: both constants renamed AND, for the ghast, changed noun — 1.8 named the
            // cue after the projectile (GHAST_FIREBALL), the flattening after the act (ENTITY_GHAST_SHOOT).
            Map.entry("GHAST_FIREBALL", "ENTITY_GHAST_SHOOT"),
            Map.entry("WITHER_SHOOT", "ENTITY_WITHER_SHOOT"),
            // ZOMBIE_WOODBREAK is the door-BREAK cue; 1.8's near-twin ZOMBIE_WOOD is the door-ATTACK one
            // (ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR) — the flattening reordered the words, easy to cross-wire.
            Map.entry("ENDERMAN_TELEPORT", "ENTITY_ENDERMAN_TELEPORT"),
            Map.entry("ZOMBIE_WOODBREAK", "ENTITY_ZOMBIE_BREAK_WOODEN_DOOR"),
            // Wave 2e.2 closes the sweep's remainder — every row javap'd against the real Spigot 1.8.8 Sound
            // enum and grepped against both committed modern fixtures, values mutually distinct so the reverse
            // scan stays deterministic. These are content's LAST unaliased cues; several files were silent on
            // the 1.8 lane entirely (Permafrost and Soul Trap each author exactly one sound, and it was this one).
            Map.entry("ENDERMAN_SCREAM", "ENTITY_ENDERMAN_SCREAM"),
            Map.entry("ENDERMAN_HIT", "ENTITY_ENDERMAN_HURT"),
            Map.entry("SPLASH", "ENTITY_GENERIC_SPLASH"),
            Map.entry("EAT", "ENTITY_GENERIC_EAT"),
            Map.entry("ARROW_HIT", "ENTITY_ARROW_HIT"),
            // 1.8 named the flesh-hit cue for the SOUND it makes; the flattening named it for who makes it.
            // ENTITY_GENERIC_HURT is arguably the closer descendant of 1.8's game.neutral.hurt, but the row
            // exists to resolve what CONTENT authors, and both sniper files author ENTITY_PLAYER_HURT (their
            // own era notes name HURT_FLESH as its 1.8 spelling). 1.8 has no player-specific hurt constant at
            // all, so this is the only 1.8 sound that can serve it; mapping to GENERIC instead would leave the
            // authored token unresolved and the cue silent, which is what this sweep set out to fix.
            Map.entry("HURT_FLESH", "ENTITY_PLAYER_HURT"),
            Map.entry("ORB_PICKUP", "ENTITY_EXPERIENCE_ORB_PICKUP"),
            Map.entry("FIRE", "BLOCK_FIRE_AMBIENT"),
            Map.entry("PORTAL_TRIGGER", "BLOCK_PORTAL_TRIGGER"),
            Map.entry("COW_IDLE", "ENTITY_COW_AMBIENT"),
            Map.entry("COW_HURT", "ENTITY_COW_HURT"),
            Map.entry("GLASS", "BLOCK_GLASS_BREAK"),
            Map.entry("BAT_TAKEOFF", "ENTITY_BAT_TAKEOFF"),
            Map.entry("WATER", "BLOCK_WATER_AMBIENT"),
            // The zombie-door trio, all three words reordered by the flattening and trivially cross-wired:
            // METAL is the iron-door ATTACK, WOOD the wooden-door ATTACK, WOODBREAK (above) the wooden BREAK.
            Map.entry("ZOMBIE_METAL", "ENTITY_ZOMBIE_ATTACK_IRON_DOOR"),
            Map.entry("ZOMBIE_WOOD", "ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR"),
            // Wave 2e.3's four: found by walking the CONTENT's authored tokens instead of this list, which
            // had only ever been closed against itself. Each key is in the real Spigot 1.8.8 Sound enum and
            // each value in both committed modern fixtures, none colliding with a row above. Clarity and
            // Permafrost-style single-cue files sit behind two of them, so the proc was wholly silent.
            Map.entry("FIZZ", "BLOCK_FIRE_EXTINGUISH"),
            Map.entry("DIG_WOOL", "BLOCK_WOOL_BREAK"),
            Map.entry("IRONGOLEM_DEATH", "ENTITY_IRON_GOLEM_DEATH"),
            Map.entry("WITHER_DEATH", "ENTITY_WITHER_DEATH"));

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
