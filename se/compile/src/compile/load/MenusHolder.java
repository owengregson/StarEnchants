package compile.load;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** The single published {@link MenusConfig}; swapped by reference in the reload transaction (atomic config). */
public final class MenusHolder {

    private final AtomicReference<MenusConfig> current = new AtomicReference<>();

    public MenusHolder(MenusConfig initial) {
        current.set(Objects.requireNonNull(initial, "initial"));
    }

    public MenusConfig config() {
        return current.get();
    }

    public void publish(MenusConfig config) {
        current.set(Objects.requireNonNull(config, "config"));
    }
}
