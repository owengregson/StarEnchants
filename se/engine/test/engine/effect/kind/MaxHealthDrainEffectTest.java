package engine.effect.kind;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import engine.sink.Sink;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.Test;
import testfx.FakeEffectCtx;

/** MAX_HEALTH_DRAIN forwards its parameters (the drain maths live in the Sink and are exercised live). */
class MaxHealthDrainEffectTest {

    @Test
    void forwardsTheDistinctParametersPerVictim() {
        LivingEntity victim = mock(LivingEntity.class);
        FakeEffectCtx ctx = FakeEffectCtx.create()
                .with("fraction", 0.5).with("baseline", 20.0).with("amount", 3.0).with("duration", 60)
                .targets("who", victim);
        Sink sink = mock(Sink.class);

        new MaxHealthDrainEffect().run(ctx, sink);

        verify(sink).drainMaxHealth(victim, 0.5, 20.0, 3.0, 60); // distinct values pin the argument order
    }
}
