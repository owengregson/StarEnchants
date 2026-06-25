package compile.load;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** The single published {@link ItemsConfig}; swapped by reference in the reload transaction (atomic config). */
public final class ItemsHolder {

    private final AtomicReference<ItemsConfig> current = new AtomicReference<>();

    public ItemsHolder(ItemsConfig initial) {
        current.set(Objects.requireNonNull(initial, "initial"));
    }

    public ItemsConfig config() {
        return current.get();
    }

    public void publish(ItemsConfig config) {
        current.set(Objects.requireNonNull(config, "config"));
    }
}
