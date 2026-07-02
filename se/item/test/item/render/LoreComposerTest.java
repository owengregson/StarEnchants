package item.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import item.codec.CombatState;
import item.codec.HeroicStat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * The single-pass composition contract of {@link LoreComposer} (ADR-0040): the ordered sections and the
 * exact set of lines {@link LoreComposer#compose} emits, verified with no server. Fragile per-section strings
 * are single-sourced by composing against the composer's OWN {@link LoreComposer#body} output rather than
 * re-typing them — this pins the ORDER + INCLUSION (the contract), not the section text those other tests own.
 */
class LoreComposerTest {

    private static final Function<String, String> NAMES = Map.of(
            "enchants/venom", "Venom",
            "crystals/a", "Aaa",
            "crystals/b", "Bbb")::get;
    private static final String HEROIC = "&6&lHEROIC {TYPE} (&e{+/-}{AMOUNT}% DMG&7)";

    private static LoreComposer composer() {
        return new LoreComposer(LoreRenderer.Config.of(LoreStyle.DEFAULT, NAMES)
                .withBaseSlots(() -> 9)
                .withSlotsLine(() -> "&a&l{TOTAL} Slots +{ADDED}")
                .withCrystalLine(() -> "&8S {CRYSTAL}")
                .withCrystalLineMulti(() -> "&8M {CRYSTAL}")
                .withHeroicLine(() -> HEROIC)
                .withTrakLine(line -> line.startsWith("§8TRAK")));
    }

    @Test
    void composeAppendsHeroicThenProtectionThenPreservedTraksAfterTheBody() {
        LoreComposer composer = composer();
        // enchants + orb slots + a merged crystal + a weapon heroic stat — every body section at once.
        CombatState state = new CombatState(Map.of("enchants/venom", 3),
                List.of("crystals/a+crystals/b"), null, false, new HeroicStat(0.10, 0.0, 0.20), 2);
        List<String> existing = List.of("§8TRAK 5", "some unrelated line", "§8TRAK 9");
        List<String> protection = List.of("§fPROTECTED");

        List<String> expected = new ArrayList<>(composer.body(state)); // the body sections, in their own order
        expected.add(LoreComposer.heroicBodyLine(state.heroic(), "SWORD", HEROIC));
        expected.addAll(protection);         // protection sits below the heroic line
        expected.add("§8TRAK 5");            // then ONLY the trak lines from existing, in their existing order
        expected.add("§8TRAK 9");            // ("some unrelated line" is dropped — not a trak line)

        assertEquals(expected, composer.compose(state, "SWORD", protection, existing));
    }

    @Test
    void composeEmitsOnlyTheBodyWhenNoHeroicNoProtectionNoTraks() {
        LoreComposer composer = composer();
        CombatState state = new CombatState(Map.of("enchants/venom", 1), List.of());

        assertEquals(composer.body(state), composer.compose(state, "SWORD", List.of(), List.of()));
    }

    @Test
    void heroicBodyLineSignsTheKindPercentByWeaponVsArmour() {
        // A weapon (percentDamage > 0) → +outgoing; {TYPE} taken from the kind string.
        assertEquals("§6§lHEROIC SWORD (§e+10% DMG§7)",
                LoreComposer.heroicBodyLine(new HeroicStat(0.10, 0.0, 0.20), "SWORD", HEROIC));
        // Armour (percentReduction only) → -incoming.
        assertEquals("§6§lHEROIC BOOTS (§e-10% DMG§7)",
                LoreComposer.heroicBodyLine(new HeroicStat(0.0, 0.10, 0.20), "BOOTS", HEROIC));
        // Not heroic → no line; a blank template → the plain legacy marker.
        assertNull(LoreComposer.heroicBodyLine(HeroicStat.NONE, "SWORD", HEROIC));
        assertEquals("§6§lHEROIC", LoreComposer.heroicBodyLine(new HeroicStat(0.10, 0.0, 0.0), "SWORD", ""));
    }
}
