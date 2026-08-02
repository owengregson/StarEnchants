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
                // ADR-0049 gank/reflect/item-damage facts — appended (slots are append-only per §3.4).
                .number("recentattackers")   // distinct attackers that hit the actor within the recent gank window
                .number("attackerindex")     // 1-based first-seen order of THIS attacker among the actor's recent attackers (defense side); 0 if absent
                .flag("actor.behindvictim")  // the actor is behind the victim's body facing (dot < 0); false with no victim
                .string("damagecause")       // Bukkit DamageCause name of the triggering damage event; empty for non-damage
                .flag("itemdamage.armor")    // ITEM_DAMAGE: the damaged item is a worn ARMOR piece (vs the held item)
                .number("ragestacks")        // the actor's live Rage stacks (min(combo streak, rage level)), sourced from RageStackStore
                // ADR-0052 pet posture fact — appended (slots are append-only per §3.4).
                .number("actor.belowvictim") // blocks the actor's feet sit BELOW the victim's (negative = above;
                                             // 0 with no victim) — Eagle authors its threshold: %actor.belowvictim% > 1.5
                // Wave 1b.2 relation facts — appended (slots are append-only per §3.4). Both read the ONE
                // installed alliance predicate (engine.selector.kind.Allies), the same one the ENEMIES/ALLIES
                // area filters and the friendly-fire gate consult, so "ally" means one thing everywhere.
                .number("nearbyallies")     // allied PLAYERS within the %nearbyenemies% radius (self excluded)
                .string("victim.relation")  // ALLY | ENEMY | NEUTRAL (non-player victim); empty with no victim
                // Wave 1b.3 facts — appended (slots are append-only per §3.4).
                // The actor's health once the incoming hit lands, priced as the SERVER prices it: current health
                // minus the event's vanilla-final damage (post-armor/protection/resistance) but BEFORE the SE
                // damage fold. Excluding the fold is deliberate — the fold's contributions come largely from the
                // death-save abilities this fact gates, so folding it in would make the fact depend on its own
                // consumers. DEFENSE-side only; 0 elsewhere.
                .number("posthit.health")
                // The victim came out of a mob spawner — the grinder test a loot/soul bonus gates on. Always
                // false on 1.8, which keeps no spawn provenance (docs/legacy-1.8.9-codeshare-design.md §3.3).
                .flag("victim.fromspawner")
                .build();
    }
}
