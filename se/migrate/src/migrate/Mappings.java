package migrate;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import migrate.model.MigratedCondition;
import migrate.model.MigratedEffect;

/**
 * The legacy → unified vocabulary tables (docs/architecture.md §10): trigger names, item-application
 * groups, effect-target tokens, and a curated per-head effect translator. Triggers and applies map
 * almost 1:1 (the unified vocabulary deliberately kept the EE/EA names). Effects are the hard part —
 * legacy arg shapes differ (EE {@code DAMAGE:MIN:MAX:TARGET} ranges, {@code FLAME:SECONDS} vs
 * StarEnchants {@code IGNITE:TICKS}), so only effects whose semantics were VERIFIED against the legacy
 * source are translated faithfully; every other head is returned as a {@link MigratedEffect#todo}
 * (flagged for manual porting), never guessed.
 */
public final class Mappings {

    private Mappings() {
    }

    /** Legacy enchant {@code type} → StarEnchants trigger (the unified vocabulary kept these names). */
    private static final Map<String, String> TRIGGERS = Map.ofEntries(
            Map.entry("ATTACK", "ATTACK"),
            Map.entry("DEFENSE", "DEFENSE"),
            Map.entry("KILL", "KILL"),
            Map.entry("MINE", "MINE"),
            Map.entry("FIRE", "FIRE"),
            Map.entry("INTERACT", "INTERACT"),
            Map.entry("BOW", "BOW"),
            Map.entry("BOW_FIRE", "BOW_FIRE"),
            Map.entry("BOW_DAMAGE", "BOW"),
            Map.entry("HELD", "HELD"),
            Map.entry("PASSIVE", "PASSIVE"));

    /** Legacy {@code applies} group → StarEnchants {@code applies-to} list. */
    private static final Map<String, List<String>> APPLIES = Map.ofEntries(
            Map.entry("SWORDS", List.of("SWORD")),
            Map.entry("SWORD", List.of("SWORD")),
            Map.entry("AXE", List.of("AXE")),
            Map.entry("BOW", List.of("BOW")),
            Map.entry("PICKAXE", List.of("PICKAXE")),
            Map.entry("TOOLS", List.of("TOOL")),
            Map.entry("WEAPON", List.of("WEAPON")),
            Map.entry("ARMOR", List.of("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS")),
            Map.entry("HELMET", List.of("HELMET")),
            Map.entry("CHESTPLATE", List.of("CHESTPLATE")),
            Map.entry("LEGGINGS", List.of("LEGGINGS")),
            Map.entry("BOOTS", List.of("BOOTS")));

    /**
     * Legacy target token → StarEnchants selector, direction-aware. On a DEFENSE enchant the foe is the
     * {@code @Attacker} (the entity that hit the wielder); on an ATTACK enchant it is the {@code @Victim}.
     * EE {@code PLAYER}/{@code SELF} is always the wielder ({@code @Self}).
     */
    private static String target(String legacy, boolean defenseDir) {
        return switch (legacy == null ? "" : legacy.trim().toUpperCase(Locale.ROOT)) {
            case "PLAYER", "SELF" -> "@Self";
            case "ATTACKER" -> defenseDir ? "@Attacker" : "@Self"; // on attack, the attacker IS the wielder
            case "TARGET", "VICTIM" -> defenseDir ? "@Attacker" : "@Victim";
            default -> defenseDir ? "@Attacker" : "@Victim"; // the combat foe is the default recipient
        };
    }

    /** ":@Selector" suffix for an EE target token, direction-aware. */
    private static String sel(String legacy, boolean defenseDir) {
        return ":" + target(legacy, defenseDir);
    }

    /** The foe selector for the current direction: {@code @Victim} on attack, {@code @Attacker} on defence. */
    private static String foe(boolean defenseDir) {
        return defenseDir ? "@Attacker" : "@Victim";
    }

    /**
     * Map a legacy enchant type to a StarEnchants trigger, or {@code null} if it has no equivalent. EE's
     * {@code REPEATING} / {@code REPEATING-<seconds>} both map to the {@code REPEATING} trigger (the period
     * is read separately by {@link #repeatTicks}).
     */
    public static String trigger(String legacyType) {
        if (legacyType == null) {
            return null;
        }
        String key = legacyType.trim().toUpperCase(Locale.ROOT);
        if (key.startsWith("REPEATING")) {
            return "REPEATING";
        }
        return TRIGGERS.get(key);
    }

    /** The REPEATING period in ticks for an EE {@code REPEATING-<seconds>} type (0 if not a repeating type). */
    public static int repeatTicks(String legacyType) {
        if (legacyType == null) {
            return 0;
        }
        String key = legacyType.trim().toUpperCase(Locale.ROOT);
        int dash = key.indexOf('-');
        if (!key.startsWith("REPEATING") || dash < 0) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(key.substring(dash + 1).trim())) * 20;
        } catch (NumberFormatException notANumber) {
            return 0;
        }
    }

    /** Map a legacy applies group to StarEnchants {@code applies-to}, or empty if unknown. */
    public static List<String> appliesTo(String legacyApplies) {
        if (legacyApplies == null) {
            return List.of();
        }
        return APPLIES.getOrDefault(legacyApplies.trim().toUpperCase(java.util.Locale.ROOT), List.of());
    }

    /** An EE numeric condition: {@code isPlayerHealth}/{@code isTargetHealth} <op> N (op may be EE's bare {@code =}). */
    private static final Pattern EE_NUMERIC_COND = Pattern.compile(
            "(?i)(isPlayerHealth|isTargetHealth)\\s*(<=|>=|<|>|==|!=|=)\\s*(-?\\d+(?:\\.\\d+)?)");

    /** Translate an EE effect token defaulting to the ATTACK direction (the wielder is the aggressor). */
    public static MigratedEffect effect(String legacyToken) {
        return effect(legacyToken, false);
    }

    /**
     * Translate one EE effect token to the LIST of StarEnchants effects it becomes — usually a singleton
     * (delegating to {@link #effect(String, boolean)}), but a compound EE effect ({@code WRATH}, {@code FROST},
     * {@code ROT_DECAY}) expands to several SE effects that together reproduce it. The EE reader flat-maps
     * these into a level's {@code effects:} list.
     */
    public static List<MigratedEffect> effects(String legacyToken, boolean defenseDirection) {
        String token = legacyToken == null ? "" : legacyToken.trim();
        String[] parts = token.split(":");
        String head = parts.length == 0 ? "" : parts[0].trim().toUpperCase(Locale.ROOT);
        try {
            return switch (head) {
                case "WRATH" -> wrath(token, parts);
                case "FROST" -> frost(token, defenseDirection);
                case "ROT_DECAY" -> rotDecay(token, defenseDirection);
                default -> List.of(effect(token, defenseDirection));
            };
        } catch (NumberFormatException badNumber) {
            return List.of(MigratedEffect.todo(token, "could not parse a numeric argument: " + badNumber.getMessage()));
        }
    }

    /**
     * WRATH:RADIUS:MIN:MAX:(MESSAGE) → a lightning storm in {@code radius}: a cosmetic strike, area damage
     * (random range → max), and the EE Slowness III + Blindness I debuffs, all on {@code @Aoe}. The per-target
     * message is dropped (SE messages the actor, not each AoE target).
     */
    private static List<MigratedEffect> wrath(String token, String[] parts) {
        if (parts.length < 4) {
            return List.of(MigratedEffect.todo(token, "unexpected WRATH arg shape (RADIUS:MIN:MAX[:MESSAGE])"));
        }
        int radius = intArg(parts[1]);
        int maxDmg = intArg(parts[3]);
        String aoe = "@Aoe{r=" + radius + "}";
        String note = "EE WRATH → lightning storm: LIGHTNING + DAMAGE + SLOWNESS III + BLINDNESS I over @Aoe";
        return List.of(
                MigratedEffect.mapped(token, "LIGHTNING:0:" + aoe, note),
                MigratedEffect.mapped(token, "DAMAGE:" + maxDmg + ":" + aoe, note),
                MigratedEffect.mapped(token, "POTION:SLOWNESS:3:200:" + aoe, note),
                MigratedEffect.mapped(token, "POTION:BLINDNESS:1:260:" + aoe, note));
    }

    /**
     * FROST → a permafrost field on the foe: a temporary packed-ice platform (WALKER) plus the EE Slowness /
     * Mining-Fatigue / Nausea debuffs (amplifier 3 → SE level 4) — the slow-the-target essence of EE's Frost.
     */
    private static List<MigratedEffect> frost(String token, boolean defenseDir) {
        String foe = foe(defenseDir);
        String note = "EE FROST → WALKER:PACKED_ICE + SLOWNESS/MINING_FATIGUE/NAUSEA IV on the foe";
        return List.of(
                MigratedEffect.mapped(token, "WALKER:PACKED_ICE:400:3:" + foe, note),
                MigratedEffect.mapped(token, "POTION:SLOWNESS:4:60:" + foe, note),
                MigratedEffect.mapped(token, "POTION:MINING_FATIGUE:4:60:" + foe, note),
                MigratedEffect.mapped(token, "POTION:NAUSEA:4:60:" + foe, note));
    }

    /**
     * ROT_DECAY → spawn 3 rotting corpses (zombies) on the foe, plus the rot (WITHER) and the decay
     * (armour-durability damage) — the rotting-zombie + decay essence of EE's Rot and Decay.
     */
    private static List<MigratedEffect> rotDecay(String token, boolean defenseDir) {
        String foe = foe(defenseDir);
        String note = "EE ROT_DECAY → SPAWN_ENTITY:ZOMBIE x3 + POTION:WITHER + armour-durability damage on the foe";
        return List.of(
                MigratedEffect.mapped(token, "SPAWN_ENTITY:ZOMBIE:3:100:100:none:" + foe, note),
                MigratedEffect.mapped(token, "POTION:WITHER:1:100:" + foe, note),
                MigratedEffect.mapped(token, "DURABILITY:10:armor:damage:" + foe, note));
    }

    /**
     * Translate one EE effect token to a {@link MigratedEffect}, direction-aware: {@code defenseDirection}
     * flips the foe selector to {@code @Attacker} on a DEFENSE enchant (vs {@code @Victim} on attack). The
     * verified EE vocabulary is translated faithfully (seconds→ticks ×20, random ranges→max, EE rarity
     * groups→SE groups, durability/suppress/spawn families); a head with no StarEnchants equivalent
     * (DROP_HEAD, GANK, ROT_DECAY, SNIPER, …) is a {@link MigratedEffect#todo}, never guessed.
     */
    public static MigratedEffect effect(String legacyToken, boolean defenseDirection) {
        String token = legacyToken == null ? "" : legacyToken.trim();
        // EE's ender-walker uses a "DEFENSE;<factor>;<inner-effect>" compound — port the inner effect and note
        // the EE damage-threshold factor was dropped (StarEnchants has no per-effect threshold gate).
        if (token.toUpperCase(Locale.ROOT).startsWith("DEFENSE;")) {
            String[] seg = token.split(";", 3);
            if (seg.length == 3) {
                MigratedEffect inner = effect(seg[2], defenseDirection);
                return inner.mapped()
                        ? MigratedEffect.mapped(token, inner.se(),
                                "EE DEFENSE-threshold factor " + seg[1] + " dropped (no SE per-effect gate)")
                        : MigratedEffect.todo(token, "EE DEFENSE-compound inner effect did not map: " + inner.note());
            }
            return MigratedEffect.todo(token, "unrecognised EE DEFENSE-compound effect shape");
        }
        String[] parts = token.split(":");
        String head = parts.length == 0 ? "" : parts[0].trim().toUpperCase(Locale.ROOT);
        try {
            return switch (head) {
                // DAMAGE:MIN:MAX:[target] → DAMAGE:<max>:@target (random range collapses to its upper bound).
                case "DAMAGE" -> parts.length >= 4
                        ? MigratedEffect.mapped(token, "DAMAGE:" + intArg(parts[2]) + sel(parts[3], defenseDirection),
                                "legacy random range " + parts[1] + "-" + parts[2] + " collapsed to its max")
                        : MigratedEffect.todo(token, "unexpected DAMAGE arg shape");
                // FLAME:SECONDS:[target] → IGNITE:<seconds*20 ticks>:@target.
                case "FLAME" -> parts.length >= 3
                        ? MigratedEffect.mapped(token, "IGNITE:" + (intArg(parts[1]) * 20) + sel(parts[2], defenseDirection),
                                "legacy seconds → ticks (x20)")
                        : MigratedEffect.todo(token, "unexpected FLAME arg shape");
                // POTION:TYPE:LEVEL:[target]:[durationSeconds] → POTION:type:level:<dur*20>:@target.
                case "POTION" -> eePotion(token, parts, defenseDirection);
                // HEAL:ADD:MIN[:MAX][:target] → MODIFY_HEALTH:<amount>:give:@Self (random range → max; self-heal).
                case "HEAL" -> eeHeal(token, parts);
                // REDUCTION:PCT → DAMAGE_MOD:defense:add:<0..100> (incoming-damage reduction percent).
                case "REDUCTION" -> parts.length >= 2
                        ? MigratedEffect.mapped(token, "DAMAGE_MOD:defense:add:" + clampPct(parts[1]),
                                pct(parts[1]) > 100 ? "clamped to the 0-100 reduction cap" : "")
                        : MigratedEffect.todo(token, "unexpected REDUCTION arg shape");
                // DAMAGE_INCREASE:PCT → DAMAGE_MOD:attack:add:<pct> (a negative self-nerf has no SE equivalent → TODO).
                case "DAMAGE_INCREASE" -> damageIncrease(token, parts);
                // CURE:TYPE[:true] → REMOVE_POTION:<type>:@Self (clear that effect from the wielder).
                case "CURE" -> parts.length >= 2
                        ? MigratedEffect.mapped(token, "REMOVE_POTION:" + parts[1].trim() + ":@Self",
                                "EE CURE → REMOVE_POTION (self)")
                        : MigratedEffect.todo(token, "unexpected CURE arg shape");
                // PARTICLE:TYPE[:...] → PARTICLE:<type> at the activation location. An EE block-crack/compound
                // particle (BLOCK_BREAK;MATERIAL, bleed) has no block-data SE form, so it lowers to a thematic
                // generic (CRIT) — cosmetic-only, faithful in spirit.
                case "PARTICLE" -> (parts.length >= 2 && !parts[1].contains(";"))
                        ? MigratedEffect.mapped(token, "PARTICLE:" + parts[1].trim(),
                                "EE particle target dropped (SE spawns at the activation location)")
                        : MigratedEffect.mapped(token, "PARTICLE:CRIT:7",
                                "EE compound/block-crack particle → a thematic generic (cosmetic only)");
                // SOUND:NAME:VOLUME[:PITCH] → SOUND:name:volume:pitch (pitch defaults to 1).
                case "SOUND" -> parts.length >= 3
                        ? MigratedEffect.mapped(token, "SOUND:" + parts[1].trim() + ":" + numArg(parts[2])
                                + ":" + (parts.length >= 4 ? numArg(parts[3]) : "1"), "")
                        : MigratedEffect.todo(token, "unexpected SOUND arg shape");
                // ADD_DURABILITY[_ITEM]:N → DURABILITY:<n>:item:restore (restore N points to the held item).
                case "ADD_DURABILITY", "ADD_DURABILITY_ITEM" -> parts.length >= 2
                        ? MigratedEffect.mapped(token, "DURABILITY:" + intArg(parts[1]) + ":item:restore",
                                "EE add-durability → DURABILITY (restore)")
                        : MigratedEffect.todo(token, "unexpected ADD_DURABILITY arg shape");
                // DAMAGE_CANCEL → DAMAGE_MOD:defense:add:100 (a full incoming-damage reduction ≈ cancel the hit).
                case "DAMAGE_CANCEL" -> MigratedEffect.mapped(token, "DAMAGE_MOD:defense:add:100",
                        "EE DAMAGE_CANCEL → 100% defensive reduction (≈ cancel the hit)");
                // ARMOR_CANCEL → IGNORE_ARMOR (the hit ignores the victim's armour + protection reduction).
                case "ARMOR_CANCEL" -> MigratedEffect.mapped(token, "IGNORE_ARMOR",
                        "EE ARMOR_CANCEL → IGNORE_ARMOR (bypass the victim's armour)");
                // DAMAGE_ARMOR:N → DURABILITY:<n>:armor:damage:@foe (damage the foe's worn armour).
                case "DAMAGE_ARMOR" -> parts.length >= 2
                        ? MigratedEffect.mapped(token, "DURABILITY:" + intArg(parts[1]) + ":armor:damage:"
                                + foe(defenseDirection), "EE DAMAGE_ARMOR → DURABILITY (damage the foe's armour)")
                        : MigratedEffect.todo(token, "unexpected DAMAGE_ARMOR arg shape");
                // DAMAGE_ARC:DMG → DAMAGE:<dmg>:@Aoe (area damage; SE @Aoe default radius may differ).
                case "DAMAGE_ARC" -> parts.length >= 2
                        ? MigratedEffect.mapped(token, "DAMAGE:" + numArg(parts[1]) + ":@Aoe",
                                "EE DAMAGE_ARC → DAMAGE @Aoe (review the radius)")
                        : MigratedEffect.todo(token, "unexpected DAMAGE_ARC arg shape");
                // LIGHTNING:[target]:REAL → LIGHTNING:5:@foe (real ≈ 5 dmg); without REAL it is cosmetic (0).
                case "LIGHTNING" -> eeLightning(token, parts, defenseDirection);
                // EXPLODE:POWER:FIRE:[target] → EXPLODE:<power>:false:@foe (EE fire flag dropped; no block break).
                case "EXPLODE" -> parts.length >= 2
                        ? MigratedEffect.mapped(token, "EXPLODE:" + numArg(parts[1]) + ":false"
                                + (parts.length >= 4 ? sel(parts[3], defenseDirection) : ""),
                                "EE fire flag dropped; breakBlocks=false")
                        : MigratedEffect.todo(token, "unexpected EXPLODE arg shape");
                // THROW:X:Y:Z:[target] → VELOCITY:add:x:y:z:@target.
                case "THROW" -> parts.length >= 4
                        ? MigratedEffect.mapped(token, "VELOCITY:add:" + numArg(parts[1]) + ":" + numArg(parts[2])
                                + ":" + numArg(parts[3])
                                + (parts.length >= 5 ? sel(parts[4], defenseDirection) : ":@Self"),
                                "EE THROW → VELOCITY (add)")
                        : MigratedEffect.todo(token, "unexpected THROW arg shape");
                // TNT:N:[target] → SPAWN_ENTITY:PRIMED_TNT:<n> at the activator.
                case "TNT" -> parts.length >= 2
                        ? MigratedEffect.mapped(token, "SPAWN_ENTITY:PRIMED_TNT:" + intArg(parts[1]),
                                "EE TNT → SPAWN_ENTITY (primed tnt at the activator)")
                        : MigratedEffect.todo(token, "unexpected TNT arg shape");
                // SPAWN:ENTITY:DURsec:AMOUNT:RADIUS:NAME → SPAWN_ENTITY:<entity>:<amount>:<dur*20>:0:activator.
                case "SPAWN" -> parts.length >= 4
                        ? MigratedEffect.mapped(token, "SPAWN_ENTITY:" + parts[1].trim() + ":" + intArg(parts[3])
                                + ":" + (intArg(parts[2]) * 20) + ":0:activator",
                                "EE SPAWN → SPAWN_ENTITY (spawn radius + custom name dropped; owned by the activator)")
                        : MigratedEffect.todo(token, "unexpected SPAWN arg shape");
                // DISABLE_ENCHANTMENT:KEY:SEC → SUPPRESS:ENCHANT:enchants/<key>:<sec*20>:@foe.
                case "DISABLE_ENCHANTMENT" -> parts.length >= 3
                        ? MigratedEffect.mapped(token, "SUPPRESS:ENCHANT:enchants/" + parts[1].trim()
                                + ":" + (intArg(parts[2]) * 20) + ":" + foe(defenseDirection),
                                "EE disable-enchant → SUPPRESS:ENCHANT (seconds → ticks)")
                        : MigratedEffect.todo(token, "unexpected DISABLE_ENCHANTMENT arg shape");
                // DISABLE_ENCHANTMENT_GROUP:GROUP:SEC → SUPPRESS:GROUP:<group>:<sec*20>:@foe (group = EE rarity).
                case "DISABLE_ENCHANTMENT_GROUP" -> parts.length >= 3
                        ? MigratedEffect.mapped(token, "SUPPRESS:GROUP:" + parts[1].trim().toLowerCase(Locale.ROOT)
                                + ":" + (intArg(parts[2]) * 20) + ":" + foe(defenseDirection),
                                "EE disable-group → SUPPRESS:GROUP (matches the SE group: = EE rarity)")
                        : MigratedEffect.todo(token, "unexpected DISABLE_ENCHANTMENT_GROUP arg shape");
                // DISABLE_ENCHANTMENT_TYPE:TYPE:SEC → SUPPRESS:TYPE:<type>:<sec*20>:@foe.
                case "DISABLE_ENCHANTMENT_TYPE" -> parts.length >= 3
                        ? MigratedEffect.mapped(token, "SUPPRESS:TYPE:" + parts[1].trim().toUpperCase(Locale.ROOT)
                                + ":" + (intArg(parts[2]) * 20) + ":" + foe(defenseDirection),
                                "EE disable-type → SUPPRESS:TYPE (seconds → ticks)")
                        : MigratedEffect.todo(token, "unexpected DISABLE_ENCHANTMENT_TYPE arg shape");
                // EXTINGUISH:[target] → EXTINGUISH:@target (defaults to the wielder).
                case "EXTINGUISH" -> MigratedEffect.mapped(token,
                        "EXTINGUISH" + (parts.length >= 2 ? sel(parts[1], defenseDirection) : ":@Self"), "");
                // FEED:[AMOUNT] → MODIFY_FOOD:<amount>:give (the wielder).
                case "FEED" -> parts.length >= 2
                        ? MigratedEffect.mapped(token, "MODIFY_FOOD:" + intArg(parts[1]) + ":give", "EE FEED → MODIFY_FOOD")
                        : MigratedEffect.todo(token, "unexpected FEED arg shape");
                // EXP:[AMOUNT] → MODIFY_EXP:<amount>:give.
                case "EXP" -> parts.length >= 2
                        ? MigratedEffect.mapped(token, "MODIFY_EXP:" + intArg(parts[1]) + ":give", "EXP → MODIFY_EXP")
                        : MigratedEffect.todo(token, "unexpected EXP arg shape");
                // KILL:[target] (excludes players in both engines) → KILL:@foe.
                case "KILL" -> MigratedEffect.mapped(token, "KILL:" + foe(defenseDirection), "");
                // REPAIR → DURABILITY:-1:item (full restore of the held item).
                case "REPAIR" -> MigratedEffect.mapped(token, "DURABILITY:-1:item", "REPAIR → DURABILITY");
                // MESSAGE:TEXT[:target] → MESSAGE:<text> (the actor). A ':'-bearing body is a TODO: the effect
                // lexer splits a bare arg on ':', so emitting it would silently truncate the message.
                case "MESSAGE" -> {
                    String body = messageBody(token);
                    yield body.indexOf(':') >= 0
                            ? MigratedEffect.todo(token,
                                    "message body contains ':' which the effect parser splits on — port manually")
                            : MigratedEffect.mapped(token, "MESSAGE:" + body,
                                    "legacy target dropped — StarEnchants messages the actor");
                }
                // SHACKLE → cancel the foe's knockback (zero their incoming knockback briefly).
                case "SHACKLE" -> MigratedEffect.mapped(token, "KNOCKBACK_CONTROL:0:2:" + foe(defenseDirection),
                        "EE SHACKLE → KNOCKBACK_CONTROL:0 (cancel the foe's knockback)");
                // DROP_HEAD:[PLAYER/TARGET] → DROP_ITEM:PLAYER_HEAD at the activation (the skin owner is dropped).
                case "DROP_HEAD" -> MigratedEffect.mapped(token, "DROP_ITEM:PLAYER_HEAD:1",
                        "EE DROP_HEAD → DROP_ITEM:PLAYER_HEAD (a generic head; the skin owner is not preserved)");
                // SNIPER (EE config effect with no implementation) → heavy bonus arrow damage (no headshot test in SE).
                case "SNIPER" -> MigratedEffect.mapped(token, "DAMAGE:8:" + foe(defenseDirection),
                        "EE SNIPER (bonus headshot damage) → flat bonus DAMAGE (SE has no headshot detection)");
                // GANK / anti-gank → more damage per nearby enemy (expression-valued DAMAGE_MOD over %nearbyenemies%).
                case "GANK" -> MigratedEffect.mapped(token, "DAMAGE_MOD:attack:add:%nearbyenemies% * 8",
                        "EE GANK (scales with nearby enemies) → expression-valued DAMAGE_MOD over %nearbyenemies%");
                // RAGE:PERCENT → stacking combo damage: DAMAGE_MOD scaling with %combo% (per-hit streak).
                case "RAGE" -> parts.length >= 2
                        ? MigratedEffect.mapped(token,
                                "DAMAGE_MOD:attack:add:%combo% * " + trimNumber(pct(parts[1]) * 100),
                                "EE RAGE (stacking combo damage) → DAMAGE_MOD scaling with %combo%")
                        : MigratedEffect.todo(token, "unexpected RAGE arg shape");
                // DAMAGE_DISTANCE → a melee bonus that falls off with range (expression over %distance%).
                case "DAMAGE_DISTANCE" -> MigratedEffect.mapped(token, "DAMAGE_MOD:attack:add:25 - %distance% * 7",
                        "EE DAMAGE_DISTANCE → distance-scaled DAMAGE_MOD (close ≈ +25%, far ≈ -10%)");
                // REDUCE_HEARTS:HP:TIME → set the foe's health to HP (EE's regen-block is dead code, so omitted).
                case "REDUCE_HEARTS" -> parts.length >= 2
                        ? MigratedEffect.mapped(token, "MODIFY_HEALTH:" + intArg(parts[1]) + ":set:" + foe(defenseDirection),
                                "EE REDUCE_HEARTS → MODIFY_HEALTH:set (EE's regen-block was dead code, omitted)")
                        : MigratedEffect.todo(token, "unexpected REDUCE_HEARTS arg shape");
                // REMOVE_ARMOR → drop a random worn armour piece from the foe.
                case "REMOVE_ARMOR" -> MigratedEffect.mapped(token, "REMOVE_ARMOR:" + foe(defenseDirection),
                        "EE REMOVE_ARMOR → REMOVE_ARMOR (the foe)");
                // TELEBLOCK:SECONDS → block the foe from teleporting for <sec*20> ticks.
                case "TELEBLOCK" -> parts.length >= 2
                        ? MigratedEffect.mapped(token, "TELEBLOCK:" + (intArg(parts[1]) * 20) + ":" + foe(defenseDirection),
                                "EE TELEBLOCK → TELEBLOCK (seconds → ticks)")
                        : MigratedEffect.todo(token, "unexpected TELEBLOCK arg shape");
                // IMMUNE:TYPE[:SECONDS] → make the wielder immune to that damage cause (default 5s when omitted).
                case "IMMUNE" -> parts.length >= 2
                        ? MigratedEffect.mapped(token, "IMMUNE:" + parts[1].trim().toLowerCase(Locale.ROOT) + ":"
                                + (parts.length >= 3 ? intArg(parts[2]) * 20 : 100) + ":@Self",
                                "EE IMMUNE → IMMUNE (self; seconds → ticks, default 100t)")
                        : MigratedEffect.todo(token, "unexpected IMMUNE arg shape");
                // SMELT → auto-smelt the mined block (MINE-side).
                case "SMELT" -> MigratedEffect.mapped(token, "SMELT", "EE SMELT → SMELT (auto-smelt on MINE)");
                // TELEPORT_DROPS → mined drops to the breaker's inventory (MINE-side).
                case "TELEPORT_DROPS" -> MigratedEffect.mapped(token, "TELEPORT_DROPS",
                        "EE TELEPORT_DROPS → TELEPORT_DROPS (drops to inventory on MINE)");
                // AUTO_LOCK → home the fired arrow onto the nearest target (BOW_FIRE-side).
                case "AUTO_LOCK" -> MigratedEffect.mapped(token, "SEEK",
                        "EE AUTO_LOCK → SEEK (homing arrow on BOW_FIRE)");
                // DRAIN_SOULS_CONSTANT:TIME:AMOUNT → debit AMOUNT souls from the activator (the short EE durations
                // drain ~once, so an instant REMOVE_SOULS is faithful).
                case "DRAIN_SOULS_CONSTANT" -> parts.length >= 3
                        ? MigratedEffect.mapped(token, "REMOVE_SOULS:" + intArg(parts[2]),
                                "EE DRAIN_SOULS_CONSTANT → REMOVE_SOULS (activator; the short drain ≈ an instant debit)")
                        : MigratedEffect.todo(token, "unexpected DRAIN_SOULS_CONSTANT arg shape");
                // REMOVE_SOULS:AMOUNT:[PLAYER/TARGET] → debit AMOUNT souls from the activator (@Self) or the foe.
                case "REMOVE_SOULS" -> parts.length >= 2
                        ? MigratedEffect.mapped(token, "REMOVE_SOULS:" + intArg(parts[1]) + ":"
                                + (parts.length >= 3 && parts[2].trim().equalsIgnoreCase("TARGET")
                                        ? foe(defenseDirection) : "@Self"),
                                "EE REMOVE_SOULS → REMOVE_SOULS (TARGET drains the foe's gem, else the activator's)")
                        : MigratedEffect.todo(token, "unexpected REMOVE_SOULS arg shape");
                default -> MigratedEffect.todo(token,
                        "no StarEnchants equivalent for EE effect '" + head + "' — port this effect manually");
            };
        } catch (NumberFormatException badNumber) {
            return MigratedEffect.todo(token, "could not parse a numeric argument: " + badNumber.getMessage());
        }
    }

    /** POTION:TYPE:LEVEL:[target]:[durationSeconds] → SE {@code POTION:type:level:<ticks>:@target}. */
    private static MigratedEffect eePotion(String token, String[] parts, boolean defenseDir) {
        if (parts.length < 3) {
            return MigratedEffect.todo(token, "unexpected POTION arg shape (TYPE:LEVEL[:target[:seconds]])");
        }
        String type = parts[1].trim();
        int level = intArg(parts[2]);
        String targetTok = parts.length >= 4 ? parts[3] : "TARGET";
        int durTicks;
        String note;
        if (parts.length >= 5) {
            durTicks = intArg(parts[4]) * 20;
            note = "EE seconds → ticks (x20)";
        } else {
            durTicks = 200; // EE omitted the duration (a held-buff form) — default 10s; review for held enchants
            note = "EE omitted duration — defaulted to 200t (10s); review for held buffs";
        }
        return MigratedEffect.mapped(token, "POTION:" + type + ":" + level + ":" + durTicks
                + sel(targetTok, defenseDir), note);
    }

    /** HEAL:ADD:MIN[:MAX][:target] → {@code MODIFY_HEALTH:<amount>:give:@Self} (amount = the last numeric arg). */
    private static MigratedEffect eeHeal(String token, String[] parts) {
        if (parts.length < 3 || !parts[1].trim().equalsIgnoreCase("ADD")) {
            return MigratedEffect.todo(token, "unexpected HEAL arg shape (only HEAL:ADD is mapped)");
        }
        Integer amount = null;
        for (int i = 2; i < parts.length; i++) { // last numeric wins (the range max); a trailing target is ignored
            try {
                amount = Integer.parseInt(parts[i].trim());
            } catch (NumberFormatException notNumeric) {
                /* an EE target word (PLAYER/…) — StarEnchants heals the wielder regardless */
            }
        }
        return amount == null
                ? MigratedEffect.todo(token, "HEAL:ADD has no numeric amount")
                : MigratedEffect.mapped(token, "MODIFY_HEALTH:" + amount + ":give:@Self",
                        "EE HEAL:ADD → MODIFY_HEALTH (give, self); random range → max");
    }

    /**
     * DAMAGE_INCREASE:PCT → {@code DAMAGE_MOD:attack:add:<pct>}. A negative percent is a self-nerf
     * ({@code DAMAGE_MOD:attack:add} now admits negatives). An EE equation form ({@code playerHealth*2.5},
     * referencing {@code playerHealth}/{@code targetHealth}/{@code targetDistance}) multiplies the hit by the
     * equation result, so it lowers to an expression-valued percent {@code (expr - 1) * 100} over the matching
     * SE facts ({@code %actor.health%}/{@code %victim.health%}/{@code %distance%}), evaluated per hit.
     */
    private static MigratedEffect damageIncrease(String token, String[] parts) {
        if (parts.length < 2) {
            return MigratedEffect.todo(token, "unexpected DAMAGE_INCREASE arg shape");
        }
        String arg = parts[1].trim();
        if (isEeEquation(arg)) {
            String expr = arg
                    .replace("playerHealth", "%actor.health%")
                    .replace("targetHealth", "%victim.health%")
                    .replace("targetDistance", "%distance%");
            // EE multiplies the hit by the equation result; DAMAGE_MOD:add is a percent, so (result - 1) * 100.
            return MigratedEffect.mapped(token, "DAMAGE_MOD:attack:add:(" + expr + " - 1) * 100",
                    "EE DAMAGE_INCREASE equation → expression-valued DAMAGE_MOD percent ((expr - 1) * 100)");
        }
        double value = pct(arg);
        return MigratedEffect.mapped(token, "DAMAGE_MOD:attack:add:" + trimNumber(value),
                value < 0 ? "EE negative DAMAGE_INCREASE → DAMAGE_MOD:attack:add (a self-nerf)"
                        : "EE DAMAGE_INCREASE → DAMAGE_MOD:attack:add");
    }

    /** Whether a DAMAGE_INCREASE arg is an EE equation (references a health/distance var or uses {@code * /}). */
    private static boolean isEeEquation(String arg) {
        String low = arg.toLowerCase(Locale.ROOT);
        return low.contains("playerhealth") || low.contains("targethealth") || low.contains("targetdistance")
                || arg.indexOf('*') >= 0 || arg.indexOf('/') >= 0;
    }

    /** LIGHTNING:[target]:REAL → {@code LIGHTNING:5:@foe} (real ≈ 5 dmg); without REAL → cosmetic ({@code 0}). */
    private static MigratedEffect eeLightning(String token, String[] parts, boolean defenseDir) {
        boolean real = false;
        String targetTok = "TARGET";
        for (int i = 1; i < parts.length; i++) {
            String p = parts[i].trim();
            if (p.equalsIgnoreCase("REAL")) {
                real = true;
            } else if (!p.isEmpty()) {
                targetTok = p;
            }
        }
        return MigratedEffect.mapped(token, "LIGHTNING:" + (real ? "5" : "0") + sel(targetTok, defenseDir),
                real ? "EE real lightning ≈ 5 damage" : "cosmetic lightning");
    }

    /** Parse a percentage/decimal arg as a double. */
    private static double pct(String s) {
        return Double.parseDouble(s.trim());
    }

    /** A percentage clamped to 0..100, rendered without a trailing {@code .0}. */
    private static String clampPct(String s) {
        return trimNumber(Math.max(0, Math.min(100, pct(s))));
    }

    /**
     * Translate one EE {@code condition:} string to a {@link MigratedCondition}. EE conditions are a small
     * fixed vocabulary: {@code isPlayerHealth <op> N} → {@code %actor.health% <op> N}; {@code isTargetHealth
     * <op> N} → {@code %victim.health% <op> N}; {@code isPlayerBlocking} → {@code %blocking% == true}.
     * {@code isTargetHolding <ITEM-GROUP>} has no SE fact ({@code %victim.helditem%} is a single material,
     * not a group test) → a TODO, never a silently-wrong gate.
     */
    public static MigratedCondition eeCondition(String line) {
        String raw = line == null ? "" : line.trim();
        if (raw.isEmpty()) {
            return MigratedCondition.todo("empty condition");
        }
        String low = raw.toLowerCase(Locale.ROOT);
        if (low.startsWith("isplayerblocking")) {
            return MigratedCondition.mapped("%blocking% == true");
        }
        Matcher num = EE_NUMERIC_COND.matcher(raw);
        if (num.matches()) {
            String fact = num.group(1).equalsIgnoreCase("isPlayerHealth") ? "actor.health" : "victim.health";
            String op = num.group(2).equals("=") ? "==" : num.group(2); // EE single '=' → SE '=='
            return MigratedCondition.mapped("%" + fact + "% " + op + " " + num.group(3));
        }
        if (low.startsWith("istargetholding")) {
            // EE checks the victim's held item against a material GROUP (SWORD/AXE/BOW). SE's %victim.helditem%
            // is the single material name (e.g. DIAMOND_SWORD), so the `contains` string operator IS the group
            // test: %victim.helditem% contains "SWORD" matches any sword. Case-insensitive in the evaluator.
            String[] words = raw.trim().split("\\s+", 2);
            if (words.length >= 2 && !words[1].isBlank()) {
                String group = words[1].trim().toUpperCase(Locale.ROOT);
                return MigratedCondition.mapped("%victim.helditem% contains \"" + group + "\"");
            }
            return MigratedCondition.todo("isTargetHolding without an item group");
        }
        return MigratedCondition.todo("unrecognised EE condition '" + raw + "' — port manually");
    }

    /**
     * Translate one EliteArmor SET-bonus token (a different vocabulary from the combat effects above:
     * passive percentages, not triggered actions). The migrated set is DEFENSE-triggered, so a
     * REDUCTION maps cleanly to {@code REDUCE_DAMAGE}; an attack-direction DAMAGE bonus has no place on
     * a single-trigger defensive set and is flagged for the operator to model as a separate ATTACK set.
     */
    public static MigratedEffect setEffect(String legacyToken) {
        String token = legacyToken == null ? "" : legacyToken.trim();
        String[] parts = token.split(":");
        String head = parts.length == 0 ? "" : parts[0].trim().toUpperCase(java.util.Locale.ROOT);
        try {
            return switch (head) {
                case "REDUCTION" -> parts.length >= 2
                        ? MigratedEffect.mapped(token, "DAMAGE_MOD:defense:add:" + Math.min(100, Math.max(0, intArg(parts[1]))),
                                intArg(parts[1]) > 100 ? "clamped to the 0-100 reduction cap" : "")
                        : MigratedEffect.todo(token, "unexpected REDUCTION arg shape");
                case "DAMAGE" -> MigratedEffect.todo(token,
                        "attack-direction set bonus — model as a separate ATTACK-triggered set");
                default -> MigratedEffect.todo(token,
                        "no StarEnchants equivalent for set effect '" + head + "' in v1 — port manually");
            };
        } catch (NumberFormatException badNumber) {
            return MigratedEffect.todo(token, "could not parse a numeric argument: " + badNumber.getMessage());
        }
    }

    // ── AdvancedEnchantments (AE) ─────────────────────────────────────────────────────────────────
    // AE differs from EE: its `type` vocabulary is larger, `applies` uses ALL_<TYPE> material groups,
    // and effects are "HEAD:args %target%" / "HEAD:args @Selector{…}" (a SPACE-separated target, not a
    // trailing :TARGET). We map the confident overlap and TODO the rest (AE's rich effect/selector DSL).

    /** A {@code %variable%} token; group 1 = the inner name (which may contain spaces, AE-style). */
    private static final Pattern VAR = Pattern.compile("%([^%]+)%");
    /** The signed delta of an AE {@code ±N %chance%} result; group 1 = the number (the only digits in the result). */
    private static final Pattern CHANCE_DELTA = Pattern.compile("([+-]?\\d+(?:\\.\\d+)?)\\s*%chance%");
    /** A real target @selector embedded in MESSAGE/ACTIONBAR text (vs an arbitrary "@word" like "@admin"). */
    private static final Pattern SELECTOR_IN_TEXT =
            Pattern.compile("(?i)@(self|victim|attacker)\\b|%(victim|attacker|player|self|target)%");
    /** One type-coherent StarEnchants condition clause: a numeric fact with a numeric comparison, or a boolean
     *  flag compared to true/false (or used bare), with an optional leading {@code !} and surrounding parens. */
    private static final Pattern COND_CLAUSE = Pattern.compile(
            "!?\\(?%(?:actor\\.health|victim\\.health|damage|combo)%\\s*(?:>=|<=|==|!=|>|<)\\s*-?\\d+(?:\\.\\d+)?\\)?"
            + "|!?\\(?%(?:sneaking|blocking|flying)%\\s*(?:==|!=)\\s*(?:true|false)\\)?"
            + "|!?\\(?%(?:sneaking|blocking|flying)%\\)?");

    /**
     * AE enchant {@code type} segment → StarEnchants trigger (mapped only to triggers that exist in
     * {@code BuiltinTriggers}; unknown → absent). AE folds player/mob/projectile variants into one
     * enchant ({@code ATTACK}, {@code ATTACK_MOB}, …); they collapse to the same StarEnchants trigger.
     */
    private static final Map<String, String> AE_TRIGGERS = Map.ofEntries(
            Map.entry("ATTACK", "ATTACK"),
            Map.entry("ATTACK_MOB", "ATTACK"),
            Map.entry("DEFENSE", "DEFENSE"),
            Map.entry("DEFENSE_MOB", "DEFENSE"),
            Map.entry("DEFENSE_PROJECTILE", "DEFENSE"),
            Map.entry("KILL", "KILL"),
            Map.entry("KILL_PLAYER", "KILL"),
            Map.entry("KILL_MOB", "KILL"),
            Map.entry("MINING", "MINE"),
            Map.entry("MINE", "MINE"),
            Map.entry("BREAK_BLOCK", "MINE"),
            Map.entry("SHOOT", "BOW"),
            Map.entry("SHOOT_MOB", "BOW"),
            Map.entry("SHOOT_BOW", "BOW"),
            Map.entry("BOW", "BOW"),
            Map.entry("ARROW_HIT", "BOW"),
            Map.entry("DEATH", "DEATH"),
            Map.entry("PASSIVE_DEATH", "DEATH"),
            Map.entry("EFFECT_STATIC", "PASSIVE"),
            Map.entry("STATIC", "PASSIVE"),
            Map.entry("PASSIVE", "PASSIVE"),
            Map.entry("BITE_HOOK", "FISHING"),
            Map.entry("HOOK_ENTITY", "FISHING"),
            Map.entry("CATCH_FISH", "FISHING"),
            Map.entry("FISHING", "FISHING"),
            Map.entry("FALL_DAMAGE", "FALL"),
            Map.entry("FALL", "FALL"),
            Map.entry("EAT", "EAT"),
            Map.entry("RIGHT_CLICK", "INTERACT_RIGHT"),
            Map.entry("LEFT_CLICK", "INTERACT_LEFT"),
            Map.entry("INTERACT", "INTERACT"),
            Map.entry("HELD", "HELD"));

    /**
     * Map an AE enchant type to a StarEnchants trigger, or {@code null} if none of its segments has a v1
     * equivalent. AE types can be compound ({@code "ATTACK;ATTACK_MOB;SHOOT"}); we take the first
     * recognised segment (StarEnchants fires one trigger per ability — the operator adds the rest).
     */
    public static String aeTrigger(String legacyType) {
        if (legacyType == null) {
            return null;
        }
        for (String segment : legacyType.split(";")) {
            String mapped = AE_TRIGGERS.get(segment.trim().toUpperCase(java.util.Locale.ROOT));
            if (mapped != null) {
                return mapped;
            }
        }
        return null;
    }

    /**
     * Map AE {@code applies} material tokens to StarEnchants {@code applies-to} groups. AE uses
     * {@code ALL_<TYPE>} (e.g. {@code ALL_SWORD}, {@code ALL_HOE}, {@code ALL_ARMOR}); we translate the
     * group forms we recognise and drop the rest (the writer flags an empty applies-to for review).
     */
    public static List<String> aeAppliesTo(List<String> applies) {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        for (String raw : applies) {
            String token = raw == null ? "" : raw.trim().toUpperCase(java.util.Locale.ROOT);
            String group = token.startsWith("ALL_") ? token.substring(4) : token;
            switch (group) {
                case "SWORD", "SWORDS" -> out.add("SWORD");
                case "AXE", "AXES" -> out.add("AXE");
                case "PICKAXE", "PICKAXES" -> out.add("PICKAXE");
                case "HOE", "HOES", "SHOVEL", "SPADE", "TOOL", "TOOLS" -> out.add("TOOL");
                case "BOW", "BOWS" -> out.add("BOW");
                case "HELMET", "HELMETS" -> out.add("HELMET");
                case "CHESTPLATE", "CHESTPLATES" -> out.add("CHESTPLATE");
                case "LEGGINGS" -> out.add("LEGGINGS");
                case "BOOTS" -> out.add("BOOTS");
                case "ARMOR", "ARMOUR" -> {
                    out.add("HELMET");
                    out.add("CHESTPLATE");
                    out.add("LEGGINGS");
                    out.add("BOOTS");
                }
                case "WEAPON", "WEAPONS" -> out.add("WEAPON");
                default -> { /* an unrecognised/material-specific token — left for manual review */ }
            }
        }
        return List.copyOf(out);
    }

    /**
     * Translate one AE effect token to a {@link MigratedEffect}. AE writes the target as a trailing
     * space-separated {@code %victim%}/{@code %attacker%}/{@code %player%} (or an {@code @Selector{…}});
     * we peel it, convert it to a StarEnchants {@code @Selector}, and translate the confident effect
     * overlap ({@code DAMAGE}, {@code ADD_HEALTH}/{@code HEAL}, {@code POTION}, {@code MESSAGE},
     * {@code ACTIONBAR}, money) faithfully. Everything else (and any non-trivial AE selector) is a TODO.
     */
    /** Translate an AE effect token, defaulting to the ATTACK direction (the wielder is the aggressor). */
    public static MigratedEffect aeEffect(String legacyToken) {
        return aeEffect(legacyToken, false);
    }

    /**
     * Translate one AE effect token to a {@link MigratedEffect}. {@code defenseDirection} is true for a
     * DEFENSE-triggered enchant, where AE's {@code @Victim}/{@code @Attacker} name the opposite entities
     * than on an attack (see {@link #aeSelector}); passing it wrong silently retargets every effect, so the
     * reader derives it from the enchant's mapped trigger.
     */
    public static MigratedEffect aeEffect(String legacyToken, boolean defenseDirection) {
        String token = legacyToken == null ? "" : legacyToken.trim();
        // Command effects take a free-form body (the rest of the line) and never carry a @selector — handle
        // them before the selector-peel so a trailing word in the command is not mistaken for a target.
        String head0 = aeHead(token);
        if (head0.equals("CONSOLE_COMMAND") || head0.equals("PLAYER_COMMAND")) {
            return aeCommand(token, head0);
        }
        // MESSAGE/ACTIONBAR bodies are free text that may legitimately contain '@word' (e.g. "contact
        // @admin") — never peel a selector off them; aeMessage handles any embedded real selector itself.
        boolean message = head0.equals("MESSAGE") || head0.equals("ACTIONBAR");
        if (message) {
            return aeMessage(token, head0);
        }
        String body = token;
        String aeTarget = null;
        // AE's modern form is "HEAD:args @Selector"; peel a trailing SPACE-separated selector when the tail
        // is a selector form ('%word%' or '@Word' / '@Word{…}') — never an inline-tag fragment.
        int sp = token.lastIndexOf(' ');
        if (sp >= 0) {
            String tail = token.substring(sp + 1).trim();
            if (tail.matches("%\\w+%") || tail.matches("@\\w+(\\{[^}]*})?")) {
                aeTarget = tail;
                body = token.substring(0, sp).trim();
            }
        }
        // Also accept a colon-attached trailing selector ("HEAD:arg:@Victim") — peel its last segment.
        if (aeTarget == null) {
            int lastColon = body.lastIndexOf(':');
            if (lastColon >= 0 && body.startsWith("@", lastColon + 1)) {
                aeTarget = body.substring(lastColon + 1).trim();
                body = body.substring(0, lastColon).trim();
            }
        }
        String selector = aeSelector(aeTarget, defenseDirection);
        if (aeTarget != null && selector == null) {
            return MigratedEffect.todo(token,
                    "AE selector '" + aeTarget + "' has no StarEnchants equivalent — port the target by hand");
        }
        String suffix = selector == null ? "" : ":" + selector;

        String[] parts = body.split(":");
        String head = parts.length == 0 ? "" : parts[0].trim().toUpperCase(java.util.Locale.ROOT);
        try {
            return switch (head) {
                case "DAMAGE" -> parts.length >= 2
                        ? MigratedEffect.mapped(token, "DAMAGE:" + intArg(parts[1]) + suffix, "")
                        : MigratedEffect.todo(token, "unexpected DAMAGE arg shape");
                case "ADD_HEALTH", "HEAL" -> parts.length >= 2
                        ? MigratedEffect.mapped(token, "MODIFY_HEALTH:" + intArg(parts[1]) + ":give" + suffix,
                                "AE add-health → MODIFY_HEALTH (give)")
                        : MigratedEffect.todo(token, "unexpected heal arg shape");
                case "POTION" -> parts.length >= 4
                        // §C: SE POTION uses 1-based level, never the 0-based amplifier. AE's amplifier is the
                        // Bukkit 0-based value, so the SE level is amplifier + 1.
                        ? MigratedEffect.mapped(token, "POTION:" + parts[1].trim() + ":" + (intArg(parts[2]) + 1)
                                + ":" + intArg(parts[3]) + suffix, "AE amplifier → SE level (1-based)")
                        : MigratedEffect.todo(token, "unexpected POTION arg shape (effect:amplifier:duration)");
                // MESSAGE/ACTIONBAR are handled before the switch (aeMessage) — never reached here.
                case "ADD_MONEY", "GIVE_MONEY" -> parts.length >= 2
                        ? MigratedEffect.mapped(token, "MODIFY_MONEY:" + numArg(parts[1]) + ":give" + suffix,
                                "AE add-money → MODIFY_MONEY (give)")
                        : MigratedEffect.todo(token, "unexpected money arg shape");
                case "REMOVE_MONEY", "TAKE_MONEY" -> parts.length >= 2
                        ? MigratedEffect.mapped(token, "MODIFY_MONEY:" + numArg(parts[1]) + ":take" + suffix,
                                "AE remove-money → MODIFY_MONEY (take)")
                        : MigratedEffect.todo(token, "unexpected money arg shape");
                case "STEAL_MONEY" -> parts.length >= 2
                        ? MigratedEffect.mapped(token, "MODIFY_MONEY:" + numArg(parts[1]) + ":transfer" + suffix,
                                "AE steal-money → MODIFY_MONEY (transfer: take from target, give to activator)")
                        : MigratedEffect.todo(token, "unexpected money arg shape");
                case "STEAL_MONEY_PERCENT" -> parts.length >= 2
                        ? MigratedEffect.mapped(token, "MODIFY_MONEY:" + numArg(parts[1]) + ":steal_percent" + suffix,
                                "AE steal-money-percent → MODIFY_MONEY (steal_percent: that % of the target's balance)")
                        : MigratedEffect.todo(token, "unexpected money arg shape");
                // AE add-food → MODIFY_FOOD:give (food points); AE EXP → MODIFY_EXP:give (XP amount).
                case "ADD_FOOD" -> parts.length >= 2
                        ? MigratedEffect.mapped(token, "MODIFY_FOOD:" + intArg(parts[1]) + ":give" + suffix,
                                "AE add-food → MODIFY_FOOD (give)")
                        : MigratedEffect.todo(token, "unexpected ADD_FOOD arg shape");
                case "EXP" -> parts.length >= 2
                        ? MigratedEffect.mapped(token, "MODIFY_EXP:" + intArg(parts[1]) + ":give" + suffix,
                                "AE EXP spawns XP orbs at the target; MODIFY_EXP (give) grants XP to the actor — review the recipient")
                        : MigratedEffect.todo(token, "unexpected EXP arg shape");
                // AE BURN takes SECONDS (×20 internally); StarEnchants IGNITE takes ticks.
                case "BURN" -> parts.length >= 2
                        ? MigratedEffect.mapped(token, "IGNITE:" + (intArg(parts[1]) * 20) + suffix,
                                "AE BURN seconds → IGNITE ticks (x20)")
                        : MigratedEffect.todo(token, "unexpected BURN arg shape");
                // Argless AE effects (a stray arg is unexpected → TODO rather than silently dropped).
                case "REPAIR" -> parts.length == 1
                        ? MigratedEffect.mapped(token, "DURABILITY:-1:item" + suffix, "AE REPAIR collapsed into DURABILITY")
                        : MigratedEffect.todo(token, "unexpected REPAIR arg shape (AE REPAIR is argless)");
                case "KILL" -> parts.length == 1
                        ? MigratedEffect.mapped(token, "KILL" + suffix, "")
                        : MigratedEffect.todo(token, "unexpected KILL arg shape (AE KILL is argless)");
                case "EXTINGUISH" -> parts.length == 1
                        ? MigratedEffect.mapped(token, "EXTINGUISH" + suffix, "")
                        : MigratedEffect.todo(token, "unexpected EXTINGUISH arg shape (AE EXTINGUISH is argless)");
                case "DISARM" -> parts.length == 1
                        ? MigratedEffect.mapped(token, "DISARM" + suffix, "")
                        : MigratedEffect.todo(token, "unexpected DISARM arg shape (AE DISARM is argless)");
                // AE LIGHTNING:<real>: false/absent = visual only → LIGHTNING (no damage). true = real vanilla
                // lightning damage, which StarEnchants expresses as a fixed damage value → TODO (operator sets it).
                case "LIGHTNING" -> (parts.length >= 2 && parts[1].trim().equalsIgnoreCase("true"))
                        ? MigratedEffect.todo(token, "AE LIGHTNING:true deals real vanilla lightning damage; "
                                + "StarEnchants LIGHTNING applies a fixed damage value — set one and port manually")
                        : MigratedEffect.mapped(token, "LIGHTNING" + suffix, "AE visual lightning → LIGHTNING (no damage)");
                default -> MigratedEffect.todo(token,
                        "no verified StarEnchants equivalent for AE effect '" + head + "' — port this effect manually");
            };
        } catch (NumberFormatException badNumber) {
            return MigratedEffect.todo(token, "could not parse a numeric argument: " + badNumber.getMessage());
        }
    }

    /**
     * Convert an AE target token to a StarEnchants selector, or {@code null} for an unmappable one. AE's
     * {@code @Victim}/{@code @Attacker} name DIFFERENT entities by trigger direction (verified from the AE
     * triggers): on an ATTACK enchant {@code @Attacker} is the wielder and {@code @Victim} is the foe; on a
     * DEFENSE enchant {@code @Victim} is the wielder and {@code @Attacker} is the foe. StarEnchants is
     * role-fixed (actor = wielder; victim = the other entity; attacker is only set on defence), so the
     * mapping is direction-aware to preserve the TARGETED entity rather than the literal token:
     * <ul>
     *   <li>ATTACK: AE @Self/@Attacker → @Self (wielder); AE @Victim → @Victim (foe).</li>
     *   <li>DEFENSE: AE @Self/@Victim → @Self (wielder); AE @Attacker → @Attacker (foe).</li>
     * </ul>
     * AE's area/mining selectors ({@code @Aoe{…}}, {@code @NearestPlayer}, {@code @Trench}, {@code @Block},
     * …) have no faithful StarEnchants equivalent — StarEnchants {@code @Aoe} differs in default radius (4
     * vs AE's 1) and has no entity cap (AE always caps at 20), {@code @Nearest} is any-living not
     * player-only — so they return {@code null} (the effect becomes a TODO, not a silently-different target).
     */
    private static String aeSelector(String aeTarget, boolean defenseDirection) {
        if (aeTarget == null) {
            return null; // no explicit target → the effect keeps its StarEnchants default
        }
        String trimmed = aeTarget.trim();
        switch (trimmed.toLowerCase(Locale.ROOT)) {
            case "@self", "%player%", "%self%" -> { return "@Self"; } // the wielder, in either direction
            case "@attacker", "%attacker%" -> { return defenseDirection ? "@Attacker" : "@Self"; }
            case "@victim", "%victim%", "%target%" -> { return defenseDirection ? "@Self" : "@Victim"; }
            default -> { /* fall through to head-based selectors */ }
        }
        // Head + optional {args}: StarEnchants now has a faithful @NearestPlayer using the same {r=N} syntax,
        // so pass it through verbatim (v3.1 §A). @Aoe still differs (default radius + entity cap) and
        // @EntityInSight/@PlayerFromName/the mining selectors have no verified AE arg shape → TODO.
        int brace = trimmed.indexOf('{');
        String head = (brace < 0 ? trimmed : trimmed.substring(0, brace)).toLowerCase(Locale.ROOT);
        String args = brace < 0 ? "" : trimmed.substring(brace);
        return "@nearestplayer".equals(head) ? "@NearestPlayer" + args : null;
    }

    /**
     * Translate an AE {@code MESSAGE}/{@code ACTIONBAR} effect. StarEnchants messages the actor, so a body
     * naming a {@code @selector} target (which AE resolves and sends to) would change the recipient → TODO;
     * a body with a bare {@code ':'} (which the v2 lowerer would re-split) → TODO; otherwise the free text
     * maps to the actor. A non-selector {@code @word} (e.g. {@code "contact @admin"}) is left as text.
     */
    private static MigratedEffect aeMessage(String token, String head) {
        int colon = token.indexOf(':');
        String msg = colon < 0 ? "" : token.substring(colon + 1).trim();
        String low = head.toLowerCase(Locale.ROOT);
        if (msg.isEmpty()) {
            return MigratedEffect.todo(token, "empty " + low + " body");
        }
        if (msg.indexOf(':') >= 0) {
            return MigratedEffect.todo(token,
                    low + " body contains ':' which the v2 effect lowerer splits on — port manually");
        }
        if (SELECTOR_IN_TEXT.matcher(msg).find()) {
            return MigratedEffect.todo(token,
                    "AE " + low + " targets a @selector; StarEnchants messages the actor — port the target manually");
        }
        // Both AE heads collapse onto the canonical MESSAGE: ACTIONBAR becomes the actionbar channel
        // (channel is the optional 2nd terse arg, so MESSAGE:<text> stays a chat line). msg has no ':'
        // (guarded above), so the trailing ':actionbar' is an unambiguous channel arg.
        String se = "ACTIONBAR".equals(head) ? "MESSAGE:" + msg + ":actionbar" : "MESSAGE:" + msg;
        return MigratedEffect.mapped(token, se, "StarEnchants " + low + "s the actor (canonical MESSAGE)");
    }

    /** Whether a StarEnchants condition expression is type-coherent (so it compiles): every {@code &&}/{@code ||}
     *  clause must be a numeric fact with a numeric comparison, or a boolean flag used as a boolean. */
    private static boolean conditionExprIsSafe(String expr) {
        for (String clause : expr.split("\\s*(?:&&|\\|\\|)\\s*")) {
            if (!COND_CLAUSE.matcher(clause.trim()).matches()) {
                return false;
            }
        }
        return true;
    }

    /** The head token of an AE effect string: the text before the first {@code ':'} or {@code ' '}, upper-cased. */
    private static String aeHead(String token) {
        int end = token.length();
        int colon = token.indexOf(':');
        int space = token.indexOf(' ');
        if (colon >= 0) {
            end = colon;
        }
        if (space >= 0 && space < end) {
            end = space;
        }
        return token.substring(0, end).trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Translate an AE command effect. {@code CONSOLE_COMMAND} maps to {@code RUN_COMMAND} (StarEnchants runs
     * commands from the console); {@code PLAYER_COMMAND} (run AS the target) has no equivalent and is a TODO.
     * A command body containing {@code ':'} would be re-split by the v2 effect lowerer, so it is a TODO too.
     */
    private static MigratedEffect aeCommand(String token, String head) {
        if (head.equals("PLAYER_COMMAND")) {
            return MigratedEffect.todo(token,
                    "AE PLAYER_COMMAND runs as the player; StarEnchants RUN_COMMAND runs from console — port manually");
        }
        int colon = token.indexOf(':');
        String command = colon < 0 ? "" : token.substring(colon + 1).trim();
        if (command.isEmpty()) {
            return MigratedEffect.todo(token, "empty command body");
        }
        if (command.indexOf(':') >= 0) {
            return MigratedEffect.todo(token,
                    "command body contains ':' which the v2 effect lowerer splits on — port manually");
        }
        return MigratedEffect.mapped(token, "RUN_COMMAND:" + command, "AE console command → RUN_COMMAND");
    }

    /**
     * Translate one AE condition line ({@code LEFT : RESULT}) to a {@link MigratedCondition}. Now that the
     * StarEnchants grammar has the flow/chance clause forms (v3.1 §A), every AE result maps:
     * {@code %allow%}/{@code %continue%} &rarr; the allow-gate {@code LEFT}; {@code %stop%} &rarr; the negated
     * gate {@code !(LEFT)}; {@code %force%} &rarr; the clause {@code LEFT : %force%}; {@code ±N %chance%} &rarr;
     * the clause {@code LEFT : ±N %chance%}. Only a LEFT with an unmappable variable/operator (or a chance
     * result with no parseable {@code ±N}) is a TODO — never a silently-wrong gate.
     */
    public static MigratedCondition aeCondition(String line) {
        String raw = line == null ? "" : line.trim();
        int sep = raw.indexOf(" : ");
        if (sep < 0) {
            return MigratedCondition.todo("AE condition '" + raw + "' has no ' : <result>' — port manually");
        }
        String left = raw.substring(0, sep).trim();
        String result = raw.substring(sep + 3).trim().toLowerCase(Locale.ROOT);
        if (!result.contains("%force%") && !result.contains("%chance%")
                && !result.contains("%stop%") && !result.contains("%allow%") && !result.contains("%continue%")) {
            return MigratedCondition.todo("unrecognised AE condition result '" + result + "': " + raw);
        }
        String expr = aeLeftToSe(left);
        if (expr == null || expr.isBlank()) {
            return MigratedCondition.todo("AE condition uses a variable/operator with no StarEnchants fact: " + raw);
        }
        // Guarantee the emitted gate is type-coherent (e.g. never a flag compared numerically), so a migrated
        // condition can never produce a blocking E_COND_TYPE that fails the whole transactional load.
        if (!conditionExprIsSafe(expr)) {
            return MigratedCondition.todo(
                    "AE condition is not type-coherent for StarEnchants (e.g. a flag compared numerically) — port manually: " + raw);
        }
        if (result.contains("%force%")) {
            return MigratedCondition.clause(expr + " : %force%");
        }
        if (result.contains("%chance%")) {
            Double delta = parseChanceDelta(result);
            if (delta == null) {
                return MigratedCondition.todo("AE chance result has no parseable ±N before %chance%: " + raw);
            }
            String signed = (delta >= 0 ? "+" : "-") + trimNumber(Math.abs(delta));
            return MigratedCondition.clause(expr + " : " + signed + " %chance%");
        }
        if (result.contains("%stop%")) {
            return MigratedCondition.mapped("!(" + expr + ")");
        }
        return MigratedCondition.mapped(expr); // %allow% / %continue%
    }

    /** The first signed number in an AE {@code ±N %chance%} result, or {@code null} if none parses. */
    private static Double parseChanceDelta(String result) {
        Matcher m = CHANCE_DELTA.matcher(result);
        if (!m.find()) {
            return null;
        }
        try {
            return Double.valueOf(m.group(1));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** Render a non-negative magnitude without a trailing {@code .0} (so {@code 50.0} → {@code "50"}). */
    private static String trimNumber(double magnitude) {
        return magnitude == Math.rint(magnitude)
                ? Long.toString((long) magnitude)
                : Double.toString(magnitude);
    }

    /**
     * Translate an AE condition LEFT expression to a StarEnchants {@code Expr} string, or {@code null} if any
     * variable or operator has no equivalent. Maps every {@code %var%} to a StarEnchants {@code %fact%}, the
     * word joiners {@code and}/{@code or} to {@code &&}/{@code ||}, and AE's single {@code =} equality to
     * {@code ==} (leaving {@code >= <= != ==} intact). String operators ({@code contains}/{@code matchesregex})
     * have no Expr equivalent → {@code null}.
     */
    private static String aeLeftToSe(String left) {
        String lower = left.toLowerCase(Locale.ROOT);
        if (lower.contains("contains") || lower.contains("matchesregex")) {
            return null;
        }
        Matcher m = VAR.matcher(left);
        StringBuilder mapped = new StringBuilder();
        while (m.find()) {
            String fact = aeVarToSeFact(m.group(1).toLowerCase(Locale.ROOT));
            if (fact == null) {
                return null; // a variable with no StarEnchants fact → the whole line is a TODO
            }
            m.appendReplacement(mapped, Matcher.quoteReplacement("%" + fact + "%"));
        }
        m.appendTail(mapped);
        String expr = mapped.toString()
                .replaceAll("(?i)\\s+and\\s+", " && ")
                .replaceAll("(?i)\\s+or\\s+", " || ")
                .replaceAll("(?<![<>=!])=(?!=)", "=="); // AE single '=' → StarEnchants '=='
        return expr.trim();
    }

    /**
     * Map one AE condition variable (the lower-cased text inside {@code %...%}, e.g. {@code "victim health"})
     * to a StarEnchants fact token (without the percents), or {@code null} if it has no StarEnchants fact. AE
     * scopes a variable with a leading {@code attacker}/{@code victim}/{@code player} word; StarEnchants has
     * scoped {@code actor}/{@code victim} health and activator-only pose flags, so this assumes the ATTACK
     * direction (activator = attacker) — correct for the common case; DEFENSE imports should be reviewed.
     */
    private static String aeVarToSeFact(String inner) {
        String scope = null;
        String name = inner.trim();
        for (String s : new String[] {"attacker", "victim", "player"}) {
            if (name.equals(s) || name.startsWith(s + " ")) {
                scope = s;
                name = name.equals(s) ? "" : name.substring(s.length() + 1).trim();
                break;
            }
        }
        return switch (name) {
            case "health" -> "victim".equals(scope) ? "victim.health" : "actor.health";
            case "combo" -> "combo";
            case "damage" -> "damage";
            // Pose flags are the activator's only — a victim-scoped flag has no StarEnchants fact.
            case "is sneaking" -> "victim".equals(scope) ? null : "sneaking";
            case "is blocking" -> "victim".equals(scope) ? null : "blocking";
            case "is flying" -> "victim".equals(scope) ? null : "flying";
            default -> null; // max health / health percentage / food / world / … have no StarEnchants fact
        };
    }

    private static int intArg(String s) {
        return Integer.parseInt(s.trim());
    }

    /** Validate a numeric arg (large money values + decimals, beyond int range) and return it verbatim. */
    private static String numArg(String s) {
        Double.parseDouble(s.trim()); // validate only — throws NumberFormatException → caught as a TODO
        return s.trim();
    }

    /**
     * The message body of a {@code MESSAGE:text[:PLAYER/TARGET]} token: the segments after the head,
     * minus a trailing target word. EE treats the LAST colon-segment as the target (case-insensitively),
     * so only an exact trailing {@code PLAYER}/{@code TARGET}/{@code SELF} is peeled — never an
     * {@code endsWith} match that could eat legitimate text.
     */
    private static String messageBody(String token) {
        String[] parts = token.split(":");
        int end = parts.length; // exclusive bound of the body segments
        if (end > 1) {
            String last = parts[end - 1].trim().toUpperCase(java.util.Locale.ROOT);
            if (last.equals("PLAYER") || last.equals("TARGET") || last.equals("SELF")) {
                end--; // EE's trailing target word
            }
        }
        return String.join(":", java.util.Arrays.copyOfRange(parts, 1, end));
    }
}
