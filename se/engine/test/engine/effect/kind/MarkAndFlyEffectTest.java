package engine.effect.kind;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import engine.sink.Sink;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import testfx.FakeEffectCtx;

/** MARK and FLY_MODE forwarding contracts (the registry maths/Sink bodies are exercised separately/live). */
class MarkAndFlyEffectTest {

    @Test
    void markStampsTheVictimWithTheActorAsMarker() {
        Player actor = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(actor.getUniqueId()).thenReturn(id);
        LivingEntity victim = mock(LivingEntity.class);
        FakeEffectCtx ctx = FakeEffectCtx.create()
                .with("amount", 25.0).with("duration", 60).actor(actor).targets("who", victim);
        Sink sink = mock(Sink.class);

        new MarkEffect().run(ctx, sink);

        verify(sink).mark(victim, id, 25.0, 60); // distinct values pin the arg order + the actor-as-marker
    }

    @Test
    void markWithoutAnActorIsANoOp() {
        FakeEffectCtx ctx = FakeEffectCtx.create()
                .with("amount", 25.0).with("duration", 60).targets("who", mock(LivingEntity.class));
        Sink sink = mock(Sink.class);
        new MarkEffect().run(ctx, sink);
        verifyNoInteractions(sink);
    }

    @Test
    void markZoneStampsAZoneAtTheTargetOwnedByTheActor() {
        Player actor = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(actor.getUniqueId()).thenReturn(id);
        LivingEntity victim = mock(LivingEntity.class);
        Location center = new Location(mock(World.class), 4, 64, -2); // real Location (getX/Y/Z are final)
        when(victim.getLocation()).thenReturn(center);
        FakeEffectCtx ctx = FakeEffectCtx.create()
                .with("radius", 4.5).with("duration", 100).actor(actor).targets("who", victim);
        Sink sink = mock(Sink.class);

        new MarkZoneEffect().run(ctx, sink);

        verify(sink).markZone(center, id, 4.5, 100); // distinct values pin the arg order + the actor-as-owner
    }

    @Test
    void markZoneWithoutAnActorIsANoOp() {
        FakeEffectCtx ctx = FakeEffectCtx.create()
                .with("radius", 4.5).with("duration", 100).targets("who", mock(LivingEntity.class));
        Sink sink = mock(Sink.class);
        new MarkZoneEffect().run(ctx, sink);
        verifyNoInteractions(sink);
    }

    @Test
    void flyModeGrantsOutOfCombatAndStopRevokes() {
        Player p = mock(Player.class);
        when(p.getUniqueId()).thenReturn(UUID.randomUUID()); // fresh id → never combat-tagged → out of combat
        FakeEffectCtx ctx = FakeEffectCtx.create().targets("who", p);
        Sink sink = mock(Sink.class);

        new FlyModeEffect().run(ctx, sink);
        verify(sink).flyMode(p, true); // out of combat → allow flight

        new FlyModeEffect().stop(ctx, sink);
        verify(sink).flyMode(p, false); // unequip → revoke
    }
}
