package engine.effect.kind;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import engine.effect.EffectCtx;
import engine.sink.Sink;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Mock-host test (docs/architecture.md §1.3): a mocked {@link EffectCtx} feeds the
 * typed strength, the resolved targets, and the actor's location, and a mocked
 * {@link Sink} records the emitted intents — so the effect's behavior is verified
 * with no server. Mirrors {@code DamageEffectTest}.
 */
class KnockbackEffectTest {

    @Test
    void emitsOneKnockbackIntentPerResolvedTargetFromActorLocation() {
        LivingEntity a = mock(LivingEntity.class);
        LivingEntity b = mock(LivingEntity.class);

        Player actor = mock(Player.class);
        Location loc = mock(Location.class);
        when(actor.getLocation()).thenReturn(loc);

        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.dbl("strength")).thenReturn(1.5);
        when(ctx.targets("who")).thenReturn(List.of(a, b));
        when(ctx.actor()).thenReturn(actor);

        Sink sink = mock(Sink.class);
        new KnockbackEffect().run(ctx, sink);

        verify(sink).knockback(a, loc, 1.5);
        verify(sink).knockback(b, loc, 1.5);
        verifyNoMoreInteractions(sink);
    }
}
