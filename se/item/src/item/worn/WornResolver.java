package item.worn;

import compile.model.Ability;
import compile.model.Snapshot;
import compile.model.StableKeyIndex;
import item.codec.CombatState;
import item.codec.HeroicStat;
import item.view.ItemView;
import item.view.ItemViewCache;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

/**
 * Resolves a {@link LivingEntity}'s worn + held items into an immutable {@link WornState} on an
 * equipment change (docs/architecture.md §5.5; ADR-0014) — NEVER per hit. Each item is read once
 * through the {@link ItemViewCache}; each enchant's on-item {@code (baseKey, level)} composes its
 * path-derived per-level stable key ({@code <base>/<level>}, ADR-0014), which (with each crystal key)
 * resolves to a dense ability id via the snapshot's {@link StableKeyIndex}; the union over all sources
 * is flattened through {@link WornFlattener} into the per-trigger + per-direction arrays the combat hit
 * walks. An unknown key (an item enchanted under content no longer present) resolves to {@code -1} and
 * is skipped — never a crash. Multiplicity is preserved (an enchant on two pieces contributes twice).
 *
 * <p>Armour sets are resolved here too: each piece's {@code setKey}/{@code omni} flag (§6.6) feeds
 * {@link SetResolver}, whose active-set {@code BitSet} both joins {@link WornState#activeSets()} and
 * contributes each completed set's bonus ability id into the same union (a set's dense ability id is
 * its set id; its completion threshold is that ability's {@code setPieces}). Heroic flat stats are
 * not yet encoded on the item, so that source resolves as {@code NONE} for now. Trigger metadata
 * (count + attack/defense predicates) is injected so {@code se-item} stays free of an
 * {@code se-engine} dependency; the caller passes the current published {@link Snapshot}, so the
 * resolution is always against live content (reload-correct).
 *
 * <p><strong>Generation invariant.</strong> The caller must pass the {@link Snapshot} whose generation
 * matches the injected {@link ItemViewCache} — both are advanced together on reload (the
 * {@code ContentReloader} publish hook invalidates the cache as it swaps the snapshot). Resolution is
 * correct regardless (the on-item keys are version-independent strings; an absent key just misses),
 * but the resulting {@link WornState#gen()} is stamped from the snapshot, so a mismatched cache would
 * make a stale-equip check read as fresh.
 */
public final class WornResolver {

    private final ItemViewCache itemViews;
    private final int triggerCount;
    private final IntPredicate attackTrigger;
    private final IntPredicate defenseTrigger;

    public WornResolver(ItemViewCache itemViews, int triggerCount,
                        IntPredicate attackTrigger, IntPredicate defenseTrigger) {
        this.itemViews = itemViews;
        this.triggerCount = triggerCount;
        this.attackTrigger = attackTrigger;
        this.defenseTrigger = defenseTrigger;
    }

    /** Resolve {@code entity}'s worn armour + held items into a WornState against the current snapshot. */
    public WornState resolve(LivingEntity entity, Snapshot snapshot) {
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) {
            return WornState.empty(snapshot.generation());
        }
        List<CombatState> combats = new ArrayList<>();
        ItemStack[] armor = equipment.getArmorContents(); // some non-player entities return null historically
        if (armor != null) {
            for (ItemStack piece : armor) {
                addCombat(piece, combats);
            }
        }
        addCombat(equipment.getItemInMainHand(), combats);
        addCombat(equipment.getItemInOffHand(), combats); // off-hand shields/totems carry enchants too
        return resolveFrom(combats, snapshot.stableKeys(), snapshot.abilities(), snapshot.generation());
    }

    private void addCombat(ItemStack stack, List<CombatState> out) {
        if (stack == null) {
            return;
        }
        ItemView view = itemViews.of(stack);
        if (!view.isEmpty()) {
            out.add(view.combat());
        }
    }

    /**
     * The pure resolution + flatten over already-decoded combat states — the version-agnostic core,
     * unit-tested with hand-built keys/abilities (no server).
     */
    WornState resolveFrom(List<CombatState> combats, StableKeyIndex keys, Ability[] abilities, int generation) {
        List<Integer> mergedIds = new ArrayList<>();
        List<Integer> crystalIds = new ArrayList<>();
        List<Integer> wornSetIds = new ArrayList<>();
        int omniCount = 0;
        HeroicStat heroic = HeroicStat.NONE;
        for (CombatState combat : combats) {
            heroic = heroic.plus(combat.heroic()); // heroic flat stats sum across every worn piece (§6)
            for (Map.Entry<String, Integer> enchant : combat.enchants().entrySet()) {
                int id = keys.idOf(enchant.getKey() + "/" + enchant.getValue());
                if (id >= 0) {
                    mergedIds.add(id);
                }
            }
            for (String crystalKey : combat.crystals()) {
                int id = keys.idOf(crystalKey);
                if (id >= 0) {
                    mergedIds.add(id);   // crystals fire on triggers like any source...
                    crystalIds.add(id);  // ...and are tracked as the dedicated crystal source (§5.5)
                }
            }
            // Set membership: an omni piece is a wildcard (counts toward any partially-worn set, §6.6);
            // a normal piece contributes its set's bonus ability id (the set's dense id is its "set id").
            if (combat.omni()) {
                omniCount++;
            } else if (combat.setKey() != null) {
                wornSetIds.add(keys.idOf(combat.setKey())); // -1 for unknown content → SetResolver ignores it
            }
        }
        // A set's bonus ability id is its set id; its completion threshold is that ability's setPieces.
        BitSet activeSets = SetResolver.activeSets(toIntArray(wornSetIds), omniCount,
                setId -> setId >= 0 && setId < abilities.length ? abilities[setId].setPieces() : 0);
        for (int setId = activeSets.nextSetBit(0); setId >= 0; setId = activeSets.nextSetBit(setId + 1)) {
            mergedIds.add(setId); // an active set's bonus fires on triggers like any other source
        }
        return WornFlattener.flatten(generation, toIntArray(mergedIds), abilities, triggerCount,
                activeSets, toIntArray(crystalIds), heroic, attackTrigger, defenseTrigger);
    }

    private static int[] toIntArray(List<Integer> values) {
        int[] out = new int[values.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = values.get(i);
        }
        return out;
    }
}
