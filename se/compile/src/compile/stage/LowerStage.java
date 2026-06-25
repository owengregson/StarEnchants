package compile.stage;

import compile.def.AbilityDef;
import schema.diag.Diagnostics;

/**
 * Stage 3: lowers one authored {@link AbilityDef} into a {@link LoweredAbility} (docs/architecture.md
 * §3.2). Never throws. Interning/bit-packing/id assignment are deferred to {@link EraseStage}; this
 * leaves names as names.
 */
public interface LowerStage {

    /** @return the lowered ability (always returned; check {@code diags.hasErrors()}) */
    LoweredAbility lower(AbilityDef def, Diagnostics diags);
}
