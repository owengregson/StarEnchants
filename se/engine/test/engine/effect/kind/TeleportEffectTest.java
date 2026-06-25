package engine.effect.kind;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import engine.effect.EffectCtx;
import engine.sink.Sink;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class TeleportEffectTest {

    @Test
    void teleportsEachTargetToTheVictimByDefault() {
        Player actor = mock(Player.class);
        LivingEntity victim = mock(LivingEntity.class);
        Location victimLoc = mock(Location.class);
        when(victim.getLocation()).thenReturn(victimLoc);
        LivingEntity mover = mock(LivingEntity.class);

        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.str("to")).thenReturn("VICTIM");
        when(ctx.actor()).thenReturn(actor);
        when(ctx.victim()).thenReturn(victim);
        when(ctx.targets("who")).thenReturn(List.of(mover));

        Sink sink = mock(Sink.class);
        new TeleportEffect().run(ctx, sink);

        verify(sink).teleport(mover, victimLoc);
        verifyNoMoreInteractions(sink);
    }

    @Test
    void teleportsToTheActorWhenRequested() {
        Player actor = mock(Player.class);
        Location actorLoc = mock(Location.class);
        when(actor.getLocation()).thenReturn(actorLoc);
        LivingEntity mover = mock(LivingEntity.class);

        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.str("to")).thenReturn("ACTOR");
        when(ctx.actor()).thenReturn(actor);
        when(ctx.targets("who")).thenReturn(List.of(mover));

        Sink sink = mock(Sink.class);
        new TeleportEffect().run(ctx, sink);

        verify(sink).teleport(mover, actorLoc);
        verifyNoMoreInteractions(sink);
    }

    @Test
    void noOpWhenTheDestinationPartyIsAbsent() {
        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.str("to")).thenReturn("VICTIM");
        when(ctx.victim()).thenReturn(null); // non-combat activation — no victim

        Sink sink = mock(Sink.class);
        new TeleportEffect().run(ctx, sink);

        verifyNoInteractions(sink);
    }
}
