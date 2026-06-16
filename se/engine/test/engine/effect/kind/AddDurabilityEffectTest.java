package engine.effect.kind;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import engine.effect.EffectCtx;
import engine.sink.Sink;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Mock-host test (docs/architecture.md §1.3): ADD_DURABILITY repairs worn armor for player targets only. */
class AddDurabilityEffectTest {

    @Test
    void emitsOneRepairArmorIntentPerResolvedPlayer() {
        Player a = mock(Player.class);
        Player b = mock(Player.class);

        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.integer("amount")).thenReturn(200);
        when(ctx.targets("who")).thenReturn(List.of(a, b));

        Sink sink = mock(Sink.class);
        new AddDurabilityEffect().run(ctx, sink);

        verify(sink).repairArmor(a, 200);
        verify(sink).repairArmor(b, 200);
        verifyNoMoreInteractions(sink);
    }

    @Test
    void skipsNonPlayerTargets() {
        LivingEntity mob = mock(LivingEntity.class);
        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.integer("amount")).thenReturn(-1);
        when(ctx.targets("who")).thenReturn(List.of(mob));

        Sink sink = mock(Sink.class);
        new AddDurabilityEffect().run(ctx, sink);

        verifyNoMoreInteractions(sink); // a mob has no armor to repair
    }
}
