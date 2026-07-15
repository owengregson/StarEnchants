package feature.menu;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import platform.lang.Messages;

/**
 * The player-facing menu shortcut ({@code /enchants}, {@code /pets}, …) opens the menu named by its TARGET, not
 * by its own label — this pins the {@code /enchants → enchanter} indirection so a routing regression fails in CI.
 * A non-player is rejected and an unknown target is reported rather than throwing. Server-free: the fake menu
 * overrides {@code open} so no Bukkit scheduling is touched.
 */
class UserMenuCommandTest {

    /** A menu that records who it was opened for, without the framework's Folia open-hop. */
    private static final class RecordingMenu implements Menu {
        private final String name;
        private Player openedFor;

        RecordingMenu(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public void render(MenuHolder holder) {
        }

        @Override
        public void open(Player player) {
            this.openedFor = player;
        }
    }

    private final Messages messages = mock(Messages.class);

    @Test
    void opensTheTargetMenuNotTheCommandLabel() {
        MenuRegistry registry = new MenuRegistry();
        RecordingMenu enchanter = new RecordingMenu("enchanter");
        registry.register(enchanter);
        // Labelled /enchants but targeting the enchanter bench — the label and the target deliberately differ.
        UserMenuCommand command = new UserMenuCommand("enchants", "enchanter", registry, messages);
        Player player = mock(Player.class);

        command.execute(player, "enchants", new String[0]);

        assertSame(player, enchanter.openedFor);
    }

    @Test
    void anUnknownTargetReportsInsteadOfOpening() {
        MenuRegistry registry = new MenuRegistry(); // the pets browser is NOT registered (family disabled)
        UserMenuCommand command = new UserMenuCommand("pets", "pets", registry, messages);
        Player player = mock(Player.class);

        command.execute(player, "pets", new String[0]); // must not throw — reports the unknown menu

        // nothing to open; the assertion is simply that execute returned without an NPE/throw
    }

    @Test
    void aNonPlayerIsRejected() {
        MenuRegistry registry = new MenuRegistry();
        RecordingMenu enchanter = new RecordingMenu("enchanter");
        registry.register(enchanter);
        UserMenuCommand command = new UserMenuCommand("enchanter", "enchanter", registry, messages);
        CommandSender console = mock(CommandSender.class);

        command.execute(console, "enchanter", new String[0]);

        assertNull(enchanter.openedFor); // the console never opens a menu
    }
}
