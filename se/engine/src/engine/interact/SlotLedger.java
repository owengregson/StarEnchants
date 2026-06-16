package engine.interact;

/**
 * The slot arbiter: an item's enchant-slot capacity (docs/architecture.md §6.4). An
 * immutable value computed from the item's state — {@code base} default slots plus any
 * {@code added} (from slot-increase items), against the {@code used} count (the number
 * of enchants applied). Fixes the originals' throwaway-copy non-persistence and the
 * 9-vs-10 default split (one unified default).
 *
 * <p>Pure arithmetic so it is trivially correct and unit-testable; the values come from
 * an {@code ItemView} and the result is persisted in PDC at apply time (a cold path, not
 * the combat hot path). {@code keepSlots} on transmog/sort reuses the same {@code added}.
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

    /** Total capacity = base + added. */
    public int max() {
        return base + added;
    }

    /** Free capacity = max − used, never negative (an over-filled item reports {@code 0}). */
    public int remaining() {
        return Math.max(0, max() - used);
    }

    /** @return {@code true} if {@code count} more enchants would fit. */
    public boolean canApply(int count) {
        return count <= remaining();
    }

    /** This ledger with {@code count} more enchants applied (does not change capacity). */
    public SlotLedger withApplied(int count) {
        return new SlotLedger(base, added, used + Math.max(0, count));
    }

    /** This ledger with {@code extra} more added slots (a slot-increase item). */
    public SlotLedger withAddedSlots(int extra) {
        return new SlotLedger(base, added + Math.max(0, extra), used);
    }
}
