package compile.load;

import java.util.List;

/**
 * One authored level bracket of a {@link PetDef} (ADR-0052): the abilities (already erased to
 * {@code AbilityDef}s by the reader) that are live while the pet's stored level is at or above
 * {@link #floor()} and below the next authored floor. The reader splits the bracket's stable keys by role so
 * neither the right-click path nor the worn resolver re-derives triggers at runtime:
 *
 * <ul>
 *   <li>{@link #useStableKeys} — the {@code USE}-trigger abilities an ACTIVE pet fires through the ADR-0048
 *       cold path on right-click (empty for a PASSIVE pet);</li>
 *   <li>{@link #wornStableKeys} — every other ability, contributed to {@code WornState} while the pet sits in
 *       the hotbar (for an ACTIVE pet: only while its armed window is open).</li>
 * </ul>
 *
 * @param floor            the minimum pet level at which this bracket is live
 * @param cooldownTicks    the bracket's right-click cooldown (ability a0's, surfaced as {@code {TIME_FORMATTED}})
 * @param durationTicks    the ACTIVE armed window; {@code 0} = no window (instant effects, no ENDED message)
 * @param useStableKeys    stable keys of the bracket's USE abilities, in authored order
 * @param wornStableKeys   stable keys of the bracket's non-USE abilities, in authored order
 * @param conditionSources authored condition strings aligned to {@link #useStableKeys} ({@code ""} where none);
 *                         the fail message renders {@code {CONDITION}} from these
 */
public record PetBracket(
        int floor,
        int cooldownTicks,
        int durationTicks,
        List<String> useStableKeys,
        List<String> wornStableKeys,
        List<String> conditionSources) {

    public PetBracket {
        useStableKeys = List.copyOf(useStableKeys);
        wornStableKeys = List.copyOf(wornStableKeys);
        conditionSources = List.copyOf(conditionSources);
    }
}
