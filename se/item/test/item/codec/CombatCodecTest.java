package item.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Pure tests for the combat-state blob format — no Bukkit. The PDC/ItemStack round-trip is
 * verified separately on real servers in the live matrix.
 */
class CombatCodecTest {

    private static CombatState state(Map<String, Integer> ench, List<String> crys) {
        return new CombatState(ench, crys);
    }

    @Test
    void roundTripsEnchantsAndCrystalsPreservingOrder() {
        Map<String, Integer> ench = new LinkedHashMap<>();
        ench.put("fire-aspect", 3);
        ench.put("soul_harvest", 5);
        ench.put("lifesteal", 1);
        List<String> crys = List.of("crit-crystal", "vampire-crystal");

        String blob = CombatCodec.encodeBlob(state(ench, crys));
        CombatState back = CombatCodec.decodeBlob(blob);

        assertEquals(ench, back.enchants());
        assertEquals(crys, back.crystals());
        // Order is preserved (the cache key must be deterministic).
        assertEquals(new ArrayList<>(ench.keySet()), new ArrayList<>(back.enchants().keySet()));
    }

    @Test
    void emptyAndNullDecodeToEmpty() {
        assertTrue(CombatCodec.decodeBlob(null).isEmpty());
        assertTrue(CombatCodec.decodeBlob("").isEmpty());
    }

    @Test
    void unknownVersionDecodesToEmpty() {
        // A legacy/foreign blob is treated as empty (lazy migration), never an exception.
        assertTrue(CombatCodec.decodeBlob("v99\u001Fe\u001Ffoo:1").isEmpty());
        assertTrue(CombatCodec.decodeBlob("garbage").isEmpty());
    }

    @Test
    void enchantsOnlyAndCrystalsOnlyRoundTrip() {
        Map<String, Integer> ench = new LinkedHashMap<>();
        ench.put("sharpness", 7);
        CombatState e = CombatCodec.decodeBlob(CombatCodec.encodeBlob(state(ench, List.of())));
        assertEquals(ench, e.enchants());
        assertTrue(e.crystals().isEmpty());

        CombatState c = CombatCodec.decodeBlob(CombatCodec.encodeBlob(state(Map.of(), List.of("k1", "k2"))));
        assertTrue(c.enchants().isEmpty());
        assertEquals(List.of("k1", "k2"), c.crystals());
    }

    @Test
    void malformedEntriesAreSkippedNotThrown() {
        // Hand-built blob with a missing level, a non-numeric level, and a good entry.
        String blob = "v1\u001Fe\u001Fbad\u001Egood:4\u001Ealsobad:x\u001Fc\u001F";
        CombatState back = CombatCodec.decodeBlob(blob);
        assertEquals(Map.of("good", 4), back.enchants());
        assertTrue(back.crystals().isEmpty());
    }

    @Test
    void unknownSectionLabelsAreIgnored() {
        // A future field ("z") must not break an older reader.
        String blob = "v1\u001Fe\u001Ffire:2\u001Fz\u001Fsomethingnew\u001Fc\u001Fcrys1";
        CombatState back = CombatCodec.decodeBlob(blob);
        assertEquals(Map.of("fire", 2), back.enchants());
        assertEquals(List.of("crys1"), back.crystals());
    }
}
