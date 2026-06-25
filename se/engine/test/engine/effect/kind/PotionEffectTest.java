package engine.effect.kind;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import engine.effect.EffectCtx;
import engine.sink.Sink;
import org.bukkit.entity.LivingEntity;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Mock-host test (docs/architecture.md §1.3): the {@code effect} handle arrives as the interned id the
 * compiler resolved (§9), read via {@code ctx.integer}.
 */
class PotionEffectTest {

    @Test
    void emitsOnePotionIntentPerResolvedTarget() {
        LivingEntity self = mock(LivingEntity.class);

        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.integer("effect")).thenReturn(7);
        when(ctx.integer("level")).thenReturn(2); // §C: authored 1-based level…
        when(ctx.integer("duration")).thenReturn(100);
        when(ctx.targets("who")).thenReturn(List.of(self));

        Sink sink = mock(Sink.class);
        new PotionEffect().run(ctx, sink);

        verify(sink).potion(self, 7, 1, 100); // …reaches the Sink as the 0-based Bukkit amplifier (level − 1)
        verifyNoMoreInteractions(sink);
    }

    /**
     * §B lifecycle teardown (ADR-0022): on HELD/PASSIVE unequip, {@code stop} must emit the exact inverse —
     * a {@code removePotion} of the same handle for every target, and nothing else.
     */
    @Test
    void stopRemovesThePotionItApplied() {
        LivingEntity a = mock(LivingEntity.class);
        LivingEntity b = mock(LivingEntity.class);

        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.integer("effect")).thenReturn(7);
        when(ctx.targets("who")).thenReturn(List.of(a, b));

        Sink sink = mock(Sink.class);
        new PotionEffect().stop(ctx, sink);

        verify(sink).removePotion(a, 7);
        verify(sink).removePotion(b, 7);
        verifyNoMoreInteractions(sink); // never re-applies, never reads amplifier/duration
    }
}
