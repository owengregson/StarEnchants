package engine.condition;

/**
 * The greppable vocabulary of built-in condition variables (docs/architecture.md §3.4). Variables are
 * written {@code %scope.name%}; {@code actor} is the activating player, {@code victim} the combat target,
 * a bare name (e.g. {@code %sneaking%}) the activator.
 *
 * <p>Slots are assigned per kind in registration order, so new facts must be <strong>appended</strong> —
 * reordering drifts a previously-compiled condition's slot from the populated buffer.
 */
public final class BuiltinVars {

    private BuiltinVars() {
    }

    public static VarVocabulary vocabulary() {
        return VarVocabulary.builder()
                .number("actor.health")
                .number("victim.health")
                .number("actor.maxhealth")
                .number("victim.maxhealth")
                .number("actor.food")
                .number("actor.level")
                .number("actor.totalexp")
                .number("damage")
                .number("combo")   // unsourced until a combo tracker exists → reads 0
                .number("actor.healthpercent")
                .number("victim.healthpercent")
                .number("victim.food")
                .number("world.time")
                .number("distance")        // actor↔victim distance in blocks
                .number("nearbyenemies")   // living entities within 8 blocks of the actor
                .flag("sneaking")
                .flag("blocking")
                .flag("flying")
                .flag("sprinting")
                .flag("swimming")
                .flag("gliding")
                .flag("victim.sneaking")   // victim flags meaningful only when the victim is a player
                .flag("victim.blocking")
                .flag("victim.flying")
                .flag("onfire")
                .flag("onground")
                .flag("victim.sprinting")
                .flag("victim.swimming")
                .flag("victim.gliding")
                .flag("isblock")
                .flag("world.raining")
                .flag("world.thundering")
                .flag("victim.inzone")     // victim stands in an actor-owned MARK_ZONE (devil hellfire)
                .string("actor.world")
                .string("actor.gamemode")
                .string("actor.helditem")
                .string("victim.type")
                .string("actor.type")
                .string("victim.helditem")
                .string("block.type")
                .string("victim.mobtype")  // MythicMobs, via a soft hook (§N)
                .string("actor.groundblock") // Material name of the block beneath the actor's feet (e.g. %actor.groundblock% contains "ICE")
                .build();
    }
}
