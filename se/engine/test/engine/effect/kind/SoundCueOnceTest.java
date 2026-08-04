package engine.effect.kind;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import engine.sink.Sink;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.Test;
import testfx.FakeEffectCtx;

/**
 * One audible cue per sound per hit (ADR-0066): the SOUND kind emits a given sound id at most once into one
 * per-event sink, however many co-activations author it — sibling enchants sharing a likeness cue (Lifesteal +
 * Chain Lifesteal both proc'ing one swing), worn multi-copies, the ECHO_STRIKE re-pass. A different sound on
 * the same hit, and the same sound on the NEXT hit (a fresh sink), still play. Emission-seam unit: the sink is
 * the event bracket, so the contract is observable as sink.sound call counts.
 */
class SoundCueOnceTest {

    private static FakeEffectCtx ctx(Location loc, int soundId, double pitch) {
        return FakeEffectCtx.create().location(loc)
                .with("sound", soundId).with("volume", 1.0).with("pitch", pitch).with("dy", 0.0);
    }

    @Test
    void sameSoundPlaysOncePerEventSink() {
        SoundEffect kind = new SoundEffect();
        Location loc = mock(Location.class);
        Sink sink = mock(Sink.class);
        // Two activations in one walk author the identical cue (the Lifesteal + Chain Lifesteal co-proc),
        // and a third authors it at another pitch (the Demonic variant) — still the same audible cue.
        kind.run(ctx(loc, 7, 1.0), sink);
        kind.run(ctx(loc, 7, 1.0), sink);
        kind.run(ctx(loc, 7, 0.8), sink);
        verify(sink, times(1)).sound(eq(loc), eq(7), anyFloat(), anyFloat());
    }

    @Test
    void differentSoundsOnOneHitBothPlay() {
        SoundEffect kind = new SoundEffect();
        Location loc = mock(Location.class);
        Sink sink = mock(Sink.class);
        kind.run(ctx(loc, 7, 1.0), sink);
        kind.run(ctx(loc, 8, 1.0), sink);
        verify(sink, times(1)).sound(eq(loc), eq(7), anyFloat(), anyFloat());
        verify(sink, times(1)).sound(eq(loc), eq(8), anyFloat(), anyFloat());
    }

    @Test
    void theWhoSlotFanOutIsOneCue() {
        SoundEffect kind = new SoundEffect();
        LivingEntity first = mock(LivingEntity.class);
        LivingEntity second = mock(LivingEntity.class);
        Sink sink = mock(Sink.class);
        // The dedupe brackets CO-ACTIVATIONS, not targets: one authored line still reaches every entity it
        // resolved, while a sibling activation authoring the same cue on the same hit stays collapsed.
        kind.run(FakeEffectCtx.create().targets("who", first, second)
                .with("sound", 7).with("volume", 1.0).with("pitch", 1.0).with("dy", 0.0), sink);
        kind.run(FakeEffectCtx.create().targets("who", first, second)
                .with("sound", 7).with("volume", 1.0).with("pitch", 1.0).with("dy", 0.0), sink);
        verify(sink, times(1)).sound(eq(first), eq(7), anyFloat(), anyFloat(), anyDouble());
        verify(sink, times(1)).sound(eq(second), eq(7), anyFloat(), anyFloat(), anyDouble());
    }

    @Test
    void nextEventSinkReopensTheCue() {
        SoundEffect kind = new SoundEffect();
        Location loc = mock(Location.class);
        Sink first = mock(Sink.class);
        Sink second = mock(Sink.class);
        kind.run(ctx(loc, 7, 1.0), first);
        kind.run(ctx(loc, 7, 1.0), second); // a new per-event sink = the next hit — the cue must play again
        verify(first, times(1)).sound(eq(loc), eq(7), anyFloat(), anyFloat());
        verify(second, times(1)).sound(eq(loc), eq(7), anyFloat(), anyFloat());
    }
}
