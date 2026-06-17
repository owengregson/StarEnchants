package engine.condition;

/**
 * The explicit, greppable vocabulary of built-in condition variables (docs/architecture.md §3.4;
 * v3.1 §A). Each entry declares a {@code %scope.name%} fact and its type; the dense {@link FactBuffer}
 * slot is assigned automatically (per kind, in registration order).
 *
 * <p>Variables are written {@code %scope.name%} in conditions; {@code actor} is the activating player
 * and {@code victim} the combat target, while a bare name (e.g. {@code %sneaking%}) is the activator.
 *
 * <p>This factory is the single source of truth for the slot assignment: the compiler lowers conditions
 * against {@link #vocabulary()}{@code .asResolver()} and the runtime {@code engine.run.FactPopulator}
 * reads the same {@link #vocabulary()}, so a compiled condition's slot and the populated buffer agree by
 * construction. {@code FactPopulator} owns the runtime extraction for each name listed here (a drift
 * guard test pins that every populated fact resolves to a slot declared here).
 *
 * <p><strong>Sourced today</strong> (by {@code FactPopulator}, on the firing thread, Folia-guarded):
 * the actor's {@code health/maxhealth/food/level/totalexp}, pose flags
 * ({@code sneaking/blocking/flying/sprinting/swimming/gliding}), {@code world/gamemode/helditem}; and the
 * victim's {@code health/maxhealth/type} and pose flags. <strong>Declared but not yet sourced</strong>
 * (read 0/"" until a later increment wires their context — no shipped content depends on them):
 * {@code damage} and {@code combo} (need the combat context threaded), and global-region facts
 * (weather/time) and per-block facts (block-type) are intentionally not declared until there is a
 * Folia-safe source for them.
 */
public final class BuiltinVars {

    private BuiltinVars() {
    }

    /** The vocabulary of all built-in condition variables. */
    public static VarVocabulary vocabulary() {
        return VarVocabulary.builder()
                // ── Numeric facts ──
                .number("actor.health")
                .number("victim.health")
                .number("actor.maxhealth")
                .number("victim.maxhealth")
                .number("actor.food")
                .number("actor.level")
                .number("actor.totalexp")
                .number("damage")  // not yet sourced (combat context) → reads 0
                .number("combo")   // not yet sourced (no combo tracker) → reads 0
                // ── Boolean flags ──
                // The activator's pose/state (bare names).
                .flag("sneaking")
                .flag("blocking")
                .flag("flying")
                .flag("sprinting")
                .flag("swimming")
                .flag("gliding")
                // The victim's pose/state (only meaningful when the victim is a player).
                .flag("victim.sneaking")
                .flag("victim.blocking")
                .flag("victim.flying")
                // ── String facts ──
                .string("actor.world")
                .string("actor.gamemode")
                .string("actor.helditem")
                .string("victim.type")
                .build();
    }
}
