package compile.load;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The single published {@link MenusConfig} (mirrors {@link ItemsHolder} for the top-level {@code menus/}
 * folder): an {@link AtomicReference} the menu framework reads and the reload path swaps by reference, on the
 * global thread, in the same transaction as content + items + config + lang. Each menu reads through this
 * holder when it renders, so a {@code /se reload} re-lays-out the next open with no re-registration.
 */
public final class MenusHolder {

    private final AtomicReference<MenusConfig> current = new AtomicReference<>();

    public MenusHolder(MenusConfig initial) {
        current.set(Objects.requireNonNull(initial, "initial"));
    }

    /** The live menus config. */
    public MenusConfig config() {
        return current.get();
    }

    /** Publish a new menus config by reference — the transactional reload swap. */
    public void publish(MenusConfig config) {
        current.set(Objects.requireNonNull(config, "config"));
    }
}
