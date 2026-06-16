package item.worn;

import compile.model.Ability;
import compile.model.Snapshot;
import compile.model.StableKeyIndex;
import item.codec.CombatState;
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
 * <p>Sets / weapon set / heroic are not yet encoded on the item (the codec carries enchants + crystals
 * today), so those sources resolve as empty for now and join when the codec encodes them. Trigger
 * metadata (count + attack/defense predicates) is injected so {@code se-item} stays free of an
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
        for (CombatState combat : combats) {
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
        }
        return WornFlattener.flatten(generation, toIntArray(mergedIds), abilities, triggerCount,
                new BitSet(), toIntArray(crystalIds), HeroicStat.NONE, attackTrigger, defenseTrigger);
    }

    private static int[] toIntArray(List<Integer> values) {
        int[] out = new int[values.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = values.get(i);
        }
        return out;
    }
}
