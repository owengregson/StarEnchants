package compile.load;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** The single published {@link MasterConfig}; swapped by reference in the reload transaction (atomic config). */
public final class MasterConfigHolder {

    private final AtomicReference<MasterConfig> current = new AtomicReference<>();

    public MasterConfigHolder(MasterConfig initial) {
        current.set(Objects.requireNonNull(initial, "initial"));
    }

    public MasterConfig config() {
        return current.get();
    }

    public void publish(MasterConfig config) {
        current.set(Objects.requireNonNull(config, "config"));
    }
}
