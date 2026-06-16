package engine.effect.kind;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import engine.effect.EffectCtx;
import engine.sink.Sink;
import org.bukkit.entity.LivingEntity;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Mock-host test (docs/architecture.md §1.3): a mocked {@link EffectCtx} feeds the
 * resolved potion-handle id + targets, and a mocked {@link Sink} records the emitted
 * intents — so the effect's behavior is verified with no server. The {@code effect}
 * arg arrives as an already-interned int (resolved at compile time, §9).
 */
class RemovePotionEffectTest {

    @Test
    void emitsOneRemovePotionIntentPerResolvedTarget() {
        LivingEntity self = mock(LivingEntity.class);

        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.integer("effect")).thenReturn(5);
        when(ctx.targets("who")).thenReturn(List.of(self));

        Sink sink = mock(Sink.class);
        new RemovePotionEffect().run(ctx, sink);

        verify(sink).removePotion(self, 5);
        verifyNoMoreInteractions(sink);
    }
}
