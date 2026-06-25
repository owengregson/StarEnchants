package engine.interact;

/**
 * The slot arbiter: an item's enchant-slot capacity (docs/architecture.md §6.4). An
 * immutable value derived from item state, with one unified default slot count.
 *
 * <p>Pure arithmetic; values come from an {@code ItemView} and the result is persisted in
 * PDC at apply time (a cold path). {@code keepSlots} on transmog/sort reuses {@code added}.
 *
 * @param base  the unified default slot count
 * @param added extra slots granted by slot-increase items (never negative)
 * @param used  enchants currently applied
 */
public record SlotLedger(int base, int added, int used) {

    public SlotLedger {
        if (base < 0 || added < 0 || used < 0) {
            throw new IllegalArgumentException("slot counts must be non-negative");
        }
    }

    public int max() {
        return base + added;
    }

    /** Free capacity, never negative — an over-filled item reports {@code 0}. */
    public int remaining() {
        return Math.max(0, max() - used);
    }

    public boolean canApply(int count) {
        return count <= remaining();
    }

    /** This ledger with {@code count} more enchants used; capacity unchanged. */
    public SlotLedger withApplied(int count) {
        return new SlotLedger(base, added, used + Math.max(0, count));
    }

    /** This ledger with {@code extra} more added slots (a slot-increase item). */
    public SlotLedger withAddedSlots(int extra) {
        return new SlotLedger(base, added + Math.max(0, extra), used);
    }
}
