package compile.load;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The single published {@link ItemsConfig} (mirrors {@link ContentHolder} for the top-level {@code items/}
 * folder): an {@link AtomicReference} swapped by reference on the global thread, in the same transaction as
 * the content swap, so a reader never sees a torn state.
 */
public final class ItemsHolder {

    private final AtomicReference<ItemsConfig> current = new AtomicReference<>();

    public ItemsHolder(ItemsConfig initial) {
        current.set(Objects.requireNonNull(initial, "initial"));
    }

    public ItemsConfig config() {
        return current.get();
    }

    /** Publish a new config by reference — the transactional reload swap. */
    public void publish(ItemsConfig config) {
        current.set(Objects.requireNonNull(config, "config"));
    }
}
