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
 * Pins {@link MaxHealthDriver#computeExpected} against a REAL production compile of the Nature-crystal shape
 * ({@code HEALTH} with the DEFAULTED {@code @Self}) — the 1.8.1 regression: the mocked-args unit test hid that
 * the real pipeline's lowered effect did not match the driver's head/target/args reads (the SUPPRESS-mode
 * lesson repeated). If this fails, the driver and the compiler disagree about what a worn HEALTH looks like.
 */
class MaxHealthDriverCompiledTest {

    private static final int GEN = 3;

    @Test
    void aProductionCompiledWornHealthContributesItsAmount(@TempDir Path root) throws IOException {
        Path file = root.resolve("crystals/nature-test.yml");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
            display: "&2&lNatureTest"
            description: [ "&2* test" ]
            applies-to: [ARMOR]
            abilities:
              - { trigger: PASSIVE, effects: [ { HEALTH: { amount: 4 } } ] }
            """, StandardCharsets.UTF_8);

        Compiler compiler = ContentCompiler.production(testfx.PermissiveResolvers.INSTANCE);
        Library lib = LibraryLoader.load(root, compiler, GEN);
        Snapshot snapshot = lib.snapshot();
        Integer id = snapshot.stableKeys().idOf("crystals/nature-test");
        assertNotNull(id);
        Ability ability = snapshot.abilities()[id];
        CompiledEffect effect = ability.effects()[0];

        // The three reads the driver depends on — pinned against the REAL lowered forms, not mocks.
        assertEquals("HEALTH", effect.head(), "compiled head");
        assertEquals("SELF", effect.target().head(), "the defaulted @Self must lower to head SELF");
        assertEquals(4.0, effect.args().dbl("amount"), "amount must read as a double from lowered args");

        int passive = passiveTriggerId(snapshot);
        WornState worn = WornStates.worn().gen(GEN).byTrigger(byTrigger(passive, id)).build();
        double expected = MaxHealthDriver.computeExpected(worn, snapshot, new SuppressionStore(),
                UUID.randomUUID(), 0L, -1, passive);
        assertEquals(4.0, expected, "a lone worn Nature-shape crystal grants its full bonus");
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
