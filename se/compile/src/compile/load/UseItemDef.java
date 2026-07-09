package compile.load;

import java.util.List;

/**
 * Non-runtime metadata of one authored use-item (§3, docs/decisions/0048-use-items.md) — a right-click item
 * whose {@code content/use-items/<key>.yml} declares BOTH its likeness (material/name/lore/shiny) and the
 * engine abilities that fire when a player right-clicks holding it. The abilities erase into {@code AbilityDef}s
 * (trigger forced to the implicit {@code USE}, {@link compile.model.SourceKind#USE_ITEM}) exactly like every
 * other source, so a use-item passes the full gate sequence (suppression, cooldown, condition, chance, souls)
 * and interacts with every other feature for free.
 *
 * <p>The {@code key} is the filename stem an item stores through the item codec; the ability {@link #stableKeys}
 * (namespaced {@code use:<key>}, then {@code use:<key>/a1}, …) are what the runtime resolves in the snapshot's
 * stable-key index. The two are decoupled on purpose: the codec identity survives a snapshot rebuild, and the
 * dense ability ids are re-derived from the stable keys after any reorder.
 *
 * @param key              filename stem; the item codec stores it and {@link Library#useItemDefOf} looks it up
 * @param name             styled display name (colour carried inline); used verbatim on the item and in messages
 * @param material         raw material token; resolved cross-version at mint, not here
 * @param lore             authored lore lines, verbatim (the feature layer substitutes {@code {TIME_FORMATTED}})
 * @param consumable       whether one item is consumed on a successful use ({@code true}) vs a reusable tool
 * @param isFood           whether the item must be EATEN (real eat animation) to trigger, not click-triggered;
 *                         forces edibility at mint on 1.20.5+ and is a no-op below (degrades to one-click).
 *                         Composes with {@link #consumable}: it decides HOW you trigger, {@code consumable}
 *                         decides WHETHER one item is removed on a successful use.
 * @param shiny            whether the minted item carries an enchant glint
 * @param permission       Bukkit permission gating use, checked at use time; {@code ""} = everyone
 * @param cooldownTicks    ability a0's cooldown, surfaced in lore as {@code {TIME_FORMATTED}}
 * @param conditionSources authored condition strings, one per ability aligned to {@link #stableKeys} ({@code ""}
 *                         where an ability declares none); the fail message renders {@code {CONDITION}} from these
 * @param stableKeys       the claimed ability stable keys in order — {@code use:<key>}, {@code use:<key>/a1}, …
 */
public record UseItemDef(
        String key,
        String name,
        String material,
        List<String> lore,
        boolean consumable,
        boolean isFood,
        boolean shiny,
        String permission,
        int cooldownTicks,
        List<String> conditionSources,
        List<String> stableKeys) {

    public UseItemDef {
        lore = List.copyOf(lore);
        conditionSources = List.copyOf(conditionSources);
        stableKeys = List.copyOf(stableKeys);
    }
}
