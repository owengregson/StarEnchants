package compile.model;

import java.util.Locale;

/** The suppress/cooldown scope-kind wire ids packed into {@code CooldownStore.key} — written by the erase
 *  stage (SUPPRESS scope lowering) and read by gate 5 and the passive-potion driver. One holder so the three
 *  modules can no longer drift apart on comment-coupled magic ints. */
public final class ScopeKinds {
    public static final int ENCHANT = 0;
    public static final int GROUP = 1;
    public static final int TYPE = 2;

    private ScopeKinds() { }

    /** The kind for a SUPPRESS scope token; anything unrecognised is ENCHANT (the erase-stage default). */
    public static int of(String scope) {
        return switch (scope.toUpperCase(Locale.ROOT)) {
            case "GROUP" -> GROUP;
            case "TYPE" -> TYPE;
            default -> ENCHANT;
        };
    }
}
