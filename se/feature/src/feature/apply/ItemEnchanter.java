package feature.apply;

import compile.load.ContentHolder;
import compile.load.CrystalDef;
import compile.load.EnchantDef;
import compile.model.Snapshot;
import item.codec.CombatCodec;
import item.codec.CombatState;
import item.render.LoreRenderer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Applies enchants and crystals to an item (docs/architecture.md §4.2) — the one cold mutation path
 * that validates against the live content, writes the {@link CombatState} into PDC, and re-renders
 * the lore from that state. Identity stays in PDC; lore is a pure projection rendered here, never
 * parsed back. Validation ({@link #check}) is separated from mutation so the eligibility rules
 * (enchant exists, level in range, {@code applies-to} matches the item) are unit-testable with no
 * server; only {@link #applyEnchant}/{@link #applyCrystal} touch an {@link ItemStack}.
 *
 * <p>Reads the current library through the {@link ContentHolder} on every call, so an apply after a
 * {@code /se reload} validates and renders against the new content.
 */
public final class ItemEnchanter {

    /** A sanity cap on crystals per item — they stack (§6.5), but unbounded growth bloats PDC. */
    private static final int MAX_CRYSTALS = 16;

    private final CombatCodec codec;
    private final LoreRenderer lore;
    private final ContentHolder content;
    private final platform.item.ItemGroups groups;

    public ItemEnchanter(CombatCodec codec, LoreRenderer lore, ContentHolder content,
                         platform.item.ItemGroups groups) {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.lore = Objects.requireNonNull(lore, "lore");
        this.content = Objects.requireNonNull(content, "content");
        this.groups = Objects.requireNonNull(groups, "groups");
    }

    /** Validate (without mutating) that enchant {@code baseKey} at {@code level} may sit on {@code material}. */
    public ApplyResult checkEnchant(Material material, String baseKey, int level) {
        EnchantDef def = enchant(baseKey);
        if (def == null) {
            return ApplyResult.fail("§cNo such enchant: §f" + baseKey);
        }
        if (level < 1 || level > def.maxLevel()) {
            return ApplyResult.fail("§cLevel must be 1–" + def.maxLevel() + " for §f" + baseKey);
        }
        Snapshot snapshot = content.snapshot();
        if (snapshot.byStableKey(baseKey + "/" + level) == null) {
            return ApplyResult.fail("§cLevel " + level + " of §f" + baseKey + " §cis not defined.");
        }
        if (!groups.matches(material, def.appliesTo())) {
            return ApplyResult.fail("§c" + def.display() + " §ccannot be applied to that item.");
        }
        return ApplyResult.ok("§a" + def.display());
    }

    /** Validate (without mutating) that crystal {@code baseKey} may sit on {@code material}. */
    public ApplyResult checkCrystal(Material material, String baseKey) {
        CrystalDef def = crystal(baseKey);
        if (def == null) {
            return ApplyResult.fail("§cNo such crystal: §f" + baseKey);
        }
        if (content.snapshot().byStableKey(baseKey) == null) {
            return ApplyResult.fail("§c" + baseKey + " §cdid not compile.");
        }
        if (!groups.matches(material, def.appliesTo())) {
            return ApplyResult.fail("§c" + def.display() + " §ccannot be applied to that item.");
        }
        return ApplyResult.ok("§a" + def.display());
    }

    /** Apply enchant {@code baseKey} at {@code level} to {@code stack} in place; re-renders the lore. */
    public ApplyResult applyEnchant(ItemStack stack, String baseKey, int level) {
        if (stack == null || stack.getType() == Material.AIR) {
            return ApplyResult.fail("§cHold an item first.");
        }
        ApplyResult check = checkEnchant(stack.getType(), baseKey, level);
        if (!check.ok()) {
            return check;
        }
        CombatState current = codec.read(stack);
        Map<String, Integer> enchants = new LinkedHashMap<>(current.enchants());
        enchants.put(baseKey, level);
        CombatState next = new CombatState(enchants, current.crystals(), current.setKey(), current.omni());
        codec.write(stack, next);
        lore.apply(stack, next);
        return ApplyResult.ok(check.message() + " §7applied (level " + level + ").");
    }

    /** Append crystal {@code baseKey} to {@code stack} in place (crystals stack); re-renders the lore. */
    public ApplyResult applyCrystal(ItemStack stack, String baseKey) {
        if (stack == null || stack.getType() == Material.AIR) {
            return ApplyResult.fail("§cHold an item first.");
        }
        ApplyResult check = checkCrystal(stack.getType(), baseKey);
        if (!check.ok()) {
            return check;
        }
        CombatState current = codec.read(stack);
        if (current.crystals().size() >= MAX_CRYSTALS) {
            return ApplyResult.fail("§cThis item already holds the maximum " + MAX_CRYSTALS + " crystals.");
        }
        List<String> crystals = new ArrayList<>(current.crystals());
        crystals.add(baseKey); // crystals stack — a list, never collapsed (§6.5)
        CombatState next = new CombatState(current.enchants(), crystals, current.setKey(), current.omni());
        codec.write(stack, next);
        lore.apply(stack, next);
        return ApplyResult.ok(check.message() + " §7crystal applied.");
    }

    private EnchantDef enchant(String baseKey) {
        for (EnchantDef def : content.library().catalog()) {
            if (def.key().equals(baseKey)) {
                return def;
            }
        }
        return null;
    }

    private CrystalDef crystal(String baseKey) {
        for (CrystalDef def : content.library().crystals()) {
            if (def.key().equals(baseKey)) {
                return def;
            }
        }
        return null;
    }
}
