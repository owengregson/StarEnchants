package bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for {@link SeCommand#complete} — the {@code /se} tab-completion decision, exercised
 * without a server. Locks subcommand completion + the context-sensitive first-argument completions
 * (enchant/crystal keys, migrate formats, reload flag).
 */
class SeCommandCompletionTest {

    private static final List<String> ENCHANTS = List.of("enchants/venom", "enchants/vigor", "enchants/blast");
    private static final List<String> CRYSTALS = List.of("crystals/jolt", "crystals/frost");

    @Test
    void firstTokenCompletesSubcommands() {
        assertEquals(SeCommand.SUBCOMMANDS, SeCommand.complete(new String[] {""}, ENCHANTS, CRYSTALS));
        assertEquals(List.of("reload"), SeCommand.complete(new String[] {"rel"}, ENCHANTS, CRYSTALS));
        assertEquals(List.of("menu", "migrate"), SeCommand.complete(new String[] {"m"}, ENCHANTS, CRYSTALS)
                .stream().sorted().toList());
    }

    @Test
    void enchantArgumentCompletesEnchantKeysByPrefix() {
        assertEquals(List.of("enchants/venom", "enchants/vigor"),
                SeCommand.complete(new String[] {"enchant", "enchants/v"}, ENCHANTS, CRYSTALS));
    }

    @Test
    void crystalArgumentCompletesCrystalKeys() {
        assertEquals(List.of("crystals/jolt", "crystals/frost"),
                SeCommand.complete(new String[] {"crystal", ""}, ENCHANTS, CRYSTALS));
    }

    @Test
    void migrateAndReloadHaveFixedFirstArgumentCompletions() {
        assertEquals(List.of("ee", "ea"), SeCommand.complete(new String[] {"migrate", ""}, ENCHANTS, CRYSTALS));
        assertEquals(List.of("--dry-run"), SeCommand.complete(new String[] {"reload", "--"}, ENCHANTS, CRYSTALS));
    }

    @Test
    void unknownContextsAndDeepArgsCompleteToNothing() {
        assertTrue(SeCommand.complete(new String[] {"gem", "x"}, ENCHANTS, CRYSTALS).isEmpty());
        assertTrue(SeCommand.complete(new String[] {"enchant", "enchants/venom", "2"}, ENCHANTS, CRYSTALS).isEmpty());
    }
}
