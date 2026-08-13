package feature.scroll;

import compile.load.ContentHolder;
import compile.load.ScrollsConfig;
import compile.load.SoundCue;
import compile.load.TierRegistry;
import engine.stores.BookRateStore;
import feature.apply.Rolls;
import feature.apply.GestureOutcome;
import feature.carrier.CarrierService;
import feature.compat.Mats;
import feature.menu.MenuIcons;
import item.codec.CombatCodec;
import item.codec.CombatState;
import item.codec.ScrollCodec;
import item.mint.ItemFactory;
import item.mint.VanillaEnchants;
import item.render.LoreRenderer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import platform.item.ItemGroups;
import platform.text.Tokens;
import schema.spec.Ranges;

/**
 * Scroll-family cold path (§I): mints the book-economy scrolls and applies one onto a target by its kind
 * (black: extract a random enchant to a book; randomizer: reroll a book's success; transmog: reorder
 * enchant lore). The roll is injected for tests.
 */
public final class ScrollService {

    /** Scroll kinds handled by this service (drag-onto-target scrolls). */
    public static final String BLACK = "BLACK";
    public static final String HEROIC_BLACK = "HEROIC-BLACK-SCROLL";
    public static final String RANDOMIZER = "RANDOMIZER";
    public static final String TRANSMOG = "TRANSMOG";

    private final ScrollCodec scrolls;
    private final CombatCodec combat;
    private final LoreRenderer lore;
    private final CarrierService carriers;
    private final ContentHolder content;
    private final Supplier<ScrollsConfig> config;
    private final Random random;
    private final platform.lang.Messages messages;
    private final item.codec.GodlyTransmogCodec godlyCodec; // null in tests that never mint the godly tool
    private final ItemGroups groups; // §I applies-to gate — the black scroll only extracts from the configured item kinds
    private final BookRateStore bookRates; // BOOK_RATE_MODIFIER's generate-site charge, spent at the extraction roll
    private final VanillaEnchants vanilla; // higher-tier Black Scroll's forced, hidden glint

    /** {@code godlyCodec} enables minting the physical godly-transmog tool (null disables it). */
    public ScrollService(ScrollCodec scrolls, CombatCodec combat, LoreRenderer lore, CarrierService carriers,
                         ContentHolder content, Supplier<ScrollsConfig> config, Random random,
                         platform.lang.Messages messages, item.codec.GodlyTransmogCodec godlyCodec,
                         ItemGroups groups) {
        this(scrolls, combat, lore, carriers, content, config, random, messages, godlyCodec, groups,
                new BookRateStore(), VanillaEnchants.NONE);
    }

    /** The composition-root form: {@code bookRates} must be the engine aggregate's store, or a pet's armed
     *  generate charge is never seen by the roll it was armed for. */
    public ScrollService(ScrollCodec scrolls, CombatCodec combat, LoreRenderer lore, CarrierService carriers,
                         ContentHolder content, Supplier<ScrollsConfig> config, Random random,
                         platform.lang.Messages messages, item.codec.GodlyTransmogCodec godlyCodec,
                         ItemGroups groups, BookRateStore bookRates) {
        this(scrolls, combat, lore, carriers, content, config, random, messages, godlyCodec, groups, bookRates,
                VanillaEnchants.NONE);
    }

    /** Composition-root form with the cross-version vanilla-enchant resolver used for the higher-tier scroll's
     *  forced, hidden glint. Older constructors remain inert for server-free tests. */
    public ScrollService(ScrollCodec scrolls, CombatCodec combat, LoreRenderer lore, CarrierService carriers,
                         ContentHolder content, Supplier<ScrollsConfig> config, Random random,
                         platform.lang.Messages messages, item.codec.GodlyTransmogCodec godlyCodec,
                         ItemGroups groups, BookRateStore bookRates, VanillaEnchants vanilla) {
        this.scrolls = Objects.requireNonNull(scrolls, "scrolls");
        this.combat = Objects.requireNonNull(combat, "combat");
        this.lore = Objects.requireNonNull(lore, "lore");
        this.carriers = Objects.requireNonNull(carriers, "carriers");
        this.content = Objects.requireNonNull(content, "content");
        this.config = Objects.requireNonNull(config, "config");
        this.random = Objects.requireNonNull(random, "random");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.godlyCodec = godlyCodec;
        this.groups = Objects.requireNonNull(groups, "groups");
        this.bookRates = Objects.requireNonNull(bookRates, "bookRates");
        this.vanilla = Objects.requireNonNull(vanilla, "vanilla");
    }

    public boolean isScroll(ItemStack stack) {
        String kind = scrolls.kind(stack);
        return BLACK.equals(kind) || HEROIC_BLACK.equals(kind) || RANDOMIZER.equals(kind) || TRANSMOG.equals(kind);
    }

    /**
     * Mint a black scroll. Extraction always succeeds (§I); the scroll's drawn-book CONVERSION success rate is
     * rolled in the config {@code [min-convert, max-convert]} range (clamped to the global ceiling) and stamped
     * on the scroll so its lore shows it.
     */
    public ItemStack mintBlack() {
        ScrollsConfig.Black cfg = config.get().black();
        return buildBlack(Rolls.between(random, cfg.minConvert(), cfg.maxConvert()));
    }

    /** Mint a black scroll whose drawn book applies at an EXPLICIT conversion success rate (§J give form). */
    public ItemStack mintBlack(int fixedConvert) {
        return buildBlack(fixedConvert);
    }

    /** Mint the configured higher-tier Black Scroll; its conversion range is stored in item state. */
    public ItemStack mintHeroicBlack() {
        ScrollsConfig.HeroicBlack cfg = config.get().heroicBlack();
        return buildHeroicBlack(cfg.minConvert(), cfg.maxConvert());
    }

    /** Visible pack-owned name; the stored kind and command key remain {@code heroic-black-scroll}. */
    public String heroicBlackName() {
        return config.get().heroicBlack().name();
    }

    private ItemStack buildBlack(int convert) {
        ScrollsConfig.Black cfg = config.get().black();
        int conv = carriers.capBookSuccess(convert); // the drawn book's rate respects the global ceiling (§I)
        String kinds = ItemGroups.kindsLabel(cfg.appliesTo());
        ItemStack stack = ItemFactory.buildItem(
                cfg.material(), Mats.or("INK_SAC", Material.PAPER),
                subConvert(cfg.name(), conv, kinds),
                Tokens.subLines(cfg.lore(), "SUCCESS", conv, "FAILURE", 100 - conv, "KINDS", kinds));
        if (cfg.shiny()) {
            MenuIcons.glow(vanilla, stack); // cosmetic-only; unsupported servers gracefully leave it plain
        }
        scrolls.mark(stack, BLACK);
        scrolls.markConvert(stack, conv);
        return stack;
    }

    private ItemStack buildHeroicBlack(int minConvert, int maxConvert) {
        ScrollsConfig.HeroicBlack cfg = config.get().heroicBlack();
        Ranges.IntRange range = Ranges.percentRange(minConvert, maxConvert);
        String kinds = ItemGroups.kindsLabel(cfg.appliesTo());
        ItemStack stack = ItemFactory.buildItem(
                cfg.material(), Mats.or("DRIED_KELP", Mats.or("INK_SAC", Material.PAPER)),
                Tokens.sub(cfg.name(), "MIN", range.min(), "MAX", range.max(), "KINDS", kinds),
                Tokens.subLines(cfg.lore(), "MIN", range.min(), "MAX", range.max(), "KINDS", kinds));
        if (cfg.shiny()) {
            MenuIcons.glow(vanilla, stack); // cosmetic-only; unsupported servers gracefully leave it plain
        }
        scrolls.mark(stack, HEROIC_BLACK);
        scrolls.markHeroicRange(stack, range.min(), range.max());
        return stack;
    }

    /**
     * Substitute the black-scroll placeholders: {@code {SUCCESS}} = the drawn book's success rate,
     * {@code {FAILURE}} = its complement, {@code {KINDS}} = the applies-to label.
     */
    private static String subConvert(String s, int convert, String kinds) {
        return Tokens.sub(s, "SUCCESS", convert, "FAILURE", 100 - convert, "KINDS", kinds);
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

    /** Dispatch a scroll-on-target gesture by the cursor scroll's kind; no actor = no book-rate charge. */
    public GestureOutcome interact(ItemStack cursor, ItemStack target) {
        return interact(null, cursor, target);
    }

    /** Dispatch a scroll-on-target gesture by the cursor scroll's kind. */
    public GestureOutcome interact(Player actor, ItemStack cursor, ItemStack target) {
        String kind = scrolls.kind(cursor);
        if (BLACK.equals(kind)) {
            return applyBlack(actor, cursor, target, false);
        }
        if (HEROIC_BLACK.equals(kind)) {
            return applyBlack(actor, cursor, target, true);
        }
        if (RANDOMIZER.equals(kind)) {
            return applyRandomizer(cursor, target);
        }
        if (TRANSMOG.equals(kind)) {
            return applyTransmog(cursor, target);
        }
        return GestureOutcome.noop(null); // not a scroll this service owns (defensive)
    }

    /**
     * Transmog scroll: ORGANISE {@code gear}'s enchant display by rarity (§I). Custom enchants are sorted by
     * tier WEIGHT descending (highest rarity on top), so the lore reads top-down by rarity; vanilla Minecraft
     * enchants render above the lore by the client, so they sit above the custom block ("real MC enchants on
     * top"). The enchant-count name suffix is NOT stamped here any more — it is a fixed part of the name on any
     * enchanted item, (re)stamped from state by {@link LoreRenderer#apply} (custom enchants only; vanilla never
     * counts), which the re-render below triggers. Consumable and re-applicable.
     */
    private GestureOutcome applyTransmog(ItemStack cursor, ItemStack gear) {
        if (gear == null || gear.getType() == Material.AIR) {
            return GestureOutcome.noop(messages.format("scroll.transmog.apply-target"));
        }
        if (gear.getAmount() > 1) {
            return GestureOutcome.noop(messages.format("common.single-item"));
        }
        CombatState current = combat.read(gear);
        if (current.enchants().isEmpty()) {
            return GestureOutcome.noop(messages.format("scroll.transmog.no-enchants"));
        }
        Map<String, Integer> reordered = sortedByTierWeight(current.enchants());
        CombatState next = new CombatState(reordered, current.crystals(), current.setKey(),
                current.setWeaponKey(), current.omni(), current.heroic(), current.added(), current.maskKey(), current.reforgeKey());
        combat.write(gear, next);
        lore.apply(gear, next); // re-renders the sorted enchant lore AND (re)stamps the §I enchant-count suffix
        consume(cursor);
        return GestureOutcome.committed(gear, messages.format("scroll.transmog.success"));
    }

    /** Order custom enchants by rarity-tier weight (highest first); ties broken by key for determinism. */
    private Map<String, Integer> sortedByTierWeight(Map<String, Integer> enchants) {
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(enchants.entrySet());
        entries.sort(Comparator
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
        TierRegistry.Tier t = content.library().tiers().tier(tier);
        return t == null ? 0 : t.weight();
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
                    current.setWeaponKey(), current.omni(), current.heroic(), current.added(), current.maskKey(), current.reforgeKey());
            combat.write(gear, next);
            lore.apply(gear, next);
            return true;
        }).orElse(false);
    }

    /**
     * The reordered enchant map, or empty when {@code orderedKeys} isn't a permutation of {@code current}'s
     * keys. Pure (no item/server) so the permutation guard is unit-tested.
     */
    public static Optional<Map<String, Integer>> reorderedEnchants(
            Map<String, Integer> current, List<String> orderedKeys) {
        if (orderedKeys.size() != current.size()
                || !new HashSet<>(orderedKeys).equals(current.keySet())) {
            return Optional.empty(); // refuse rather than lose/duplicate an enchant
        }
        Map<String, Integer> reordered = new LinkedHashMap<>();
        for (String key : orderedKeys) {
            reordered.put(key, current.get(key));
        }
        return Optional.of(reordered);
    }

    /**
     * Black scroll: extract one random enchant from {@code gear} into a book. The extraction ALWAYS succeeds
     * (§I); the drawn book carries the scroll's stamped conversion success rate (a legacy scroll with no stamp
     * falls back to the global ceiling). Both the scroll's stamp and the apply re-cap to the live ceiling.
     */
    private GestureOutcome applyBlack(Player actor, ItemStack cursor, ItemStack gear, boolean heroic) {
        ScrollsConfig active = config.get();
        ScrollsConfig.Black bcfg = active.black();
        ScrollsConfig.HeroicBlack hcfg = active.heroicBlack();
        String scrollName = heroic ? hcfg.name() : bcfg.name();
        if (gear == null || gear.getType() == Material.AIR) {
            return GestureOutcome.noop(messages.format(
                    heroic ? "scroll.heroic-black.apply-target" : "scroll.black.apply-target",
                    "SCROLL", scrollName));
        }
        if (gear.getAmount() > 1) {
            return GestureOutcome.noop(messages.format("common.single-item"));
        }
        List<String> appliesTo = heroic ? hcfg.appliesTo() : bcfg.appliesTo();
        if (!groups.matches(gear.getType(), appliesTo)) {
            return GestureOutcome.noop(messages.format("common.wrong-applies", "KINDS", ItemGroups.kindsLabel(appliesTo)));
        }
        CombatState current = combat.read(gear);
        if (current.enchants().isEmpty()) {
            return GestureOutcome.noop(messages.format(
                    heroic ? "scroll.heroic-black.no-eligible" : "scroll.black.no-enchants",
                    "SCROLL", scrollName));
        }
        List<String> allowed = heroic ? hcfg.eligibleTiers() : bcfg.eligibleTiers();
        Set<String> knownTiers = new HashSet<>();
        for (TierRegistry.Tier tier : content.library().tiers().tiers()) {
            knownTiers.add(tier.name());
        }
        Optional<ScrollCandidates.Candidate> selected = ScrollCandidates.choose(
                current.enchants(), content.library()::tierOf, knownTiers, allowed, random);
        if (selected.isEmpty()) {
            return GestureOutcome.noop(messages.format(
                    heroic ? "scroll.heroic-black.no-eligible" : "scroll.black.no-eligible",
                    "SCROLL", scrollName));
        }
        ScrollCandidates.Candidate candidate = selected.get();
        String key = candidate.key();
        int level = candidate.level();
        int baseConvert;
        if (heroic) {
            Ranges.IntRange range = scrolls.heroicRangeOf(cursor, hcfg.minConvert(), hcfg.maxConvert());
            baseConvert = Rolls.between(random, range.min(), range.max());
        } else {
            baseConvert = scrolls.convertOf(cursor, carriers.capBookSuccess(100));
        }
        // The generate-site charge is spent HERE, not at mint: a scroll already in the world was minted long
        // before anyone armed a pet, so the modifier has to land on the extraction roll that reads the rate
        // back. Eligibility is already confirmed, so a refused scroll never spends the charge.
        int bonus = actor == null ? 0 : bookRates.consume(actor.getUniqueId(), BookRateStore.GENERATE);
        int convert = carriers.capBookSuccess(baseConvert + bonus);
        Map<String, Integer> remaining = ScrollCandidates.remove(current.enchants(), candidate).orElseThrow();
        CombatState next = current.withEnchants(remaining);
        combat.write(gear, next);
        lore.apply(gear, next);
        consume(cursor); // eligibility was confirmed before either item is consumed or mutated
        ItemStack book = carriers.mintBook(key, level, convert); // extracted enchant → a book at the conversion rate
        String message = messages.format(heroic ? "scroll.heroic-black.success" : "scroll.black.success",
                "ENCHANT", displayOf(key), "SCROLL", scrollName);
        SoundCue sound = heroic ? hcfg.sound() : bcfg.sound();
        List<String> particles = heroic ? hcfg.particles() : bcfg.particles();
        return GestureOutcome.committed(gear, book, GestureOutcome.Cue.of(sound, particles), message);
    }

    /** Randomizer scroll: reroll a book's success chance to a random value in the configured range. */
    private GestureOutcome applyRandomizer(ItemStack cursor, ItemStack book) {
        ScrollsConfig.Randomizer cfg = config.get().randomizer();
        if (book == null || book.getType() == Material.AIR) {
            return GestureOutcome.noop(messages.format("scroll.randomizer.apply-target"));
        }
        if (book.getAmount() > 1) {
            return GestureOutcome.noop(messages.format("scroll.randomizer.single-book"));
        }
        int target = Rolls.between(random, cfg.minPercent(), cfg.maxPercent());
        if (!carriers.rerollSuccess(book, target)) {
            return GestureOutcome.noop(messages.format("scroll.randomizer.not-book")); // not a book — don't waste the scroll
        }
        consume(cursor);
        return GestureOutcome.committed(book, messages.format("scroll.randomizer.success", "PERCENT", target));
    }

    private String displayOf(String key) {
        String name = content.library().displayNameOf(key);
        return name != null ? name : key;
    }

    private static void consume(ItemStack stack) {
        stack.setAmount(stack.getAmount() - 1);
    }
}
