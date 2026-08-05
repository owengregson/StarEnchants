package engine.sink;

import engine.condition.GroundOwnership;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Who owns the ground under a {@code PHANTOM_BLOCKS} overlay. The overlay is packets only — it writes no
 * block, so {@link TempBlockLedger} never hears about it — and that is exactly why this exists: a field's
 * CLAIM and its VISIBILITY are two different things, and only the first is what {@code OWNED_GROUND} and
 * {@code STACKING_DOT} are asking about. Without it a phantom patch was ground nobody stood on, and Rot and
 * Decay's whole ramping DoT half never started.
 *
 * <p>Coordinate-keyed and Bukkit-free for {@link TempBlockLedger}'s reason: the fact layer must not reach into
 * the sink's world types, and a plain {@code (world, x, y, z)} tuple is answerable from either era.
 *
 * <p><strong>Consistency model.</strong> One {@link #claim} runs on the patch anchor's region thread and one
 * {@link #release} on that same thread when the window closes; every {@link #ownerAt} is a read from whatever
 * thread owns the queried block (the DoT pulse runs on its victim's). All state is therefore either immutable
 * ({@link Field}) or a {@link ConcurrentHashMap} entry, so a cross-region read is safe and a stale one at worst
 * misses a field that just closed. The expiry is authoritative rather than advisory: a release that never ran
 * (a dropped region task) leaves entries that answer {@code null} from their deadline on, and the next
 * {@link #claim} sweeps them.
 */
public final class PhantomFields {

    /** A block position: the world id plus block coordinates ({@link TempBlockLedger.Key}'s twin). */
    private record Key(UUID world, int x, int y, int z) {
    }

    /** One live overlay: its claimant, its deadline, and every position it covers. Immutable once claimed. */
    private record Field(UUID owner, long expiryTick, List<Key> keys) {
    }

    private final ConcurrentHashMap<Long, Field> fields = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Key, Long> fieldByKey = new ConcurrentHashMap<>();
    private final AtomicLong nextFieldId = new AtomicLong();

    /**
     * Claim every position of one overlay for {@code owner} until {@code expiryTick}, returning the id
     * {@link #release} closes it with. Positions are {@code {x, y, z}} triples in {@code world} — the surface
     * blocks the overlay repaints, i.e. the blocks bodies stand ON. A position already claimed by a live field
     * is TAKEN OVER, matching the ledger's same-material refresh rule: two overlapping fields are
     * indistinguishable to whoever is standing there, so the ground belongs to whoever painted it last.
     */
    public long claim(UUID world, UUID owner, long expiryTick, List<int[]> positions, long nowTicks) {
        sweep(nowTicks);
        long id = nextFieldId.incrementAndGet();
        List<Key> keys = new ArrayList<>(positions.size());
        for (int[] at : positions) {
            keys.add(new Key(world, at[0], at[1], at[2]));
        }
        fields.put(id, new Field(owner, expiryTick, List.copyOf(keys)));
        for (Key key : keys) {
            fieldByKey.put(key, id);
        }
        return id;
    }

    /**
     * Drop one overlay's claim. Only positions THIS field still holds are released, so a patch another field
     * has since painted over keeps its newer claimant rather than being blanked by an older window closing.
     */
    public void release(long fieldId) {
        Field field = fields.remove(fieldId);
        if (field == null) {
            return;
        }
        for (Key key : field.keys()) {
            fieldByKey.remove(key, fieldId);
        }
    }

    /** The player whose live overlay covers this position, or {@code null} for unclaimed or lapsed ground. */
    public UUID ownerAt(UUID world, int x, int y, int z, long nowTicks) {
        Long id = fieldByKey.get(new Key(world, x, y, z));
        if (id == null) {
            return null;
        }
        Field field = fields.get(id);
        return field == null || nowTicks >= field.expiryTick() ? null : field.owner();
    }

    /** Whether no overlay is claimed — the tests' and callers' allocation-free emptiness check. */
    public boolean isEmpty() {
        return fields.isEmpty();
    }

    /**
     * The ONE composed ground-ownership read: a REAL temp block's claimant ({@code TEMP_BLOCK}/{@code WALKER})
     * first, else a live phantom patch's owner. Both consumers take it from here — {@code %actor.ownedground%}
     * at the fact layer and {@code STACKING_DOT}'s per-pulse field test — because a fact and a DoT that
     * disagreed about what "standing in someone's field" means is precisely the bug: the ladder re-asked the
     * ledger every pulse about ground the overlay had never told it about.
     */
    public static GroundOwnership over(TempBlockLedger<?> placed, PhantomFields phantom, LongSupplier nowTicks) {
        return (owner, world, x, y, z) -> owner != null
                && (owner.equals(placed.ownerAt(world, x, y, z))
                        || owner.equals(phantom.ownerAt(world, x, y, z, nowTicks.getAsLong())));
    }

    /** Forget every field whose deadline has passed — the reaper for a release its region task never ran. */
    private void sweep(long nowTicks) {
        for (Map.Entry<Long, Field> entry : fields.entrySet()) {
            if (nowTicks >= entry.getValue().expiryTick()) {
                release(entry.getKey());
            }
        }
    }
}
