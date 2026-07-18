package compile.load;

import java.util.List;
import schema.diag.Source;

/**
 * Non-runtime metadata of one authored weapon reforge (ADR-0070) — a catalogue item that drag-applies onto a
 * WEAPON (the config.yml reforges.weapon-groups set; default SWORD+AXE) and, while that weapon is HELD, grants
 * the reforge's abilities. The ACTIVE (a0) fires on the implicit USE trigger (shift+right-click holding the
 * weapon); support abilities (a1+) may author PASSIVE/REPEATING for maintained bits.
 *
 * @param key              the source-prefixed key ({@code reforges/<stem>}) a reforged weapon stamps and
 *                         {@link Library#reforgeDefOf} looks up
 * @param display          bare display name (no colour codes) filled into the universal likeness as {@code {NAME}}
 * @param color            the reforge's colour code sequence (e.g. {@code "&5"}), filled as {@code {COLOR}}
 * @param description      the authored description lines, verbatim (filled as {@code {DESCRIPTION}})
 * @param icon             material token — the cosmetic catalogue identity (resolved cross-version at mint)
 * @param cooldownTicks    the ACTIVE (a0)'s cooldown, surfaced in lore as {@code {TIME_FORMATTED}}
 * @param useStableKeys    stable keys of the USE-trigger abilities, in authored order — what
 *                         {@code TriggerDispatch.fireUse} runs on activation (the {@code PetBracket.useStableKeys}
 *                         shape); a0 is always first (its USE is forced)
 * @param conditionSources authored condition strings aligned to {@link #useStableKeys} ({@code ""} where an
 *                         ability declares none); the fail message renders {@code {CONDITION}} from these
 */
public record ReforgeDef(
        String key,
        String display,
        String color,
        List<String> description,
        String icon,
        int cooldownTicks,
        List<String> useStableKeys,
        List<String> conditionSources,
        Source source) {

    public ReforgeDef {
        description = List.copyOf(description);
        useStableKeys = List.copyOf(useStableKeys);
        conditionSources = List.copyOf(conditionSources);
    }
}
