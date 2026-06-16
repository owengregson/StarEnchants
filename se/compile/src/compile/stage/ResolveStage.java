package compile.stage;

import schema.diag.Diagnostics;

/**
 * Cross-version name resolution (docs/architecture.md §9) — the compile phase that
 * turns every version-volatile token in an effect's arguments (a material, sound,
 * potion effect, …) into a stable interned int handle, via the injected
 * {@code PlatformResolvers}. After this stage the runtime is constitutionally
 * incapable of touching a renamed constant: it only ever sees resolved ids.
 *
 * <p>Runs between {@link LowerStage} and {@link EraseStage} and never throws. An
 * unknown token becomes a file/line diagnostic and the one offending effect is
 * warn-and-skipped (dropped); the rest of the ability survives, and the load never
 * crashes.
 */
public interface ResolveStage {

    /**
     * Resolve every handle-typed argument of {@code ability}'s effects.
     *
     * @param ability the lowered ability
     * @param diags   the collector for unknown-handle diagnostics
     * @return the ability with handle args rewritten to interned ids and any effect
     *     whose handle could not be resolved dropped
     */
    LoweredAbility resolve(LoweredAbility ability, Diagnostics diags);
}
