package feature.trigger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import compile.Compiler;
import compile.load.Library;
import compile.load.LibraryLoader;
import compile.model.Ability;
import compile.model.CompiledEffect;
import compile.model.Snapshot;
import engine.boot.ContentCompiler;
import engine.stores.SuppressionStore;
import item.worn.WornState;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import testfx.WornStates;

/**
 * Pins {@link LightningBoost#compute} against a REAL production compile of the Bolt-crystal shape
 * (ADR-0063) — the {@code MaxHealthDriverCompiledTest} lesson (1.8.1): a mocked-args unit test can hide
 * the channel and the compiler disagreeing about the lowered head/args reads.
 */
class LightningBoostCompiledTest {

    private static final int GEN = 3;

    @Test
    void aProductionCompiledWornLightningModContributesItsFraction(@TempDir Path root) throws IOException {
        Path file = root.resolve("crystals/bolt-test.yml");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
            display: "&bBoltTest"
            description: [ "&b* test" ]
            applies-to: [ARMOR]
            abilities:
              - { trigger: PASSIVE, effects: [ { LIGHTNING_MOD: { amount: 10 } } ] }
            """, StandardCharsets.UTF_8);

        Compiler compiler = ContentCompiler.production(testfx.PermissiveResolvers.INSTANCE);
        Library lib = LibraryLoader.load(root, compiler, GEN);
        Snapshot snapshot = lib.snapshot();
        Integer id = snapshot.stableKeys().idOf("crystals/bolt-test");
        assertNotNull(id);
        Ability ability = snapshot.abilities()[id];
        CompiledEffect effect = ability.effects()[0];

        // The two reads the channel depends on — pinned against the REAL lowered forms, not mocks.
        assertEquals("LIGHTNING_MOD", effect.head(), "compiled head");
        assertEquals(10.0, effect.args().dbl("amount"), "amount must read as a double from lowered args");

        int passive = passiveTriggerId(snapshot);
        WornState worn = WornStates.worn().gen(GEN).byTrigger(byTrigger(passive, id)).build();
        double boost = LightningBoost.compute(worn, snapshot, new SuppressionStore(),
                UUID.randomUUID(), 0L, passive);
        assertEquals(0.10, boost, 1e-9, "a lone worn Bolt-shape crystal grants its full +10% fraction");
    }

    /** The canonical PASSIVE trigger id in the production vocabulary (the same registry the boot wiring uses). */
    private static int passiveTriggerId(Snapshot snapshot) {
        return engine.trigger.BuiltinTriggers.registry().idOf("PASSIVE").orElseThrow();
    }

    private static int[][] byTrigger(int trigger, int abilityId) {
        int[][] byTrigger = new int[trigger + 1][];
        Arrays.fill(byTrigger, new int[0]);
        byTrigger[trigger] = new int[]{abilityId};
        return byTrigger;
    }
}
