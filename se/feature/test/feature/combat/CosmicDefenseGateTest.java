package feature.combat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.Test;

final class CosmicDefenseGateTest {

    @Test
    void sourceCombatPassAcceptsOnlyMeleeAndProjectileCauses() {
        assertTrue(CosmicDefenseGate.sourceCombatCause(EntityDamageEvent.DamageCause.ENTITY_ATTACK));
        assertTrue(CosmicDefenseGate.sourceCombatCause(EntityDamageEvent.DamageCause.PROJECTILE));

        for (EntityDamageEvent.DamageCause cause : EntityDamageEvent.DamageCause.values()) {
            if (cause != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                    && cause != EntityDamageEvent.DamageCause.PROJECTILE) {
                assertFalse(CosmicDefenseGate.sourceCombatCause(cause), cause.name());
            }
        }
    }
}
