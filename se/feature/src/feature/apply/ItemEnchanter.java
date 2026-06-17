package feature.apply;

import compile.load.ContentHolder;
import compile.load.CrystalDef;
import compile.load.EnchantDef;
import compile.model.Snapshot;
import engine.interact.SlotLedger;
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

    /** The default base enchant-slot capacity (docs/v3-directives.md §H; was 6, now 9 by default). */
    public static final int DEFAULT_BASE_SLOTS = 9;

    /** The default per-item crystal-slot capacity (§E; the master config supplies it later). */
    public static final int DEFAULT_CRYSTAL_SLOTS = 1;

    private final CombatCodec codec;
    private final LoreRenderer lore;
    private final ContentHolder content;
    private final platform.item.ItemGroups groups;
    private final int baseSlots;
    private final int crystalSlots;

    public ItemEnchanter(CombatCodec codec, LoreRenderer lore, ContentHolder content,
                         platform.item.ItemGroups groups) {
        this(codec, lore, content, groups, DEFAULT_BASE_SLOTS);
    }

    /** As above, with a configurable base enchant-slot count (§H; the master config supplies it). */
    public ItemEnchanter(CombatCodec codec, LoreRenderer lore, ContentHolder content,
                         platform.item.ItemGroups groups, int baseSlots) {
        this(codec, lore, content, groups, baseSlots, DEFAULT_CRYSTAL_SLOTS);
    }

    /** As above, with configurable enchant AND crystal slot counts (§H/§E; the master config supplies them). */
    public ItemEnchanter(CombatCodec codec, LoreRenderer lore, ContentHolder content,
                         platform.item.ItemGroups groups, int baseSlots, int crystalSlots) {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.lore = Objects.requireNonNull(lore, "lore");
        this.content = Objects.requireNonNull(content, "content");
        this.groups = Objects.requireNonNull(groups, "groups");
        this.baseSlots = Math.max(0, baseSlots);
        this.crystalSlots = Math.max(0, crystalSlots);
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

    /**
     * Validate (without mutating) that {@code baseKey} fits in {@code current}'s enchant slots
     * (§H): a NEW enchant needs a free slot; re-applying one already present (a level change) does
     * not. Pure — testable with a hand-built {@link CombatState}. Capacity is the configured
     * {@code baseSlots} plus any purchased {@code added} slots persisted on the item (slot expander /
     * gem, §H). This base form is unaware of {@code removes-required}
     * (the admin/force path); player paths use {@link #checkApplicable} which nets out removed
     * prerequisites.
     */
    public ApplyResult checkSlots(CombatState current, String baseKey) {
        return checkSlots(current, baseKey, 0);
    }

    /** As {@link #checkSlots(CombatState, String)} but a NEW enchant that frees {@code freed} prerequisites
     * (a {@code removes-required} upgrade) costs {@code 1 - freed} net slots — never below zero. */
    private ApplyResult checkSlots(CombatState current, String baseKey, int freed) {
        if (current.enchants().containsKey(baseKey)) {
            return ApplyResult.ok(""); // re-applying an existing enchant consumes no new slot
        }
        SlotLedger slots = new SlotLedger(baseSlots, current.added(), current.enchants().size());
        return slots.canApply(1 - Math.max(0, freed)) // net cost; ≤0 always fits (the upgrade supersedes)
                ? ApplyResult.ok("")
                : ApplyResult.fail("§cThis item has no free enchant slots (" + slots.max() + " max).");
    }

    /**
     * Validate (without mutating) the apply-time enchant relationships (§G) for putting {@code def} at
     * {@code level} onto {@code current}: every {@code requires} prerequisite must be present at a level
     * &ge; {@code level}, and no {@code blacklist} pairing may hold (checked <em>bidirectionally</em> —
     * either {@code def} blacklists a present enchant, or a present enchant blacklists {@code def}). Pure.
     */
    public ApplyResult checkRelationships(CombatState current, EnchantDef def, int level) {
        Map<String, Integer> present = current.enchants();
        for (String req : def.requires()) {
            Integer have = present.get(req);
            if (have == null) {
                return ApplyResult.fail("§cRequires §f" + displayOf(req) + " §cfirst.");
            }
            if (have < level) {
                return ApplyResult.fail("§cRequires §f" + displayOf(req) + " §cat level " + level + " or higher.");
            }
        }
        for (String other : present.keySet()) {
            if (other.equals(def.key())) {
                continue;
            }
            EnchantDef otherDef = enchant(other);
            if (def.blacklist().contains(other) || (otherDef != null && otherDef.blacklist().contains(def.key()))) {
                return ApplyResult.fail("§c" + def.display() + " §ccannot be combined with §f" + displayOf(other) + "§c.");
            }
        }
        return ApplyResult.ok("");
    }

    /**
     * Full player-facing apply eligibility for enchant {@code baseKey} at {@code level} onto {@code target}
     * — material/level/applies-to + relationships (§G) + slots (§H, netting out removed prerequisites).
     * The carrier/menu pre-check this BEFORE consuming a book so a violation never wastes the carrier.
     */
    public ApplyResult checkApplicable(ItemStack target, String baseKey, int level) {
        ApplyResult eligible = checkEnchant(target.getType(), baseKey, level);
        if (!eligible.ok()) {
            return eligible;
        }
        EnchantDef def = enchant(baseKey); // non-null: checkEnchant passed
        CombatState current = codec.read(target);
        ApplyResult rel = checkRelationships(current, def, level);
        if (!rel.ok()) {
            return rel;
        }
        ApplyResult slots = checkSlots(current, baseKey, freedBy(def, current));
        return slots.ok() ? eligible : slots;
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

    /**
     * Apply enchant {@code baseKey} at {@code level} to {@code stack} in place, enforcing the apply-time
     * relationships (§G) — the player path (book/menu). Re-renders the lore.
     */
    public ApplyResult applyEnchant(ItemStack stack, String baseKey, int level) {
        return applyEnchant(stack, baseKey, level, true);
    }

    /**
     * Apply enchant {@code baseKey} at {@code level} to {@code stack} in place; re-renders the lore. When
     * {@code enforceRelationships} is {@code true} (player paths) the §G {@code requires}/{@code blacklist}
     * gates apply and a {@code removes-required} upgrade strips its prerequisites on success; when
     * {@code false} (admin force-give) those gates are skipped and the enchant is applied verbatim.
     */
    public ApplyResult applyEnchant(ItemStack stack, String baseKey, int level, boolean enforceRelationships) {
        if (stack == null || stack.getType() == Material.AIR) {
            return ApplyResult.fail("§cHold an item first.");
        }
        ApplyResult check = checkEnchant(stack.getType(), baseKey, level);
        if (!check.ok()) {
            return check;
        }
        EnchantDef def = enchant(baseKey); // non-null: checkEnchant passed
        CombatState current = codec.read(stack);
        if (enforceRelationships) {
            ApplyResult rel = checkRelationships(current, def, level);
            if (!rel.ok()) {
                return rel;
            }
        }
        int freed = enforceRelationships ? freedBy(def, current) : 0;
        ApplyResult slots = checkSlots(current, baseKey, freed);
        if (!slots.ok()) {
            return slots;
        }
        Map<String, Integer> enchants = new LinkedHashMap<>(current.enchants());
        if (enforceRelationships && def.removesRequired()) {
            def.requires().forEach(enchants::remove); // the superior enchant supersedes its prerequisites
        }
        enchants.put(baseKey, level);
        CombatState next = new CombatState(enchants, current.crystals(),
                current.setKey(), current.omni(), current.heroic(), current.added());
        codec.write(stack, next);
        lore.apply(stack, next);
        return ApplyResult.ok(check.message() + " §7applied (level " + level + ").");
    }

    /** How many of {@code def}'s prerequisites a successful apply would free (0 unless removes-required). */
    private static int freedBy(EnchantDef def, CombatState current) {
        if (!def.removesRequired()) {
            return 0;
        }
        int freed = 0;
        for (String req : def.requires()) {
            if (current.enchants().containsKey(req)) {
                freed++;
            }
        }
        return freed;
    }

    /** The display name of an enchant base key, or the key itself if it has no def. */
    private String displayOf(String baseKey) {
        EnchantDef def = enchant(baseKey);
        return def != null ? def.display() : baseKey;
    }

    /**
     * Validate (without mutating) that a crystal item carrying {@code keys} (one for a single, two for a
     * multi-crystal, §E) may be applied to {@code gear}: a single item target, every component eligible
     * for the material, and a free crystal slot ({@code crystalSlots}, a SEPARATE ledger from enchants).
     * The crystal drag-apply pre-checks this BEFORE its success roll so a violation never wastes the gem.
     */
    public ApplyResult checkCrystalEntry(ItemStack gear, List<String> keys) {
        if (gear == null || gear.getType() == Material.AIR) {
            return ApplyResult.fail("§cApply the crystal onto an item.");
        }
        if (gear.getAmount() > 1) {
            return ApplyResult.fail("§cApply the crystal to a single item — split the stack first.");
        }
        if (keys.isEmpty()) {
            return ApplyResult.fail("§cThat is not a crystal.");
        }
        String label = "";
        for (String key : keys) {
            ApplyResult c = checkCrystal(gear.getType(), key);
            if (!c.ok()) {
                return c;
            }
            label = label.isEmpty() ? c.message() : label + " §7+ " + c.message();
        }
        CombatState current = codec.read(gear);
        if (current.crystals().size() >= crystalSlots) {
            return ApplyResult.fail("§cThis item has no free crystal slots (" + crystalSlots + " max).");
        }
        return ApplyResult.ok(label);
    }

    /** Append crystal {@code baseKey} to {@code stack} in place (a single crystal); re-renders the lore. */
    public ApplyResult applyCrystal(ItemStack stack, String baseKey) {
        return applyCrystal(stack, baseKey, true);
    }

    /** As {@link #applyCrystal(ItemStack, String)}; {@code enforceSlots=false} is the admin force path. */
    public ApplyResult applyCrystal(ItemStack stack, String baseKey, boolean enforceSlots) {
        return applyCrystalEntry(stack, List.of(baseKey), enforceSlots);
    }

    /**
     * Append a crystal ENTRY (its 1–2 component {@code keys}) to {@code stack} as ONE crystal-slot entry
     * (encoded {@code "a+b"} for a multi-crystal, §E); re-renders the lore. When {@code enforceSlots} the
     * per-item crystal-slot limit applies (player paths); the admin force path skips it. Crystals stack as
     * an order-preserving list, never collapsed (§6.5).
     */
    public ApplyResult applyCrystalEntry(ItemStack stack, List<String> keys, boolean enforceSlots) {
        if (stack == null || stack.getType() == Material.AIR) {
            return ApplyResult.fail("§cHold an item first.");
        }
        if (keys.isEmpty()) {
            return ApplyResult.fail("§cThat is not a crystal.");
        }
        String label = "";
        for (String key : keys) {
            ApplyResult check = checkCrystal(stack.getType(), key);
            if (!check.ok()) {
                return check;
            }
            label = label.isEmpty() ? check.message() : label + " §7+ " + check.message();
        }
        CombatState current = codec.read(stack);
        if (enforceSlots && current.crystals().size() >= crystalSlots) {
            return ApplyResult.fail("§cThis item has no free crystal slots (" + crystalSlots + " max).");
        }
        if (current.crystals().size() >= MAX_CRYSTALS) {
            return ApplyResult.fail("§cThis item already holds the maximum " + MAX_CRYSTALS + " crystals.");
        }
        List<String> crystals = new ArrayList<>(current.crystals());
        crystals.add(String.join(item.codec.CrystalItemData.DELIMITER, keys)); // ONE entry = ONE slot
        CombatState next = new CombatState(current.enchants(), crystals,
                current.setKey(), current.omni(), current.heroic(), current.added());
        codec.write(stack, next);
        lore.apply(stack, next);
        return ApplyResult.ok(label + " §7crystal applied.");
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
