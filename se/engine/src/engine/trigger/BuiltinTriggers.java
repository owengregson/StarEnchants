package engine.trigger;

import engine.trigger.TriggerKind.Direction;

/**
 * The explicit, greppable vocabulary of built-in triggers (docs/architecture.md §3.7,
 * §1.4 {@code EffectType}). Registration order is the canonical id order, so the
 * compiler and runtime agree on what each {@code triggerMask} bit means. Adding a
 * trigger is one line here plus its Bukkit event binding in the (server-side) listener
 * set — no other code changes (§3.7 "no router edit, no other trigger touched").
 *
 * <p>Combat direction follows EE's {@code getPvPEffects} (ATTACK/KILL/BOW/BOW_FIRE/
 * TRIDENT are attacker-side; DEFENSE/FALL/FIRE are defender-side); the rest are neutral.
 * Routing metadata (held / scans-equipment / needs-target) is the fix for EE's
 * never-re-checked {@code applies} (§1.4).
 */
public final class BuiltinTriggers {

    private BuiltinTriggers() {
    }

    /** A registry of all built-in triggers, in canonical id order. */
    public static TriggerRegistry registry() {
        return TriggerRegistry.builder()
                // Attacker-side combat (deal damage).
                .register(Trigger.attack("ATTACK"))
                .register(Trigger.attack("BOW"))
                .register(Trigger.attack("TRIDENT"))
                .register(Trigger.attack("KILL"))
                .register(new Trigger("BOW_FIRE", Direction.ATTACK, false, true, false)) // shooter, no victim yet
                // Defender-side combat (take damage).
                .register(Trigger.defense("DEFENSE"))
                .register(new Trigger("FALL", Direction.DEFENSE, false, true, false))
                .register(new Trigger("FIRE", Direction.DEFENSE, false, true, false))
                // Neutral, equipment-scanned.
                .register(Trigger.neutral("PASSIVE"))
                .register(Trigger.neutral("MINE"))
                .register(Trigger.neutral("DEATH"))
                // Held-item triggers (read enchants from that item only).
                .register(Trigger.held("HELD"))
                .register(Trigger.held("BREAK"))
                .register(Trigger.held("ITEM_DAMAGE"))
                .register(Trigger.held("EAT"))
                .register(Trigger.held("FISHING"))
                .register(Trigger.held("INTERACT"))
                .register(Trigger.held("INTERACT_LEFT"))
                .register(Trigger.held("INTERACT_RIGHT"))
                // Equipment-scanned, timer-driven (§B): armed per worn ability with repeat>0, fired by the
                // RepeatingDriver on each ability's period. Appended last so existing trigger ids are unshifted.
                .register(Trigger.neutral("REPEATING"))
                .build();
    }
}
