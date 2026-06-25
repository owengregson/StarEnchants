package compile.load;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.Compiler;
import compile.MapSpecRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import schema.spec.D;
import schema.spec.ParamSpec;

/**
 * The crystal reader carries each crystal's own physical likeness (material/name/lore, §E), falling
 * back to the shared {@code items/crystal.yml} per field when omitted. Rendering is covered on a real
 * server by the tester's CrystalSuite.
 */
class CrystalLikenessTest {

    private static Compiler compiler() {
        return Compiler.of(MapSpecRegistry.of(ParamSpec.of("HEAL").param("amount", D.DOUBLE.min(0)).build()));
    }

    private static void write(Path root, String relative, String yaml) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, yaml, StandardCharsets.UTF_8);
    }

    @Test
    void crystalCarriesItsOwnMaterialNameAndLore(@TempDir Path root) throws IOException {
        write(root, "crystals/power.yml", """
            tier: epic
            display: "&cPower Crystal"
            description: "Raw striking power."
            material: REDSTONE
            name: "&cPower Crystal"
            lore:
              - "&7Socket into a sword or axe."
              - "&7Adds &c+15&7 melee damage."
            trigger: ATTACK
            effects: [ { HEAL: { amount: 1 } } ]
            """);
        Library lib = LibraryLoader.load(root, compiler(), 1);

        assertFalse(lib.hasErrors(), () -> lib.diagnostics().toString());
        CrystalDef def = lib.crystalDefOf("crystals/power");
        assertNotNull(def, "power crystal should load");
        assertEquals("REDSTONE", def.material());
        assertEquals("&cPower Crystal", def.name());
        assertEquals(List.of("&7Socket into a sword or axe.", "&7Adds &c+15&7 melee damage."), def.lore());
    }

    @Test
    void omittedLikenessFieldsAreNullSoMintFallsBackToTheSharedConfig(@TempDir Path root) throws IOException {
        write(root, "crystals/plain.yml", """
            tier: rare
            display: "&7Plain Crystal"
            description: "No custom likeness."
            trigger: ATTACK
            effects: [ { HEAL: { amount: 1 } } ]
            """);
        Library lib = LibraryLoader.load(root, compiler(), 1);

        assertFalse(lib.hasErrors(), () -> lib.diagnostics().toString());
        CrystalDef def = lib.crystalDefOf("crystals/plain");
        assertNotNull(def);
        assertNull(def.material(), "absent material → null → mint uses the shared CrystalConfig material");
        assertNull(def.name(), "absent name → null → mint uses the shared CrystalConfig name");
        assertTrue(def.lore().isEmpty(), "absent lore → empty → mint uses the shared CrystalConfig lore");
    }
}
