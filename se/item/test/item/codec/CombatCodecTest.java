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
        // Order must survive: the content-hash cache key depends on it.
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
    void roundTripsSetMembershipAndOmniFlag() {
        CombatState withSet = new CombatState(Map.of("guard", 2), List.of(), "sets/yeti", false);
        CombatState back = CombatCodec.decodeBlob(CombatCodec.encodeBlob(withSet));
        assertEquals("sets/yeti", back.setKey());
        assertEquals(false, back.omni());
        assertEquals(Map.of("guard", 2), back.enchants());

        CombatState omniPiece = new CombatState(Map.of(), List.of(), "sets/yeti", true);
        CombatState backOmni = CombatCodec.decodeBlob(CombatCodec.encodeBlob(omniPiece));
        assertEquals("sets/yeti", backOmni.setKey());
        assertTrue(backOmni.omni());

        // An item with only a set key (no enchants/crystals) is NOT empty — it must persist.
        assertTrue(!new CombatState(Map.of(), List.of(), "sets/yeti", false).isEmpty());
    }

    @Test
    void roundTripsWeaponSetMembership() {
        // A set WEAPON member carries setWeaponKey (label 'w') and NO setKey — it must survive the codec
        // distinct from an armour member, and is not empty.
        CombatState weapon = CombatState.weaponMember("sets/titan");
        CombatState back = CombatCodec.decodeBlob(CombatCodec.encodeBlob(weapon));
        assertEquals("sets/titan", back.setWeaponKey());
        assertEquals(null, back.setKey());
        assertTrue(!weapon.isEmpty());
    }

    @Test
    void roundTripsHeroicStats() {
        CombatState s = new CombatState(Map.of(), List.of(), null, false, new HeroicStat(0.35, 0.2, 0.5));
        CombatState back = CombatCodec.decodeBlob(CombatCodec.encodeBlob(s));
        assertEquals(0.35, back.heroic().percentDamage());
        assertEquals(0.2, back.heroic().percentReduction());
        assertEquals(0.5, back.heroic().durability());
        // A heroic-only item (no enchants/crystals/set) is NOT empty — it must persist.
        assertTrue(!new CombatState(Map.of(), List.of(), null, false, new HeroicStat(1, 0, 0)).isEmpty());
    }

    @Test
    void roundTripsPurchasedSlots() {
        // §H: an item's purchased slot count survives the blob round-trip.
        CombatState s = new CombatState(Map.of("sharpness", 1), List.of()).withAdded(4);
        CombatState back = CombatCodec.decodeBlob(CombatCodec.encodeBlob(s));
        assertEquals(4, back.added());
        assertEquals(Map.of("sharpness", 1), back.enchants());

        // A slot-only item (extra slots, no enchants/crystals/set/heroic) is NOT empty — it must persist.
        CombatState slotOnly = CombatState.EMPTY.withAdded(2);
        assertTrue(!slotOnly.isEmpty());
        assertEquals(2, CombatCodec.decodeBlob(CombatCodec.encodeBlob(slotOnly)).added());

        // Zero added slots emit no 'a' section (back-compat: an old item decodes to added 0).
        assertEquals(0, CombatCodec.decodeBlob(CombatCodec.encodeBlob(state(Map.of("x", 1), List.of()))).added());
    }

    @Test
    void legacyBlobWithoutSetSectionsDecodesToNoSet() {
        // An old v1 blob (no s/o labels) → setKey null, omni false (forward-compatible).
        CombatState back = CombatCodec.decodeBlob("v1efire:2c");
        assertEquals(null, back.setKey());
        assertEquals(false, back.omni());
    }

    @Test
    void malformedEntriesAreSkippedNotThrown() {
        // One bad entry must not drop the good ones: missing level, non-numeric level, then a valid pair.
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
