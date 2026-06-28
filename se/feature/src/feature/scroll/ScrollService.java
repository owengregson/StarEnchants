package feature.scroll;

import compile.load.ContentHolder;
import compile.load.ScrollsConfig;
import feature.carrier.CarrierResult;
import feature.carrier.CarrierService;
import feature.compat.Mats;
import item.codec.CombatCodec;
import item.codec.CombatState;
import item.codec.ScrollCodec;
import item.mint.ItemFactory;
import item.render.LoreRenderer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Scroll-family cold path (§I): mints the book-economy scrolls and applies one onto a target by its kind
 * (black: extract a random enchant to a book; randomizer: reroll a book's success; transmog: reorder
 * enchant lore). The roll is injected for tests.
 */
public final class ScrollService {

    /** Scroll kinds handled by this service (drag-onto-target scrolls). */
    public static final String BLACK = "BLACK";
    public static final String RANDOMIZER = "RANDOMIZER";
    public static final String TRANSMOG = "TRANSMOG";

    private final ScrollCodec scrolls;
    private final CombatCodec combat;
    private final LoreRenderer lore;
    private final CarrierService carriers;
    private final ContentHolder content;
    private final Supplier<ScrollsConfig> config;
    private final Random random;
    private final item.lang.Messages messages;
    private final item.codec.GodlyTransmogCodec godlyCodec; // null in tests that never mint the godly tool

    /** Default-messages form (tests/fixtures). */
    public ScrollService(ScrollCodec scrolls, CombatCodec combat, LoreRenderer lore, CarrierService carriers,
                         ContentHolder content, Supplier<ScrollsConfig> config, Random random) {
        this(scrolls, combat, lore, carriers, content, config, random, item.lang.Messages.defaults());
    }

    public ScrollService(ScrollCodec scrolls, CombatCodec combat, LoreRenderer lore, CarrierService carriers,
                         ContentHolder content, Supplier<ScrollsConfig> config, Random random,
                         item.lang.Messages messages) {
        this(scrolls, combat, lore, carriers, content, config, random, messages, null);
    }

    /** Full form (the composition root) — {@code godlyCodec} enables minting the physical godly-transmog tool. */
    public ScrollService(ScrollCodec scrolls, CombatCodec combat, LoreRenderer lore, CarrierService carriers,
                         ContentHolder content, Supplier<ScrollsConfig> config, Random random,
                         item.lang.Messages messages, item.codec.GodlyTransmogCodec godlyCodec) {
        this.scrolls = Objects.requireNonNull(scrolls, "scrolls");
        this.combat = Objects.requireNonNull(combat, "combat");
        this.lore = Objects.requireNonNull(lore, "lore");
        this.carriers = Objects.requireNonNull(carriers, "carriers");
        this.content = Objects.requireNonNull(content, "content");
        this.config = Objects.requireNonNull(config, "config");
        this.random = Objects.requireNonNull(random, "random");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.godlyCodec = godlyCodec;
    }

    public boolean isScroll(ItemStack stack) {
        String kind = scrolls.kind(stack);
        return BLACK.equals(kind) || RANDOMIZER.equals(kind) || TRANSMOG.equals(kind);
    }

    /**
     * Mint a black scroll. Extraction always succeeds (§I); the scroll's drawn-book CONVERSION success rate is
     * rolled in the config {@code [min-convert, max-convert]} range (clamped to the global ceiling) and stamped
     * on the scroll so its lore shows it.
     */
    public ItemStack mintBlack() {
        ScrollsConfig.Black cfg = config.get().black();
        int span = cfg.maxConvert() - cfg.minConvert();
        return buildBlack(span <= 0 ? cfg.minConvert() : cfg.minConvert() + random.nextInt(span + 1));
    }

    /** Mint a black scroll whose drawn book applies at an EXPLICIT conversion success rate (§J give form). */
    public ItemStack mintBlack(int fixedConvert) {
        return buildBlack(fixedConvert);
    }

    private ItemStack buildBlack(int convert) {
        ScrollsConfig.Black cfg = config.get().black();
        int conv = carriers.capBookSuccess(convert); // the drawn book's rate respects the global ceiling (§I)
        ItemStack stack = ItemFactory.buildItem(
                cfg.material(), Mats.or("INK_SAC", Material.PAPER),
                subConvert(cfg.name(), conv), subConvertLore(cfg.lore(), conv));
        scrolls.mark(stack, BLACK);
        scrolls.markConvert(stack, conv);
        return stack;
    }

    /**
     * Substitute the black-scroll conversion placeholders: {@code {SUCCESS}} = the drawn book's success rate,
     * {@code {FAILURE}} = its complement.
     */
    private static String subConvert(String s, int convert) {
        return s.replace("{SUCCESS}", Integer.toString(convert)).replace("{FAILURE}", Integer.toString(100 - convert));
    }

    private static List<String> subConvertLore(List<String> lore, int convert) {
        List<String> out = new ArrayList<>(lore.size());
        for (String line : lore) {
            out.add(subConvert(line, convert));
        }
        return out;
    }

    /** Mint a randomizer scroll (reroll an enchant book's success chance). */
    public ItemStack mintRandomizer() {
        ScrollsConfig.Randomizer cfg = config.get().randomizer();
        ItemStack stack = ItemFactory.buildItem(
                cfg.material(), Material.SUGAR, cfg.name(), cfg.lore());
        scrolls.mark(stack, RANDOMIZER);
        return stack;
    }

    /** Mint a transmog scroll (reorder an item's enchant lore + append a name suffix). */
    public ItemStack mintTransmog() {
        ScrollsConfig.Transmog cfg = config.get().transmog();
        ItemStack stack = ItemFactory.buildItem(
                cfg.material(), Mats.or("PURPLE_DYE", Material.PAPER), cfg.name(), cfg.lore());
        scrolls.mark(stack, TRANSMOG);
        return stack;
    }

    /** Whether {@code stack} is a physical godly-transmog tool (§I/§K) — opens the reorder GUI on a piece. */
    public boolean isGodlyTransmog(ItemStack stack) {
        return godlyCodec != null && godlyCodec.isGodlyTransmog(stack);
    }

    /** Mint the physical godly-transmog tool from its configured likeness (drag onto gear → reorder GUI). */
    public ItemStack mintGodlyTransmog() {
        Objects.requireNonNull(godlyCodec, "godlyCodec — this ScrollService was built without the godly codec");
        ScrollsConfig.Godly cfg = config.get().godly();
        ItemStack stack = ItemFactory.buildItem(
                cfg.material(), Material.NETHER_STAR, cfg.name(), cfg.lore());
        godlyCodec.mark(stack);
        return stack;
    }

    /** Dispatch a scroll-on-target gesture by the cursor scroll's kind. */
    public ScrollResult interact(ItemStack cursor, ItemStack target) {
        String kind = scrolls.kind(cursor);
        if (BLACK.equals(kind)) {
            return applyBlack(cursor, target);
        }
        if (RANDOMIZER.equals(kind)) {
            return applyRandomizer(cursor, target);
        }
        if (TRANSMOG.equals(kind)) {
            return applyTransmog(cursor, target);
        }
        return ScrollResult.unchanged(null); // not a scroll this service owns (defensive)
    }

    /**
     * Transmog scroll: ORGANISE {@code gear}'s enchant display by rarity and stamp the enchant count into the
     * name (§I redesign). Custom enchants are sorted by tier WEIGHT descending (highest rarity on top), so the
     * lore reads top-down by rarity; vanilla Minecraft enchants render above the lore by the client, so they
     * sit above the custom block ("real MC enchants on top"). The name gains a {@code {COUNT}} suffix —
     * re-applying replaces that suffix rather than stacking it. Consumable and re-applicable.
     */
    @SuppressWarnings("deprecation") // getDisplayName/setDisplayName: the floor-stable item-meta path
    private ScrollResult applyTransmog(ItemStack cursor, ItemStack gear) {
        ScrollsConfig.Transmog cfg = config.get().transmog();
        if (gear == null || gear.getType() == Material.AIR) {
            return ScrollResult.unchanged(messages.format("scroll.transmog.apply-target"));
        }
        if (gear.getAmount() > 1) {
            return ScrollResult.unchanged(messages.format("common.single-item"));
        }
        CombatState current = combat.read(gear);
        if (current.enchants().isEmpty()) {
            return ScrollResult.unchanged(messages.format("scroll.transmog.no-enchants"));
        }
        Map<String, Integer> reordered = sortedByTierWeight(current.enchants());
        CombatState next = new CombatState(reordered, current.crystals(), current.setKey(),
                current.setWeaponKey(), current.omni(), current.heroic(), current.added());
        combat.write(gear, next);
        lore.apply(gear, next);
        int count = current.enchants().size() + vanillaEnchantCount(gear); // custom + real MC enchants
        applyCountName(gear, cfg.nameSuffix(), count);
        consume(cursor);
        return ScrollResult.committed(gear, null, messages.format("scroll.transmog.success"));
    }

    /** Order custom enchants by rarity-tier weight (highest first); ties broken by key for determinism. */
    private Map<String, Integer> sortedByTierWeight(Map<String, Integer> enchants) {
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(enchants.entrySet());
        entries.sort(java.util.Comparator
                .comparingInt((Map.Entry<String, Integer> e) -> tierWeightOf(e.getKey())).reversed()
                .thenComparing(Map.Entry::getKey));
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : entries) {
            out.put(e.getKey(), e.getValue());
        }
        return out;
    }

    /** The rarity-tier weight of an enchant key (0 for no/unknown tier). */
    private int tierWeightOf(String enchantKey) {
        String tier = content.library().tierOf(enchantKey);
        if (tier == null) {
            return 0;
        }
        compile.load.TierRegistry.Tier t = content.library().tiers().tier(tier);
        return t == null ? 0 : t.weight();
    }

    @SuppressWarnings("deprecation") // getEnchants: the floor-stable item-meta path
    private static int vanillaEnchantCount(ItemStack gear) {
        org.bukkit.inventory.meta.ItemMeta meta = gear.getItemMeta();
        return meta == null ? 0 : meta.getEnchants().size();
    }

    /**
     * Deterministic enchant reorder behind the Godly Transmog GUI (vs {@link #applyTransmog}'s shuffle).
     * No-op {@code false} unless {@code orderedKeys} is a permutation of the current keys, so an enchant
     * can't be dropped or duplicated.
     */
    public boolean reorder(ItemStack gear, List<String> orderedKeys) {
        if (gear == null || gear.getType() == Material.AIR || gear.getAmount() > 1) {
            return false;
        }
        CombatState current = combat.read(gear);
        return reorderedEnchants(current.enchants(), orderedKeys).map(reordered -> {
            CombatState next = new CombatState(reordered, current.crystals(), current.setKey(),
                    current.setWeaponKey(), current.omni(), current.heroic(), current.added());
            combat.write(gear, next);
            lore.apply(gear, next);
            return true;
        }).orElse(false);
    }

    /**
     * The reordered enchant map, or empty when {@code orderedKeys} isn't a permutation of {@code current}'s
     * keys. Pure (no item/server) so the permutation guard is unit-tested.
     */
    public static java.util.Optional<Map<String, Integer>> reorderedEnchants(
            Map<String, Integer> current, List<String> orderedKeys) {
        if (orderedKeys.size() != current.size()
                || !new java.util.HashSet<>(orderedKeys).equals(current.keySet())) {
            return java.util.Optional.empty(); // refuse rather than lose/duplicate an enchant
        }
        Map<String, Integer> reordered = new LinkedHashMap<>();
        for (String key : orderedKeys) {
            reordered.put(key, current.get(key));
        }
        return java.util.Optional.of(reordered);
    }

    /** The transmog name-count placeholder, substituted into the configured suffix template. */
    private static final String COUNT_PLACEHOLDER = "{COUNT}";

    /**
     * Stamp the enchant {@code count} into {@code gear}'s name via the configured suffix template (default
     * {@code &r &d[&b&l&n{COUNT}&r&d]}): strip any previously-applied count suffix first so re-applying
     * REPLACES it rather than stacking. An item with no custom name gets the bare suffix.
     */
    @SuppressWarnings("deprecation") // getDisplayName/setDisplayName: the floor-stable item-meta path
    private static void applyCountName(ItemStack gear, String suffixTemplate, int count) {
        org.bukkit.inventory.meta.ItemMeta meta = gear.getItemMeta();
        if (meta == null) {
            return;
        }
        String base = meta.hasDisplayName() ? stripCountSuffix(meta.getDisplayName(), suffixTemplate) : "";
        String suffix = ItemFactory.color(suffixTemplate.replace(COUNT_PLACEHOLDER, Integer.toString(count)));
        meta.setDisplayName(base + suffix);
        gear.setItemMeta(meta);
    }

    /**
     * Strip a previously-applied count suffix from {@code name} (so a re-transmog replaces it). Builds a regex
     * from the translated suffix template with the count region as {@code \d+}, anchored to the end; if the
     * template carries no placeholder there is nothing to strip.
     */
    private static String stripCountSuffix(String name, String suffixTemplate) {
        String sentinel = "\u0000"; // never appears in a name -> marks the count slot
        String translated = ItemFactory.color(suffixTemplate.replace(COUNT_PLACEHOLDER, sentinel));
        int idx = translated.indexOf(sentinel);
        if (idx < 0) {
            return name;
        }
        String regex = java.util.regex.Pattern.quote(translated.substring(0, idx))
                + "\\d+" + java.util.regex.Pattern.quote(translated.substring(idx + sentinel.length())) + "$";
        return name.replaceAll(regex, "");
    }

    /**
     * Black scroll: extract one random enchant from {@code gear} into a book. The extraction ALWAYS succeeds
     * (§I); the drawn book carries the scroll's stamped conversion success rate (a legacy scroll with no stamp
     * falls back to the global ceiling). Both the scroll's stamp and the apply re-cap to the live ceiling.
     */
    private ScrollResult applyBlack(ItemStack cursor, ItemStack gear) {
        if (gear == null || gear.getType() == Material.AIR) {
            return ScrollResult.unchanged(messages.format("scroll.black.apply-target"));
        }
        if (gear.getAmount() > 1) {
            return ScrollResult.unchanged(messages.format("common.single-item"));
        }
        CombatState current = combat.read(gear);
        if (current.enchants().isEmpty()) {
            return ScrollResult.unchanged(messages.format("scroll.black.no-enchants"));
        }
        List<String> keys = new ArrayList<>(current.enchants().keySet());
        String key = keys.get(random.nextInt(keys.size()));
        int level = current.enchants().get(key);
        int convert = carriers.capBookSuccess(scrolls.convertOf(cursor, carriers.capBookSuccess(100)));
        consume(cursor);
        Map<String, Integer> remaining = new LinkedHashMap<>(current.enchants());
        remaining.remove(key);
        CombatState next = new CombatState(remaining, current.crystals(), current.setKey(),
                current.setWeaponKey(), current.omni(), current.heroic(), current.added());
        combat.write(gear, next);
        lore.apply(gear, next);
        ItemStack book = carriers.mintBook(key, level, convert); // extracted enchant → a book at the conversion rate
        return ScrollResult.committed(gear, book, messages.format("scroll.black.success", "ENCHANT", displayOf(key)));
    }

    /** Randomizer scroll: reroll a book's success chance to a random value in the configured range. */
    private ScrollResult applyRandomizer(ItemStack cursor, ItemStack book) {
        ScrollsConfig.Randomizer cfg = config.get().randomizer();
        if (book == null || book.getType() == Material.AIR) {
            return ScrollResult.unchanged(messages.format("scroll.randomizer.apply-target"));
        }
        if (book.getAmount() > 1) {
            return ScrollResult.unchanged(messages.format("scroll.randomizer.single-book"));
        }
        int target = cfg.minPercent() + random.nextInt(cfg.maxPercent() - cfg.minPercent() + 1);
        CarrierResult rolled = carriers.rerollSuccess(book, target);
        if (!rolled.consumed()) {
            return ScrollResult.unchanged(messages.format("scroll.randomizer.not-book")); // not a book — don't waste the scroll
        }
        consume(cursor);
        return ScrollResult.committed(book, null,
                messages.format("scroll.randomizer.success", "PERCENT", target));
    }

    private String displayOf(String key) {
        String name = content.library().displayNameOf(key);
        return name != null ? name : key;
    }

    private static void consume(ItemStack stack) {
        stack.setAmount(stack.getAmount() - 1);
    }
}
