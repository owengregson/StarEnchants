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
import java.util.Objects;
import java.util.function.Function;

/**
 * The content compiler: authored {@link AbilityDef}s &rarr; an immutable
 * {@link Snapshot} by running the pipeline stages in order (docs/architecture.md §2, §3.2).
 *
 * <pre>
 *   defs --lower--> LoweredAbility[] --erase--> ErasedContent --snapshot--> Snapshot
 * </pre>
 *
 * <p>Never throws on bad content: faults collect into the shared {@link Diagnostics} and the
 * caller checks {@link Diagnostics#hasErrors()} before publishing — a broken edit leaves the
 * previous snapshot live and never reaches the hot path (§10). Stages are injected so each is
 * independently testable and a future stage can be slotted in without touching the others.
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
                new DefaultLowerStage(registry, affinityOf, selectors, defaultSelectorOf),
                new DefaultResolveStage(registry, resolvers),
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
                new DefaultLowerStage(registry, affinityOf, selectors, defaultSelectorOf, vars),
                new DefaultResolveStage(registry, resolvers),
                new DefaultEraseStage(),
                new DefaultSnapshotStage());
    }

    /** Full selector + condition-variable support; no handles are resolvable. */
    public static Compiler of(SpecRegistry registry, Function<String, Affinity> affinityOf,
                              SpecRegistry selectors, Function<String, String> defaultSelectorOf,
                              VarResolver vars) {
        return of(registry, affinityOf, selectors, defaultSelectorOf, vars, PlatformResolvers.none());
    }

    /**
     * Full production wiring incl. the canonical trigger vocabulary, so a compiled
     * {@code triggerMask} bit means the same trigger the runtime routes; unknown triggers
     * are diagnostics (§3.7).
     */
    public static Compiler of(SpecRegistry registry, Function<String, Affinity> affinityOf,
                              SpecRegistry selectors, Function<String, String> defaultSelectorOf,
                              VarResolver vars, List<String> canonicalTriggers,
                              PlatformResolvers resolvers) {
        return new Compiler(
                new DefaultLowerStage(registry, affinityOf, selectors, defaultSelectorOf, vars),
                new DefaultResolveStage(registry, resolvers),
                new DefaultEraseStage(canonicalTriggers),
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
                new DefaultLowerStage(registry, affinityOf),
                new DefaultResolveStage(registry, resolvers),
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
