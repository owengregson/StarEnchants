package feature.heroic;

import compile.load.ContentHolder;
import compile.load.Library;
import compile.load.SetDef;
import java.util.function.Predicate;

/**
 * ADR-0073 Decision 3 — a set whose completion bonus already folds its heroic wall in cannot also take the
 * heroic stamp: {@code DamageFold} sums {@code heroicReductionPercent + reductionPercent}, so the one wall
 * would be billed from two channels (the four M-Kit sets fold 45% and the stamp would add another 27% on top,
 * uncapped).
 *
 * <p>The set declares it ({@code folds-heroic: true}); it is NOT inferred from the bonus. A
 * {@code DAMAGE_MOD(side: defense)} row is near-universal across the shipped packs — supreme's is a damage-
 * TAKEN penalty — so deriving the refusal from one would refuse the upgrade on almost every set on the server.
 * Cold path only: the upgrade gesture, never a hit.
 */
public final class HeroicSetFold {

    private HeroicSetFold() {
    }

    /** The gesture-time gate, reading the live library so a reload re-reads the marker. */
    public static Predicate<String> over(ContentHolder content) {
        return setKey -> foldsHeroicWall(content.library(), setKey);
    }

    /** Whether {@code setKey} names a set that declares {@code folds-heroic}. A piece of no set never refuses. */
    public static boolean foldsHeroicWall(Library library, String setKey) {
        if (library == null || setKey == null || setKey.isBlank()) {
            return false;
        }
        SetDef def = library.setDefOf(setKey);
        return def != null && def.foldsHeroic();
    }
}
