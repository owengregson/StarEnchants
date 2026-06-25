package compile.stage;

import schema.diag.Diagnostics;

/**
 * Cross-version name resolution (docs/architecture.md §9): every version-volatile token (material,
 * sound, …) becomes a stable interned int handle, so the runtime never touches a renamed constant.
 * Runs between {@link LowerStage} and {@link EraseStage}; never throws. An unknown token is a diagnostic
 * and that one effect is warn-and-skipped; the rest of the ability survives.
 */
public interface ResolveStage {

    /** @return the ability with handle args resolved to interned ids; any unresolvable effect is dropped */
    LoweredAbility resolve(LoweredAbility ability, Diagnostics diags);
}
