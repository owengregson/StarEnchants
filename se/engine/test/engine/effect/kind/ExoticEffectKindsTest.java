package engine.effect.kind;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import engine.effect.EffectCtx;
import engine.sink.Sink;
import java.util.List;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

/**
 * Mock-host smoke tests for the Cosmic Enchants exotic-effect ports. Pins effect→sink wiring only
 * (no entity touched directly — the Sink owns the thread hop); combat-flag behaviour is verified
 * live in the integration suites.
 */
class ExoticEffectKindsTest {

    @Test
    void removeArmorEmitsTheRemoveArmorIntentPerTarget() {
        LivingEntity victim = mock(LivingEntity.class);
        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.targets("who")).thenReturn(List.of(victim));
        Sink sink = mock(Sink.class);
        new RemoveArmorEffect().run(ctx, sink);
        verify(sink).removeArmor(victim);
    }

    @Test
    void teleblockFlagsThePlayerTargetForItsDuration() {
        Player victim = mock(Player.class);
        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.integer("duration")).thenReturn(400);
        when(ctx.targets("who")).thenReturn(List.<LivingEntity>of(victim));
        Sink sink = mock(Sink.class);
        new TeleblockEffect().run(ctx, sink);
        verify(sink).teleblock(victim, 400);
    }

    @Test
    void immuneMapsTheTypeEnumToItsWireCode() {
        Player self = mock(Player.class);
        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.str("type")).thenReturn("potion");  // → wire code 3
        when(ctx.integer("duration")).thenReturn(100);
        when(ctx.targets("who")).thenReturn(List.<LivingEntity>of(self));
        Sink sink = mock(Sink.class);
        new ImmuneEffect().run(ctx, sink);
        verify(sink).immune(self, 3, 100);
    }

    @Test
    void inlineMineAndBowFlagsAreSet() {
        EffectCtx ctx = mock(EffectCtx.class);
        Sink smelt = mock(Sink.class);
        new SmeltEffect().run(ctx, smelt);
        verify(smelt).smelt();

        Sink teleport = mock(Sink.class);
        new TeleportDropsEffect().run(ctx, teleport);
        verify(teleport).teleportDrops();

        Sink seek = mock(Sink.class);
        new SeekEffect().run(ctx, seek);
        verify(seek).seek();
    }
}
