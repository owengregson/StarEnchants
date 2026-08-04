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
            "masks/agent", "Agent",
            "masks/blaze", "Blaze",
            "reforges/testforge", "Testforge")::get;
    private static final String HEROIC = "&6&lHEROIC {TYPE} (&e{+/-}{AMOUNT}% DMG&7)";

    private static LoreComposer composer() {
        return new LoreComposer(LoreRenderer.Config.of(LoreStyle.DEFAULT, NAMES)
                .withBaseSlots(() -> 9)
                .withSlotsLine(() -> "&a&l{TOTAL} Slots +{ADDED}")
                .withCrystalLine(() -> "&8S {CRYSTAL}")
                .withCrystalLineMulti(() -> "&8M {CRYSTAL}")
                .withMaskLine(() -> "&8Mask: {NAME}")
                .withMaskLineMulti(() -> "&8Masks: {NAME}")
                .withReforgeLine(() -> "&8Reforge: {NAME}")
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
    void anArmourSetPieceRendersItsOwnSlotsLoreAndTheSlotlessReadStaysShared() {
        // §6.6: the discriminator is the item's gear KIND, so nothing has to be stamped on the piece to say
        // which member it is. A composer that ignored the kind would render every piece the shared block.
        List<String> shared = List.of("&7SET BONUS");
        List<String> bootsOnly = List.of("&7&oFlavour.");
        LoreComposer composer = new LoreComposer(LoreRenderer.Config.of(LoreStyle.DEFAULT, NAMES)
                .withSetLore(new LoreRenderer.SetLore() {
                    @Override public List<String> armor(String setKey) {
                        return shared;
                    }

                    @Override public List<String> armor(String setKey, String slotToken) {
                        return "BOOTS".equals(slotToken)
                                ? List.of(bootsOnly.get(0), shared.get(0)) : shared;
                    }

                    @Override public List<String> weapon(String setKey) {
                        return List.of();
                    }
                }));
        CombatState piece = new CombatState(Map.of(), List.of(), "sets/ghost", false);

        assertEquals(List.of("§7§oFlavour.", "§7SET BONUS"), composer.body(piece, "BOOTS"));
        assertEquals(List.of("§7SET BONUS"), composer.body(piece, "HELMET"));
        // the slot-less read (menu previews, fixtures) keeps the pre-per-piece behaviour exactly
        assertEquals(List.of("§7SET BONUS"), composer.body(piece));
    }

    @Test
    void aSetLoreLookupThatOverridesNeitherArmorOverloadRendersTheSharedBlockForEverySlot() {
        // The default method on the interface: an implementation written before per-piece lore existed keeps
        // answering for every slot, so no wiring silently loses a set's lore when the kind is threaded in.
        List<String> shared = List.of("&7SET BONUS");
        LoreComposer composer = new LoreComposer(LoreRenderer.Config.of(LoreStyle.DEFAULT, NAMES)
                .withSetLore(new LoreRenderer.SetLore() {
                    @Override public List<String> armor(String setKey) {
                        return shared;
                    }

                    @Override public List<String> weapon(String setKey) {
                        return List.of();
                    }
                }));
        CombatState piece = new CombatState(Map.of(), List.of(), "sets/ghost", false);

        assertEquals(composer.body(piece), composer.body(piece, "BOOTS"));
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
    void aCompositeHelmetTakesTheMultiTemplateAndNamesEveryChild() {
        // ADR-0074, the Multi Crystal line's twin: a folded mask renders from its own template with {NAME}
        // reading every child. A composite falling through to the single template would name only one of them.
        LoreComposer composer = composer();
        CombatState state = new CombatState(Map.of(), List.of()).withMask("masks/agent+masks/blaze");
        List<String> body = composer.body(state);
        // The join separator is the template's own leading colour run + ", " (StyledNames), so each child's gap
        // resets to the line's base colour before the next name supplies its own — the Multi Crystal rule.
        assertEquals("§8Masks: Agent§8, Blaze", body.get(body.size() - 1));
    }

    @Test
    void aPlainMaskStillTakesTheSingleTemplateWhenBothAreWired() {
        LoreComposer composer = composer();
        CombatState state = new CombatState(Map.of(), List.of()).withMask("masks/agent");
        List<String> body = composer.body(state);
        assertEquals("§8Mask: Agent", body.get(body.size() - 1), "one child is not a composite");
    }

    @Test
    void aCompositeFallsBackToTheSingleTemplateWhenNoMultiIsWired() {
        // The cascade (ADR-0035's, reused): a pack that sets only `lore-while-on-item` still renders a folded
        // mask — from the one template it has, naming every child through the same {NAME} join.
        LoreComposer noMulti = new LoreComposer(LoreRenderer.Config.of(LoreStyle.DEFAULT, NAMES)
                .withMaskLine(() -> "&8Mask: {NAME}"));
        CombatState state = new CombatState(Map.of(), List.of()).withMask("masks/agent+masks/blaze");
        assertEquals(List.of("§8Mask: Agent§8, Blaze"), noMulti.body(state));
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
    void reforgeLineLandsBetweenSlotsAndCrystalLines() {
        // ADR-0070: on a reforged weapon the reforge line sits directly BELOW the orb-slots line and ABOVE the
        // crystal line(s) — the owner-pinned on-weapon position. {NAME} → the reforge's styled display.
        LoreComposer composer = composer();
        CombatState state = new CombatState(Map.of(), List.of("crystals/a"), null, false, HeroicStat.NONE, 2)
                .withReforge("reforges/testforge");
        List<String> body = composer.body(state);
        assertEquals(List.of("§a§l11 Slots +2", "§8Reforge: Testforge", "§8S Aaa"), body,
                "slots line, then reforge line, then crystal line");
    }

    @Test
    void reforgeLineRendersWithoutSlotsLine() {
        // The reforge line does NOT depend on the orb-slots line (which renders only when added>0) — a reforged
        // weapon with zero purchased slots still shows it (contracts §3 independence clause).
        LoreComposer composer = composer();
        CombatState state = new CombatState(Map.of(), List.of()).withReforge("reforges/testforge");
        assertEquals(List.of("§8Reforge: Testforge"), composer.body(state));
    }

    @Test
    void blankReforgeTemplateEmitsNoReforgeLine() {
        // A null/blank reforge template → no line even for a reforged weapon (mirrors the mask/crystal sections).
        LoreComposer noReforgeLine = new LoreComposer(LoreRenderer.Config.of(LoreStyle.DEFAULT, NAMES)
                .withCrystalLine(() -> "&8S {CRYSTAL}")); // reforgeLine defaults to null
        CombatState state = new CombatState(Map.of(), List.of("crystals/a")).withReforge("reforges/testforge");
        assertEquals(List.of("§8S Aaa"), noReforgeLine.body(state),
                "no reforge template → only the crystal line, no reforge line");
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
