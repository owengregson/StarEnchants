package engine.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import engine.condition.BuiltinVars;
import engine.condition.FactBuffer;
import engine.condition.VarVocabulary;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

/**
 * Unit-pins the runtime half of the condition variable system: mapping a live {@link ActivationContext}
 * to the dense {@link FactBuffer} slots the compiler lowered against. Slots are resolved from the SAME
 * {@link BuiltinVars} vocabulary the populator uses, so this also guards against name/kind drift between
 * the populator and the vocabulary. The Folia cross-region behaviour (a wrong-region read fails hard) is
 * pinned here against a synthetic {@link IllegalStateException}; the end-to-end gate is proven live in
 * {@code ConditionSuite}.
 */
class FactPopulatorTest {

    private static final VarVocabulary VOCAB = BuiltinVars.vocabulary();
    private static final int ACTOR_HEALTH = VOCAB.lookup("actor", "health").orElseThrow().slot();
    private static final int VICTIM_HEALTH = VOCAB.lookup("victim", "health").orElseThrow().slot();
    private static final int SNEAKING = VOCAB.lookup("", "sneaking").orElseThrow().slot();
    private static final int BLOCKING = VOCAB.lookup("", "blocking").orElseThrow().slot();
    private static final int FLYING = VOCAB.lookup("", "flying").orElseThrow().slot();

    private final FactPopulator populator = FactPopulator.builtin();

    @Test
    void populatesActorHealthAndPoseFlags() {
        Player actor = mock(Player.class);
        when(actor.getHealth()).thenReturn(15.0);
        when(actor.isSneaking()).thenReturn(true);
        when(actor.isBlocking()).thenReturn(false);
        when(actor.isFlying()).thenReturn(true);

        FactBuffer buf = populator.populate(new ActivationContext(actor, null, null, null));

        assertEquals(15.0, buf.number(ACTOR_HEALTH));
        assertTrue(buf.flag(SNEAKING));
        assertFalse(buf.flag(BLOCKING));
        assertTrue(buf.flag(FLYING));
    }

    @Test
    void populatesVictimHealth() {
        Player actor = mock(Player.class);
        LivingEntity victim = mock(LivingEntity.class);
        when(victim.getHealth()).thenReturn(7.5);

        FactBuffer buf = populator.populate(new ActivationContext(actor, victim, null, null));

        assertEquals(7.5, buf.number(VICTIM_HEALTH));
    }

    @Test
    void nullContextAndMissingEntitiesLeaveDefaults() {
        FactBuffer fromNull = populator.populate(null);
        assertEquals(0.0, fromNull.number(ACTOR_HEALTH));

        FactBuffer noEntities = populator.populate(new ActivationContext(null, null, null, null));
        assertEquals(0.0, noEntities.number(ACTOR_HEALTH));
        assertEquals(0.0, noEntities.number(VICTIM_HEALTH));
        assertFalse(noEntities.flag(SNEAKING));
    }

    @Test
    void crossRegionActorReadIsGuardedAndDefaults() {
        // Folia fails a cross-region access with IllegalStateException (e.g. a projectile shooter on the
        // ATTACK pass). Population must swallow it and leave the actor facts defaulted, not abort the hit.
        Player actor = mock(Player.class);
        when(actor.getHealth()).thenThrow(new IllegalStateException("Accessing entity from wrong region"));

        FactBuffer buf = populator.populate(new ActivationContext(actor, null, null, null));

        assertEquals(0.0, buf.number(ACTOR_HEALTH));
        assertFalse(buf.flag(SNEAKING)); // the throw short-circuits the remaining actor reads
    }

    @Test
    void guardCoversTheWholeActorBlockNotJustItsFirstRead() {
        // A throw on a LATER actor read (isFlying, the last one) must not lose the facts read before it
        // and must not propagate — proving the entire actor block is guarded, not only its first statement.
        Player actor = mock(Player.class);
        when(actor.getHealth()).thenReturn(12.0);
        when(actor.isSneaking()).thenReturn(true);
        when(actor.isBlocking()).thenReturn(true);
        when(actor.isFlying()).thenThrow(new IllegalStateException("Accessing entity from wrong region"));

        FactBuffer buf = populator.populate(new ActivationContext(actor, null, null, null));

        assertEquals(12.0, buf.number(ACTOR_HEALTH));
        assertTrue(buf.flag(SNEAKING));
        assertTrue(buf.flag(BLOCKING));
        assertFalse(buf.flag(FLYING)); // the throwing read leaves this defaulted
    }

    @Test
    void crossRegionVictimReadIsGuardedAndDefaults() {
        Player actor = mock(Player.class);
        LivingEntity victim = mock(LivingEntity.class);
        when(victim.getHealth()).thenThrow(new IllegalStateException("Accessing entity from wrong region"));

        FactBuffer buf = populator.populate(new ActivationContext(actor, victim, null, null));

        assertEquals(0.0, buf.number(VICTIM_HEALTH));
    }
}
