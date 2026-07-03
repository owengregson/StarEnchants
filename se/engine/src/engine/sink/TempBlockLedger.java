package engine.sink;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The layered per-position temp-block ledger shared by {@code TEMP_BLOCK} (per-position) and {@code WALKER}
 * (per-platform-tile), so overlapping placements compound instead of clobbering (the devil netherrack-trail
 * over the Hell's-Kitchen magma-floor bug: each stacked placement used to capture the previous temp block as
 * its "original" and restore it forever). One shared instance rides the per-boot {@link SinkEnv}, so two
 * SEPARATE activations (a DEFENSE floor + a REPEATING trail) that write the same tile coordinate can compound
 * correctly through it — a per-event sink cannot, being freshly allocated per activation.
 *
 * <p><strong>Consistency model.</strong> Every {@link #place}/{@link #revert} for one {@link Key} runs on that
 * key's OWNING region thread by construction: the sink enters via {@code regionOp(pos)} and schedules each
 * revert via {@code Scheduling.onRegionLater(pos)}, both targeting the same Folia region (the main thread on
 * Paper). So one key's {@link Entry} — its layer list, each layer's deadline/seq — is mutated single-threaded
 * and needs no per-entry lock; the {@link ConcurrentHashMap} only guards the cross-region MAP structure while
 * different keys' region threads add/drop entries concurrently. (A {@code WALKER} radius straddling a region
 * boundary is treated as the platform origin's region, exactly as the pre-ledger code placed it.)
 *
 * <p>The place/revert DECISION logic is pure and drives the world only through the {@link BlockOps} seam
 * (type-id reads/writes + capture/restore of the {@code S} original token), so it is unit-tested by
 * hand-computed scenarios with no Bukkit; {@link BukkitBlockOps} supplies the real {@code S = BlockState}
 * backing. The material identity {@code typeId} is opaque to the core — it only ever tests equality and
 * re-sets it — so the caller may use any stable within-run mapping (the sink uses {@code Material.ordinal()}).
 *
 * @param <S> the captured-original token type — {@code BlockState} in production, {@code Integer} in tests
 */
public final class TempBlockLedger<S> {

    /** A block position: the world id plus block coordinates. */
    record Key(UUID world, int x, int y, int z) {
    }

    /**
     * The platform seam the pure core drives. {@code typeId} is an opaque, within-run-stable material
     * identity (the sink uses {@code Material.ordinal()}); {@code S} is the captured-original token restored
     * verbatim on the final revert.
     */
    interface BlockOps<S> {

        /** The visible block's current type id (a value the world may have changed out from under us). */
        int readTypeId(Key key);

        /** Set the block to {@code typeId} (no physics — matching the original {@code setType(mat, false)}). */
        void setTypeId(Key key, int typeId);

        /** Capture the pre-placement block, full-fidelity, for a later verbatim restore. */
        S captureOriginal(Key key);

        /** Restore the captured original (full-fidelity, the {@code update(true, false)} technique). */
        void restoreOriginal(Key key, S original);
    }

    /** The revert a placement asks the caller to schedule: the target layer's identity + seq, and the delay. */
    record Pending(long layerId, long seq, long delayTicks) {
    }

    /** One temporary layer over a position. {@code deadline}/{@code seq} mutate on a same-material refresh. */
    private static final class Layer {
        private final long id;
        private final int typeId;
        private long deadline;
        private long seq;

        private Layer(long id, int typeId, long deadline) {
            this.id = id;
            this.typeId = typeId;
            this.deadline = deadline;
        }
    }

    /** The captured original plus the ordered layer stack (index 0 = bottom, last = the visible top). */
    private static final class Entry<S> {
        private final S original;
        private final List<Layer> layers = new ArrayList<>();

        private Entry(S original) {
            this.original = original;
        }
    }

    private final BlockOps<S> ops;
    private final ConcurrentHashMap<Key, Entry<S>> entries = new ConcurrentHashMap<>();
    private final AtomicLong nextLayerId = new AtomicLong();

    TempBlockLedger(BlockOps<S> ops) {
        this.ops = ops;
    }

    /**
     * Place {@code typeId} at {@code key} for {@code ticks} and return the revert to schedule (a
     * deadline-relative delay). First placement over a live-free tile captures the original once; a
     * same-material top refresh only extends the deadline and bumps the seq (no re-capture, no block touch),
     * so the earlier scheduled revert goes stale and exactly one restore fires at the final deadline; a
     * different material pushes a new layer over the current one. Never re-captures the original.
     */
    Pending place(Key key, int typeId, int ticks, long now) {
        Entry<S> entry = entries.get(key);
        if (entry == null) {
            entry = new Entry<>(ops.captureOriginal(key));
            Layer layer = pushLayer(entry, typeId, now + ticks);
            entries.put(key, entry);
            ops.setTypeId(key, typeId);
            return new Pending(layer.id, layer.seq, layer.deadline - now);
        }
        Layer top = entry.layers.get(entry.layers.size() - 1);
        if (top.typeId == typeId) {
            top.deadline = Math.max(top.deadline, now + ticks);
            top.seq++;
            return new Pending(top.id, top.seq, top.deadline - now);
        }
        Layer layer = pushLayer(entry, typeId, now + ticks);
        ops.setTypeId(key, typeId);
        return new Pending(layer.id, layer.seq, layer.deadline - now);
    }

    /**
     * Best-effort revert of ONE scheduled layer. No-op if the entry/layer is gone or the seq is stale (a
     * same-material refresh superseded it). Guards an external change first: if the world no longer shows the
     * visible layer's material the tile was changed out from under us, so the whole entry is dropped and
     * nothing is touched. Otherwise a buried layer is forgotten silently; the visible layer is popped along
     * with any newly-exposed layers already past their deadline, then the next live layer is repainted — or,
     * if none remain, the captured original is restored and the entry removed.
     */
    void revert(Key key, long layerId, long seq, long now) {
        Entry<S> entry = entries.get(key);
        if (entry == null) {
            return;
        }
        Layer layer = findLayer(entry, layerId);
        if (layer == null || layer.seq != seq) {
            return; // layer already gone, or superseded by a same-material refresh
        }
        Layer visible = entry.layers.get(entry.layers.size() - 1);
        if (ops.readTypeId(key) != visible.typeId) {
            entries.remove(key); // the world changed this tile — drop the whole entry, restore nothing
            return;
        }
        if (layer != visible) {
            entry.layers.remove(layer); // a buried layer expired — forget it, repaint nothing
            return;
        }
        entry.layers.remove(entry.layers.size() - 1);
        while (!entry.layers.isEmpty() && entry.layers.get(entry.layers.size() - 1).deadline <= now) {
            entry.layers.remove(entry.layers.size() - 1); // a middle that expired while buried
        }
        if (entry.layers.isEmpty()) {
            ops.restoreOriginal(key, entry.original);
            entries.remove(key);
        } else {
            ops.setTypeId(key, entry.layers.get(entry.layers.size() - 1).typeId);
        }
    }

    private Layer pushLayer(Entry<S> entry, int typeId, long deadline) {
        Layer layer = new Layer(nextLayerId.incrementAndGet(), typeId, deadline);
        entry.layers.add(layer);
        return layer;
    }

    private static Layer findLayer(Entry<?> entry, long layerId) {
        for (Layer layer : entry.layers) {
            if (layer.id == layerId) {
                return layer;
            }
        }
        return null;
    }

    /**
     * The {@code canReplace} decision, single-sourced so the sink's live-block gate and the unit tests agree:
     * {@code 0} = air only, {@code 1} = air/liquid, {@code 3} = solid only, anything else = replace anything.
     * The sink reads the three predicates off the LIVE block and calls this, keeping the gate where it was.
     */
    static boolean replaceable(int replaceMode, boolean air, boolean liquid, boolean solid) {
        return switch (replaceMode) {
            case 0 -> air;
            case 1 -> air || liquid;
            case 3 -> solid;
            default -> true;
        };
    }
}
