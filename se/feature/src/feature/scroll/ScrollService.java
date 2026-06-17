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
 * The scroll-family cold path (docs/v3-directives.md §I) — MINTS the book-economy scrolls and APPLIES one
 * onto a target by its kind. The black scroll extracts one (random) enchant from gear into an enchant
 * book; the randomizer scroll rerolls an enchant book's success chance. Both are one-shot consumables.
 *
 * <p>Gear/book mutation reuses the shared authorities ({@link CombatCodec} + {@link LoreRenderer} for gear,
 * {@link CarrierService} for minting the extracted book and rerolling a book's success). The roll is the
 * only non-determinism, injected as a {@link Random} for testability. Folia-correct: a gesture fires on
 * the clicking player's own region thread, so mutating their cursor/inventory is in-thread.
 */
public final class ScrollService {

    /** Scroll kinds handled by this service (the book-economy scrolls). */
    public static final String BLACK = "BLACK";
    public static final String RANDOMIZER = "RANDOMIZER";

    private final ScrollCodec scrolls;
    private final CombatCodec combat;
    private final LoreRenderer lore;
    private final CarrierService carriers;
    private final ContentHolder content;
    private final Supplier<ScrollsConfig> config;
    private final Random random;

    public ScrollService(ScrollCodec scrolls, CombatCodec combat, LoreRenderer lore, CarrierService carriers,
                         ContentHolder content, Supplier<ScrollsConfig> config, Random random) {
        this.scrolls = Objects.requireNonNull(scrolls, "scrolls");
        this.combat = Objects.requireNonNull(combat, "combat");
        this.lore = Objects.requireNonNull(lore, "lore");
        this.carriers = Objects.requireNonNull(carriers, "carriers");
        this.content = Objects.requireNonNull(content, "content");
        this.config = Objects.requireNonNull(config, "config");
        this.random = Objects.requireNonNull(random, "random");
    }

    /** Whether {@code stack} is a scroll handled by this service (black / randomizer). */
    public boolean isScroll(ItemStack stack) {
        String kind = scrolls.kind(stack);
        return BLACK.equals(kind) || RANDOMIZER.equals(kind);
    }

    /** Mint a black scroll (extract one enchant from gear into a book). */
    public ItemStack mintBlack() {
        ScrollsConfig.Black cfg = config.get().black();
        ItemStack stack = ItemFactory.build(
                ItemFactory.material(cfg.material(), Material.INK_SAC), cfg.name(), cfg.lore());
        scrolls.mark(stack, BLACK);
        return stack;
    }

    /** Mint a randomizer scroll (reroll an enchant book's success chance). */
    public ItemStack mintRandomizer() {
        ScrollsConfig.Randomizer cfg = config.get().randomizer();
        ItemStack stack = ItemFactory.build(
                ItemFactory.material(cfg.material(), Material.SUGAR), cfg.name(), cfg.lore());
        scrolls.mark(stack, RANDOMIZER);
        return stack;
    }

    /**
     * Handle a scroll-on-target gesture: dispatch by the cursor scroll's kind. A kind this service does not
     * handle leaves both stacks untouched (the listener falls through).
     */
    public ScrollResult interact(ItemStack cursor, ItemStack target) {
        String kind = scrolls.kind(cursor);
        if (BLACK.equals(kind)) {
            return applyBlack(cursor, target);
        }
        if (RANDOMIZER.equals(kind)) {
            return applyRandomizer(cursor, target);
        }
        return ScrollResult.unchanged(null); // not a scroll this service owns (defensive)
    }

    /** Black scroll: extract one random enchant from {@code gear} into a book (success roll). */
    private ScrollResult applyBlack(ItemStack cursor, ItemStack gear) {
        ScrollsConfig.Black cfg = config.get().black();
        if (gear == null || gear.getType() == Material.AIR) {
            return ScrollResult.unchanged("§cApply the black scroll onto enchanted gear.");
        }
        if (gear.getAmount() > 1) {
            return ScrollResult.unchanged("§cApply to a single item — split the stack first.");
        }
        CombatState current = combat.read(gear);
        if (current.enchants().isEmpty()) {
            return ScrollResult.unchanged(color(cfg.messageNoEnchants()));
        }
        // Pick one enchant to (attempt to) extract — random across the item's enchants.
        List<String> keys = new ArrayList<>(current.enchants().keySet());
        String key = keys.get(random.nextInt(keys.size()));
        int level = current.enchants().get(key);
        consume(cursor); // the scroll is spent whether the roll succeeds or fails
        if (random.nextInt(100) >= cfg.successChance()) {
            return ScrollResult.committed(gear, null, color(cfg.messageFail())); // gear untouched, scroll spent
        }
        Map<String, Integer> remaining = new LinkedHashMap<>(current.enchants());
        remaining.remove(key);
        CombatState next = new CombatState(remaining, current.crystals(), current.setKey(),
                current.omni(), current.heroic(), current.added());
        combat.write(gear, next);
        lore.apply(gear, next); // re-render the gear's lore from the reduced state
        ItemStack book = carriers.mintBook(key, level); // the extracted enchant, as an enchant book
        return ScrollResult.committed(gear, book, color(cfg.messageSuccess().replace("{ENCHANT}", displayOf(key))));
    }

    /** Randomizer scroll: reroll a book's success chance to a random value in the configured range. */
    private ScrollResult applyRandomizer(ItemStack cursor, ItemStack book) {
        ScrollsConfig.Randomizer cfg = config.get().randomizer();
        if (book == null || book.getType() == Material.AIR) {
            return ScrollResult.unchanged("§cApply the randomizer onto an enchant book.");
        }
        if (book.getAmount() > 1) {
            return ScrollResult.unchanged("§cApply to a single book — split the stack first.");
        }
        int target = cfg.minPercent() + random.nextInt(cfg.maxPercent() - cfg.minPercent() + 1);
        CarrierResult rolled = carriers.rerollSuccess(book, target);
        if (!rolled.consumed()) {
            return ScrollResult.unchanged(color(cfg.messageNotBook())); // not a book — don't waste the scroll
        }
        consume(cursor);
        return ScrollResult.committed(book, null,
                color(cfg.messageSuccess().replace("{PERCENT}", Integer.toString(target))));
    }

    private String displayOf(String key) {
        String name = content.library().displayNameOf(key);
        return name != null ? name : key;
    }

    private static void consume(ItemStack stack) {
        stack.setAmount(stack.getAmount() - 1);
    }

    private static String color(String raw) {
        return ItemFactory.color(raw);
    }
}
