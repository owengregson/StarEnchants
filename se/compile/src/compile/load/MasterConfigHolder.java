package compile.load;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The single published {@link MasterConfig} (mirrors {@link ItemsHolder} / {@link ContentHolder}): an
 * {@link AtomicReference} the reload path swaps by reference on the global thread, in the same transaction
 * as the content + items swap. A reader always sees a fully-built, immutable config, never a torn state, so
 * knobs read through a supplier off this holder pick up a {@code /se reload} live.
 */
public final class MasterConfigHolder {

    private final AtomicReference<MasterConfig> current = new AtomicReference<>();

    public MasterConfigHolder(MasterConfig initial) {
        current.set(Objects.requireNonNull(initial, "initial"));
    }

    /** The live master config. */
    public MasterConfig config() {
        return current.get();
    }

    /** Publish a new master config by reference — the transactional reload swap. */
    public void publish(MasterConfig config) {
        current.set(Objects.requireNonNull(config, "config"));
    }
}
