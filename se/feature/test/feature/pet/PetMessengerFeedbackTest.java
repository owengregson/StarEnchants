package feature.pet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import compile.load.Lang;
import compile.load.MasterConfig;
import compile.load.PetDef;
import java.util.List;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import platform.lang.Messages;

/**
 * The universal out-of-range message (ADR-0061), end to end at the unit layer: the key SHIPS in the canonical
 * catalogue (the bundled lang.yml — Lang.defaults() parses that resource, so a missing key would render the
 * visible marker instead), and PetMessenger.outOfRange sends exactly its colour-translated, prefix-free text.
 */
class PetMessengerFeedbackTest {

    @Test
    void outOfRangeKeyShipsInTheCanonicalCatalogue() {
        assertTrue(Lang.defaults().has("feedback.out-of-range"),
                "feedback.out-of-range must ship in se/compile/resources/lang.yml");
    }

    @Test
    void outOfRangeSendsThePrefixFreeTranslatedLine() {
        Player player = mock(Player.class);
        PetMessenger messenger = new PetMessenger(new Messages(Lang::defaults),
                MasterConfig.PetsSection::defaults);

        messenger.outOfRange(player);

        // fragment (no prefix) + Colors.translate: & → § — the exact universal-send shape (UseItemRunner).
        verify(player).sendMessage("§c§l(!) You are too far away!");
    }

    @Test
    void noHomeSendsTheDefAuthoredLinePrefixFree() {
        Player player = mock(Player.class);
        PetMessenger messenger = new PetMessenger(new Messages(Lang::defaults),
                MasterConfig.PetsSection::defaults);

        messenger.noHome(player, def("&cNo burrow yet, {NAME}!"));

        // colour-translate + {NAME} substitution, never uppercased (the conversational-line rule).
        verify(player).sendMessage("§cNo burrow yet, Mole!");
    }

    @Test
    void noHomeFallsBackToTheUniversalFailLineWhenUnauthored() {
        Player noHomePlayer = mock(Player.class);
        Player failedPlayer = mock(Player.class);
        PetMessenger messenger = new PetMessenger(new Messages(Lang::defaults),
                MasterConfig.PetsSection::defaults);
        PetDef def = def("");

        messenger.noHome(noHomePlayer, def);
        messenger.failed(failedPlayer, def);

        // an un-authored no-home line IS the universal fail line — compare production to production, never a re-typed template.
        ArgumentCaptor<String> viaNoHome = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> viaFailed = ArgumentCaptor.forClass(String.class);
        verify(noHomePlayer).sendMessage(viaNoHome.capture());
        verify(failedPlayer).sendMessage(viaFailed.capture());
        assertEquals(viaFailed.getValue(), viaNoHome.getValue());
    }

    private static PetDef def(String noHome) {
        return new PetDef("mole", "Mole", "&7", true, "", "PLAYER_HEAD", List.of(), List.of(), "", noHome,
                null, 0, List.of());
    }
}
