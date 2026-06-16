package compile.load;

import compile.model.Snapshot;
import java.util.List;
import schema.diag.Diagnostic;

/**
 * The result of loading a content library (ADR-0014): the compiled runtime {@link Snapshot} the
 * engine walks, the parsed {@link EnchantDef} + {@link CrystalDef} catalogs (display metadata for
 * the render/apply cycles), and every {@link Diagnostic} the load produced. Immutable; published by
 * reference through {@link ContentHolder}.
 *
 * <p>A {@code Snapshot} is always present (the compiler always returns one); inspect
 * {@link #hasErrors()} before publishing — a load with blocking diagnostics keeps the previous
 * library live (transactional reload, §10).
 */
public record Library(Snapshot snapshot, List<EnchantDef> catalog, List<CrystalDef> crystals,
                      List<Diagnostic> diagnostics) {

    public Library {
        catalog = List.copyOf(catalog);
        crystals = List.copyOf(crystals);
        diagnostics = List.copyOf(diagnostics);
    }

    /** Whether the load produced any blocking diagnostic (the caller then keeps the old library). */
    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(Diagnostic::blocking);
    }

    /**
     * The display name for a stored base key — enchant or crystal — or {@code null} if no content
     * defines it (the renderer shows such a key as the unknown label, §5.3). A linear scan over the
     * catalogs: fine for the cold render/apply paths, never the combat hot path.
     */
    public String displayNameOf(String baseKey) {
        for (EnchantDef def : catalog) {
            if (def.key().equals(baseKey)) {
                return def.display();
            }
        }
        for (CrystalDef def : crystals) {
            if (def.key().equals(baseKey)) {
                return def.display();
            }
        }
        return null;
    }
}
