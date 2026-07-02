package compile.load;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import schema.diag.DiagCode;
import schema.diag.Diagnostics;
import testfx.YamlAcceptance;

/** Locks {@code compile.load.YamlNode.newYaml} to the shared spec (dup-key last-wins + 64-alias cap) —
 *  in lockstep with {@code migrate.YamlFactoryAcceptanceTest} over the same {@link YamlAcceptance} inputs. */
final class YamlFactoryAcceptanceTest {

    @Test
    void duplicateKeyParsesLastWins() {
        Diagnostics diags = new Diagnostics();
        YamlNode root = YamlNode.compose("dup.yml", YamlAcceptance.DUP_KEY_DOC, diags);
        assertTrue(diags.isEmpty(), () -> diags.all().toString());
        assertEquals("2", root.string("b"));
    }

    @Test
    void aliasesWithinTheCapParse() {
        Diagnostics diags = new Diagnostics();
        YamlNode root = YamlNode.compose("aliases.yml", YamlAcceptance.aliasDoc(60), diags);
        assertTrue(diags.isEmpty(), () -> diags.all().toString());
        assertTrue(root.isMapping());
    }

    @Test
    void anAliasBombIsDiagnosedNotThrown() {
        Diagnostics diags = new Diagnostics();
        YamlNode root = YamlNode.compose("bomb.yml", YamlAcceptance.aliasDoc(70), diags);
        assertTrue(diags.all().stream().anyMatch(d -> d.is(DiagCode.E_LOAD_YAML)),
                "an over-cap alias bomb is an E_LOAD_YAML diagnostic");
        assertFalse(root.has("base"), "the failed parse yields an absent node, never an exception");
    }
}
