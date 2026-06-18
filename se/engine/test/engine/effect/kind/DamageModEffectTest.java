package engine.effect.kind;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import engine.effect.EffectCtx;
import engine.sink.Sink;
import org.junit.jupiter.api.Test;

/**
 * Mock-host test for the canonical {@code DAMAGE_MOD} (which replaced ADD_DAMAGE/REDUCE_DAMAGE/
 * FLAT_DAMAGE/FLAT_REDUCE): each (side, mode) pair routes to the SAME fold method as its old kind,
 * so the additive damage fold is touched byte-identically. Percent modes divide by 100.
 */
class DamageModEffectTest {

    private static EffectCtx ctx(String side, String mode, double amount) {
        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.str("side")).thenReturn(side);
        when(ctx.str("mode")).thenReturn(mode);
        when(ctx.dbl("amount")).thenReturn(amount);
        return ctx;
    }

    @Test
    void attackAddIsOutgoingPercent() {
        Sink sink = mock(Sink.class);
        new DamageModEffect().run(ctx("attack", "add", 25.0), sink);
        verify(sink).addOutgoingDamage(0.25);
        verifyNoMoreInteractions(sink);
    }

    @Test
    void defenseAddIsReductionPercent() {
        Sink sink = mock(Sink.class);
        new DamageModEffect().run(ctx("defense", "add", 15.0), sink);
        verify(sink).addDamageReduction(0.15);
        verifyNoMoreInteractions(sink);
    }

    @Test
    void attackFlatIsFlatDamage() {
        Sink sink = mock(Sink.class);
        new DamageModEffect().run(ctx("attack", "flat", 2.0), sink);
        verify(sink).addFlatDamage(2.0);
        verifyNoMoreInteractions(sink);
    }

    @Test
    void defenseFlatIsFlatReduction() {
        Sink sink = mock(Sink.class);
        new DamageModEffect().run(ctx("defense", "flat", 3.0), sink);
        verify(sink).addFlatReduction(3.0);
        verifyNoMoreInteractions(sink);
    }
}
