package feature.heroic;

import compile.load.ContentHolder;
import compile.model.Ability;
import compile.model.CompiledEffect;
import compile.model.Snapshot;
import java.util.function.Predicate;

/**
 * ADR-0073 Decision 3 — a set whose completion bonus already folds its heroic wall in as a
 * {@code DAMAGE_MOD(side: defense)} row cannot also take the heroic stamp: {@code DamageFold} sums
 * {@code heroicReductionPercent + reductionPercent}, so the one wall would be billed from two channels
 * (the four M-Kit sets fold 45% and the stamp would add another 27% on top, uncapped).
 *
 * <p>Derived from the compiled bonus rather than a per-set flag, so the fold declares itself and a newly
 * authored set cannot forget to opt out. Cold path only — the upgrade gesture, never a hit.
 */
public final class HeroicSetFold {

    private HeroicSetFold() {
    }

    /** The gesture-time gate, reading the live library so a reload re-derives it. */
    public static Predicate<String> over(ContentHolder content) {
        return setKey -> foldsDefensiveWall(content.snapshot(), setKey);
    }

    /** Whether any {@code on: armor} bonus of {@code setKey} contributes to the defensive fold. */
    public static boolean foldsDefensiveWall(Snapshot snapshot, String setKey) {
        if (snapshot == null || setKey == null || setKey.isBlank()) {
            return false;
        }
        // Armour bonuses key to <setKey>, then /a1, /a2, … dense with no gaps (SetDefReader); /wN are the
        // weapon-held ones, which never pay while the piece is merely worn.
        for (int index = 0; ; index++) {
            Ability bonus = snapshot.byStableKey(index == 0 ? setKey : setKey + "/a" + index);
            if (bonus == null) {
                return false;
            }
            if (declaresDefensiveMod(bonus)) {
                return true;
            }
        }
    }

    private static boolean declaresDefensiveMod(Ability bonus) {
        for (CompiledEffect effect : bonus.effects()) {
            if ("DAMAGE_MOD".equals(effect.head())
                    && effect.args().has("side")
                    && "defense".equalsIgnoreCase(effect.args().str("side"))) {
                return true;
            }
        }
        return false;
    }
}
