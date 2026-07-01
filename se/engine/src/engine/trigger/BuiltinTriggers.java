package engine.trigger;

import engine.trigger.TriggerKind.Direction;

/**
 * The greppable vocabulary of built-in triggers (docs/architecture.md §3.7, §1.4).
 * Registration order IS canonical id order, so compiler and runtime agree on what each
 * {@code triggerMask} bit means. Adding a trigger is one line here plus its Bukkit event
 * binding in the server-side listener set (§3.7).
 *
 * <p>Combat direction mirrors Cosmic Enchants' {@code getPvPEffects}. Routing metadata
 * (held / scans-equipment / needs-target) fixes Cosmic Enchants' never-re-checked
 * {@code applies} bug — a helmet enchant firing on ATTACK (§1.4).
 */
public final class BuiltinTriggers {

    private BuiltinTriggers() {
    }

    /** All built-in triggers, in canonical id order. */
    public static TriggerRegistry registry() {
        return TriggerRegistry.builder()
                .register(Trigger.attack("ATTACK"))
                .register(Trigger.attack("BOW"))
                .register(Trigger.attack("TRIDENT"))
                .register(Trigger.attack("KILL"))
                .register(new Trigger("BOW_FIRE", Direction.ATTACK, false, true, false)) // shooter, no victim yet
                .register(Trigger.defense("DEFENSE"))
                .register(new Trigger("FALL", Direction.DEFENSE, false, true, false))
                .register(new Trigger("FIRE", Direction.DEFENSE, false, true, false))
                .register(Trigger.neutral("PASSIVE"))
                .register(Trigger.neutral("MINE"))
                .register(Trigger.neutral("DEATH"))
                .register(Trigger.held("HELD"))
                .register(Trigger.held("BREAK"))
                .register(Trigger.held("ITEM_DAMAGE"))
                .register(Trigger.held("EAT"))
                .register(Trigger.held("FISHING"))
                .register(Trigger.held("INTERACT"))
                .register(Trigger.held("INTERACT_LEFT"))
                .register(Trigger.held("INTERACT_RIGHT"))
                // §B timer-driven, fired by RepeatingDriver per ability period. Appended last to keep prior ids unshifted.
                .register(Trigger.neutral("REPEATING"))
                // §B command-driven, fired by CommandTriggerCommand. Appended after REPEATING to keep prior ids unshifted.
                .register(Trigger.neutral("COMMAND"))
                // Fired by a landing FALLING_BLOCK (FallingBlockListener); victim = whoever it hit. Appended last.
                .register(Trigger.attack("IMPACT"))
                // Fired by TriggerListeners.onExpChange on a PlayerExpChangeEvent; EXP_MULTIPLY scales it in place. Appended last.
                .register(Trigger.neutral("EXP_GAIN"))
                .build();
    }
}
