package feature.crystal;

import compile.load.ContentHolder;
import compile.load.CrystalConfig;
import compile.load.CrystalDef;
import feature.apply.ApplyResult;
import feature.apply.ExtractResult;
import feature.apply.ItemEnchanter;
import feature.compat.Mats;
import item.codec.CrystalExtractorCodec;
import item.codec.CrystalItemCodec;
import item.codec.CrystalItemData;
import item.mint.ItemFactory;
import item.render.CrystalNames;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import org.bukkit.inventory.ItemStack;
import platform.item.ItemGroups;

/**
 * The crystal item economy (docs/v3-directives.md §E, ADR-0034) — mints crystals from the ONE global likeness,
 * APPLIES them to gear (unconditional — no success roll), MERGES crystals into a multi-crystal (up to the global
 * {@code crystals.max-merge} cap), and EXTRACTS the topmost single off gear or off a multi-crystal item. A minted
 * crystal (single or merged) takes the shared {@link CrystalConfig} likeness with its {@code {CRYSTAL}} /
 * {@code {DESCRIPTION}} / {@code {KINDS}} tokens filled from the component crystal(s); the name string is
 * single-sourced with the gear renderer through {@link CrystalNames}. Gear mutation + slot/merge validation
 * delegate to {@link ItemEnchanter}.
 */
public final class CrystalService {

    private final CrystalItemCodec codec;
    private final CrystalExtractorCodec extractorCodec;
    private final ItemEnchanter enchanter;
    private final ContentHolder content;
    private final Supplier<CrystalConfig> config;
    private final IntSupplier maxMerge; // §E crystals.max-merge — the global multi-crystal cap (read live)
    private final item.lang.Messages messages; // §L lang.yml

    /** Default-messages form (tests/fixtures). */
    public CrystalService(CrystalItemCodec codec, CrystalExtractorCodec extractorCodec, ItemEnchanter enchanter,
                          ContentHolder content, Supplier<CrystalConfig> config, IntSupplier maxMerge) {
        this(codec, extractorCodec, enchanter, content, config, maxMerge, item.lang.Messages.defaults());
    }

    public CrystalService(CrystalItemCodec codec, CrystalExtractorCodec extractorCodec, ItemEnchanter enchanter,
                          ContentHolder content, Supplier<CrystalConfig> config, IntSupplier maxMerge,
                          item.lang.Messages messages) {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.extractorCodec = Objects.requireNonNull(extractorCodec, "extractorCodec");
        this.enchanter = Objects.requireNonNull(enchanter, "enchanter");
        this.content = Objects.requireNonNull(content, "content");
        this.config = Objects.requireNonNull(config, "config");
        this.maxMerge = Objects.requireNonNull(maxMerge, "maxMerge");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    public boolean isCrystal(ItemStack stack) {
        return codec.read(stack) != null;
    }

    public boolean isExtractor(ItemStack stack) {
        return extractorCodec.isExtractor(stack);
    }

    public ItemStack mintExtractor() {
        CrystalConfig cfg = config.get();
        ItemStack stack = ItemFactory.buildItem(
                cfg.extractorMaterial(), Mats.or("AMETHYST_CLUSTER", org.bukkit.Material.PAPER),
                cfg.extractorName(),
                cfg.extractorLore());
        extractorCodec.mark(stack);
        return stack;
    }

    public ItemStack mint(List<String> keys) {
        return mint(new CrystalItemData(keys));
    }

    /**
     * Mint a crystal for {@code data} from the ONE global likeness (§E). The {@code {CRYSTAL}} token renders the
     * component name(s) (single-sourced with the gear renderer via {@link CrystalNames}); the lore template's
     * {@code {DESCRIPTION}} expands to each component's authored block (blank-separated) and {@code {KINDS}} to
     * the item kinds the crystal applies to.
     */
    public ItemStack mint(CrystalItemData data) {
        CrystalConfig cfg = config.get();
        List<String> keys = data.keys();
        // A merged crystal takes the "Multi Crystal" name template; a single takes the plain one (ADR-0035).
        String nameTemplate = data.isMulti() ? cfg.nameMulti() : cfg.name();
        ItemStack stack = ItemFactory.buildItem(
                cfg.material(), Mats.or("AMETHYST_SHARD", org.bukkit.Material.PAPER),
                CrystalNames.render(nameTemplate, keys, this::displayName),
                renderLore(cfg.lore(), keys));
        codec.write(stack, data);
        return stack;
    }

    /** Crystal-on-something gesture: target crystal → MERGE (up to the cap), else APPLY to gear. */
    public CrystalResult interact(ItemStack cursor, ItemStack target) {
        CrystalItemData crystal = codec.read(cursor);
        if (crystal == null) {
            return CrystalResult.unchanged(null);
        }
        CrystalItemData targetCrystal = codec.read(target);
        if (targetCrystal != null) {
            return merge(cursor, crystal, target, targetCrystal);
        }
        return apply(cursor, crystal, target);
    }

    private CrystalResult apply(ItemStack cursor, CrystalItemData crystal, ItemStack gear) {
        CrystalConfig cfg = config.get();
        ApplyResult eligible = enchanter.checkCrystalEntry(gear, crystal.keys());
        if (!eligible.ok()) {
            // The drag-onto-gear gesture shows its own crystal.no-slots wording; any other ineligibility keeps
            // the enchanter's reason. Branch on the STRUCTURAL reason, never the rendered text — sniffing the
            // message string breaks the moment apply.crystal.no-slots is customised in lang.yml.
            String message = eligible.reason() == ApplyResult.Reason.NO_CRYSTAL_SLOTS
                    ? messages.format("crystal.no-slots") : eligible.message();
            return CrystalResult.unchanged(message); // never consume on an ineligible target
        }
        // 100% apply (ADR-0034 §3): no roll — an eligible crystal always lands, then the cursor is spent.
        enchanter.applyCrystalEntry(gear, crystal.keys(), true);
        consume(cursor);
        return CrystalResult.committed(gear, applySound(cfg),
                messages.format("crystal.apply-success", "CRYSTAL", label(crystal.keys())));
    }

    /** Merge two crystals (cursor ON TOP of the target) into one multi-crystal, capped at {@code max-merge}. */
    private CrystalResult merge(ItemStack cursor, CrystalItemData cursorCrystal, ItemStack target, CrystalItemData targetCrystal) {
        if (target.getAmount() > 1) {
            return CrystalResult.unchanged(messages.format("crystal.merge-single"));
        }
        int cap = maxMerge.getAsInt();
        CrystalItemData merged = targetCrystal.mergeWith(cursorCrystal, cap); // target keeps the slot; cursor lands on top
        if (merged == null) {
            return CrystalResult.unchanged(messages.format("crystal.merge-cap", "MAX", cap));
        }
        // §ADR-0035: a non-stackable crystal cannot merge with another of the same type into one multi-crystal.
        String clash = duplicateNonStackable(merged.keys());
        if (clash != null) {
            return CrystalResult.unchanged(messages.format("crystal.merge-not-stackable", "CRYSTAL", label(List.of(clash))));
        }
        ItemStack multi = mint(merged);
        consume(cursor);
        return CrystalResult.committed(multi, applySound(config.get()),
                messages.format("crystal.merge", "CRYSTAL", label(merged.keys())));
    }

    /** Extractor gesture: pop the topmost single off a multi-crystal ITEM, else off crystal-bearing GEAR. */
    public CrystalResult extract(ItemStack cursor, ItemStack target) {
        CrystalItemData targetCrystal = codec.read(target);
        if (targetCrystal != null) {
            return extractFromCrystal(cursor, target, targetCrystal);
        }
        return extractFromGear(cursor, target);
    }

    /** Pop {@code gear}'s last crystal entry and mint it back to the player; no-op when gear carries none. */
    private CrystalResult extractFromGear(ItemStack cursor, ItemStack gear) {
        ExtractResult result = enchanter.extractCrystal(gear);
        if (!result.ok()) {
            return CrystalResult.unchanged(result.message());
        }
        // The whole entry pops off intact (§ADR-0035): a merged entry mints back as ONE multi-crystal, which a
        // further extractor gesture on that item then splits into singles (extractFromCrystal).
        List<String> popped = CrystalItemData.componentsOf(result.poppedEntry());
        ItemStack minted = mint(new CrystalItemData(popped));
        consume(cursor);
        return CrystalResult.extracted(gear, minted, removeSound(config.get()),
                messages.format("crystal.extract-success", "CRYSTAL", label(popped)));
    }

    /** Split the topmost single off a multi-crystal ITEM: the item becomes the remainder, the single goes back. */
    private CrystalResult extractFromCrystal(ItemStack cursor, ItemStack crystalItem, CrystalItemData data) {
        if (crystalItem.getAmount() > 1) {
            return CrystalResult.unchanged(messages.format("crystal.merge-single")); // a stack of >1 is ambiguous
        }
        if (!data.isMulti()) {
            return CrystalResult.unchanged(messages.format("crystal.extract-not-multi")); // a single has nothing to split
        }
        List<String> components = new ArrayList<>(data.keys());
        String popped = components.remove(components.size() - 1); // the topmost (most-recently-merged) crystal
        ItemStack single = mint(CrystalItemData.single(popped));
        ItemStack remainder = mint(new CrystalItemData(components));
        consume(cursor);
        return CrystalResult.extracted(remainder, single, removeSound(config.get()),
                messages.format("crystal.extract-success", "CRYSTAL", label(List.of(popped))));
    }

    /** The lore template rendered for {@code keys}: {@code {DESCRIPTION}} expands to the stacked blocks, {@code {KINDS}} to the item kinds. */
    private List<String> renderLore(List<String> template, List<String> keys) {
        List<String> descriptionBlock = descriptionBlock(keys);
        String kinds = kindsLabel(keys);
        List<String> out = new ArrayList<>(template.size() + descriptionBlock.size());
        for (String line : template) {
            if (line.contains("{DESCRIPTION}")) {
                out.addAll(descriptionBlock); // a LINE-EXPANDING token: this template line becomes the whole block
            } else {
                out.add(line.replace("{KINDS}", kinds));
            }
        }
        return out;
    }

    /** Each component's authored description block, in merge order, separated by ONE blank line (§1). */
    private List<String> descriptionBlock(List<String> keys) {
        List<String> out = new ArrayList<>();
        for (String key : keys) {
            CrystalDef def = content.library().crystalDefOf(key);
            List<String> block = def != null ? def.description() : List.of();
            if (block.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.add("");
            }
            out.addAll(block);
        }
        return out;
    }

    /** The {@code {KINDS}} label — the INTERSECTION of the components' applies-to (where the whole stack can sit). */
    private String kindsLabel(List<String> keys) {
        List<String> intersection = null;
        for (String key : keys) {
            CrystalDef def = content.library().crystalDefOf(key);
            List<String> applies = def != null ? def.appliesTo() : List.of();
            if (intersection == null) {
                intersection = new ArrayList<>(applies);
            } else {
                intersection.retainAll(applies);
            }
        }
        return ItemGroups.kindsLabel(intersection == null ? List.of() : intersection);
    }

    private static String applySound(CrystalConfig cfg) {
        return cfg.sounds() ? cfg.soundApply() : null;
    }

    private static String removeSound(CrystalConfig cfg) {
        return cfg.sounds() ? cfg.soundRemove() : null;
    }

    /** The component display name(s) for a chat message, joined like the item name ({@link CrystalNames}). */
    private String label(List<String> keys) {
        return CrystalNames.join(config.get().name(), keys, this::displayName);
    }

    private String displayName(String key) {
        String name = content.library().displayNameOf(key);
        return name != null ? name : key;
    }

    /** The first non-stackable component that would appear more than once in {@code keys} (§ADR-0035), or {@code null}. */
    private String duplicateNonStackable(List<String> keys) {
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String key : keys) {
            if (!seen.add(key) && !isStackable(key)) {
                return key;
            }
        }
        return null;
    }

    /** Whether a crystal key stacks — unknown content defaults to stackable, so a stale key never blocks a merge. */
    private boolean isStackable(String key) {
        CrystalDef def = content.library().crystalDefOf(key);
        return def == null || def.stackable();
    }

    private static void consume(ItemStack stack) {
        stack.setAmount(stack.getAmount() - 1);
    }
}
