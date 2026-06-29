package item.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

/**
 * Pure composition tests for the scroll protection-line refresh — the head (enchant body / a soul gem's authored
 * lore) and trak lines are preserved while the PROTECTED lines are rebuilt. This is the robustness contract: a
 * scroll apply must never wipe an item's authored or trak lore (the on-{@code ItemStack} wrapper is covered live).
 */
class ProtectionLoreRefreshTest {

    private static final Predicate<String> IS_PROTECTION = line -> line.contains("PROTECTED");
    private static final Predicate<String> IS_TRAK = line -> line.contains("Mobs Slain");

    @Test
    void dropsOldProtectionKeepsHeadAndTraksAndReInsertsAboveTraks() {
        List<String> existing = List.of("§7Sword Lore", "§f§lPROTECTED", "§7Mobs Slain: §f5");
        List<String> out = ProtectionLoreRefresh.compose(existing, List.of("§e§l*HOLY* PROTECTED"), IS_PROTECTION, IS_TRAK);
        assertEquals(List.of("§7Sword Lore", "§e§l*HOLY* PROTECTED", "§7Mobs Slain: §f5"), out);
    }

    @Test
    void emptyProtectionDropsOldAndPreservesHeadAndTraks() {
        // a soul gem's authored lore (head) and a trak line must survive when the protection is removed
        List<String> existing = List.of("§7Souls: 100", "§f§lPROTECTED", "§7Mobs Slain: §f1");
        List<String> out = ProtectionLoreRefresh.compose(existing, List.of(), IS_PROTECTION, IS_TRAK);
        assertEquals(List.of("§7Souls: 100", "§7Mobs Slain: §f1"), out);
    }

    @Test
    void noTraksNoProtectionLeavesTheHeadUntouched() {
        List<String> existing = List.of("§7Line A", "§7Line B");
        assertEquals(existing, ProtectionLoreRefresh.compose(existing, List.of(), IS_PROTECTION, IS_TRAK));
    }
}
