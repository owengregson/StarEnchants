package bootstrap.wire;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.bukkit.command.Command;

/**
 * One dynamic command-map registration (no plugin.yml entry), carrying today's exact guarded-register +
 * WARNING-fallback semantics (ADR-0047). The fold skips it when {@code !enabled}, else registers it and logs
 * {@code successLog} when non-empty, catching any {@link Throwable} to log {@code failLog} at WARNING.
 */
public record DynCommand(String label, BooleanSupplier enabled, Supplier<Command> command,
                         String failLog, String successLog) {

    public DynCommand {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(enabled, "enabled");
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(failLog, "failLog");
        Objects.requireNonNull(successLog, "successLog");
    }

    /** The unconditional shape ({@code /enchants}, {@code /splitsouls}): always enabled, no success log. */
    public static DynCommand always(String label, Supplier<Command> command, String failLog) {
        return new DynCommand(label, () -> true, command, failLog, "");
    }
}
