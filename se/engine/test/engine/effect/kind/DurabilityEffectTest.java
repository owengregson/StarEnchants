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

/** Mock-host DURABILITY test. Asymmetry pinned: restore is player-only, armor damage hits any living target. */
class DurabilityEffectTest {

    @Test
    void restoreItemDefaultFullyRepairsHand() {
        Player p = mock(Player.class);
        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.integer("amount")).thenReturn(-1);
        when(ctx.str("target")).thenReturn("item");
        when(ctx.str("mode")).thenReturn("restore");
        when(ctx.targets("who")).thenReturn(List.of(p));

        Sink sink = mock(Sink.class);
        new DurabilityEffect().run(ctx, sink);

        verify(sink).repairHand(p, -1);
        verifyNoMoreInteractions(sink);
    }

    @Test
    void restoreArmorRepairsArmor() {
        Player p = mock(Player.class);
        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.integer("amount")).thenReturn(200);
        when(ctx.str("target")).thenReturn("armor");
        when(ctx.str("mode")).thenReturn("restore");
        when(ctx.targets("who")).thenReturn(List.of(p));

        Sink sink = mock(Sink.class);
        new DurabilityEffect().run(ctx, sink);

        verify(sink).repairArmor(p, 200);
        verifyNoMoreInteractions(sink);
    }

    @Test
    void damageArmorWearsArmorOnAnyLivingTarget() {
        LivingEntity victim = mock(LivingEntity.class);
        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.integer("amount")).thenReturn(50);
        when(ctx.str("target")).thenReturn("armor");
        when(ctx.str("mode")).thenReturn("damage");
        when(ctx.targets("who")).thenReturn(List.of(victim));

        Sink sink = mock(Sink.class);
        new DurabilityEffect().run(ctx, sink);

        verify(sink).damageArmor(victim, 50);
        verifyNoMoreInteractions(sink);
    }

    @Test
    void damageItemWearsHand() {
        Player p = mock(Player.class);
        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.integer("amount")).thenReturn(10);
        when(ctx.str("target")).thenReturn("item");
        when(ctx.str("mode")).thenReturn("damage");
        when(ctx.targets("who")).thenReturn(List.of(p));

        Sink sink = mock(Sink.class);
        new DurabilityEffect().run(ctx, sink);

        verify(sink).damageHand(p, 10);
        verifyNoMoreInteractions(sink);
    }

    @Test
    void restoreAllRepairsBoth() {
        Player p = mock(Player.class);
        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.integer("amount")).thenReturn(-1);
        when(ctx.str("target")).thenReturn("all");
        when(ctx.str("mode")).thenReturn("restore");
        when(ctx.targets("who")).thenReturn(List.of(p));

        Sink sink = mock(Sink.class);
        new DurabilityEffect().run(ctx, sink);

        verify(sink).repairHand(p, -1);
        verify(sink).repairArmor(p, -1);
        verifyNoMoreInteractions(sink);
    }

    @Test
    void restoreSkipsNonPlayers() {
        LivingEntity mob = mock(LivingEntity.class);
        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.integer("amount")).thenReturn(-1);
        when(ctx.str("target")).thenReturn("item");
        when(ctx.str("mode")).thenReturn("restore");
        when(ctx.targets("who")).thenReturn(List.of(mob));

        Sink sink = mock(Sink.class);
        new DurabilityEffect().run(ctx, sink);

        verifyNoMoreInteractions(sink);
    }
}
