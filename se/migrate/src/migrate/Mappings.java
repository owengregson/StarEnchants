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

    private static int intArg(String s) {
        return Integer.parseInt(s.trim());
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
