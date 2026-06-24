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
 * <p><strong>Sourced today</strong> (by {@code FactPopulator}, on the firing thread, Folia-guarded): the
 * actor's {@code health/maxhealth/healthpercent/food/level/totalexp/type}, pose flags
 * ({@code sneaking/blocking/flying/sprinting/swimming/gliding/onfire/onground}), {@code world/gamemode/helditem};
 * the victim's {@code health/maxhealth/healthpercent/food/type/helditem} and pose flags
 * ({@code victim.sneaking/blocking/flying/sprinting/swimming/gliding}); the combat {@code damage}; the broken
 * {@code block.type}/{@code isblock} (MINE); and the world {@code world.raining}/{@code world.thundering}/{@code world.time}.
 * Also sourced: {@code combo} — the activator's consecutive-hit streak, from the combat dispatch's
 * {@code ComboStore} (RAGE-style scaling); {@code distance} — actor↔victim distance in blocks; and
 * {@code nearbyenemies} — the count of other living entities within 8 blocks of the actor (GANK-style
 * scaling). The latter two are derived in {@code FactPopulator} on the firing thread (Folia-guarded; a
 * cross-region read defaults them to 0).
 *
 * <p>Slots are assigned per kind in registration order, so new facts are <strong>appended</strong> — never
 * reordered — or a previously-compiled condition's slot would drift from the populated buffer.
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
                .number("damage")  // sourced from the combat event payload (v3.1 §A)
                .number("combo")   // not yet sourced (no combo tracker) → reads 0
                // Appended in v3.1 §A — never reorder the above (compiled conditions bind these slots).
                .number("actor.healthpercent")
                .number("victim.healthpercent")
                .number("victim.food")
                .number("world.time")
                // Appended for the exotic-effect port — never reorder the above.
                .number("distance")        // actor↔victim distance in blocks (DAMAGE_DISTANCE-style scaling)
                .number("nearbyenemies")   // count of other living entities within 8 blocks (GANK-style scaling)
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
                // Appended in v3.1 §A — never reorder the above.
                .flag("onfire")
                .flag("onground")
                .flag("victim.sprinting")
                .flag("victim.swimming")
                .flag("victim.gliding")
                .flag("isblock")
                .flag("world.raining")
                .flag("world.thundering")
                // ── String facts ──
                .string("actor.world")
                .string("actor.gamemode")
                .string("actor.helditem")
                .string("victim.type")
                // Appended in v3.1 §A — never reorder the above.
                .string("actor.type")
                .string("victim.helditem")
                .string("block.type")
                .build();
    }
}
