package engine.effect.kind;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import engine.effect.EffectCtx;
import engine.sink.Sink;
import org.bukkit.entity.Player;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Mock-host effect test (docs/architecture.md §1.3): mocked EffectCtx in, Sink intents verified. */
class FlyEffectTest {

    @Test
    void emitsOneSetFlightIntentPerResolvedPlayer() {
        Player a = mock(Player.class);
        Player b = mock(Player.class);

        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.integer("ticks")).thenReturn(200);
        when(ctx.targets("who")).thenReturn(List.of(a, b));

        Sink sink = mock(Sink.class);
        new FlyEffect().run(ctx, sink);

        verify(sink).setFlight(a, 200);
        verify(sink).setFlight(b, 200);
        verifyNoMoreInteractions(sink);
    }
}
