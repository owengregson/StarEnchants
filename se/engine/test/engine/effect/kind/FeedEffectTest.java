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
 * the effect's behavior is verified with no server.
 */
class FeedEffectTest {

    @Test
    void feedsOnlyPlayerTargetsAndSkipsNonPlayers() {
        Player a = mock(Player.class);
        LivingEntity mob = mock(LivingEntity.class);
        Player b = mock(Player.class);

        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.integer("amount")).thenReturn(6);
        when(ctx.targets("who")).thenReturn(List.of(a, mob, b));

        Sink sink = mock(Sink.class);
        new FeedEffect().run(ctx, sink);

        verify(sink).feed(a, 6);
        verify(sink).feed(b, 6);
        verifyNoMoreInteractions(sink);
    }
}
