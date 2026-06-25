package compile.load;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** The single published {@link Lang}; swapped by reference in the reload transaction (atomic config). */
public final class LangHolder {

    private final AtomicReference<Lang> current = new AtomicReference<>();

    public LangHolder(Lang initial) {
        current.set(Objects.requireNonNull(initial, "initial"));
    }

    public Lang lang() {
        return current.get();
    }

    public void publish(Lang lang) {
        current.set(Objects.requireNonNull(lang, "lang"));
    }
}
