package compile.load;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The single published {@link Lang} (mirrors {@link ItemsHolder} / {@link MasterConfigHolder} for
 * {@code lang.yml}): an {@link AtomicReference} the runtime reads and the reload path swaps by reference,
 * on the global thread, in the same transaction as the content + items + master-config swap. The
 * {@code item.lang.Messages} facade reads through this holder, so a {@code /se reload} re-texts every
 * message with no re-wiring.
 */
public final class LangHolder {

    private final AtomicReference<Lang> current = new AtomicReference<>();

    public LangHolder(Lang initial) {
        current.set(Objects.requireNonNull(initial, "initial"));
    }

    /** The live language catalogue. */
    public Lang lang() {
        return current.get();
    }

    /** Publish a new catalogue by reference — the transactional reload swap. */
    public void publish(Lang lang) {
        current.set(Objects.requireNonNull(lang, "lang"));
    }
}
