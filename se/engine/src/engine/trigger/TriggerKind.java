package engine.trigger;

/**
 * One trigger kind — an event family an ability can fire on (docs/architecture.md §3.7).
 * Declares its DSL {@code name} (interned by {@link TriggerRegistry}), combat
 * {@link Direction}, and routing metadata. The routing flags are what Cosmic Enchants
 * never modelled, causing a helmet enchant to fire on {@code ATTACK} (§1.4 "applies is
 * NOT re-checked").
 *
 * <p>Pure declarations — the Bukkit event→{@code Activation} binding lives server-side —
 * so the vocabulary is unit-testable and add-ons register triggers without a server.
 */
public interface TriggerKind {

    /** Combat direction — decides which pre-flattened {@code WornState} array the trigger feeds. */
    enum Direction {
        /** The activator is dealing damage (ATTACK, KILL, BOW, …) — feeds {@code combatAttack}. */
        ATTACK,
        /** The activator is taking damage (DEFENSE, FALL, FIRE) — feeds {@code combatDefense}. */
        DEFENSE,
        /** Not a combat direction (MINE, BREAK, INTERACT, PASSIVE, …). */
        NEUTRAL
    }

    /** The DSL trigger name, matched case-insensitively. */
    String name();

    Direction direction();

    /** Read the ability from the <em>held</em> item only, not worn equipment. */
    boolean usesHeld();

    /** Read the ability from the player's merged armor + main-hand equipment. */
    boolean scansEquipment();

    /** Supplies a target entity, so target-directed effects/selectors resolve. */
    boolean needsTarget();
}
