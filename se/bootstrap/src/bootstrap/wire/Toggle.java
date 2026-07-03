package bootstrap.wire;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * One feature's master-config gate with its DECLARED depth — the axis that dissolves the silent
 * boot-vs-live inconsistency (ADR-0047): a feature's toggle depth is now a stated, introspectable
 * property ({@code /se modules}), not an accident of where the boolean happens to be read.
 */
public record Toggle(String key, Depth depth, BooleanSupplier enabled, String disabledLog) {

    /**
     * NONE = always on. BOOT = gates wire-time registration (restart to change). LIVE = read per
     * worn-resolve; the listeners register regardless (the actual gating stays in the worn resolver).
     */
    public enum Depth { NONE, BOOT, LIVE }

    /** The always-on toggle (no key, no gate) — the builder default. */
    public static final Toggle ALWAYS = new Toggle("", Depth.NONE, () -> true, "");

    public Toggle {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(depth, "depth");
        Objects.requireNonNull(enabled, "enabled");
        Objects.requireNonNull(disabledLog, "disabledLog");
    }

    /** A BOOT gate: {@code enabled} is read once at wire time; a false value skips the module's wires. */
    public static Toggle boot(String key, BooleanSupplier enabled, String disabledLog) {
        return new Toggle(key, Depth.BOOT, enabled, disabledLog);
    }

    /** A LIVE gate: introspection only — listeners register regardless; {@code enabled} is read per render. */
    public static Toggle live(String key, BooleanSupplier enabled) {
        return new Toggle(key, Depth.LIVE, enabled, "");
    }

    /** Whether this toggle gates wire-time listener registration (a false BOOT gate skips the module's wires). */
    public boolean gatesBoot() {
        return depth == Depth.BOOT;
    }
}
