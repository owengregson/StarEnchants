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
        assertEquals(List.of("ee", "ea", "ae"), SeCommand.complete(new String[] {"migrate", ""}, ENCHANTS, CRYSTALS));
        assertEquals(List.of("--dry-run"), SeCommand.complete(new String[] {"reload", "--"}, ENCHANTS, CRYSTALS));
    }

    @Test
    void unknownContextsAndDeepArgsCompleteToNothing() {
        assertTrue(SeCommand.complete(new String[] {"gem", "x"}, ENCHANTS, CRYSTALS).isEmpty());
        assertTrue(SeCommand.complete(new String[] {"enchant", "enchants/venom", "2"}, ENCHANTS, CRYSTALS).isEmpty());
    }

    // §J give-tree + removeenchant completion

    private static final List<String> PLAYERS = List.of("Bob", "Alice");
    private static final List<String> SETS = List.of("sets/titan", "sets/yeti");

    private static List<String> complete(String... args) {
        return SeCommand.complete(args, ENCHANTS, CRYSTALS, List.of("common", "rare"), List.of(), PLAYERS, SETS);
    }

    @Test
    void giveCompletesTypeThenPlayerThenTypeKey() {
        assertTrue(complete("give", "").containsAll(List.of("gem", "crystal", "book", "dust", "set", "upgrade")));
        assertTrue(complete("give", "").stream().noneMatch("item"::equals)); // no bare "item" type
        assertEquals(List.of("Bob"), complete("give", "crystal", "Bo"));               // arg 2 = online player
        assertEquals(CRYSTALS, complete("give", "crystal", "Bob", ""));                 // arg 3 = crystal key
        assertEquals(List.of("enchants/venom", "enchants/vigor"),
                complete("give", "book", "Bob", "enchants/v"));                          // arg 3 = enchant key
        assertEquals(List.of("rare"), complete("give", "unopened", "Bob", "ra"));        // arg 3 = tier
    }

    @Test
    void removeEnchantCompletesEnchantKeys() {
        assertEquals(List.of("enchants/venom", "enchants/vigor"),
                complete("removeenchant", "enchants/v"));
        assertEquals(List.of("enchants/blast"), complete("unenchant", "enchants/b"));
    }

    @Test
    void giveSetCompletesSetKeys() {
        assertEquals(SETS, complete("give", "set", "Bob", ""));               // arg 3 = set key (@sets)
        assertEquals(List.of("sets/yeti"), complete("give", "set", "Bob", "sets/y"));
    }

    // §packs (ADR-0023) /se pack completion

    private static final List<String> PACKS = List.of("elite-enchantments", "vanilla-plus");

    private static List<String> completePack(String... args) {
        return SeCommand.complete(args, ENCHANTS, CRYSTALS, List.of(), List.of(), PLAYERS, SETS, PACKS);
    }

    @Test
    void packCompletesActionsThenNamesForInfoAndApply() {
        assertEquals(List.of("list", "info", "apply", "export"), completePack("pack", ""));
        assertEquals(List.of("apply"), completePack("pack", "ap"));
        assertEquals(PACKS, completePack("pack", "info", ""));                       // arg 2 = pack name
        assertEquals(List.of("elite-enchantments"), completePack("pack", "apply", "elite"));
        assertTrue(completePack("pack", "export", "").isEmpty());                    // export takes a NEW name
        assertTrue(completePack("pack", "list", "x").isEmpty());                     // list takes no further arg
    }

    @Test
    void giveBookOffersRandomThenCompletesTheTier() {
        assertTrue(complete("give", "book", "Bob", "").contains("random"));   // arg 3 offers the `random` form
        assertEquals(List.of("common", "rare"), complete("give", "book", "Bob", "random", ""));  // arg 4 = tier
        assertEquals(List.of("rare"), complete("give", "book", "Bob", "random", "ra"));
    }
}
