package compile.load;

import java.util.List;
import java.util.Map;

/**
 * Non-runtime metadata of one authored pet (ADR-0052) — a textured player-head item whose
 * {@code content/pets/<key>.yml} declares its likeness identity plus a sparse level-bracket map of engine
 * abilities. The abilities erase into {@code AbilityDef}s ({@link compile.model.SourceKind#PET}) exactly like
 * every other source; which bracket is live for a given item is resolved at worn-state build time from the
 * item's stored level ({@link #bracketFor}), so the engine needs no level-scaling primitive.
 *
 * <p>The {@code key} is the filename stem the pet codec stores; ability stable keys namespace it as
 * {@code pet:<key>/<floor>} (first ability of the bracket whose floor is {@code <floor>}), then
 * {@code pet:<key>/<floor>/a1}, …. The two are decoupled like every family: codec identity survives snapshot
 * rebuilds, dense ids re-derive from the stable keys.
 *
 * @param key         filename stem; the pet codec stores it and {@link Library#petDefOf} looks it up
 * @param display     bare display name (no colour codes) filled into the universal likeness as {@code {NAME}}
 * @param color       the pet's colour code sequence (e.g. {@code "&7"}), filled as {@code {COLOR}}
 * @param active      {@code true} for {@code type: ACTIVE} (right-click), {@code false} for {@code PASSIVE}
 * @param head        base64 texture value for the player-head skin; {@code ""} = untextured (material fallback)
 * @param material    material token minted when head textures are unsupported; defaults to {@code PLAYER_HEAD}
 * @param descriptor  authored flavour line(s) shown at the TOP of the lore (filled as {@code {DESCRIPTOR}});
 *                    empty = the template's descriptor line renders nothing
 * @param description authored ability description lines, verbatim (filled as {@code {DESCRIPTION}})
 * @param permission  Bukkit permission gating use, checked at use time; {@code ""} = everyone
 * @param messageOnNoHome optional line sent when a digger pet is used with no home dug
 *                    ({@code message-on-no-home}); {@code ""} = fall back to the universal fail template
 *                    (ADR-0067)
 * @param maxLevel    authored per-pet cap; {@code 0} = use the global pets.max-level compatibility default
 * @param experienceToLevel XP required to reach each target level (keys start at 2); absent keys use the
 *                    global pets.exp-per-level compatibility default
 * @param brackets    the authored level brackets, sorted ascending by {@link PetBracket#floor()}
 */
public record PetDef(
        String key,
        String display,
        String color,
        boolean active,
        String head,
        String material,
        List<String> descriptor,
        List<String> description,
        String permission,
        String messageOnNoHome,
        int maxLevel,
        Map<Integer, Integer> experienceToLevel,
        List<PetBracket> brackets) {

    public PetDef {
        descriptor = List.copyOf(descriptor);
        description = List.copyOf(description);
        experienceToLevel = Map.copyOf(experienceToLevel);
        brackets = List.copyOf(brackets);
    }

    /** Compatibility constructor for definitions using the pre-per-pet-progression shape. */
    public PetDef(String key, String display, String color, boolean active, String head, String material,
                  List<String> descriptor, List<String> description, String permission, String messageOnNoHome,
                  List<PetBracket> brackets) {
        this(key, display, color, active, head, material, descriptor, description, permission, messageOnNoHome,
                0, Map.of(), brackets);
    }

    /** Resolve the authored cap, retaining the global value for legacy content. */
    public int maxLevelOr(int globalDefault) {
        return maxLevel > 0 ? maxLevel : globalDefault;
    }

    /** XP needed to move from {@code currentLevel} to the next level. */
    public int expToNext(int currentLevel, int globalDefault) {
        return experienceToLevel.getOrDefault(currentLevel + 1, globalDefault);
    }

    /**
     * The live bracket for an item at {@code level}: the highest authored floor {@code <= level}, or the
     * lowest bracket when {@code level} sits below every floor (a level-0/corrupt item still resolves).
     * {@code null} only for a def with no brackets (blocked at load).
     */
    public PetBracket bracketFor(int level) {
        PetBracket live = brackets.isEmpty() ? null : brackets.get(0);
        for (PetBracket bracket : brackets) { // ascending; the last floor <= level wins
            if (bracket.floor() <= level) {
                live = bracket;
            }
        }
        return live;
    }
}
