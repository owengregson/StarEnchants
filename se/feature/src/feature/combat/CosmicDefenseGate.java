package feature.combat;

import engine.sink.SinkEnv;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

/** Shared routing rules from Cosmic's EListener defensive pass. */
final class CosmicDefenseGate {

    private CosmicDefenseGate() {
    }

    static boolean sourceCombatCause(EntityDamageEvent.DamageCause cause) {
        return cause == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || cause == EntityDamageEvent.DamageCause.PROJECTILE;
    }

    static boolean silenced(Player wearer, SinkEnv env) {
        return env.stores().suppression().defenseSuppressed(
                wearer.getUniqueId(), env.nowTicks().getAsLong());
    }
}
