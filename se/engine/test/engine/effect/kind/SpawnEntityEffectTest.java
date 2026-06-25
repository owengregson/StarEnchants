package engine.effect.kind;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import engine.effect.EffectCtx;
import engine.sink.Sink;
import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

/** Mock-host SPAWN_ENTITY test: spawns at each target's location, falling back to the activation location when none resolve. */
class SpawnEntityEffectTest {

    @Test
    void spawnsAtEachTargetLocation() {
        Location loc = mock(Location.class);
        LivingEntity who = mock(LivingEntity.class);
        when(who.getLocation()).thenReturn(loc);

        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.integer("type")).thenReturn(5);
        when(ctx.integer("count")).thenReturn(3);
        when(ctx.integer("ttl")).thenReturn(0);
        when(ctx.dbl("health")).thenReturn(20.0);
        when(ctx.targets("who")).thenReturn(List.of(who));

        Sink sink = mock(Sink.class);
        new SpawnEntityEffect().run(ctx, sink);

        verify(sink).spawnEntity(loc, 5, 3, 0, 20.0, null);
        verifyNoMoreInteractions(sink);
    }

    @Test
    void ownerActivatorTamesToTheActor() {
        Location loc = mock(Location.class);
        LivingEntity who = mock(LivingEntity.class);
        when(who.getLocation()).thenReturn(loc);
        UUID actorId = UUID.randomUUID();
        Player actor = mock(Player.class);
        when(actor.getUniqueId()).thenReturn(actorId);

        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.integer("type")).thenReturn(9);
        when(ctx.integer("count")).thenReturn(1);
        when(ctx.integer("ttl")).thenReturn(0);
        when(ctx.dbl("health")).thenReturn(0.0);
        when(ctx.str("owner")).thenReturn("activator");
        when(ctx.actor()).thenReturn(actor);
        when(ctx.targets("who")).thenReturn(List.of(who));

        Sink sink = mock(Sink.class);
        new SpawnEntityEffect().run(ctx, sink);

        verify(sink).spawnEntity(loc, 9, 1, 0, 0.0, actorId);
        verifyNoMoreInteractions(sink);
    }

    @Test
    void fallsBackToActivationLocationWhenNoTargets() {
        Location loc = mock(Location.class);
        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.integer("type")).thenReturn(7);
        when(ctx.integer("count")).thenReturn(1);
        when(ctx.integer("ttl")).thenReturn(200);
        when(ctx.dbl("health")).thenReturn(0.0);
        when(ctx.targets("who")).thenReturn(List.of());
        when(ctx.location()).thenReturn(loc);

        Sink sink = mock(Sink.class);
        new SpawnEntityEffect().run(ctx, sink);

        verify(sink).spawnEntity(loc, 7, 1, 200, 0.0, null);
        verifyNoMoreInteractions(sink);
    }
}
