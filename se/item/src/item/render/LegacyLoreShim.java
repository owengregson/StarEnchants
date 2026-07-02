package item.render;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * One-release migration of pre-composer lore (ADR-0040 §migration). Before {@link LoreComposer} owned the whole
 * gear lore, ownership of a line was encoded in its visible text: separate writers stamped protection / trak
 * lines and coordinated through prefix classifiers. This shim is the ONLY place those classifiers survive, and it
 * runs at most once per item — gated by {@link item.codec.ComposerMark} — so the visible-text hazard never
 * reaches the permanent (marked) path.
 *
 * <p>On an unmarked item it reconciles the old lore with the freshly composed lore: it drops every line the
 * composer now renders from state (a recognised legacy managed line, or a line the fresh render reproduces) and
 * keeps genuine authored/base lore as a head above the composed sections — so a pre-composer item's authored
 * flavour survives the switch to composer-owned lore, while stale/duplicate managed lines do not.
 */
final class LegacyLoreShim {

    private LegacyLoreShim() {
    }

    static List<String> migrate(List<String> existing, List<String> composed, Predicate<String> legacyManagedLine) {
        List<String> head = new ArrayList<>();
        for (String line : existing) {
            if (legacyManagedLine.test(line) || composed.contains(line)) {
                continue; // the composer owns this line now — it is (re)produced from state below
            }
            head.add(line); // genuine authored/base lore the composer does not derive → keep it once
        }
        if (head.isEmpty()) {
            return composed;
        }
        List<String> out = new ArrayList<>(head.size() + composed.size());
        out.addAll(head);
        out.addAll(composed);
        return out;
    }
}
