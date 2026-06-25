package engine.selector.kind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import engine.selector.SelectorCtx;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import java.util.List;
import org.junit.jupiter.api.Test;

class SimpleSelectorsTest {

    @Test
    void selfResolvesToTheActor() {
        Player actor = mock(Player.class);
        SelectorCtx ctx = mock(SelectorCtx.class);
        when(ctx.actor()).thenReturn(actor);
        assertEquals(List.of(actor), new SelfSelector().resolve(ctx));
    }

    @Test
    void victimResolvesToTheVictimOrEmpty() {
        LivingEntity victim = mock(LivingEntity.class);
        SelectorCtx ctx = mock(SelectorCtx.class);
        when(ctx.victim()).thenReturn(victim);
        assertEquals(List.of(victim), new VictimSelector().resolve(ctx));

        SelectorCtx none = mock(SelectorCtx.class);
        when(none.victim()).thenReturn(null);
        assertTrue(new VictimSelector().resolve(none).isEmpty());
    }

    @Test
    void attackerResolvesToTheAttackerOrEmpty() {
        LivingEntity attacker = mock(LivingEntity.class);
        SelectorCtx ctx = mock(SelectorCtx.class);
        when(ctx.attacker()).thenReturn(attacker);
        assertEquals(List.of(attacker), new AttackerSelector().resolve(ctx));

        SelectorCtx none = mock(SelectorCtx.class);
        when(none.attacker()).thenReturn(null);
        assertTrue(new AttackerSelector().resolve(none).isEmpty());
    }
}
