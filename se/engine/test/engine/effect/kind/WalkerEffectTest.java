package engine.effect.kind;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import engine.effect.EffectCtx;
import engine.sink.Sink;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.Test;

/** Pins the {@code replace} enum → Sink 0/1/2 mode mapping. */
class WalkerEffectTest {

    private static EffectCtx ctx(String replace, Location loc, LivingEntity who) {
        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.integer("material")).thenReturn(42);
        when(ctx.integer("ticks")).thenReturn(80);
        when(ctx.integer("radius")).thenReturn(1);
        when(ctx.str("replace")).thenReturn(replace);
        when(who.getLocation()).thenReturn(loc);
        when(ctx.targets("who")).thenReturn(List.of(who));
        return ctx;
    }

    @Test
    void replaceableMapsToModeOne() {
        Location loc = mock(Location.class);
        LivingEntity who = mock(LivingEntity.class);
        Sink sink = mock(Sink.class);
        new WalkerEffect().run(ctx("REPLACEABLE", loc, who), sink);
        verify(sink).tempPlatform(loc, 42, 1, 80, 1);
        verifyNoMoreInteractions(sink);
    }

    @Test
    void airOnlyMapsToModeZero() {
        Location loc = mock(Location.class);
        LivingEntity who = mock(LivingEntity.class);
        Sink sink = mock(Sink.class);
        new WalkerEffect().run(ctx("AIR_ONLY", loc, who), sink);
        verify(sink).tempPlatform(loc, 42, 1, 80, 0);
        verifyNoMoreInteractions(sink);
    }

    @Test
    void anyMapsToModeTwo() {
        Location loc = mock(Location.class);
        LivingEntity who = mock(LivingEntity.class);
        Sink sink = mock(Sink.class);
        new WalkerEffect().run(ctx("ANY", loc, who), sink);
        verify(sink).tempPlatform(loc, 42, 1, 80, 2);
        verifyNoMoreInteractions(sink);
    }
}
