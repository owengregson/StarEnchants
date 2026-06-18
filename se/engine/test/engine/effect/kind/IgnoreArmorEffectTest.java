package engine.effect.kind;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import engine.effect.EffectCtx;
import engine.sink.Sink;
import org.junit.jupiter.api.Test;

/**
 * Mock-host test for {@code IGNORE_ARMOR}: paramless, it emits exactly one {@code ignoreArmor} read-back
 * (mirrors {@code CANCEL}). The combat dispatcher's modifier-zeroing is integration-pinned in the live suite.
 */
class IgnoreArmorEffectTest {

    @Test
    void emitsTheIgnoreArmorReadBack() {
        EffectCtx ctx = mock(EffectCtx.class);
        Sink sink = mock(Sink.class);

        new IgnoreArmorEffect().run(ctx, sink);

        verify(sink).ignoreArmor();
        verifyNoMoreInteractions(sink);
    }
}
