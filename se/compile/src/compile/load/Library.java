package compile.load;

import compile.model.Snapshot;
import java.util.List;
import schema.diag.Diagnostic;

/**
 * The result of loading a content library (ADR-0014, ADR-0016): the compiled runtime {@link Snapshot}
 * the engine walks, the parsed {@link EnchantDef}/{@link CrystalDef}/{@link SetDef}/{@link ItemDef}
 * catalogs (display metadata for the render/apply cycles), the {@link TierRegistry}, and every
 * {@link Diagnostic} the load produced. Immutable; published by reference through {@link ContentHolder}.
 *
 * <p>A {@code Snapshot} is always present (the compiler always returns one); inspect
 * {@link #hasErrors()} before publishing — a load with blocking diagnostics keeps the previous
 * library live (transactional reload, §10).
 */
public record Library(Snapshot snapshot, List<EnchantDef> catalog, List<CrystalDef> crystals,
                      List<SetDef> sets, List<ItemDef> items, TierRegistry tiers,
                      List<Diagnostic> diagnostics) {

    public Library {
        catalog = List.copyOf(catalog);
        crystals = List.copyOf(crystals);
        sets = List.copyOf(sets);
        items = List.copyOf(items);
        diagnostics = List.copyOf(diagnostics);
    }

    /** The rarity tier of a stored base key (enchant/crystal/set), or {@code null} if unknown. */
    public String tierOf(String baseKey) {
        for (EnchantDef def : catalog) {
            if (def.key().equals(baseKey)) {
                return def.tier();
            }
        }
        for (CrystalDef def : crystals) {
            if (def.key().equals(baseKey)) {
                return def.tier();
            }
        }
        for (SetDef def : sets) {
            if (def.key().equals(baseKey)) {
                return def.tier();
            }
        }
        return null;
    }

    /** An empty library around an already-compiled (empty) snapshot — the boot-failure fallback. */
    public static Library empty(Snapshot snapshot, List<Diagnostic> diagnostics) {
        return new Library(snapshot, List.of(), List.of(), List.of(), List.of(), TierRegistry.BUILTIN, diagnostics);
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
        for (SetDef def : sets) {
            if (def.key().equals(baseKey)) {
                return def.display();
            }
        }
        return null;
    }
}
