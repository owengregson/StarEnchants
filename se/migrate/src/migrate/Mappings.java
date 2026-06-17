package migrate;

import java.util.List;
import java.util.Map;
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

    /** Legacy target token → StarEnchants selector. */
    private static String target(String legacy) {
        return switch (legacy == null ? "" : legacy.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "PLAYER", "SELF" -> "@Self";
            case "TARGET", "VICTIM" -> "@Victim";
            case "ATTACKER" -> "@Attacker";
            default -> "@Victim"; // the common combat default
        };
    }

    /** Map a legacy enchant type to a StarEnchants trigger, or {@code null} if it has no equivalent. */
    public static String trigger(String legacyType) {
        return legacyType == null ? null : TRIGGERS.get(legacyType.trim().toUpperCase(java.util.Locale.ROOT));
    }

    /** Map a legacy applies group to StarEnchants {@code applies-to}, or empty if unknown. */
    public static List<String> appliesTo(String legacyApplies) {
        if (legacyApplies == null) {
            return List.of();
        }
        return APPLIES.getOrDefault(legacyApplies.trim().toUpperCase(java.util.Locale.ROOT), List.of());
    }

    /**
     * Translate one legacy effect token to a {@link MigratedEffect}. Faithful for the verified core
     * (semantics confirmed against the legacy source); everything else is a TODO carrying the original
     * token and a reason, so the operator ports it by hand rather than receiving a silently-wrong value.
     */
    public static MigratedEffect effect(String legacyToken) {
        String token = legacyToken == null ? "" : legacyToken.trim();
        String[] parts = token.split(":");
        String head = parts.length == 0 ? "" : parts[0].trim().toUpperCase(java.util.Locale.ROOT);
        try {
            return switch (head) {
                // DAMAGE:MIN:MAX:[PLAYER/TARGET] → DAMAGE:<max>:@target (the random range collapses to its upper bound).
                case "DAMAGE" -> parts.length >= 4
                        ? MigratedEffect.mapped(token, "DAMAGE:" + intArg(parts[2]) + ":" + target(parts[3]),
                                "legacy random range " + parts[1] + "-" + parts[2] + " collapsed to its max")
                        : MigratedEffect.todo(token, "unexpected DAMAGE arg shape");
                // FLAME:SECONDS:[PLAYER/TARGET] → IGNITE:<seconds*20 ticks>:@target.
                case "FLAME" -> parts.length >= 3
                        ? MigratedEffect.mapped(token, "IGNITE:" + (intArg(parts[1]) * 20) + ":" + target(parts[2]),
                                "legacy seconds converted to ticks (x20)")
                        : MigratedEffect.todo(token, "unexpected FLAME arg shape");
                // EXTINGUISH:[PLAYER/TARGET] → EXTINGUISH:@target.
                case "EXTINGUISH" -> MigratedEffect.mapped(token,
                        "EXTINGUISH:" + target(parts.length >= 2 ? parts[1] : "TARGET"), "");
                // FEED:[AMOUNT] → FEED:<amount> (StarEnchants feeds the actor).
                case "FEED" -> parts.length >= 2
                        ? MigratedEffect.mapped(token, "FEED:" + intArg(parts[1]), "")
                        : MigratedEffect.todo(token, "unexpected FEED arg shape");
                // EXP:[AMOUNT] → GIVE_EXP:<amount>.
                case "EXP" -> parts.length >= 2
                        ? MigratedEffect.mapped(token, "GIVE_EXP:" + intArg(parts[1]), "renamed EXP → GIVE_EXP")
                        : MigratedEffect.todo(token, "unexpected EXP arg shape");
                // KILL (excludes players in both engines) → KILL (defaults to @Victim).
                case "KILL" -> MigratedEffect.mapped(token, "KILL", "");
                // REPAIR (held item) → REPAIR.
                case "REPAIR" -> MigratedEffect.mapped(token, "REPAIR", "");
                // MESSAGE:[MESSAGE]:[PLAYER/TARGET] → MESSAGE:<text> (StarEnchants messages the actor). A body
                // that itself contains ':' is demoted to a TODO: the effect lexer splits a bare arg on ':',
                // so emitting it would silently truncate the message — better the operator quotes it by hand.
                case "MESSAGE" -> {
                    String body = messageBody(token);
                    yield body.indexOf(':') >= 0
                            ? MigratedEffect.todo(token,
                                    "message body contains ':' which the effect parser splits on — port manually")
                            : MigratedEffect.mapped(token, "MESSAGE:" + body,
                                    "legacy target dropped — StarEnchants messages the actor");
                }
                default -> MigratedEffect.todo(token,
                        "no StarEnchants equivalent for '" + head + "' in v1 — port this effect manually");
            };
        } catch (NumberFormatException badNumber) {
            return MigratedEffect.todo(token, "could not parse a numeric argument: " + badNumber.getMessage());
        }
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
                        ? MigratedEffect.mapped(token, "REDUCE_DAMAGE:" + Math.min(100, Math.max(0, intArg(parts[1]))),
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
    public static MigratedEffect aeEffect(String legacyToken) {
        String token = legacyToken == null ? "" : legacyToken.trim();
        String body = token;
        String aeTarget = null;
        // AE's modern form is "HEAD:args @Selector"; peel a trailing SPACE-separated selector, but only
        // when the tail is genuinely a selector ('%word%' or '@Word' / '@Word{…}') — never a fragment of
        // an inline tag like '%allow%</condition>'.
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
        String selector = aeSelector(aeTarget);
        if (aeTarget != null && selector == null) {
            return MigratedEffect.todo(token,
                    "AE selector '" + aeTarget + "' has no v1 equivalent — port the target by hand");
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
                        ? MigratedEffect.mapped(token, "HEAL:" + intArg(parts[1]) + suffix, "AE add-health → HEAL")
                        : MigratedEffect.todo(token, "unexpected heal arg shape");
                case "POTION" -> parts.length >= 4
                        ? MigratedEffect.mapped(token, "POTION:" + parts[1].trim() + ":" + intArg(parts[2])
                                + ":" + intArg(parts[3]) + suffix, "")
                        : MigratedEffect.todo(token, "unexpected POTION arg shape (effect:amplifier:duration)");
                case "MESSAGE" -> {
                    String msg = body.substring(body.indexOf(':') + 1);
                    yield msg.indexOf(':') >= 0
                            ? MigratedEffect.todo(token, "message body contains ':' which the parser splits on — port manually")
                            : MigratedEffect.mapped(token, "MESSAGE:" + msg, "AE target dropped — messages the actor");
                }
                case "ACTIONBAR" -> {
                    String msg = body.substring(body.indexOf(':') + 1);
                    yield msg.indexOf(':') >= 0
                            ? MigratedEffect.todo(token, "actionbar body contains ':' — port manually")
                            : MigratedEffect.mapped(token, "ACTIONBAR:" + msg, "AE target dropped — actionbars the actor");
                }
                case "ADD_MONEY", "GIVE_MONEY" -> parts.length >= 2
                        ? MigratedEffect.mapped(token, "GIVE_MONEY:" + numArg(parts[1]) + suffix, "AE add-money → GIVE_MONEY")
                        : MigratedEffect.todo(token, "unexpected money arg shape");
                case "REMOVE_MONEY", "TAKE_MONEY" -> parts.length >= 2
                        ? MigratedEffect.mapped(token, "TAKE_MONEY:" + numArg(parts[1]) + suffix, "AE remove-money → TAKE_MONEY")
                        : MigratedEffect.todo(token, "unexpected money arg shape");
                default -> MigratedEffect.todo(token,
                        "no verified StarEnchants equivalent for AE effect '" + head + "' — port this effect manually");
            };
        } catch (NumberFormatException badNumber) {
            return MigratedEffect.todo(token, "could not parse a numeric argument: " + badNumber.getMessage());
        }
    }

    /**
     * Convert an AE target token to a StarEnchants selector, or {@code null} for an unmappable one. AE
     * targets the foe with a capital-{@code @} selector ({@code @Victim}/{@code @Attacker}/{@code @Self})
     * in modern configs (and {@code %victim%} in legacy ones); both forms map 1:1. AE's area/mining
     * selectors with args ({@code @Aoe{…}}, {@code @Tunnel{…}}, {@code @Block}, …) have different
     * semantics and are NOT auto-mapped — the operator ports the target by hand.
     */
    private static String aeSelector(String aeTarget) {
        if (aeTarget == null) {
            return null; // no explicit target → the effect keeps its StarEnchants default
        }
        return switch (aeTarget.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "@victim", "%victim%", "%target%" -> "@Victim";
            case "@attacker", "%attacker%" -> "@Attacker";
            case "@self", "%player%", "%self%" -> "@Self";
            default -> null; // an AE @Selector{…} with args / a mining selector / unknown placeholder
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
