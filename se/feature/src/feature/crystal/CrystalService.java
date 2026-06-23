package feature.crystal;

import compile.load.ContentHolder;
import compile.load.CrystalConfig;
import compile.load.CrystalDef;
import feature.apply.ApplyResult;
import feature.apply.ExtractResult;
import feature.apply.ItemEnchanter;
import item.codec.CrystalExtractorCodec;
import item.codec.CrystalItemCodec;
import item.codec.CrystalItemData;
import item.mint.ItemFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * The crystal item economy (docs/v3-directives.md §E) — the cold path that MINTS physical crystal items
 * from a configured likeness, drag-APPLIES them to gear (a {@link CrystalConfig#successChance} roll,
 * optionally {@link CrystalConfig#consumeOnFail}), and MERGES two single crystals into a multi-crystal
 * (pairs only). Identity lives in PDC ({@link CrystalItemCodec}); the applied crystal becomes one
 * {@link item.codec.CombatState} crystal-slot entry (a single key, or {@code "a+b"} for a multi), so a
 * multi-crystal occupies one slot but contributes both abilities — the additive fold sums overlaps.
 *
 * <p>Gear mutation + slot/eligibility validation are delegated to {@link ItemEnchanter} (the one apply
 * authority); this layer owns the roll, the consume, the merge, and the configured messaging. The roll
 * is the only non-determinism, injected as a {@link Random} for testability. Folia-correct: a gesture
 * fires on the clicking player's own region thread, so mutating their cursor/inventory is in-thread.
 */
public final class CrystalService {

    private final CrystalItemCodec codec;
    private final CrystalExtractorCodec extractorCodec;
    private final ItemEnchanter enchanter;
    private final ContentHolder content;
    private final Supplier<CrystalConfig> config;
    private final Random random;
    private final item.lang.Messages messages; // §L lang.yml — apply/merge/extract result messages

    /** Default-messages form (tests/fixtures). */
    public CrystalService(CrystalItemCodec codec, CrystalExtractorCodec extractorCodec, ItemEnchanter enchanter,
                          ContentHolder content, Supplier<CrystalConfig> config, Random random) {
        this(codec, extractorCodec, enchanter, content, config, random, item.lang.Messages.defaults());
    }

    public CrystalService(CrystalItemCodec codec, CrystalExtractorCodec extractorCodec, ItemEnchanter enchanter,
                          ContentHolder content, Supplier<CrystalConfig> config, Random random,
                          item.lang.Messages messages) {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.extractorCodec = Objects.requireNonNull(extractorCodec, "extractorCodec");
        this.enchanter = Objects.requireNonNull(enchanter, "enchanter");
        this.content = Objects.requireNonNull(content, "content");
        this.config = Objects.requireNonNull(config, "config");
        this.random = Objects.requireNonNull(random, "random");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /** Whether {@code stack} is a physical crystal item. */
    public boolean isCrystal(ItemStack stack) {
        return codec.read(stack) != null;
    }

    /** Whether {@code stack} is a crystal extractor item. */
    public boolean isExtractor(ItemStack stack) {
        return extractorCodec.isExtractor(stack);
    }

    /** Mint a crystal extractor item from the configured likeness. */
    public ItemStack mintExtractor() {
        CrystalConfig cfg = config.get();
        ItemStack stack = ItemFactory.build(
                ItemFactory.material(cfg.extractorMaterial(), Material.AMETHYST_CLUSTER),
                cfg.extractorName(),
                cfg.extractorLore());
        extractorCodec.mark(stack);
        return stack;
    }

    /** Mint a physical crystal item carrying {@code keys} (1 = single, 2 = multi) from the configured likeness. */
    public ItemStack mint(List<String> keys) {
        return mint(new CrystalItemData(keys));
    }

    /**
     * Mint a physical crystal item for {@code data}. A SINGLE crystal uses its own per-crystal likeness
     * (material/name/lore from its {@link compile.load.CrystalDef}, §E — "the lore is different per
     * crystal so they can explain their effects"), each field falling back to the shared
     * {@link CrystalConfig} when the crystal omits it. A MERGED multi-crystal carries two effects that
     * can't share one material, so it takes the shared likeness and concatenates each component's own
     * lore — the item still explains both effects.
     */
    public ItemStack mint(CrystalItemData data) {
        CrystalConfig cfg = config.get();
        List<String> keys = data.keys();
        String label = labelOf(keys);
        String materialToken;
        String nameTemplate;
        List<String> loreTemplate;
        if (keys.size() == 1) {
            CrystalDef def = content.library().crystalDefOf(keys.get(0));
            materialToken = def != null && def.material() != null ? def.material() : cfg.material();
            nameTemplate = def != null && def.name() != null ? def.name() : cfg.name();
            loreTemplate = def != null && !def.lore().isEmpty() ? def.lore() : cfg.lore();
        } else {
            materialToken = cfg.material();
            nameTemplate = cfg.name();
            loreTemplate = mergedLore(keys, cfg);
        }
        ItemStack stack = ItemFactory.build(
                ItemFactory.material(materialToken, Material.AMETHYST_SHARD),
                nameTemplate.replace("{CRYSTAL}", label),
                renderLore(loreTemplate, label));
        codec.write(stack, data);
        return stack;
    }

    /** The concatenated per-component lore of a merged multi-crystal (each rendered with its own name). */
    private List<String> mergedLore(List<String> keys, CrystalConfig cfg) {
        List<String> out = new ArrayList<>();
        for (String key : keys) {
            CrystalDef def = content.library().crystalDefOf(key);
            List<String> componentLore = def != null && !def.lore().isEmpty() ? def.lore() : cfg.lore();
            String name = content.library().displayNameOf(key);
            out.addAll(renderLore(componentLore, name != null ? name : key));
        }
        return out;
    }

    /**
     * Handle a crystal-on-something gesture: the {@code cursor} is a crystal; if {@code target} is also a
     * crystal it MERGES (pairs only), otherwise it APPLIES the crystal to {@code target} gear. Both stacks
     * are mutated in place where applicable; the returned {@link CrystalResult} tells the listener what to
     * commit. A {@code null} return-via-unchanged leaves both stacks untouched.
     */
    public CrystalResult interact(ItemStack cursor, ItemStack target) {
        CrystalItemData crystal = codec.read(cursor);
        if (crystal == null) {
            return CrystalResult.unchanged(null); // not a crystal — listener falls through (defensive)
        }
        CrystalItemData targetCrystal = codec.read(target);
        if (targetCrystal != null) {
            return merge(cursor, crystal, target, targetCrystal);
        }
        return apply(cursor, crystal, target);
    }

    /** Apply {@code crystal} to {@code gear}: pre-check eligibility/slots, roll, consume, mutate. */
    private CrystalResult apply(ItemStack cursor, CrystalItemData crystal, ItemStack gear) {
        CrystalConfig cfg = config.get();
        ApplyResult eligible = enchanter.checkCrystalEntry(gear, crystal.keys());
        if (!eligible.ok()) {
            // A slot-full message is configurable; other ineligibility uses the enchanter's reason.
            String message = eligible.message() != null && eligible.message().contains("crystal slot")
                    ? messages.format("crystal.no-slots") : eligible.message();
            return CrystalResult.unchanged(message); // never consume on an ineligible target
        }
        String label = labelOf(crystal.keys());
        if (random.nextInt(100) < cfg.successChance()) {
            enchanter.applyCrystalEntry(gear, crystal.keys(), true); // re-validates + appends one slot entry
            consume(cursor);
            return CrystalResult.committed(gear, applySound(cfg), messages.format("crystal.apply-success", "CRYSTAL", label));
        }
        if (cfg.consumeOnFail()) {
            consume(cursor);
            return CrystalResult.committed(gear, null, messages.format("crystal.apply-fail")); // gear unchanged, cursor spent
        }
        return CrystalResult.unchanged(messages.format("crystal.apply-fail"));
    }

    /**
     * Extract the most-recent crystal off {@code gear} using the extractor on the {@code cursor}: pop the
     * gear's last crystal, mint it back as a whole crystal item to hand the player, and spend the extractor.
     * A no-op (no consume) when the gear carries no crystal.
     */
    public CrystalResult extract(ItemStack cursor, ItemStack gear) {
        ExtractResult result = enchanter.extractCrystal(gear);
        if (!result.ok()) {
            return CrystalResult.unchanged(result.message());
        }
        List<String> components = CrystalItemData.componentsOf(result.poppedEntry());
        ItemStack minted = mint(new CrystalItemData(components));
        consume(cursor); // spend the extractor
        return CrystalResult.extracted(gear, minted, removeSound(config.get()),
                messages.format("crystal.extract-success", "CRYSTAL", labelOf(components)));
    }

    /** Merge two SINGLE crystals into a multi-crystal (pairs only): the target slot becomes the multi. */
    private CrystalResult merge(ItemStack cursor, CrystalItemData a, ItemStack target, CrystalItemData b) {
        if (target.getAmount() > 1) {
            return CrystalResult.unchanged(messages.format("crystal.merge-single"));
        }
        CrystalItemData merged = a.mergeWith(b);
        if (merged == null) {
            return CrystalResult.unchanged(messages.format("crystal.merge-pairs"));
        }
        ItemStack multi = mint(merged);
        consume(cursor);
        return CrystalResult.committed(multi, applySound(config.get()),
                messages.format("crystal.merge", "CRYSTAL", labelOf(merged.keys())));
    }

    /** The configured apply/merge sound token, or {@code null} when sounds are disabled. */
    private static String applySound(CrystalConfig cfg) {
        return cfg.sounds() ? cfg.soundApply() : null;
    }

    /** The configured extract sound token, or {@code null} when sounds are disabled. */
    private static String removeSound(CrystalConfig cfg) {
        return cfg.sounds() ? cfg.soundRemove() : null;
    }

    /** The component crystal display names joined for a {@code {CRYSTAL}} placeholder ({@code "Jolt + Frost"}). */
    private String labelOf(List<String> keys) {
        StringBuilder out = new StringBuilder();
        for (String key : keys) {
            String name = content.library().displayNameOf(key);
            if (out.length() > 0) {
                out.append("&7 + ");
            }
            out.append(name != null ? name : key);
        }
        return out.toString();
    }

    private static List<String> renderLore(List<String> lore, String label) {
        List<String> out = new ArrayList<>(lore.size());
        for (String line : lore) {
            out.add(line.replace("{CRYSTAL}", label));
        }
        return out;
    }

    private static void consume(ItemStack stack) {
        stack.setAmount(stack.getAmount() - 1);
    }
}
