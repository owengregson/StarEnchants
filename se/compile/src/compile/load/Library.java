package compile.load;

import compile.model.Snapshot;
import java.util.List;
import schema.diag.Diagnostic;

/**
 * The result of loading a content library (ADR-0014, ADR-0016): the runtime {@link Snapshot}, the parsed
 * def catalogs, the {@link TierRegistry}, and the load's {@link Diagnostic}s. Immutable; published by
 * reference through {@link ContentHolder}. Inspect {@link #hasErrors()} before publishing — a blocking load
 * keeps the previous library live (transactional reload, §10). Carrier items (books, dust, scrolls) are not
 * content and have no catalog here; they are minted from {@code items/*.yml}.
 */
public record Library(Snapshot snapshot, List<EnchantDef> catalog, List<CrystalDef> crystals,
                      List<SetDef> sets, TierRegistry tiers,
                      List<Diagnostic> diagnostics) {

    public Library {
        catalog = List.copyOf(catalog);
        crystals = List.copyOf(crystals);
        sets = List.copyOf(sets);
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

    /** The parsed {@link CrystalDef} for a stored crystal base key, or {@code null} if none defines it. */
    public CrystalDef crystalDefOf(String baseKey) {
        for (CrystalDef def : crystals) {
            if (def.key().equals(baseKey)) {
                return def;
            }
        }
        return null;
    }

    /** The parsed {@link SetDef} for a stored set base key, or {@code null} if no content defines it. */
    public SetDef setDefOf(String baseKey) {
        for (SetDef def : sets) {
            if (def.key().equals(baseKey)) {
                return def;
            }
        }
        return null;
    }

    /** An empty library around an already-compiled (empty) snapshot — the boot-failure fallback. */
    public static Library empty(Snapshot snapshot, List<Diagnostic> diagnostics) {
        return new Library(snapshot, List.of(), List.of(), List.of(), TierRegistry.BUILTIN, diagnostics);
    }

    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(Diagnostic::blocking);
    }

    /**
     * The display name for a stored base key, or {@code null} if no content defines it (the renderer then
     * shows the unknown label, §5.3). Linear scan: fine for the cold render/apply paths, never combat.
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
