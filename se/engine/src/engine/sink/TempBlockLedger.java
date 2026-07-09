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
 * key's OWNING region thread by construction: the sink enters via {@code regionOp(pos)} — and a {@code WALKER}
 * platform re-keys each tile to its own region via {@code Scheduling.onRegion(tile)} first — then schedules
 * each revert via {@code Scheduling.onRegionLater(pos)}, both targeting the same Folia region (the main thread
 * on Paper). So one key's {@link Entry} — its layer list, each layer's deadline/seq — is mutated single-threaded
 * and needs no per-entry lock; the {@link ConcurrentHashMap} only guards the cross-region MAP structure while
 * different keys' region threads add/drop entries concurrently.
 *
 * <p>The place/revert DECISION logic is pure and drives the world only through the {@link BlockOps} seam
 * (type-id reads/writes + capture/restore of the {@code S} original token), so it is unit-tested by
 * hand-computed scenarios with no Bukkit; {@link BukkitBlockOps} supplies the real {@code S = BlockState}
 * backing. The material identity {@code typeId} is opaque to the core — it only ever tests equality and
 * re-sets it — so the caller may use any stable within-run mapping (the sink uses {@code Material.ordinal()}).
 *
 * <p><strong>The feature-layer guard API (F25/F26/F29).</strong> A handful of public entry points let the
 * {@code TempBlockGuardListener} enforce the {@code unbreakable}/immovable contract that the coordinate-keyed
 * ledger otherwise cannot: {@link #reclaim}, {@link #revertAt}, {@link #rearmChunk} and {@link #healIfExpired}
 * MUST run on the target key's OWNING region thread (their events — break/chunk-load/the region-hopped sweep —
 * always do), so they observe the same single-threaded layer list as {@link #place}/{@link #revert}.
 * {@link #isEmpty}, {@link #guarded} and {@link #forEachTile} are the only sanctioned CROSS-region reads: they
 * touch just the {@link ConcurrentHashMap} structure (key presence / weakly-consistent key iteration), never a
 * layer list, so a piston/explosion/sweep on a neighbouring region may call them safely — a stale read at worst
 * cancels one already-doomed piston stroke or skips one tile this pass.
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
    public record Pending(long layerId, long seq, long delayTicks) {
    }

    /** Receives one re-armed revert per live layer during {@link #rearmChunk} — the caller schedules each. */
    public interface RearmSink {
        void schedule(int x, int y, int z, long layerId, long seq, long delayTicks);
    }

    /** Visits every live tile coordinate for {@link #forEachTile}; the caller hops each to its owning region. */
    public interface TileVisitor {
        void tile(UUID world, int x, int y, int z);
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

    /**
     * Ticks an entry may outlive its newest deadline before it is treated as abandoned — a scheduled revert that
     * never fired (chunk unload, a dropped region task), which would strand the entry and its temp block forever.
     * Three paths reclaim such a leak (F29): {@link #place} self-heals an entry it is about to overwrite, the
     * periodic {@link #healIfExpired} sweep force-reverts loaded-chunk leaks, and {@link #rearmChunk} replays the
     * dropped reverts on chunk reload (which clamps the delay, so a reload heals near-instantly rather than
     * waiting out this grace). Generous (60s @ 20 tps) so a normal in-flight refresh is never mistaken for a leak.
     */
    static final long STALE_GRACE_TICKS = 1200;

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
     * different material pushes a new layer over the current one. Never re-captures the original. An entry whose
     * every layer is long past its deadline (its reverts never fired — {@link #STALE_GRACE_TICKS}) is
     * force-reverted and dropped first, so this placement starts fresh over the true original, not a leaked block.
     */
    Pending place(Key key, int typeId, int ticks, long now) {
        Entry<S> entry = entries.get(key);
        if (entry != null && expired(entry, now)) {
            // A scheduled revert never fired (chunk unload / dropped region task): the entry — and its temp block
            // in the world — would leak forever. Force the normal revert (honour an external change, else restore
            // the true original), drop the abandoned entry, then fall through to a fresh capture over it.
            healExpired(key, entry);
            entry = null;
        }
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

    /** Whether the ledger tracks no tile — the world-event listeners' allocation-free fast-path gate. */
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * Whether a tile is a tracked temp block. A read-only {@link ConcurrentHashMap} lookup, safe to call from any
     * region thread (see the class consistency model) — the piston/explosion/flow guards use it to spot a tile
     * that a neighbouring region owns without ever touching that entry's layer list.
     */
    public boolean guarded(UUID world, int x, int y, int z) {
        return entries.containsKey(new Key(world, x, y, z));
    }

    /**
     * Mine-as-early-revert: pop ALL layers at a tile at once and restore the TRUE original, so a broken/displaced
     * temp block yields no vanilla drop and never strands the captured original. Returns whether it reclaimed.
     * MUST run on the key's owning region thread. No-op (returns {@code false}) if the tile is untracked, or if
     * the world already shows something other than our visible layer — a foreign change we must NOT hijack; the
     * scheduled {@link #revert}'s drop branch handles that entry. Every still-pending scheduled revert for the
     * key then no-ops on the entry-null guard, so no cancellation machinery is needed. Mirrors {@link #healExpired}.
     */
    public boolean reclaim(UUID world, int x, int y, int z) {
        Key key = new Key(world, x, y, z);
        Entry<S> entry = entries.get(key);
        if (entry == null) {
            return false;
        }
        Layer visible = entry.layers.get(entry.layers.size() - 1);
        if (ops.readTypeId(key) != visible.typeId) {
            return false; // the world diverged — leave the entry for revert()'s drop branch
        }
        ops.restoreOriginal(key, entry.original);
        entries.remove(key);
        return true;
    }

    /** A public wrapper over {@link #revert} for the feature-layer chunk-load re-arm (F29). Owning thread only. */
    public void revertAt(UUID world, int x, int y, int z, long layerId, long seq, long now) {
        revert(new Key(world, x, y, z), layerId, seq, now);
    }

    /**
     * Re-arm a revert for EVERY layer of every entry in the given chunk (F29): a Folia chunk unload can drop the
     * scheduled region task, stranding the temp block until reload. On {@code ChunkLoadEvent} the owning region
     * thread — which owns every key in the chunk — replays each layer's revert with the deadline-relative delay
     * (clamped to {@code >= 1} so a past-deadline stranded tile heals next tick, not after the stale grace). A
     * duplicate delivery (Paper's original task also fired, or a fast unload/reload that retained it) is a
     * harmless no-op on {@link #revert}'s entry-null + seq guard. Re-arms buried layers too — a long floor under a
     * short trail has its own dropped revert, which {@link #revert} handles as a buried pop.
     */
    public void rearmChunk(UUID world, int chunkX, int chunkZ, long now, RearmSink out) {
        for (Key key : entries.keySet()) {
            if (!key.world().equals(world) || (key.x() >> 4) != chunkX || (key.z() >> 4) != chunkZ) {
                continue;
            }
            Entry<S> entry = entries.get(key); // in-chunk key → this thread owns it → the layer read is race-free
            if (entry == null) {
                continue;
            }
            for (Layer layer : entry.layers) {
                out.schedule(key.x(), key.y(), key.z(), layer.id, layer.seq, Math.max(1, layer.deadline - now));
            }
        }
    }

    /**
     * Visit every live tile coordinate (F29 self-heal sweep). Iterates only the weakly-consistent key set and
     * NEVER touches a layer list, so it is safe from the global sweep thread; the visitor hops each tile to its
     * owning region before {@link #healIfExpired} reads that entry.
     */
    public void forEachTile(TileVisitor v) {
        for (Key key : entries.keySet()) {
            v.tile(key.world(), key.x(), key.y(), key.z());
        }
    }

    /** Force-revert a tile whose scheduled revert was dropped and is now past the stale grace. Owning thread only. */
    public void healIfExpired(UUID world, int x, int y, int z, long now) {
        Key key = new Key(world, x, y, z);
        Entry<S> entry = entries.get(key);
        if (entry != null && expired(entry, now)) {
            healExpired(key, entry);
        }
    }

    /** Whether every layer's deadline is so far past that its scheduled revert must have been dropped. */
    private static boolean expired(Entry<?> entry, long now) {
        long newest = Long.MIN_VALUE;
        for (Layer layer : entry.layers) {
            newest = Math.max(newest, layer.deadline);
        }
        return newest < now - STALE_GRACE_TICKS;
    }

    /** Force-revert an abandoned entry: honour an external change (restore nothing), else restore the original; always drop it. */
    private void healExpired(Key key, Entry<S> entry) {
        Layer visible = entry.layers.get(entry.layers.size() - 1);
        if (ops.readTypeId(key) == visible.typeId) {
            ops.restoreOriginal(key, entry.original); // still our block → put the captured original back
        }
        entries.remove(key); // external change or restored — the leaked entry is gone either way
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
