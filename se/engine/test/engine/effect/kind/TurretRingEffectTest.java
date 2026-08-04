package engine.effect.kind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import engine.sink.FieldCue;
import engine.sink.Sink;
import engine.sink.TurretRingProfile;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import testfx.FakeEffectCtx;
import testfx.SpecDrivenCtx;

/**
 * TURRET_RING's activation-side contract: one ring intent per origin, the two cues assembled independently of
 * each other, and the profile carrying the authored numbers into the slots the sink reads them from. Everything
 * the ring DOES — the ground scan, the per-site protection query, the volleys — is the sink's, and the pure
 * arithmetic it runs on is pinned in {@code FieldProfileTest}.
 */
class TurretRingEffectTest {

    /** A ring with neither cue's handles authored, so the cue assembly starts from nothing. */
    private static FakeEffectCtx ring() {
        return FakeEffectCtx.create()
                .with("type", 3).with("count", 5).with("ring-radius", 8.0).with("ttl", 300)
                .with("acquire-range", 11.0).with("initial-delay", 30)
                .with("period-min", 8).with("period-max", 13)
                .with("filter", "ENEMIES").with("projectile", 4).with("projectile-speed", 0.065)
                .with("spawn-volume", 3.0).with("spawn-pitch", 0.9).with("spawn-particle-count", 24)
                .with("spawn-particle-spread", 0.25).with("spawn-lightning", true)
                .with("despawn-volume", 0.5).with("despawn-pitch", 1.4).with("despawn-particle-count", 16)
                .with("despawn-particle-spread", 0.75);
    }

    private static LivingEntity originAt(World world, int x, int z) {
        LivingEntity who = mock(LivingEntity.class);
        when(who.getLocation()).thenReturn(new Location(world, x, 64, z));
        return who;
    }

    @Test
    void eachCuesHandlesAndSpreadStayWithTheirOwnPhase() {
        World world = mock(World.class);
        Sink sink = mock(Sink.class);
        FakeEffectCtx ctx = ring()
                .with("spawn-sound", 7).with("spawn-particle", 9)
                .with("despawn-sound", 11).with("despawn-particle", 13)
                .targets("who", originAt(world, 0, 0));

        new TurretRingEffect().run(ctx, sink);

        // Every term distinct, so a transposed volume/pitch/spread or a swapped phase cannot pass.
        verify(sink).turretRing(any(Location.class), isNull(), any(TurretRingProfile.class),
                eq(new FieldCue(7, 3.0f, 0.9f, 9, 24)), eq(0.25), eq(true),
                eq(new FieldCue(11, 0.5f, 1.4f, 13, 16)), eq(0.75), eq(-1));
    }

    @Test
    void anAbsentCueHandleIsSilenceRatherThanHandleZero() {
        World world = mock(World.class);
        Sink sink = mock(Sink.class);

        new TurretRingEffect().run(ring().targets("who", originAt(world, 0, 0)), sink);

        // Interned ids start at 0, so a "no cue" that fell through as 0 would play whichever sound got id 0.
        verify(sink).turretRing(any(), isNull(), any(), eq(FieldCue.SILENT), anyDouble(), anyBoolean(),
                eq(FieldCue.SILENT), anyDouble(), anyInt());
    }

    @Test
    void theProfileCarriesEveryAuthoredNumberIntoItsOwnSlot() {
        World world = mock(World.class);
        Sink sink = mock(Sink.class);
        ArgumentCaptor<TurretRingProfile> profile = ArgumentCaptor.forClass(TurretRingProfile.class);

        new TurretRingEffect().run(ring().targets("who", originAt(world, 0, 0)), sink);

        verify(sink).turretRing(any(), isNull(), profile.capture(), any(), anyDouble(), anyBoolean(),
                any(), anyDouble(), anyInt());
        // The two handle slots differ, as do the two tick ranges — a transposed pair here would arm the wrong
        // entity or the wrong beat, and every value below is distinct precisely so that fails.
        assertEquals(new TurretRingProfile(3, 5, 8.0, 300, 11.0, 30, 8, 13, 4, 0.065, "ENEMIES"),
                profile.getValue());
    }

    @Test
    void everyOriginGetsItsOwnRing() {
        World world = mock(World.class);
        Sink sink = mock(Sink.class);
        FakeEffectCtx ctx = ring().targets("who", originAt(world, 0, 0), originAt(world, 40, 40));

        new TurretRingEffect().run(ctx, sink);

        verify(sink, times(2)).turretRing(any(), isNull(), any(), any(), anyDouble(), anyBoolean(),
                any(), anyDouble(), anyInt());
    }

    @Test
    void theDeclaredDefaultsStillExpressAStandingRingThatArmsBeforeItFires() {
        // Driven from the kind's OWN spec defaults: what defines the ability is not the raw params but the
        // relations they produce — a ring that really is a ring, a beat that can vary, and a real arming pause.
        World world = mock(World.class);
        Sink sink = mock(Sink.class);
        TurretRingEffect kind = new TurretRingEffect();
        FakeEffectCtx ctx = SpecDrivenCtx.defaults(kind.spec()).targets("who", originAt(world, 0, 0));
        ArgumentCaptor<TurretRingProfile> profile = ArgumentCaptor.forClass(TurretRingProfile.class);

        kind.run(ctx, sink);

        verify(sink).turretRing(any(), isNull(), profile.capture(), any(), anyDouble(), anyBoolean(),
                any(), anyDouble(), anyInt());
        TurretRingProfile p = profile.getValue();
        assertTrue(p.count() > 1, "a ring of one is a spawn, not a ring");
        assertEquals(p.ringRadius(), Math.hypot(p.siteOffset(0)[0], p.siteOffset(0)[1]), 1e-9);
        assertTrue(p.periodMinTicks() < p.periodMaxTicks(), "the default beat must actually jitter");
        assertTrue(p.initialDelayTicks() > 0, "the ring must be dodgeable before the first volley lands");
        assertTrue(p.inAcquireRange(p.ringRadius() * p.ringRadius()),
                "a body standing at the ring's own radius has to be acquirable, or the ring never fires");
    }

    @Test
    void skipsAnOriginWhoseLocationReadFaults() {
        LivingEntity remote = mock(LivingEntity.class); // @Attacker on a DEFENSE trigger can be cross-region
        when(remote.getLocation()).thenThrow(new IllegalStateException("wrong region"));
        Sink sink = mock(Sink.class);

        new TurretRingEffect().run(ring().targets("who", remote), sink);

        verifyNoInteractions(sink);
    }
}
