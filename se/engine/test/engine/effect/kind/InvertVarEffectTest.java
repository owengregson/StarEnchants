package engine.effect.kind;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import engine.effect.EffectCtx;
import engine.sink.Sink;
import java.util.List;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

/**
 * Mock-host test for {@code INVERT_VAR}: it emits one {@code invertVar} per resolved PLAYER target, skipping
 * non-players.
 */
class InvertVarEffectTest {

    @Test
    void invertsTheVariableForEachPlayerTarget() {
        Player p = mock(Player.class);
        LivingEntity mob = mock(LivingEntity.class);
        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.str("name")).thenReturn("flag");
        when(ctx.targets("who")).thenReturn(List.of(p, mob));

        Sink sink = mock(Sink.class);
        new InvertVarEffect().run(ctx, sink);

        verify(sink).invertVar(p, "flag");
        verifyNoMoreInteractions(sink);
    }
}
