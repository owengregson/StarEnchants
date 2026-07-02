package platform.lang;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import compile.load.Lang;
import java.util.List;
import java.util.Map;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/**
 * Pins the ADR-0041 policy-seam methods: {@link Messages#sendText} and {@link Messages#sendLines} both honour
 * the feedback gate + PAPI passthrough, {@code sendText} skips null/blank, and {@code sendLines} keeps blank
 * spacer lines verbatim.
 */
class MessagesTest {

    private static Lang linesLang() {
        return new Lang(Map.of(), Map.of("test.lines", List.of(" ", "&aON", " ")), List.of());
    }

    @Test
    void sendTextAppliesGateAndPlaceholders() {
        Messages messages = new Messages(Lang::defaults, () -> "", () -> true, (p, s) -> s + "!");
        Player player = mock(Player.class);
        messages.sendText(player, "&ahi");
        verify(player).sendMessage("&ahi!");
    }

    @Test
    void sendTextSkipsNullBlankAndGatedOff() {
        Player player = mock(Player.class);
        new Messages(Lang::defaults, () -> "", () -> true).sendText(player, null);
        new Messages(Lang::defaults, () -> "", () -> true).sendText(player, " ");
        new Messages(Lang::defaults, () -> "", () -> false).sendText(player, "&ahi");
        verify(player, never()).sendMessage(any(String.class));
    }

    @Test
    void sendLinesPreservesBlankSpacers() {
        Player player = mock(Player.class);
        new Messages(() -> linesLang(), () -> "", () -> true).sendLines(player, "test.lines");
        InOrder order = inOrder(player);
        order.verify(player).sendMessage(" ");
        order.verify(player).sendMessage("§aON"); // Messages.lines translates & → §
        order.verify(player).sendMessage(" ");

        Player gated = mock(Player.class);
        new Messages(() -> linesLang(), () -> "", () -> false).sendLines(gated, "test.lines");
        verify(gated, never()).sendMessage(any(String.class));
    }
}
