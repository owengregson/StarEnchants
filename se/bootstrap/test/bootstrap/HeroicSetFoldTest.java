package bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.load.Library;
import compile.load.LibraryLoader;
import compile.load.SetDef;
import engine.boot.ContentCompiler;
import feature.heroic.HeroicSetFold;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * ADR-0073 D3 over the REAL shipped content: exactly the four M-Kit sets refuse the heroic upgrade, and every
 * other set on the server still takes it.
 *
 * <p>This is the test the rule needs. The refusal was once derived from "any {@code on: armor} bonus carrying
 * {@code DAMAGE_MOD(side: defense)}", which reads true on most shipped sets — supreme's row is a damage-TAKEN
 * penalty — and a hand-built fixture could never show that. Compiling the packs can.
 */
class HeroicSetFoldTest {

    /** The M-Kit sets whose 4/4 bonus IS their heroic wall (ADR-0073 D3). Nothing else may join them silently. */
    private static final Set<String> M_KIT =
            Set.of("sets/architect", "sets/ghost", "sets/death-knight", "sets/necromancer");

    @Test
    void onlyTheMKitSetsRefuseTheHeroicUpgrade() {
        Library cosmic = compile(Path.of("packs-src/cosmic-pack/content"));

        assertEquals(M_KIT, refusing(cosmic));
        // The sets a defensive-row inference would have wrongly refused, named so a regression says which.
        for (String taking : Set.of("sets/supreme", "sets/ranger", "sets/yeti", "sets/dragon-slayer",
                "sets/mother-of-yijki", "sets/koth")) {
            assertFalse(HeroicSetFold.foldsHeroicWall(cosmic, taking), taking + " must still take the stamp");
        }
    }

    @Test
    void noSignatureOrBundledSetRefusesTheHeroicUpgrade() {
        // Neither pack ships an M-Kit, so every one of their sets is upgradable — the rule must not leak.
        assertEquals(Set.of(), refusing(compile(Path.of("packs-src/signature-pack/content"))));
        assertEquals(Set.of(), refusing(compile(Path.of("resources/content"))));
    }

    @Test
    void aPieceOfNoSetIsNeverRefused() {
        Library cosmic = compile(Path.of("packs-src/cosmic-pack/content"));

        assertFalse(HeroicSetFold.foldsHeroicWall(cosmic, null));
        assertFalse(HeroicSetFold.foldsHeroicWall(cosmic, ""));
        assertFalse(HeroicSetFold.foldsHeroicWall(cosmic, "sets/nothing-here"));
        assertFalse(HeroicSetFold.foldsHeroicWall(null, "sets/architect"));
    }

    /** Every set key the gate refuses, read back through the gate itself rather than the def flag. */
    private static Set<String> refusing(Library library) {
        return library.sets().stream()
                .map(SetDef::key)
                .filter(key -> HeroicSetFold.foldsHeroicWall(library, key))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Library compile(Path content) {
        assertTrue(Files.isDirectory(content), content + " not found from " + Path.of("").toAbsolutePath());
        // Permissive handles: the set DEFS are what this reads, and token existence is guarded elsewhere.
        Library library = LibraryLoader.load(content,
                ContentCompiler.production(testfx.PermissiveResolvers.INSTANCE), 0);
        assertFalse(library.sets().isEmpty(), () -> "no sets loaded from " + content);
        return library;
    }
}
