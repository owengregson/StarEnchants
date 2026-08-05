package compile;

import compile.cond.VarResolver;
import compile.def.AbilityDef;
import compile.model.Affinity;
import compile.model.Snapshot;
import compile.resolve.PlatformResolvers;
import compile.stage.DefaultEraseStage;
import compile.stage.DefaultLowerStage;
import compile.stage.DefaultResolveStage;
import compile.stage.DefaultSnapshotStage;
import compile.stage.EraseStage;
import compile.stage.LowerStage;
import compile.stage.LoweredAbility;
import compile.stage.ErasedContent;
import compile.stage.ResolveStage;
import compile.stage.SnapshotStage;
import schema.diag.Diagnostics;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * The content compiler: authored {@link AbilityDef}s &rarr; an immutable {@link Snapshot} via the
 * injected lower / resolve / erase / snapshot stages (docs/architecture.md §2, §3.2).
 *
 * <p>Never throws on bad content: faults collect into the shared {@link Diagnostics} and the
 * caller checks {@link Diagnostics#hasErrors()} before publishing, so a broken edit leaves the
 * previous snapshot live and never reaches the hot path (§10).
 */
public final class Compiler {

    private final LowerStage lower;
    private final ResolveStage resolve;
    private final EraseStage erase;
    private final SnapshotStage snapshot;

    public Compiler(LowerStage lower, ResolveStage resolve, EraseStage erase, SnapshotStage snapshot) {
        this.lower = Objects.requireNonNull(lower, "lower");
        this.resolve = Objects.requireNonNull(resolve, "resolve");
        this.erase = Objects.requireNonNull(erase, "erase");
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
    }

    /** Default stages with full selector support and handle resolution ({@code defaultSelectorOf} null &rarr; {@code SELF}). */
    public static Compiler of(SpecRegistry registry, Function<String, Affinity> affinityOf,
                              SpecRegistry selectors, Function<String, String> defaultSelectorOf,
                              PlatformResolvers resolvers) {
        return new Compiler(
                new DefaultLowerStage(registry, affinityOf, selectors, defaultSelectorOf, VarResolver.none(),
                        head -> -1, head -> -1, resolvers),
                new DefaultResolveStage(registry, selectors, resolvers),
                new DefaultEraseStage(),
                new DefaultSnapshotStage());
    }

    /** Default stages with full selector support; no handles are resolvable. */
    public static Compiler of(SpecRegistry registry, Function<String, Affinity> affinityOf,
                              SpecRegistry selectors, Function<String, String> defaultSelectorOf) {
        return of(registry, affinityOf, selectors, defaultSelectorOf, PlatformResolvers.none());
    }

    /** Adds the condition-variable vocabulary to the selector + handle-resolving wiring. */
    public static Compiler of(SpecRegistry registry, Function<String, Affinity> affinityOf,
                              SpecRegistry selectors, Function<String, String> defaultSelectorOf,
                              VarResolver vars, PlatformResolvers resolvers) {
        return new Compiler(
                new DefaultLowerStage(registry, affinityOf, selectors, defaultSelectorOf, vars,
                        head -> -1, head -> -1, resolvers),
                new DefaultResolveStage(registry, selectors, resolvers),
                new DefaultEraseStage(),
                new DefaultSnapshotStage());
    }

    /** Full selector + condition-variable support; no handles are resolvable. */
    public static Compiler of(SpecRegistry registry, Function<String, Affinity> affinityOf,
                              SpecRegistry selectors, Function<String, String> defaultSelectorOf,
                              VarResolver vars) {
        return of(registry, affinityOf, selectors, defaultSelectorOf, vars, PlatformResolvers.none());
    }

    /** Full wiring incl. the canonical trigger vocabulary, so a {@code triggerMask} bit means the trigger the runtime routes; unknowns are diagnostics (§3.7). */
    public static Compiler of(SpecRegistry registry, Function<String, Affinity> affinityOf,
                              SpecRegistry selectors, Function<String, String> defaultSelectorOf,
                              VarResolver vars, List<String> canonicalTriggers,
                              PlatformResolvers resolvers) {
        // No dense-id stamping — the erase stage also gets NO effect registry (a SUPPRESS scope KIND key then
        // erases to -1 silently rather than mis-diagnosing every head as unknown, matching the kindId -1 path).
        return new Compiler(
                new DefaultLowerStage(registry, affinityOf, selectors, defaultSelectorOf, vars,
                        head -> -1, head -> -1, resolvers),
                new DefaultResolveStage(registry, selectors, resolvers),
                new DefaultEraseStage(canonicalTriggers),
                new DefaultSnapshotStage());
    }

    /**
     * As above, but stamping each effect/selector with its dense kind id (ADR-0039) so the executor dispatches
     * by array index. {@code effectIdOf}/{@code selectorIdOf} come from the same registries whose {@code EffectKind[]}/
     * {@code SelectorKind[]} the executor is bound to, so a stamped id and its array position agree by construction.
     */
    public static Compiler of(SpecRegistry registry, Function<String, Affinity> affinityOf,
                              SpecRegistry selectors, Function<String, String> defaultSelectorOf,
                              VarResolver vars, List<String> canonicalTriggers,
                              PlatformResolvers resolvers, ToIntFunction<String> effectIdOf,
                              ToIntFunction<String> selectorIdOf) {
        return of(registry, affinityOf, selectors, defaultSelectorOf, vars, canonicalTriggers, resolvers,
                effectIdOf, selectorIdOf, Map.of());
    }

    /**
     * As above, plus {@code triggerTypes} — the trigger&rarr;combat-direction vocabulary the erase stage stamps
     * unauthored abilities' TYPE suppression scope from (R-QC3, ADR-0075), so
     * {@code SUPPRESS { scope: TYPE, key: DEFENSE }} reaches the whole defender side with nothing authored per
     * file. Empty leaves every TYPE scope unstamped, which is what every lower-level wiring below wants.
     */
    public static Compiler of(SpecRegistry registry, Function<String, Affinity> affinityOf,
                              SpecRegistry selectors, Function<String, String> defaultSelectorOf,
                              VarResolver vars, List<String> canonicalTriggers,
                              PlatformResolvers resolvers, ToIntFunction<String> effectIdOf,
                              ToIntFunction<String> selectorIdOf, Map<String, String> triggerTypes) {
        return new Compiler(
                new DefaultLowerStage(registry, affinityOf, selectors, defaultSelectorOf, vars,
                        effectIdOf, selectorIdOf, resolvers),
                new DefaultResolveStage(registry, selectors, resolvers),
                // effectIdOf threaded to erase too: SUPPRESS scope KIND keys resolve to dense kindIds (ADR-0053).
                new DefaultEraseStage(canonicalTriggers, effectIdOf, triggerTypes),
                new DefaultSnapshotStage());
    }

    /** Full wiring with the trigger vocabulary; no handles are resolvable. */
    public static Compiler of(SpecRegistry registry, Function<String, Affinity> affinityOf,
                              SpecRegistry selectors, Function<String, String> defaultSelectorOf,
                              VarResolver vars, List<String> canonicalTriggers) {
        return of(registry, affinityOf, selectors, defaultSelectorOf, vars, canonicalTriggers,
                PlatformResolvers.none());
    }

    /** Default stages with handle resolution but no selectors — every effect targets {@code SELF}. */
    public static Compiler of(SpecRegistry registry, Function<String, Affinity> affinityOf,
                              PlatformResolvers resolvers) {
        return new Compiler(
                new DefaultLowerStage(registry, affinityOf, MapSpecRegistry.of(), head -> null,
                        VarResolver.none(), head -> -1, head -> -1, resolvers),
                new DefaultResolveStage(registry, MapSpecRegistry.of(), resolvers),
                new DefaultEraseStage(),
                new DefaultSnapshotStage());
    }

    /** Default stages with the given affinity lookup; no handles are resolvable. */
    public static Compiler of(SpecRegistry registry, Function<String, Affinity> affinityOf) {
        return of(registry, affinityOf, PlatformResolvers.none());
    }

    /** Default stages; every effect defaults to {@code CONTEXT_LOCAL} and no handles resolve. */
    public static Compiler of(SpecRegistry registry) {
        return new Compiler(
                new DefaultLowerStage(registry),
                new DefaultResolveStage(registry, PlatformResolvers.none()),
                new DefaultEraseStage(),
                new DefaultSnapshotStage());
    }

    /**
     * Compile a content library into a snapshot.
     *
     * @param defs       authored abilities, in the order dense ids should be assigned
     * @param generation build counter stamped into the snapshot (§5.2)
     * @param diags      collector for every diagnostic over the whole compile
     * @return the immutable snapshot — always returned; inspect {@code diags} before publishing
     */
    public Snapshot compile(List<AbilityDef> defs, int generation, Diagnostics diags) {
        Objects.requireNonNull(defs, "defs");
        Objects.requireNonNull(diags, "diags");
        List<LoweredAbility> lowered = new ArrayList<>(defs.size());
        for (AbilityDef def : defs) {
            lowered.add(resolve.resolve(lower.lower(def, diags), diags));
        }
        ErasedContent erased = erase.erase(lowered, diags);
        return snapshot.assemble(erased, diags, generation);
    }
}
