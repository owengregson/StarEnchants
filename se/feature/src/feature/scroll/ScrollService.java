package feature.scroll;

import compile.load.ContentHolder;
import compile.load.ScrollsConfig;
import feature.carrier.CarrierResult;
import feature.carrier.CarrierService;
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
 * Scroll-family cold path (docs/v3-directives.md §I) — mints the book-economy scrolls and applies one onto
 * a target by its kind (black: extract a random enchant to a book; randomizer: reroll a book's success;
 * transmog: reorder enchant lore). Gear/book mutation reuses the shared authorities ({@link CombatCodec} +
 * {@link LoreRenderer}, {@link CarrierService}). The roll is injected for tests. Folia-correct: a gesture
 * fires on the clicking player's own region thread.
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
    private final item.lang.Messages messages; // §L lang.yml — black/randomizer/transmog result messages + guards
    private final item.codec.GodlyTransmogCodec godlyCodec; // §I/§K physical godly-transmog marker; null in tests

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
        this.godlyCodec = godlyCodec; // nullable: tests that never mint the godly tool omit it
    }

    /** Whether {@code stack} is a drag-onto-target scroll handled by this service (black / randomizer / transmog). */
    public boolean isScroll(ItemStack stack) {
        String kind = scrolls.kind(stack);
        return BLACK.equals(kind) || RANDOMIZER.equals(kind) || TRANSMOG.equals(kind);
    }

    /** Mint a black scroll (extract one enchant from gear into a book). */
    public ItemStack mintBlack() {
        ScrollsConfig.Black cfg = config.get().black();
        ItemStack stack = ItemFactory.build(
                cfg.material(), Material.INK_SAC, cfg.name(), cfg.lore());
        scrolls.mark(stack, BLACK);
        return stack;
    }

    /** Mint a randomizer scroll (reroll an enchant book's success chance). */
    public ItemStack mintRandomizer() {
        ScrollsConfig.Randomizer cfg = config.get().randomizer();
        ItemStack stack = ItemFactory.build(
                cfg.material(), Material.SUGAR, cfg.name(), cfg.lore());
        scrolls.mark(stack, RANDOMIZER);
        return stack;
    }

    /** Mint a transmog scroll (reorder an item's enchant lore + append a name suffix). */
    public ItemStack mintTransmog() {
        ScrollsConfig.Transmog cfg = config.get().transmog();
        ItemStack stack = ItemFactory.build(
                cfg.material(), Material.PURPLE_DYE, cfg.name(), cfg.lore());
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
        ItemStack stack = ItemFactory.build(
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

    /** Transmog scroll: reorder {@code gear}'s enchant display order and append the configured name suffix. */
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
        // Cosmetic reorder — combat behaviour is order-independent.
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(current.enchants().entrySet());
        java.util.Collections.shuffle(entries, random);
        Map<String, Integer> reordered = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : entries) {
            reordered.put(e.getKey(), e.getValue());
        }
        CombatState next = new CombatState(reordered, current.crystals(), current.setKey(),
                current.omni(), current.heroic(), current.added());
        combat.write(gear, next);
        lore.apply(gear, next); // re-render the enchant lore in the new order
        appendNameSuffix(gear, cfg.nameSuffix());
        consume(cursor);
        return ScrollResult.committed(gear, null, messages.format("scroll.transmog.success"));
    }

    /**
     * Deterministic enchant reorder behind the Godly Transmog GUI (vs {@link #applyTransmog}'s shuffle);
     * cosmetic only. No-op {@code false} when {@code orderedKeys} isn't a permutation of the current keys,
     * so an enchant can't be dropped or duplicated. Caller (the reorder menu) runs on the region thread.
     */
    public boolean reorder(ItemStack gear, List<String> orderedKeys) {
        if (gear == null || gear.getType() == Material.AIR || gear.getAmount() > 1) {
            return false;
        }
        CombatState current = combat.read(gear);
        return reorderedEnchants(current.enchants(), orderedKeys).map(reordered -> {
            CombatState next = new CombatState(reordered, current.crystals(), current.setKey(),
                    current.omni(), current.heroic(), current.added());
            combat.write(gear, next);
            lore.apply(gear, next); // re-render the enchant lore in the chosen order
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

    /** Append {@code suffix} to {@code gear}'s display name when it has one and is not already suffixed. */
    @SuppressWarnings("deprecation") // getDisplayName/setDisplayName: the floor-stable item-meta path
    private static void appendNameSuffix(ItemStack gear, String suffix) {
        String translated = ItemFactory.color(suffix);
        if (translated.isEmpty()) {
            return;
        }
        org.bukkit.inventory.meta.ItemMeta meta = gear.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return; // no custom name to append to — leave the vanilla name untouched (cross-version-safe)
        }
        String name = meta.getDisplayName();
        if (name.endsWith(translated)) {
            return; // already transmogged — don't stack suffixes
        }
        meta.setDisplayName(name + translated);
        gear.setItemMeta(meta);
    }

    /** Black scroll: extract one random enchant from {@code gear} into a book (success roll). */
    private ScrollResult applyBlack(ItemStack cursor, ItemStack gear) {
        ScrollsConfig.Black cfg = config.get().black();
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
        consume(cursor); // the scroll is spent whether the roll succeeds or fails
        if (random.nextInt(100) >= cfg.successChance()) {
            return ScrollResult.committed(gear, null, messages.format("scroll.black.fail")); // gear untouched, scroll spent
        }
        Map<String, Integer> remaining = new LinkedHashMap<>(current.enchants());
        remaining.remove(key);
        CombatState next = new CombatState(remaining, current.crystals(), current.setKey(),
                current.omni(), current.heroic(), current.added());
        combat.write(gear, next);
        lore.apply(gear, next); // re-render the gear's lore from the reduced state
        ItemStack book = carriers.mintBook(key, level); // the extracted enchant, as an enchant book
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
