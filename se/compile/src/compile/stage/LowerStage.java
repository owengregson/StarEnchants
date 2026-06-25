package compile.stage;

import compile.def.AbilityDef;
import schema.diag.Diagnostics;

/**
 * Stage 3: lowers one authored {@link AbilityDef} into a {@link LoweredAbility} — effects validated
 * against their {@code ParamSpec}s, condition parsed, cumulative {@code WAIT} resolved, ability-level
 * {@link compile.model.Affinity} folded (docs/architecture.md §3.2, §3.6). Never throws (§7, §10).
 * Interning/bit-packing/id assignment are deferred to {@link EraseStage}; this leaves names as names.
 */
public interface LowerStage {

    /** @return the lowered ability (always returned; check {@code diags.hasErrors()}) */
    LoweredAbility lower(AbilityDef def, Diagnostics diags);
}
