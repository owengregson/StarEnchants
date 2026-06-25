package engine.effect.kind;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import engine.effect.EffectCtx;
import engine.sink.Sink;
import java.util.List;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class RemoveItemEffectTest {

    @Test
    void emitsRemoveItemForPlayerTarget() {
        Player player = mock(Player.class);

        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.integer("material")).thenReturn(9);
        when(ctx.integer("count")).thenReturn(5);
        when(ctx.targets("who")).thenReturn(List.of(player));

        Sink sink = mock(Sink.class);
        new RemoveItemEffect().run(ctx, sink);

        verify(sink).removeItem(player, 9, 5);
        verifyNoMoreInteractions(sink);
    }
}
