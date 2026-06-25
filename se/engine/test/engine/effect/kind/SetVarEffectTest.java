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

/** Mock-host SET_VAR test: one setVar per resolved PLAYER target; non-players skipped. */
class SetVarEffectTest {

    @Test
    void setsTheVariableForEachPlayerTarget() {
        Player p = mock(Player.class);
        LivingEntity mob = mock(LivingEntity.class); // skipped, not a player
        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.str("name")).thenReturn("rage");
        when(ctx.str("value")).thenReturn("1");
        when(ctx.integer("ttl")).thenReturn(200);
        when(ctx.targets("who")).thenReturn(List.of(p, mob));

        Sink sink = mock(Sink.class);
        new SetVarEffect().run(ctx, sink);

        verify(sink).setVar(p, "rage", "1", 200);
        verifyNoMoreInteractions(sink);
    }
}
