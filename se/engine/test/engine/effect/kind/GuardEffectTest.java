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
import org.junit.jupiter.api.Test;

/**
 * Mock-host test for {@code GUARD}: one {@code guard} intent per attacker target at the activation
 * location with the configured type/count/ttl/name; a no-op when there is no location. The actual spawn +
 * setTarget is integration-pinned in the live suite.
 */
class GuardEffectTest {

    @Test
    void emitsGuardPerAttackerTarget() {
        Location at = mock(Location.class);
        LivingEntity attacker = mock(LivingEntity.class);

        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.location()).thenReturn(at);
        when(ctx.integer("type")).thenReturn(42);
        when(ctx.integer("count")).thenReturn(2);
        when(ctx.integer("ttl")).thenReturn(200);
        when(ctx.str("name")).thenReturn("&bGuardian");
        when(ctx.targets("who")).thenReturn(List.of(attacker));

        Sink sink = mock(Sink.class);
        new GuardEffect().run(ctx, sink);

        verify(sink).guard(attacker, at, 42, 2, 200, "&bGuardian");
        verifyNoMoreInteractions(sink);
    }

    @Test
    void noOpWithoutALocation() {
        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.location()).thenReturn(null);

        Sink sink = mock(Sink.class);
        new GuardEffect().run(ctx, sink);

        verifyNoInteractions(sink);
    }
}
