package engine.sink;

/**
 * Which named engine status window a {@code STATUS_CLEAR} op lifts — the codes the {@code STATUS_CLEAR} kind
 * maps its {@code status} enum to and {@code Sink.clearStatus} reads back. Held here rather than on the kind
 * so the sink never depends on the effect package; the values ARE the enum's declaration ordinals, so the
 * erase stage needs no second mapping table.
 */
public final class StatusKinds {

    private StatusKinds() {
    }

    /** The teleport denial ({@code TELEBLOCK}). */
    public static final int TELEBLOCK = 0;

    /** Every potion denial held on the player ({@code POTION_LOCK}). */
    public static final int POTION_LOCK = 1;

    /** The armed disarm window. */
    public static final int DISARM = 2;

    /** A live freeze — the pin, its DoT chain and both attribute modifiers, lifted through one teardown. */
    public static final int FREEZE = 3;
}
