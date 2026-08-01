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
                .number("finaldamage")
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
                .string("actor.environment")
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
                .number("actor.belowvictim")
                .number("projectilemark")  // generic BOW_FIRE→impact integer carrier; 0 for non-projectile/unmarked
                .number("projectileheight") // projectile Y minus victim feet Y at impact; 0 otherwise
                .flag("victim.rageaffected") // victim was stamped by a Rage multiplier within the Cosmic 200 ms window
                .flag("victim.devouraffected") // victim was stamped by Devour within the Cosmic 200 ms window
                .number("victim.bleedstacks") // victim's shared 0..20 Cosmic Bleed stack count
                .flag("victim.necromancermask") // victim currently wears a Necromancer mask component
                .number("victim.metaphysical") // victim's highest worn Metaphysical enchant level
                .number("actor.walkspeed") // current Bukkit walk speed, used by Trap's re-entry guard
                .number("victim.dragonslayer") // 1 when victim currently has the Dragon Slayer set active
                .number("victim.sticky") // victim's highest worn Sticky enchant level
                .number("victim.poltergeist") // victim's highest worn Poltergeist mastery level
                .flag("victim.slowed") // victim currently has the SLOW potion type
                .number("victim.isplayer") // 1 for a player victim, otherwise 0
                .flag("actor.heldsettled5") // Cosmic anti-hot-swap gate: current tick - held change > 5
                .flag("victim.boss") // victim carries the boss marker consumed by Boss Slayer
                .number("actor.falldistance") // current fall distance; used by Ethereal Dodge's >5 message gate
                .flag("victim.frommobspawner") // Bukkit spawn-origin marker maintained by SpawnerOriginListener
                .flag("victim.induel") // Cosmic duel marker consumed by Obliterate
                .number("antigankattackers") // distinct attackers in Anti Gank's exact 120-tick window
                .number("aegisattackerindex") // current attacker's index in Aegis's exact 100-tick window
                .number("victim.walkspeed") // current player victim walk speed; corrected Trap/Titan Trap re-entry guard
                .number("victim.polymorphicmetaphysical") // heroic Metaphysical level; independently blocks Ice Aspect
                .build();
    }
}
