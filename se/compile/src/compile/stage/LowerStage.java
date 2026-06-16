package compile.stage;

import compile.def.AbilityDef;
import schema.diag.Diagnostics;

/**
 * Stage 3 of the compiler: lowers one authored {@link AbilityDef} into a
 * {@link LoweredAbility} — effect lines validated into flyweight
 * {@link compile.model.CompiledEffect}s against their {@code ParamSpec}s, the
 * condition string parsed into a {@link compile.model.CompiledCondition}, cumulative
 * {@code WAIT} resolved, and the ability-level {@link compile.model.Affinity} folded
 * (docs/architecture.md §3.2, §3.6).
 *
 * <p>Never throws: every fault is reported into {@code diags} and lowering proceeds,
 * so one bad argument yields one precise diagnostic rather than aborting the load
 * (§7, §10). Interning, bit-packing and dense-id assignment are deferred to
 * {@link EraseStage} — this stage leaves worlds/triggers/suppression as names.
 */
public interface LowerStage {

    /**
     * Lower one ability definition.
     *
     * @param def   the authored ability
     * @param diags the collector for any faults
     * @return the lowered ability (always returned; check {@code diags.hasErrors()})
     */
    LoweredAbility lower(AbilityDef def, Diagnostics diags);
}
