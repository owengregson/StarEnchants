package feature.apply;

import compile.load.ContentHolder;
import compile.load.CrystalDef;
import compile.load.EnchantDef;
import compile.model.Snapshot;
import engine.interact.SlotLedger;
import item.codec.CombatCodec;
import item.codec.CombatState;
import item.lang.Messages;
import item.render.LoreRenderer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntSupplier;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Applies enchants and crystals to an item (docs/architecture.md §4.2) — the one cold mutation path.
 * Validation ({@code check*}) is split from mutation ({@code apply*}) so eligibility is unit-testable with
 * no server. Reads the live library through {@link ContentHolder} per call, so it validates/renders against
 * post-reload content.
 */
public final class ItemEnchanter {

    /** Sanity cap on crystals per item — they stack (§6.5), but unbounded growth bloats PDC. */
    public static final int DEFAULT_MAX_CRYSTALS = 16;

    /** Default base enchant-slot capacity (docs/v3-directives.md §H). */
    public static final int DEFAULT_BASE_SLOTS = 9;

    /** Default per-item crystal-slot capacity (§E). */
    public static final int DEFAULT_CRYSTAL_SLOTS = 1;

    private final CombatCodec codec;
    private final LoreRenderer lore;
    private final ContentHolder content;
    private final platform.item.ItemGroups groups;
    private final IntSupplier baseSlots;     // §H slots.base — read live per apply so a reload re-tunes it
    private final IntSupplier crystalSlots;  // §E crystals.slots
    private final IntSupplier maxCrystals;   // §E crystals.max-stack
    private final Messages messages;

    public ItemEnchanter(CombatCodec codec, LoreRenderer lore, ContentHolder content,
                         platform.item.ItemGroups groups) {
        this(codec, lore, content, groups, DEFAULT_BASE_SLOTS);
    }

    public ItemEnchanter(CombatCodec codec, LoreRenderer lore, ContentHolder content,
                         platform.item.ItemGroups groups, int baseSlots) {
        this(codec, lore, content, groups, baseSlots, DEFAULT_CRYSTAL_SLOTS);
    }

    /** Fixed slot counts and default messages — the common test/fixture form. */
    public ItemEnchanter(CombatCodec codec, LoreRenderer lore, ContentHolder content,
                         platform.item.ItemGroups groups, int baseSlots, int crystalSlots) {
        this(codec, lore, content, groups, () -> Math.max(0, baseSlots), () -> Math.max(0, crystalSlots),
                () -> DEFAULT_MAX_CRYSTALS, Messages.defaults());
    }

    /** Canonical ctor (composition root): slot capacities are read per apply so a reload re-tunes them live. */
    public ItemEnchanter(CombatCodec codec, LoreRenderer lore, ContentHolder content,
                         platform.item.ItemGroups groups, IntSupplier baseSlots, IntSupplier crystalSlots,
                         IntSupplier maxCrystals, Messages messages) {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.lore = Objects.requireNonNull(lore, "lore");
        this.content = Objects.requireNonNull(content, "content");
        this.groups = Objects.requireNonNull(groups, "groups");
        this.baseSlots = Objects.requireNonNull(baseSlots, "baseSlots");
        this.crystalSlots = Objects.requireNonNull(crystalSlots, "crystalSlots");
        this.maxCrystals = Objects.requireNonNull(maxCrystals, "maxCrystals");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /** Validate (without mutating) that enchant {@code baseKey} at {@code level} may sit on {@code material}. */
    public ApplyResult checkEnchant(Material material, String baseKey, int level) {
        EnchantDef def = enchant(baseKey);
        if (def == null) {
            return ApplyResult.fail(messages.format("apply.no-such-enchant", "KEY", baseKey));
        }
        if (level < 1 || level > def.maxLevel()) {
            return ApplyResult.fail(messages.format("apply.level-range", "MAX", def.maxLevel(), "KEY", baseKey));
        }
        Snapshot snapshot = content.snapshot();
        if (snapshot.byStableKey(baseKey + "/" + level) == null) {
            return ApplyResult.fail(messages.format("apply.level-undefined", "LEVEL", level, "KEY", baseKey));
        }
        if (!groups.matches(material, def.appliesTo())) {
            return ApplyResult.fail(messages.format("apply.not-applicable", "DISPLAY", def.display()));
        }
        return ApplyResult.ok(messages.format("apply.ok", "DISPLAY", def.display()));
    }

    /**
     * Validate (without mutating) that {@code baseKey} fits in {@code current}'s enchant slots (§H): a NEW
     * enchant needs a free slot, re-applying a present one does not. This base form ignores
     * {@code removes-required}; player paths use {@link #checkApplicable}, which nets out freed prereqs.
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
        SlotLedger slots = new SlotLedger(baseSlots.getAsInt(), current.added(), current.enchants().size());
        return slots.canApply(1 - Math.max(0, freed)) // net cost; ≤0 always fits (the upgrade supersedes)
                ? ApplyResult.ok("")
                : ApplyResult.fail(messages.format("apply.no-enchant-slots", "MAX", slots.max()));
    }

    /**
     * Validate (without mutating) the §G relationships for {@code def} at {@code level}: every
     * {@code requires} prereq present at a level &ge; {@code level}, and no {@code blacklist} pairing —
     * checked bidirectionally (either side may blacklist the other).
     */
    public ApplyResult checkRelationships(CombatState current, EnchantDef def, int level) {
        Map<String, Integer> present = current.enchants();
        for (String req : def.requires()) {
            Integer have = present.get(req);
            if (have == null) {
                return ApplyResult.fail(messages.format("apply.requires", "REQ", displayOf(req)));
            }
            if (have < level) {
                return ApplyResult.fail(messages.format("apply.requires-level", "REQ", displayOf(req), "LEVEL", level));
            }
        }
        for (String other : present.keySet()) {
            if (other.equals(def.key())) {
                continue;
            }
            EnchantDef otherDef = enchant(other);
            if (def.blacklist().contains(other) || (otherDef != null && otherDef.blacklist().contains(def.key()))) {
                return ApplyResult.fail(messages.format("apply.conflicts",
                        "DISPLAY", def.display(), "OTHER", displayOf(other)));
            }
        }
        return ApplyResult.ok("");
    }

    /**
     * Full player-facing eligibility: material/level/applies-to + §G relationships + §H slots (netting out
     * freed prereqs). Carrier/menu pre-check this BEFORE consuming a book so a violation never wastes it.
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
            return ApplyResult.fail(messages.format("apply.crystal.no-such", "KEY", baseKey));
        }
        if (content.snapshot().byStableKey(baseKey) == null) {
            return ApplyResult.fail(messages.format("apply.crystal.no-compile", "KEY", baseKey));
        }
        if (!groups.matches(material, def.appliesTo())) {
            return ApplyResult.fail(messages.format("apply.not-applicable", "DISPLAY", def.display()));
        }
        return ApplyResult.ok(messages.format("apply.ok", "DISPLAY", def.display()));
    }

    /** The player path (book/menu): apply with §G relationships enforced. */
    public ApplyResult applyEnchant(ItemStack stack, String baseKey, int level) {
        return applyEnchant(stack, baseKey, level, true);
    }

    /**
     * Apply {@code baseKey} at {@code level} in place; re-renders lore. {@code enforceRelationships} (player
     * paths) applies the §G gates and strips a {@code removes-required} upgrade's prereqs; {@code false}
     * (admin force-give) skips them and the enchant lands verbatim.
     */
    public ApplyResult applyEnchant(ItemStack stack, String baseKey, int level, boolean enforceRelationships) {
        if (stack == null || stack.getType() == Material.AIR) {
            return ApplyResult.fail(messages.format("apply.hold-item"));
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
        return ApplyResult.ok(messages.format("apply.applied-suffix", "MSG", check.message(), "LEVEL", level));
    }

    /**
     * Remove {@code baseKey} in place (inverse of {@link #applyEnchant}, §J); re-renders lore. The freed slot
     * is implicit since occupancy derives from the enchant count. No-op fail when the enchant is absent.
     */
    public ApplyResult removeEnchant(ItemStack stack, String baseKey) {
        if (stack == null || stack.getType() == Material.AIR) {
            return ApplyResult.fail(messages.format("apply.hold-item"));
        }
        CombatState current = codec.read(stack);
        if (!current.enchants().containsKey(baseKey)) {
            return ApplyResult.fail(messages.format("apply.not-present", "KEY", baseKey));
        }
        Map<String, Integer> enchants = new LinkedHashMap<>(current.enchants());
        enchants.remove(baseKey);
        CombatState next = new CombatState(enchants, current.crystals(),
                current.setKey(), current.omni(), current.heroic(), current.added());
        codec.write(stack, next);
        lore.apply(stack, next);
        return ApplyResult.ok(messages.format("apply.removed", "KEY", baseKey));
    }

    /**
     * Extract the most-recently-applied crystal ENTRY off {@code gear} in place (inverse of
     * {@link #applyCrystalEntry}, §E); re-renders lore. Returns the popped entry so the caller can mint it
     * back whole. No-op fail when no crystal.
     */
    public ExtractResult extractCrystal(ItemStack gear) {
        if (gear == null || gear.getType() == Material.AIR) {
            return ExtractResult.fail(messages.format("apply.hold-item"));
        }
        CombatState current = codec.read(gear);
        if (current.crystals().isEmpty()) {
            return ExtractResult.fail(messages.format("apply.crystal.none"));
        }
        List<String> crystals = new ArrayList<>(current.crystals());
        String popped = crystals.remove(crystals.size() - 1);
        CombatState next = new CombatState(current.enchants(), crystals,
                current.setKey(), current.omni(), current.heroic(), current.added());
        codec.write(gear, next);
        lore.apply(gear, next);
        return ExtractResult.ok(popped, messages.format("apply.crystal.extracted"));
    }

    /**
     * Mint a SET MEMBER item for {@code setKey} (§J): {@code memberToken} names a declared armour slot or
     * {@code weapon}. An armour member stamps {@link CombatState#setKey()} (counts toward completion); the
     * weapon stamps {@link CombatState#setWeaponKey()} (the extra bonus while complete and held). Unknown
     * set/member &rarr; empty (ADR-0019, no invented data).
     */
    public java.util.Optional<ItemStack> mintSetPiece(String setKey, String memberToken) {
        compile.load.SetDef def = set(setKey);
        if (def == null) {
            return java.util.Optional.empty();
        }
        String token = memberToken == null ? "" : memberToken.toLowerCase(java.util.Locale.ROOT);
        if (def.hasWeapon() && token.equals("weapon")) {
            Material material = item.mint.ItemFactory.material(def.weapon().material(), Material.IRON_SWORD);
            String name = def.weapon().name() != null ? def.weapon().name() : def.display();
            ItemStack stack = item.mint.ItemFactory.build(material, name, List.of());
            CombatState next = CombatState.weaponMember(setKey);
            codec.write(stack, next);
            lore.apply(stack, next);
            return java.util.Optional.of(stack);
        }
        for (compile.load.SetDef.Member member : def.armorMembers()) {
            if (member.slot().equalsIgnoreCase(token)) {
                Material material = item.mint.ItemFactory.material(member.material(), Material.LEATHER_HELMET);
                String name = member.name() != null ? member.name() : def.display();
                ItemStack stack = item.mint.ItemFactory.build(material, name, List.of());
                CombatState next = new CombatState(Map.of(), List.of(), setKey, false);
                codec.write(stack, next);
                lore.apply(stack, next);
                return java.util.Optional.of(stack);
            }
        }
        return java.util.Optional.empty();
    }

    private compile.load.SetDef set(String key) {
        for (compile.load.SetDef def : content.library().sets()) {
            if (def.key().equals(key)) {
                return def;
            }
        }
        return null;
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
     * Validate (without mutating) that a crystal carrying {@code keys} (1 single, 2 multi-crystal, §E) may
     * apply to {@code gear}: single-item target, every component eligible, and a free crystal slot (a
     * SEPARATE ledger from enchants). Drag-apply pre-checks this BEFORE its roll so a violation never wastes
     * the gem.
     */
    public ApplyResult checkCrystalEntry(ItemStack gear, List<String> keys) {
        if (gear == null || gear.getType() == Material.AIR) {
            return ApplyResult.fail(messages.format("apply.crystal.on-item"));
        }
        if (gear.getAmount() > 1) {
            return ApplyResult.fail(messages.format("apply.crystal.single-item"));
        }
        if (keys.isEmpty()) {
            return ApplyResult.fail(messages.format("apply.crystal.not-crystal"));
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
        int crystalCap = crystalSlots.getAsInt();
        if (current.crystals().size() >= crystalCap) {
            return ApplyResult.fail(messages.format("apply.crystal.no-slots", "MAX", crystalCap));
        }
        return ApplyResult.ok(label);
    }

    public ApplyResult applyCrystal(ItemStack stack, String baseKey) {
        return applyCrystal(stack, baseKey, true);
    }

    /** {@code enforceSlots=false} is the admin force path (skips the per-item crystal-slot limit). */
    public ApplyResult applyCrystal(ItemStack stack, String baseKey, boolean enforceSlots) {
        return applyCrystalEntry(stack, List.of(baseKey), enforceSlots);
    }

    /**
     * Append a crystal ENTRY (its 1–2 {@code keys}) to {@code stack} as ONE crystal-slot entry (encoded
     * {@code "a+b"} for a multi-crystal, §E); re-renders lore. {@code enforceSlots} gates the per-item
     * crystal-slot limit (player paths); the admin force path skips it. Crystals stack order-preserving,
     * never collapsed (§6.5).
     */
    public ApplyResult applyCrystalEntry(ItemStack stack, List<String> keys, boolean enforceSlots) {
        if (stack == null || stack.getType() == Material.AIR) {
            return ApplyResult.fail(messages.format("apply.hold-item"));
        }
        if (keys.isEmpty()) {
            return ApplyResult.fail(messages.format("apply.crystal.not-crystal"));
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
        int crystalCap = crystalSlots.getAsInt();
        if (enforceSlots && current.crystals().size() >= crystalCap) {
            return ApplyResult.fail(messages.format("apply.crystal.no-slots", "MAX", crystalCap));
        }
        int maxStack = maxCrystals.getAsInt();
        if (current.crystals().size() >= maxStack) {
            return ApplyResult.fail(messages.format("apply.crystal.max-reached", "MAX", maxStack));
        }
        List<String> crystals = new ArrayList<>(current.crystals());
        crystals.add(String.join(item.codec.CrystalItemData.DELIMITER, keys)); // ONE entry = ONE slot
        CombatState next = new CombatState(current.enchants(), crystals,
                current.setKey(), current.omni(), current.heroic(), current.added());
        codec.write(stack, next);
        lore.apply(stack, next);
        return ApplyResult.ok(messages.format("apply.crystal.applied", "LABEL", label));
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
