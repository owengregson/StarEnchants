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

/** Mock-host test for {@code GIVE_ITEM}: one giveItem per resolved PLAYER target; non-players skipped. */
class GiveItemEffectTest {

    @Test
    void emitsGiveItemForPlayerTargetsOnly() {
        Player player = mock(Player.class);
        LivingEntity mob = mock(LivingEntity.class); // not a player → skipped

        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.integer("material")).thenReturn(4);
        when(ctx.integer("count")).thenReturn(2);
        when(ctx.targets("who")).thenReturn(List.of(player, mob));

        Sink sink = mock(Sink.class);
        new GiveItemEffect().run(ctx, sink);

        verify(sink).giveItem(player, 4, 2);
        verifyNoMoreInteractions(sink);
    }
}
