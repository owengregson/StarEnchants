package compile.load;

import compile.model.Snapshot;
import java.util.List;
import schema.diag.Diagnostic;

/**
 * The result of loading a content library (ADR-0014): the compiled runtime {@link Snapshot} the
 * engine walks, the parsed {@link EnchantDef} catalog (metadata for the render/apply cycles), and
 * every {@link Diagnostic} the load produced. Immutable; published by reference through
 * {@link ContentHolder}.
 *
 * <p>A {@code Snapshot} is always present (the compiler always returns one); inspect
 * {@link #hasErrors()} before publishing — a load with blocking diagnostics keeps the previous
 * library live (transactional reload, §10).
 */
public record Library(Snapshot snapshot, List<EnchantDef> catalog, List<Diagnostic> diagnostics) {

    public Library {
        catalog = List.copyOf(catalog);
        diagnostics = List.copyOf(diagnostics);
    }

    /** Whether the load produced any blocking diagnostic (the caller then keeps the old library). */
    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(Diagnostic::blocking);
    }
}
