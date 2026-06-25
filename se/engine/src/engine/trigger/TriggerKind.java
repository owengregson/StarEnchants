package engine.trigger;

/**
 * One trigger kind — a self-describing event family an ability can fire on
 * (docs/architecture.md §3.7). A {@code TriggerKind} declares its DSL {@code name}
 * (interned to a canonical id by the {@link TriggerRegistry}), its combat
 * {@link Direction} (which pre-flattened {@code WornState} array it feeds), and the
 * three pieces of routing metadata a Cosmic Enchants-style plugin never modelled explicitly — fixing the bug where
 * a helmet enchant could fire on {@code ATTACK} (§1.4 "applies is NOT re-checked").
 *
 * <p>The Bukkit event binding (translating an event into an {@code Activation}) is a
 * server-side concern of the listener set, not this SPI — kinds are pure declarations
 * so the vocabulary is unit-testable and add-ons can register new triggers without a
 * server. Same one-interface-one-registration rule as effects/selectors.
 */
public interface TriggerKind {

    /** Which combat direction a trigger belongs to (decides the {@code WornState} array it feeds). */
    enum Direction {
        /** The activator is dealing damage (ATTACK, KILL, BOW, …) — feeds {@code combatAttack}. */
        ATTACK,
        /** The activator is taking damage (DEFENSE, FALL, FIRE) — feeds {@code combatDefense}. */
        DEFENSE,
        /** Not a combat direction (MINE, BREAK, INTERACT, PASSIVE, …). */
        NEUTRAL
    }

    /** The DSL trigger name, e.g. {@code ATTACK} (matched case-insensitively). */
    String name();

    /** This trigger's combat direction. */
    Direction direction();

    /** Whether the ability is read from the <em>held</em> item only (HELD/BREAK/ITEM_DAMAGE). */
    boolean usesHeld();

    /** Whether the ability is read from the player's merged armor + main-hand equipment. */
    boolean scansEquipment();

    /** Whether this trigger supplies a target entity (so target-directed effects/selectors resolve). */
    boolean needsTarget();
}
