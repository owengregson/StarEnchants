package migrate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import testfx.YamlAcceptance;

/** Locks {@code migrate.LegacyYaml.newYaml} to the shared spec (dup-key last-wins + 64-alias cap) — in
 *  lockstep with {@code compile.YamlFactoryAcceptanceTest} over the same {@link YamlAcceptance} inputs. The
 *  migrator keeps its hard-fail posture (an over-cap alias bomb throws, unlike the loader's diagnose-only). */
final class YamlFactoryAcceptanceTest {

    @Test
    void duplicateKeyParsesLastWins() {
        Map<?, ?> parsed = LegacyYaml.parse(YamlAcceptance.DUP_KEY_DOC);
        assertEquals(2, parsed.get("b"));
    }

    @Test
    void aliasesWithinTheCapParse() {
        Map<?, ?> parsed = LegacyYaml.parse(YamlAcceptance.aliasDoc(60));
        List<?> items = assertInstanceOf(List.class, parsed.get("items"));
        assertEquals(60, items.size());
    }

    @Test
    void anAliasBombThrows() {
        assertThrows(RuntimeException.class, () -> LegacyYaml.parse(YamlAcceptance.aliasDoc(70)));
    }
}
