package compile.load;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The single published {@link ItemsConfig} (mirrors {@link ContentHolder} for the top-level {@code items/}
 * folder): an {@link AtomicReference} the runtime reads and the reload path swaps by reference, on the
 * global thread, in the same transaction as the content swap. A reader always sees a fully-built,
 * immutable config, never a torn state.
 */
public final class ItemsHolder {

    private final AtomicReference<ItemsConfig> current = new AtomicReference<>();

    public ItemsHolder(ItemsConfig initial) {
        current.set(Objects.requireNonNull(initial, "initial"));
    }

    /** The live items config. */
    public ItemsConfig config() {
        return current.get();
    }

    /** Publish a new items config by reference — the transactional reload swap. */
    public void publish(ItemsConfig config) {
        current.set(Objects.requireNonNull(config, "config"));
    }
}
