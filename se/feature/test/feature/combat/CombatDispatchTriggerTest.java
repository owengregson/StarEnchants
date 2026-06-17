package feature.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Trident;
import org.junit.jupiter.api.Test;

/**
 * Pins {@link CombatDispatch#attackTrigger}: which attacker-side trigger a hit fires, by the raw damager
 * type. A bow arrow fires BOW, a thrown trident fires TRIDENT, and melee (or any projectile with no
 * distinct trigger) fires ATTACK — the EE model where ATTACK is melee-only. Staging real projectile hits
 * with the fake-player harness is impractical, so the decision is pinned here; the dispatch path itself is
 * the one CombatSuite already proves for melee ATTACK.
 */
class CombatDispatchTriggerTest {

    private static final int ATTACK = 0;
    private static final int BOW = 1;
    private static final int TRIDENT = 2;

    @Test
    void thrownTridentFiresTheTridentTrigger() {
        assertEquals(TRIDENT, CombatDispatch.attackTrigger(mock(Trident.class), ATTACK, BOW, TRIDENT));
    }

    @Test
    void bowArrowFiresTheBowTrigger() {
        assertEquals(BOW, CombatDispatch.attackTrigger(mock(Arrow.class), ATTACK, BOW, TRIDENT));
    }

    @Test
    void meleeFiresTheAttackTrigger() {
        assertEquals(ATTACK, CombatDispatch.attackTrigger(mock(Player.class), ATTACK, BOW, TRIDENT));
    }

    @Test
    void nonArrowProjectileFiresTheAttackTrigger() {
        assertEquals(ATTACK, CombatDispatch.attackTrigger(mock(Snowball.class), ATTACK, BOW, TRIDENT));
    }

    @Test
    void projectilesFallBackToAttackWhenTheDistinctTriggersAreUnwired() {
        assertEquals(ATTACK, CombatDispatch.attackTrigger(mock(Arrow.class), ATTACK, -1, -1));
        assertEquals(ATTACK, CombatDispatch.attackTrigger(mock(Trident.class), ATTACK, -1, -1));
    }
}
