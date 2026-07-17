package feature.pet;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import compile.load.Lang;
import compile.load.MasterConfig;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
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
}
