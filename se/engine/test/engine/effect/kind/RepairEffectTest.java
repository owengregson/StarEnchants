package engine.effect.kind;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import engine.effect.EffectCtx;
import engine.sink.Sink;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Mock-host test (docs/architecture.md §1.3): a mocked {@link EffectCtx} feeds typed
 * args + resolved targets, and a mocked {@link Sink} records the emitted intents — so
 * the effect's behavior is verified with no server. Mirrors {@code DamageEffectTest}.
 */
class RepairEffectTest {

    @Test
    void emitsOneRepairIntentPerResolvedPlayer() {
        Player a = mock(Player.class);
        Player b = mock(Player.class);

        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.integer("amount")).thenReturn(-1);
        when(ctx.targets("who")).thenReturn(List.of(a, b));

        Sink sink = mock(Sink.class);
        new RepairEffect().run(ctx, sink);

        verify(sink).repairHand(a, -1);
        verify(sink).repairHand(b, -1);
        verifyNoMoreInteractions(sink);
    }

    @Test
    void skipsNonPlayerTargets() {
        LivingEntity beast = mock(LivingEntity.class);

        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.integer("amount")).thenReturn(40);
        when(ctx.targets("who")).thenReturn(List.of(beast));

        Sink sink = mock(Sink.class);
        new RepairEffect().run(ctx, sink);

        verifyNoMoreInteractions(sink);
    }
}
