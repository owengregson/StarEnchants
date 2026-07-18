package feature.reforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * The 1.8 weapon-damage table (ADR-0071 Javelin), driven through the shared
 * {@link WeaponDamage#legacyMaterialDamage} seam so it is unit-tested without a legacy-tree compile (§B9.9):
 * swords wood/gold 4, stone 5, iron 6, diamond 7; axes wood/gold 3, stone 4, iron 5, diamond 6; a non-weapon
 * and an empty hand ({@code null} — the NPE-trap row) are the 1-damage fist. Material NAME strings, never a
 * modern {@code Material} constant (the CLOCK trap).
 */
class WeaponDamageTest {

    @TestFactory
    List<DynamicTest> legacyMaterialTable() {
        Map<String, Double> rows = new LinkedHashMap<>();
        rows.put("DIAMOND_SWORD", 7.0);
        rows.put("IRON_SWORD", 6.0);
        rows.put("STONE_SWORD", 5.0);
        rows.put("WOOD_SWORD", 4.0);
        rows.put("GOLD_SWORD", 4.0);
        rows.put("DIAMOND_AXE", 6.0);
        rows.put("IRON_AXE", 5.0);
        rows.put("STONE_AXE", 4.0);
        rows.put("WOOD_AXE", 3.0);
        rows.put("GOLD_AXE", 3.0);
        rows.put("STICK", 1.0); // a non-weapon in hand is the fist
        return rows.entrySet().stream()
                .map(e -> dynamicTest(e.getKey() + " → " + e.getValue(),
                        () -> assertEquals(e.getValue(), WeaponDamage.legacyMaterialDamage(e.getKey()))))
                .toList();
    }

    @TestFactory
    List<DynamicTest> emptyHandIsTheFist() {
        return List.of(
                // The 1.8 empty-hand NPE trap: LegacyWeaponDamage null-guards the main hand, and the shared
                // table treats a null material name as the 1-damage fist.
                dynamicTest("null (empty hand) → 1", () -> assertEquals(1.0, WeaponDamage.legacyMaterialDamage(null))));
    }
}
