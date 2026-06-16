package compile;

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
 * {@link Snapshot}, by running the pipeline stages in order
 * (docs/architecture.md §2 "se-compile", §3.2).
 *
 * <pre>
 *   defs --lower--> LoweredAbility[] --erase--> ErasedContent --snapshot--> Snapshot
 * </pre>
 *
 * <p>The compile never throws on bad content: every fault is collected into the
 * shared {@link Diagnostics}, and the caller decides whether to publish the result
 * by checking {@link Diagnostics#hasErrors()} — a broken edit leaves the previous
 * snapshot live and never reaches the hot path (§10). Stages are injected so each is
 * independently testable and a future stage (cross-version {@code resolve},
 * {@code typecheck}) can be slotted in without touching the others.
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

    /**
     * A compiler wired with the default stages: each effect's affinity resolved
     * through {@code affinityOf} (head &rarr; declared {@link Affinity}; {@code null}
     * &rarr; {@code CONTEXT_LOCAL}) and version-volatile handles resolved through
     * {@code resolvers}.
     */
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
     * @param defs       the authored abilities, in the order dense ids should be assigned
     * @param generation the build counter to stamp into the snapshot (§5.2)
     * @param diags      the collector for every diagnostic over the whole compile
     * @return the immutable snapshot (always returned; inspect {@code diags} before publishing)
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
