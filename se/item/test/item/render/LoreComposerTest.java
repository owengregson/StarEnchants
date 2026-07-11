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
 * Protection + trak lines arrive as pre-rendered sections (rendered from state by the caller, ADR-0040).
 */
class LoreComposerTest {

    private static final Function<String, String> NAMES = Map.of(
            "enchants/venom", "Venom",
            "crystals/a", "Aaa",
            "crystals/b", "Bbb",
            "masks/agent", "Agent")::get;
    private static final String HEROIC = "&6&lHEROIC {TYPE} (&e{+/-}{AMOUNT}% DMG&7)";

    private static LoreComposer composer() {
        return new LoreComposer(LoreRenderer.Config.of(LoreStyle.DEFAULT, NAMES)
                .withBaseSlots(() -> 9)
                .withSlotsLine(() -> "&a&l{TOTAL} Slots +{ADDED}")
                .withCrystalLine(() -> "&8S {CRYSTAL}")
                .withCrystalLineMulti(() -> "&8M {CRYSTAL}")
                .withMaskLine(() -> "&8Mask: {NAME}")
                .withHeroicLine(() -> HEROIC));
    }

    @Test
    void composeAppendsHeroicThenProtectionThenTraksAfterTheBody() {
        LoreComposer composer = composer();
        // enchants + orb slots + a merged crystal + a weapon heroic stat — every body section at once.
        CombatState state = new CombatState(Map.of("enchants/venom", 3),
                List.of("crystals/a+crystals/b"), null, false, new HeroicStat(0.10, 0.0, 0.20), 2);
        List<String> protection = List.of("§fPROTECTED");
        List<String> traks = List.of("§8TRAK 5", "§8TRAK 9"); // rendered from state by the caller, passed as a section

        List<String> expected = new ArrayList<>(composer.body(state)); // the body sections, in their own order
        expected.add(LoreComposer.heroicBodyLine(state.heroic(), "SWORD", HEROIC));
        expected.addAll(protection);         // protection sits below the heroic line
        expected.addAll(traks);              // then the trak section, in order

        assertEquals(expected, composer.compose(state, "SWORD", protection, traks));
    }

    @Test
    void composeEmitsOnlyTheBodyWhenNoHeroicNoProtectionNoTraks() {
        LoreComposer composer = composer();
        CombatState state = new CombatState(Map.of("enchants/venom", 1), List.of());

        assertEquals(composer.body(state), composer.compose(state, "SWORD", List.of(), List.of()));
    }

    @Test
    void maskLineLandsDirectlyBelowTheCrystalLineInTheBody() {
        // ADR-0053: a masked helmet's mask line is the LAST body line, immediately after the crystal line(s).
        LoreComposer composer = composer();
        CombatState state = new CombatState(Map.of(), List.of("crystals/a")).withMask("masks/agent");
        List<String> body = composer.body(state);
        assertEquals("§8S Aaa", body.get(body.size() - 2), "the crystal line sits directly above the mask line");
        assertEquals("§8Mask: Agent", body.get(body.size() - 1), "the mask line is the last body line, {NAME}→display");
    }

    @Test
    void blankMaskTemplateEmitsNoMaskLine() {
        // A null/blank mask template → no line even for a masked helmet (mirrors the crystal/orb/heroic sections).
        LoreComposer noMaskLine = new LoreComposer(LoreRenderer.Config.of(LoreStyle.DEFAULT, NAMES)
                .withCrystalLine(() -> "&8S {CRYSTAL}")); // maskLine defaults to null
        CombatState state = new CombatState(Map.of(), List.of("crystals/a")).withMask("masks/agent");
        List<String> body = noMaskLine.body(state);
        assertEquals(List.of("§8S Aaa"), body, "no mask template → only the crystal line, no mask line");
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
