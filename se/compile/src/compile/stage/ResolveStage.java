package compile.stage;

import schema.diag.Diagnostics;

/**
 * Cross-version name resolution (docs/architecture.md §9): turns every version-volatile token in an
 * effect's arguments (material, sound, …) into a stable interned int handle via the injected
 * {@code PlatformResolvers}, so the runtime only ever sees resolved ids and can't touch a renamed constant.
 *
 * <p>Runs between {@link LowerStage} and {@link EraseStage}; never throws. An unknown token is a
 * diagnostic and that one effect is warn-and-skipped; the rest of the ability survives.
 */
public interface ResolveStage {

    /** @return the ability with handle args resolved to interned ids; any unresolvable effect is dropped */
    LoweredAbility resolve(LoweredAbility ability, Diagnostics diags);
}
