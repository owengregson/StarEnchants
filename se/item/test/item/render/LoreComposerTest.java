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
            "reforges/testforge", "Testforge")::get;
    private static final String HEROIC = "&6&lHEROIC {TYPE} (&e{+/-}{AMOUNT}% DMG&7)";

    private static LoreComposer composer() {
        return new LoreComposer(LoreRenderer.Config.of(LoreStyle.DEFAULT, NAMES)
                .withBaseSlots(() -> 9)
                .withSlotsLine(() -> "&a&l{TOTAL} Slots +{ADDED}")
                .withCrystalLine(() -> "&8S {CRYSTAL}")
                .withCrystalLineMulti(() -> "&8M {CRYSTAL}")
                .withMaskLine(() -> "&8Mask: {NAME}")
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
    void setMemberTokenSelectsExactLoreAndSuppressesDuplicateGenericHeroicLine() {
        LoreRenderer.SetLore setLore = new LoreRenderer.SetLore() {
            @Override public List<String> armor(String setKey) {
                return List.of("&3Shared set bonus");
            }

            @Override public List<String> armor(String setKey, String memberKey) {
                if (memberKey == null) {
                    return armor(setKey);
                }
                return List.of("&7Ghostly " + memberKey, "", "&7+2 Armor Value", "&3Shared set bonus");
            }

            @Override public boolean authoredHeroic(String setKey, String memberKey) {
                return "helmet".equals(memberKey);
            }

            @Override public List<String> weapon(String setKey) {
                return List.of("&7Set weapon");
            }
        };
        LoreComposer exact = new LoreComposer(LoreRenderer.Config.of(LoreStyle.DEFAULT, NAMES)
                .withSetLore(setLore)
                .withHeroicLine(() -> HEROIC));
        CombatState helmet = new CombatState(Map.of(), List.of(), "sets/ghost", "helmet", null,
                false, new HeroicStat(0, 0.08, 0, 0, 0.5), 0, null, null);

        assertEquals(List.of("§7Ghostly helmet", "", "§7+2 Armor Value", "§3Shared set bonus"),
                exact.compose(helmet, "HELMET", List.of(), List.of()),
                "the authored member already contains exact Heroic presentation, so no generic line is appended");

        CombatState legacyPiece = new CombatState(Map.of(), List.of(), "sets/ghost", false,
                new HeroicStat(0, 0.08, 0));
        assertEquals(List.of("§3Shared set bonus", "§6§lHEROIC HELMET (§e-8% DMG§7)"),
                exact.compose(legacyPiece, "HELMET", List.of(), List.of()),
                "old pieces without a member token use shared lore and retain the generic Heroic marker");
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
    void maskSummarySectionRendersOnlyForMasksWithAnAbilitySummary() {
        LoreComposer withSummary = new LoreComposer(LoreRenderer.Config.of(LoreStyle.DEFAULT, NAMES)
                .withMaskLine(() -> "&7&lATTACHED: {NAME}{SUMMARY_SECTION}")
                .withMaskSummaryOf(key -> "50% Mastery Enchant Negation."));
        CombatState mask = new CombatState(Map.of(), List.of()).withMask("masks/agent");
        assertEquals(List.of("§7§lATTACHED: Agent§f (§c50% Mastery Enchant Negation.§f)"),
                withSummary.body(mask));

        LoreComposer cosmetic = new LoreComposer(LoreRenderer.Config.of(LoreStyle.DEFAULT, NAMES)
                .withMaskLine(() -> "&7&lATTACHED: {NAME}{SUMMARY_SECTION}")
                .withMaskSummaryOf(key -> ""));
        assertEquals(List.of("§7§lATTACHED: Agent"), cosmetic.body(mask),
                "a null-special-ability mask must not render empty parentheses");
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
