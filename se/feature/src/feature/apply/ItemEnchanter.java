package feature.apply;

import compile.load.ContentHolder;
import compile.load.CrystalDef;
import compile.load.EnchantDef;
import compile.load.MaskDef;
import compile.model.Snapshot;
import engine.interact.SlotLedger;
import item.codec.CombatCodec;
import item.codec.CombatState;
import item.mint.VanillaEnchants;
import platform.lang.Messages;
import item.render.LoreRenderer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Applies enchants, crystals, and masks to an item (docs/architecture.md §4.2) — the one cold mutation path.
 * Validation ({@code check*}) is split from mutation ({@code apply*}) so eligibility is unit-testable with
 * no server. Reads the live library through {@link ContentHolder} per call, so it validates/renders against
 * post-reload content.
 */
public final class ItemEnchanter {

    /** Generous default merge cap for the fixture ctor (the real plugin passes {@code crystals.max-merge}). */
    public static final int DEFAULT_MAX_MERGE = item.codec.CrystalItemData.ABSOLUTE_MAX;

    /** Default base enchant-slot capacity (docs/v3-directives.md §H). */
    public static final int DEFAULT_BASE_SLOTS = 9;

    /** Default per-item crystal-slot capacity (§E). */
    public static final int DEFAULT_CRYSTAL_SLOTS = 1;

    /** Set-config enchant refs with this prefix are CUSTOM plugin enchants (stamped into combat state); any
     *  other key is a vanilla enchant NAME applied cross-version at mint (§6.6). */
    private static final String CUSTOM_PREFIX = "enchants/";

    /** Masks are helmets-only by construction (ADR-0053 §1) — a fixed target group, never an authored applies-to. */
    private static final List<String> HELMET_ONLY = List.of("HELMET");

    private final CombatCodec codec;
    private final LoreRenderer lore;
    private final ContentHolder content;
    private final platform.item.ItemGroups groups;
    private final IntSupplier baseSlots;     // §H slots.base — read live per apply so a reload re-tunes it
    private final IntSupplier crystalSlots;  // §E crystals.slots (entries per item)
    private final IntSupplier maxMerge;      // §E crystals.max-merge (components per entry)
    private final Supplier<List<String>> weaponGroups; // ADR-0070 reforges.weapon-groups — read live per apply
    private final Messages messages;
    private final VanillaEnchants vanilla;   // §6.6 cross-version set-piece base enchants (ADR-0047 instance wiring)
    private final java.util.Random rolls;    // §6.6 the mint roster's draws — injected so a mint is stubbable
    private final HeroicMint heroic;         // §F a set member's heroic: true, stamped at mint

    /** Fixture form: no injected RNG and no heroic economy — a pack with neither mints exactly as before. */
    public ItemEnchanter(CombatCodec codec, LoreRenderer lore, ContentHolder content,
                         platform.item.ItemGroups groups, IntSupplier baseSlots, IntSupplier crystalSlots,
                         IntSupplier maxMerge, Supplier<List<String>> weaponGroups, Messages messages,
                         VanillaEnchants vanilla) {
        this(codec, lore, content, groups, baseSlots, crystalSlots, maxMerge, weaponGroups, messages, vanilla,
                new java.util.Random(), HeroicMint.NONE);
    }

    /** Slot/merge caps and the reforge weapon-groups are read per apply so a reload re-tunes them live. */
    public ItemEnchanter(CombatCodec codec, LoreRenderer lore, ContentHolder content,
                         platform.item.ItemGroups groups, IntSupplier baseSlots, IntSupplier crystalSlots,
                         IntSupplier maxMerge, Supplier<List<String>> weaponGroups, Messages messages,
                         VanillaEnchants vanilla, java.util.Random rolls, HeroicMint heroic) {
        this.rolls = Objects.requireNonNull(rolls, "rolls");
        this.heroic = Objects.requireNonNull(heroic, "heroic");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.lore = Objects.requireNonNull(lore, "lore");
        this.content = Objects.requireNonNull(content, "content");
        this.groups = Objects.requireNonNull(groups, "groups");
        this.baseSlots = Objects.requireNonNull(baseSlots, "baseSlots");
        this.crystalSlots = Objects.requireNonNull(crystalSlots, "crystalSlots");
        this.maxMerge = Objects.requireNonNull(maxMerge, "maxMerge");
        this.weaponGroups = Objects.requireNonNull(weaponGroups, "weaponGroups");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.vanilla = Objects.requireNonNull(vanilla, "vanilla");
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
            return ApplyResult.fail(messages.format("crystal.no-such", "KEY", baseKey));
        }
        if (content.snapshot().byStableKey(baseKey) == null) {
            return ApplyResult.fail(messages.format("crystal.no-compile", "KEY", baseKey));
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
        CombatState next = current.withEnchants(enchants); // preserves setWeaponKey (a set weapon stays a set weapon)
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
        CombatState next = current.withEnchants(enchants); // preserves setWeaponKey
        codec.write(stack, next);
        lore.apply(stack, next);
        return ApplyResult.ok(messages.format("apply.removed", "KEY", baseKey));
    }

    /**
     * Extract the entire last crystal ENTRY off {@code gear} (ADR-0035); re-renders lore. A multi-crystal comes off
     * INTACT — the whole {@code "a+b+c"} entry — so the caller mints it back as one multi-crystal, and its freed
     * slot opens. Splitting a multi-crystal back into singles is a SECOND extractor gesture, applied to the popped
     * multi-crystal ITEM ({@code CrystalService.extractFromCrystal}). No-op fail when the item carries no crystal.
     */
    public ExtractResult extractCrystal(ItemStack gear) {
        if (gear == null || gear.getType() == Material.AIR) {
            return ExtractResult.fail(messages.format("apply.hold-item"));
        }
        if (gear.getAmount() > 1) {
            // Symmetric with removeMask: one blob write on a stack would strip N crystals but mint back one.
            return ExtractResult.fail(messages.format("crystal.single-item"));
        }
        CombatState current = codec.read(gear);
        if (current.crystals().isEmpty()) {
            return ExtractResult.fail(messages.format("crystal.none"));
        }
        List<String> entries = new ArrayList<>(current.crystals());
        String popped = entries.remove(entries.size() - 1); // the whole last entry — a multi-crystal pops off intact
        CombatState next = current.withCrystals(entries); // preserves setWeaponKey; the slot always frees
        codec.write(gear, next);
        lore.apply(gear, next);
        return ExtractResult.ok(popped);
    }

    /**
     * Mint a SET MEMBER item for {@code setKey} (§J): {@code memberToken} names a declared armour slot or
     * {@code weapon}. An armour member stamps {@link CombatState#setKey()} (counts toward completion); the
     * weapon stamps {@link CombatState#setWeaponKey()} (the extra bonus while complete and held). Unknown
     * set/member &rarr; empty (ADR-0019, no invented data).
     *
     * <p>A member's own likeness rides here too (§6.6): its dye, the roster entries it adds to the shared ones,
     * and its {@code heroic: true}. Its own LORE is not written onto the stack — it is rendered from state like
     * every other section (ADR-0040), keyed on the piece's gear kind.
     */
    public java.util.Optional<ItemStack> mintSetPiece(String setKey, String memberToken) {
        compile.load.SetDef def = set(setKey);
        if (def == null) {
            return java.util.Optional.empty();
        }
        String token = memberToken == null ? "" : memberToken.toLowerCase(java.util.Locale.ROOT);
        if (def.hasWeapon() && token.equals("weapon")) {
            compile.load.SetDef.Member member = def.weapon();
            Material material = item.mint.ItemFactory.material(member.material(), Material.IRON_SWORD);
            String name = member.name() != null ? member.name() : def.display();
            ItemStack stack = item.mint.ItemFactory.build(material, name, List.of());
            // §6.6 configured weapon enchants: custom ones stamp into the combat state (so the engine runs
            // them while held), vanilla names apply cross-version at mint. Rolled entries draw ONCE, here.
            Map<String, Integer> roster = SetMint.resolve(def.weaponEnchants(), rolls);
            CombatState next = new CombatState(customEnchants(roster), List.of(), null, setKey,
                    false, item.codec.HeroicStat.NONE, 0, null, null); // weaponMember(setKey) + carried custom enchants
            codec.write(stack, next);
            lore.apply(stack, next);
            vanilla.apply(stack, vanillaEnchants(roster));
            finishPiece(stack, member, true);
            return java.util.Optional.of(stack);
        }
        for (compile.load.SetDef.Member member : def.armorMembers()) {
            if (member.slot().equalsIgnoreCase(token)) {
                Material material = item.mint.ItemFactory.material(member.material(), Material.LEATHER_HELMET);
                String name = member.name() != null ? member.name() : def.display();
                ItemStack stack = item.mint.ItemFactory.build(material, name, List.of());
                // §6.6 the shared armour roster plus this slot's own entries: custom ones stamp into the
                // combat state, vanilla names apply cross-version at mint.
                Map<String, Integer> roster = SetMint.resolve(def.armorEnchantsFor(member.slot()), rolls);
                CombatState next = new CombatState(customEnchants(roster), List.of(), setKey, false);
                codec.write(stack, next);
                lore.apply(stack, next);
                vanilla.apply(stack, vanillaEnchants(roster));
                finishPiece(stack, member, false);
                return java.util.Optional.of(stack);
            }
        }
        return java.util.Optional.empty();
    }

    /**
     * The per-member finish: the leather dye, then the heroic stamp LAST — it re-renders lore from state, so
     * anything that changes state has to be in place before it runs.
     */
    private void finishPiece(ItemStack stack, compile.load.SetDef.Member member, boolean weapon) {
        item.mint.ItemFactory.dye(stack, member.color());
        if (member.heroic()) {
            heroic.stampOn(stack, weapon);
        }
    }

    /** The custom plugin enchants ({@code enchants/<id> → level}) from a set's configured enchants, for the combat state. */
    private static Map<String, Integer> customEnchants(Map<String, Integer> configured) {
        Map<String, Integer> out = new LinkedHashMap<>();
        configured.forEach((ref, level) -> {
            if (ref.startsWith(CUSTOM_PREFIX)) {
                out.put(ref, level);
            }
        });
        return out;
    }

    /** The vanilla enchants (NAME → level) from a set's configured enchants, applied cross-version at mint. */
    private static Map<String, Integer> vanillaEnchants(Map<String, Integer> configured) {
        Map<String, Integer> out = new LinkedHashMap<>();
        configured.forEach((ref, level) -> {
            if (!ref.startsWith(CUSTOM_PREFIX)) {
                out.put(ref, level);
            }
        });
        return out;
    }

    private compile.load.SetDef set(String key) {
        return content.library().setDefOf(key);
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
            return ApplyResult.fail(messages.format("crystal.on-item"));
        }
        if (gear.getAmount() > 1) {
            return ApplyResult.fail(messages.format("crystal.single-item"));
        }
        if (keys.isEmpty()) {
            return ApplyResult.fail(messages.format("crystal.not-crystal"));
        }
        int mergeCap = maxMerge.getAsInt();
        if (keys.size() > mergeCap) {
            return ApplyResult.fail(messages.format("crystal.max-reached", "MAX", mergeCap));
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
            return ApplyResult.fail(messages.format("crystal.no-slots", "MAX", crystalCap));
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
            return ApplyResult.fail(messages.format("crystal.not-crystal"));
        }
        int mergeCap = maxMerge.getAsInt();
        if (enforceSlots && keys.size() > mergeCap) {
            return ApplyResult.fail(messages.format("crystal.max-reached", "MAX", mergeCap));
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
            return ApplyResult.fail(messages.format("crystal.no-slots", "MAX", crystalCap));
        }
        List<String> crystals = new ArrayList<>(current.crystals());
        crystals.add(String.join(item.codec.CrystalItemData.DELIMITER, keys)); // ONE entry = ONE slot
        CombatState next = current.withCrystals(crystals); // preserves setWeaponKey
        codec.write(stack, next);
        lore.apply(stack, next);
        return ApplyResult.ok(messages.format("crystal.applied", "LABEL", label));
    }

    /**
     * Validate (without mutating) that mask {@code maskKey} may sit on {@code gear} (ADR-0053 §3): a single
     * HELMET carrying no mask yet — one mask per helmet, a boolean occupancy, never the crystal slot ledger.
     * Drag-apply pre-checks this BEFORE consuming the mask, so a violation never wastes it.
     */
    public ApplyResult checkMask(ItemStack gear, String maskKey) {
        if (gear == null || gear.getType() == Material.AIR) {
            return ApplyResult.fail(messages.format("mask.on-item"));
        }
        if (gear.getAmount() > 1) {
            return ApplyResult.fail(messages.format("mask.single-item"));
        }
        MaskDef def = mask(maskKey);
        if (def == null) {
            return ApplyResult.fail(messages.format("mask.no-such", "KEY", maskKey));
        }
        if (content.snapshot().byStableKey(maskKey) == null) {
            return ApplyResult.fail(messages.format("mask.no-compile", "KEY", maskKey));
        }
        if (!groups.matches(gear.getType(), HELMET_ONLY)) {
            return ApplyResult.fail(messages.format("apply.not-applicable", "DISPLAY", def.display()));
        }
        if (codec.read(gear).maskKey() != null) {
            return ApplyResult.fail(messages.format("mask.already"));
        }
        return ApplyResult.ok(messages.format("apply.ok", "DISPLAY", def.display()));
    }

    /** Stamp mask {@code maskKey} onto {@code gear} in place (re-validating first); re-renders lore (ADR-0040). */
    public ApplyResult applyMask(ItemStack gear, String maskKey) {
        ApplyResult check = checkMask(gear, maskKey);
        if (!check.ok()) {
            return check;
        }
        CombatState current = codec.read(gear);
        CombatState next = current.withMask(maskKey); // preserves every other field (the setWeaponKey trap)
        codec.write(gear, next);
        lore.apply(gear, next);
        return check;
    }

    /**
     * Pop the mask off {@code gear} (the right-click remove gesture, ADR-0053 §3); re-renders lore. Returns
     * the popped key for the caller to mint back as a mask item. No-op fail when the helmet carries no mask.
     */
    public ExtractResult removeMask(ItemStack gear) {
        if (gear == null || gear.getType() == Material.AIR) {
            return ExtractResult.fail(messages.format("apply.hold-item"));
        }
        if (gear.getAmount() > 1) {
            // Symmetric with checkMask: one blob write on a stack would strip N masks but pop back one.
            return ExtractResult.fail(messages.format("mask.single-item"));
        }
        CombatState current = codec.read(gear);
        String popped = current.maskKey();
        if (popped == null) {
            return ExtractResult.fail(messages.format("mask.none"));
        }
        CombatState next = current.withMask(null);
        codec.write(gear, next);
        lore.apply(gear, next);
        return ExtractResult.ok(popped);
    }

    /**
     * Validate (without mutating) that reforge {@code reforgeKey} may sit on {@code gear} (ADR-0070): a single
     * WEAPON (the live {@code reforges.weapon-groups} set) whose one reforge socket is empty. Drag-apply
     * pre-checks this BEFORE consuming the reforge item, so a violation never wastes it.
     */
    public ApplyResult checkReforge(ItemStack gear, String reforgeKey) {
        if (gear == null || gear.getType() == Material.AIR) {
            return ApplyResult.fail(messages.format("reforge.on-item"));
        }
        if (gear.getAmount() > 1) {
            return ApplyResult.fail(messages.format("reforge.single-item"));
        }
        compile.load.ReforgeDef def = reforge(reforgeKey);
        if (def == null) {
            return ApplyResult.fail(messages.format("reforge.no-such", "KEY", reforgeKey));
        }
        if (content.snapshot().byStableKey(reforgeKey) == null) {
            return ApplyResult.fail(messages.format("reforge.no-compile", "KEY", reforgeKey));
        }
        if (!groups.matches(gear.getType(), weaponGroups.get())) {
            return ApplyResult.fail(messages.format("reforge.not-weapon", "DISPLAY", def.display()));
        }
        if (codec.read(gear).reforgeKey() != null) {
            return ApplyResult.fail(messages.format("reforge.occupied"));
        }
        return ApplyResult.ok(messages.format("apply.ok", "DISPLAY", def.display()));
    }

    /** Stamp reforge {@code reforgeKey} onto {@code gear} in place (re-validating first); re-renders lore. */
    public ApplyResult applyReforge(ItemStack gear, String reforgeKey) {
        ApplyResult check = checkReforge(gear, reforgeKey);
        if (!check.ok()) {
            return check;
        }
        CombatState current = codec.read(gear);
        CombatState next = current.withReforge(reforgeKey); // preserves every other field (the setWeaponKey trap)
        codec.write(gear, next);
        lore.apply(gear, next);
        return check;
    }

    /**
     * Pop the reforge off {@code gear} (the Item Extractor gesture, ADR-0070); re-renders lore. Returns the
     * popped key for the caller to mint back as a reforge item. No-op fail when the weapon carries none.
     */
    public ExtractResult extractReforge(ItemStack gear) {
        if (gear == null || gear.getType() == Material.AIR) {
            return ExtractResult.fail(messages.format("apply.hold-item"));
        }
        if (gear.getAmount() > 1) {
            return ExtractResult.fail(messages.format("reforge.single-item"));
        }
        CombatState current = codec.read(gear);
        String popped = current.reforgeKey();
        if (popped == null) {
            return ExtractResult.fail(messages.format("reforge.none"));
        }
        CombatState next = current.withReforge(null);
        codec.write(gear, next);
        lore.apply(gear, next);
        return ExtractResult.ok(popped);
    }

    private compile.load.ReforgeDef reforge(String key) {
        return content.library().reforgeDefOf(key);
    }

    private EnchantDef enchant(String baseKey) {
        return content.library().enchantDefOf(baseKey);
    }

    private CrystalDef crystal(String baseKey) {
        return content.library().crystalDefOf(baseKey);
    }

    private MaskDef mask(String key) {
        return content.library().maskDefOf(key);
    }
}
