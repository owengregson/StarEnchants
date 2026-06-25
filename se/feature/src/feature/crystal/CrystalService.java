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
 * The crystal item economy (docs/v3-directives.md §E) — mints crystals, APPLIES them to gear (a
 * {@link CrystalConfig#successChance} roll, optionally {@link CrystalConfig#consumeOnFail}), and MERGES two
 * singles into a multi-crystal (pairs only). A multi applies as one {@code "a+b"} slot entry, so it occupies
 * one slot yet contributes both abilities — the additive fold sums overlaps. Gear mutation + slot validation
 * delegate to {@link ItemEnchanter}; the roll is injected as a {@link Random} for testability.
 */
public final class CrystalService {

    private final CrystalItemCodec codec;
    private final CrystalExtractorCodec extractorCodec;
    private final ItemEnchanter enchanter;
    private final ContentHolder content;
    private final Supplier<CrystalConfig> config;
    private final Random random;
    private final item.lang.Messages messages; // §L lang.yml

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

    public boolean isCrystal(ItemStack stack) {
        return codec.read(stack) != null;
    }

    public boolean isExtractor(ItemStack stack) {
        return extractorCodec.isExtractor(stack);
    }

    public ItemStack mintExtractor() {
        CrystalConfig cfg = config.get();
        ItemStack stack = ItemFactory.build(
                cfg.extractorMaterial(), Material.AMETHYST_CLUSTER,
                cfg.extractorName(),
                cfg.extractorLore());
        extractorCodec.mark(stack);
        return stack;
    }

    public ItemStack mint(List<String> keys) {
        return mint(new CrystalItemData(keys));
    }

    /**
     * Mint a crystal for {@code data}. A SINGLE uses its own per-crystal likeness (§E), falling back to the
     * shared {@link CrystalConfig}; a MERGED multi can't share one material, so it takes the shared likeness
     * and concatenates each component's lore.
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
                materialToken, Material.AMETHYST_SHARD,
                nameTemplate.replace("{CRYSTAL}", label),
                renderLore(loreTemplate, label));
        codec.write(stack, data);
        return stack;
    }

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

    /** Crystal-on-something gesture: target crystal → MERGE (pairs only), else APPLY to gear. */
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
            // A slot-full message is configurable; other ineligibility uses the enchanter's reason.
            String message = eligible.message() != null && eligible.message().contains("crystal slot")
                    ? messages.format("crystal.no-slots") : eligible.message();
            return CrystalResult.unchanged(message); // never consume on an ineligible target
        }
        String label = labelOf(crystal.keys());
        if (random.nextInt(100) < cfg.successChance()) {
            enchanter.applyCrystalEntry(gear, crystal.keys(), true);
            consume(cursor);
            return CrystalResult.committed(gear, applySound(cfg), messages.format("crystal.apply-success", "CRYSTAL", label));
        }
        if (cfg.consumeOnFail()) {
            consume(cursor);
            return CrystalResult.committed(gear, null, messages.format("crystal.apply-fail")); // gear unchanged, cursor spent
        }
        return CrystalResult.unchanged(messages.format("crystal.apply-fail"));
    }

    /** Pop {@code gear}'s most-recent crystal and mint it back to the player; no-op when gear carries none. */
    public CrystalResult extract(ItemStack cursor, ItemStack gear) {
        ExtractResult result = enchanter.extractCrystal(gear);
        if (!result.ok()) {
            return CrystalResult.unchanged(result.message());
        }
        List<String> components = CrystalItemData.componentsOf(result.poppedEntry());
        ItemStack minted = mint(new CrystalItemData(components));
        consume(cursor);
        return CrystalResult.extracted(gear, minted, removeSound(config.get()),
                messages.format("crystal.extract-success", "CRYSTAL", labelOf(components)));
    }

    /** Merge two single crystals into a multi (pairs only). */
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

    private static String applySound(CrystalConfig cfg) {
        return cfg.sounds() ? cfg.soundApply() : null;
    }

    private static String removeSound(CrystalConfig cfg) {
        return cfg.sounds() ? cfg.soundRemove() : null;
    }

    /** Component display names joined for the {@code {CRYSTAL}} placeholder ({@code "Jolt + Frost"}). */
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
