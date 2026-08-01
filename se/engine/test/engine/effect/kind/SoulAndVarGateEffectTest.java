package engine.effect.kind;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import engine.effect.EffectHalt;
import engine.sink.Sink;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import testfx.FakeEffectCtx;

final class SoulAndVarGateEffectTest {

    @Test
    void soulTotalGateUsesTheLivePostSpendModulo() {
        Player actor = mock(Player.class);
        Sink sink = mock(Sink.class);
        FakeEffectCtx ctx = FakeEffectCtx.create()
                .with("divisor", 20).with("remainder", 0).with("require-paid", true).actor(actor);

        when(sink.soulTotal(actor)).thenReturn(40);
        assertDoesNotThrow(() -> new RequireSoulTotalEffect().run(ctx, sink));

        when(sink.soulCostFree(actor)).thenReturn(true);
        assertThrows(EffectHalt.class, () -> new RequireSoulTotalEffect().run(ctx, sink));

        when(sink.soulCostFree(actor)).thenReturn(false);
        when(sink.soulTotal(actor)).thenReturn(39);
        assertThrows(EffectHalt.class, () -> new RequireSoulTotalEffect().run(ctx, sink));
    }

    @Test
    void variableGateCanRequireAbsenceForAThrottle() {
        Player actor = mock(Player.class);
        Sink sink = mock(Sink.class);
        FakeEffectCtx ctx = FakeEffectCtx.create()
                .with("name", "feedback-throttle").with("present", false)
                .targets("who", actor);

        when(sink.hasVar(actor, "feedback-throttle")).thenReturn(false);
        assertDoesNotThrow(() -> new RequireVarEffect().run(ctx, sink));

        when(sink.hasVar(actor, "feedback-throttle")).thenReturn(true);
        assertThrows(EffectHalt.class, () -> new RequireVarEffect().run(ctx, sink));
    }
}
