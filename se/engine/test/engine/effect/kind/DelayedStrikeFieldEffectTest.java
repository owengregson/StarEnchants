package engine.effect.kind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import engine.sink.FieldCue;
import engine.sink.Sink;
import engine.sink.StrikeFieldProfile;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import testfx.FakeEffectCtx;
import testfx.SpecDrivenCtx;

/**
 * DELAYED_STRIKE_FIELD's activation-side contract: one field intent per origin, the two phases' cues assembled
 * independently, and the declared defaults still expressing the ability the content was ported from. Everything
 * the field DOES — sampling, the delay, the strike — is the sink's, and its arithmetic is pinned in
 * {@code FieldProfileTest}.
 */
class DelayedStrikeFieldEffectTest {

    /** A field with neither phase's handles authored, so the cue assembly starts from nothing. */
    private static FakeEffectCtx field() {
        return FakeEffectCtx.create()
                .with("points", 16).with("offset-min", 2).with("offset-max", 9).with("delay", 20)
                .with("hit-radius", 1.5).with("target-range", 32.0).with("filter", "ENEMIES")
                .with("damage", 16.0).with("health-floor", 1.0).with("warning", "boom")
                .with("cue-volume", 1.0).with("cue-pitch", 0.4).with("cue-particle-count", 32)
                .with("strike-volume", 0.5).with("strike-pitch", 0.9).with("strike-particle-count", 4)
                .with("lightning", true);
    }

    private static LivingEntity originAt(World world, int x, int z) {
        LivingEntity who = mock(LivingEntity.class);
        when(who.getLocation()).thenReturn(new Location(world, x, 64, z));
        return who;
    }

    @Test
    void eachPhasesCuesAreAssembledFromItsOwnHandlesAndNeverCrossed() {
        World world = mock(World.class);
        Sink sink = mock(Sink.class);
        FakeEffectCtx ctx = field()
                .with("cue-sound", 7).with("cue-particle", 9)
                .with("strike-sound", 11).with("strike-particle", 13)
                .targets("who", originAt(world, 0, 0));

        new DelayedStrikeFieldEffect().run(ctx, sink);

        // Every term distinct, so a transposed volume/pitch or a swapped phase cannot pass.
        verify(sink).delayedStrikeField(any(Location.class), isNull(), any(StrikeFieldProfile.class),
                eq(new FieldCue(7, 1.0f, 0.4f, 9, 32)), eq(new FieldCue(11, 0.5f, 0.9f, 13, 4)),
                eq(true), eq("boom"));
    }

    @Test
    void anAbsentCueHandleIsSilenceRatherThanHandleZero() {
        World world = mock(World.class);
        Sink sink = mock(Sink.class);

        new DelayedStrikeFieldEffect().run(field().targets("who", originAt(world, 0, 0)), sink);

        // Interned ids start at 0, so a "no cue" that fell through as 0 would play whichever sound got id 0.
        verify(sink).delayedStrikeField(any(), isNull(), any(), eq(FieldCue.SILENT), eq(FieldCue.SILENT),
                anyBoolean(), anyString());
    }

    @Test
    void everyOriginGetsItsOwnField() {
        World world = mock(World.class);
        Sink sink = mock(Sink.class);
        FakeEffectCtx ctx = field().targets("who", originAt(world, 0, 0), originAt(world, 40, 40));

        new DelayedStrikeFieldEffect().run(ctx, sink);

        verify(sink, times(2)).delayedStrikeField(any(), isNull(), any(), any(), any(), anyBoolean(), anyString());
    }

    @Test
    void theDeclaredDefaultsStillExpressTheAbilityTheContentWasPortedFrom() {
        // Driven from the kind's OWN spec defaults: the two numbers that define the ability are not the raw
        // params but the relations they produce — a hit test that is exactly the ported squared radius, and a
        // floor that leaves a body alive no matter how many overlapping points strike it.
        World world = mock(World.class);
        Sink sink = mock(Sink.class);
        DelayedStrikeFieldEffect kind = new DelayedStrikeFieldEffect();
        FakeEffectCtx ctx = SpecDrivenCtx.defaults(kind.spec()).targets("who", originAt(world, 0, 0));
        ArgumentCaptor<StrikeFieldProfile> profile = ArgumentCaptor.forClass(StrikeFieldProfile.class);

        kind.run(ctx, sink);

        verify(sink).delayedStrikeField(any(), isNull(), profile.capture(), any(), any(), anyBoolean(), anyString());
        StrikeFieldProfile p = profile.getValue();
        assertTrue(p.hits(2.0), "the default radius still reproduces the ported `distanceSquared <= 2` test");
        assertFalse(p.hits(2.01));
        assertEquals(p.healthFloor(), p.struckHealth(p.damage() + p.healthFloor()),
                "a body at damage+floor lands exactly on the floor");
        assertEquals(p.healthFloor(), p.struckHealth(p.healthFloor()), "and one already there cannot be taken lower");
    }

    @Test
    void skipsAnOriginWhoseLocationReadFaults() {
        LivingEntity remote = mock(LivingEntity.class); // @Attacker on a DEFENSE trigger can be cross-region
        when(remote.getLocation()).thenThrow(new IllegalStateException("wrong region"));
        Sink sink = mock(Sink.class);

        new DelayedStrikeFieldEffect().run(field().targets("who", remote), sink);

        verifyNoInteractions(sink);
    }
}
