package bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import api.spi.AddonAffinity;
import api.spi.AddonEffect;
import api.spi.AddonEffectCtx;
import api.spi.AddonSink;
import api.spi.AddonSpec;
import compile.model.Affinity;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.TargetSpec;
import java.util.List;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import schema.spec.D;
import schema.spec.Param;
import schema.spec.ParamType;
import testfx.FakeEffectCtx;

/**
 * The add-on adaptation seam (ADR-0038): an {@link AddonSpec} must survive translation into the engine's
 * {@link EffectSpec} intact, and every {@link AddonSink} call must reach the wrapped engine {@link Sink}
 * with its arguments unchanged. Both are the contract the public SPI leans on — a dropped param or a
 * transposed intent argument would silently break every add-on.
 */
class AddonBridgeTest {

    @Test
    void specSurvivesAdaptationToEngineSpec() {
        AddonEffect addon = specOnly(AddonSpec.of("ADDON_TEST_BOLT")
                .param("power", D.DOUBLE.min(0).max(100), "how hard")
                .param("count", D.INT.min(1))
                .affinity(AddonAffinity.GLOBAL)
                .target("who", "@Victim")
                .doc("a test bolt")
                .example("{ ADDON_TEST_BOLT: { power: 5, count: 2 } }")
                .build());

        EffectSpec spec = new AddonBridge(addon).spec();

        assertEquals("ADDON_TEST_BOLT", spec.head());
        assertEquals(Affinity.GLOBAL, spec.affinity(), "AddonAffinity must map by name to the engine Affinity");
        assertEquals("a test bolt", spec.doc());
        assertEquals("{ ADDON_TEST_BOLT: { power: 5, count: 2 } }", spec.example());

        List<Param> params = spec.paramSpec().params();
        assertEquals(List.of("power", "count"), params.stream().map(Param::name).toList(), "param names + order");
        assertEquals(ParamType.Kind.DOUBLE, params.get(0).type().kind());
        assertEquals(ParamType.Kind.INT, params.get(1).type().kind());
        assertEquals("how hard", params.get(0).doc(), "per-param doc must carry across");

        assertEquals(List.of("who"), spec.targets().stream().map(TargetSpec::name).toList());
        assertEquals("@Victim", spec.targets().get(0).selectorType());
    }

    @Test
    void sinkFacadeForwardsIntentsToTheEngineSink() {
        Sink engineSink = mock(Sink.class);
        Player actor = mock(Player.class);
        // Distinct, non-default values so a transposed or dropped argument fails loudly (not a mock's 0/null).
        FakeEffectCtx ctx = FakeEffectCtx.create().actor(actor)
                .with("percent", 0.42)
                .with("reduce", 0.17)
                .with("xp", 13)
                .with("msg", "zap");

        new AddonBridge(forwardingEffect()).run(ctx, engineSink);

        verify(engineSink).addOutgoingDamage(0.42);
        verify(engineSink).addDamageReduction(0.17);
        verify(engineSink).giveExp(actor, 13);
        verify(engineSink).message(actor, "zap");
        verify(engineSink).cancelEvent();
        verifyNoMoreInteractions(engineSink);
    }

    /** An add-on whose run forwards the ctx reads above to the curated sink — the shape a real add-on takes. */
    private static AddonEffect forwardingEffect() {
        return new AddonEffect() {
            @Override public AddonSpec spec() {
                return AddonSpec.of("ADDON_TEST_ZAP")
                        .param("percent", D.DOUBLE).param("reduce", D.DOUBLE)
                        .param("xp", D.INT).param("msg", D.STRING).build();
            }

            @Override public void run(AddonEffectCtx ctx, AddonSink sink) {
                sink.addOutgoingDamage(ctx.dbl("percent"));
                sink.addDamageReduction(ctx.dbl("reduce"));
                sink.giveExp(ctx.actor(), ctx.integer("xp"));
                sink.message(ctx.actor(), ctx.str("msg"));
                sink.cancelEvent();
            }
        };
    }

    private static AddonEffect specOnly(AddonSpec spec) {
        return new AddonEffect() {
            @Override public AddonSpec spec() { return spec; }
            @Override public void run(AddonEffectCtx ctx, AddonSink sink) { }
        };
    }
}
