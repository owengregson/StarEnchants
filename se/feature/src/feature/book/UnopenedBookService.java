package feature.book;

import compile.load.ContentHolder;
import compile.load.EnchantDef;
import compile.load.UnopenedBookConfig;
import feature.carrier.CarrierService;
import item.codec.UnopenedBookCodec;
import item.mint.ItemFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * The unopened/randomized book cold path (§I) — mints a tier-scoped unopened book and opens one into a
 * concrete enchant book (random tier enchant, level, and base success). The roll is the only
 * non-determinism, injected as a {@link Random} for testability.
 */
public final class UnopenedBookService {

    private final UnopenedBookCodec codec;
    private final CarrierService carriers;
    private final ContentHolder content;
    private final Supplier<UnopenedBookConfig> config;
    private final Random random;
    private final item.lang.Messages messages;

    /** Default-messages form (tests/fixtures). */
    public UnopenedBookService(UnopenedBookCodec codec, CarrierService carriers, ContentHolder content,
                               Supplier<UnopenedBookConfig> config, Random random) {
        this(codec, carriers, content, config, random, item.lang.Messages.defaults());
    }

    public UnopenedBookService(UnopenedBookCodec codec, CarrierService carriers, ContentHolder content,
                               Supplier<UnopenedBookConfig> config, Random random, item.lang.Messages messages) {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.carriers = Objects.requireNonNull(carriers, "carriers");
        this.content = Objects.requireNonNull(content, "content");
        this.config = Objects.requireNonNull(config, "config");
        this.random = Objects.requireNonNull(random, "random");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    public boolean isUnopened(ItemStack stack) {
        return codec.isUnopened(stack);
    }

    /** Mint an unopened book scoped to {@code tier} ({@code {TIER}} substituted into the config likeness). */
    public ItemStack mint(String tier) {
        UnopenedBookConfig cfg = config.get();
        ItemStack stack = ItemFactory.buildItem(
                cfg.material(), Material.BOOK,
                cfg.name().replace("{TIER}", tier),
                renderLore(cfg.lore(), tier));
        codec.mark(stack, tier);
        return stack;
    }

    /**
     * Open the unopened {@code book}, minting the rolled concrete book. An empty tier yields nothing and
     * preserves the book; otherwise one is consumed and the rolled book produced.
     */
    public UnopenedResult open(ItemStack book) {
        String tier = codec.tierOf(book);
        if (tier == null) {
            return UnopenedResult.nothing(null);
        }
        java.util.Optional<Rolled> rolled = rollDetailed(tier);
        if (rolled.isEmpty()) {
            return UnopenedResult.nothing(messages.format("book.unopened.empty-tier"));
        }
        Rolled r = rolled.get();
        String message = messages.format("book.unopened.open",
                "ENCHANT", r.display(), "LEVEL", r.level(), "PERCENT", r.success());
        return UnopenedResult.opened(r.book(), message);
    }

    /** The same roll {@link #open} performs, exposed for the §J {@code /se give book ... random <tier>} form. */
    public java.util.Optional<ItemStack> roll(String tier) {
        return rollDetailed(tier).map(Rolled::book);
    }

    /** The shared tier&rarr;concrete-book roll behind both {@link #open} and {@link #roll}. */
    private java.util.Optional<Rolled> rollDetailed(String tier) {
        UnopenedBookConfig cfg = config.get();
        List<EnchantDef> pool = new ArrayList<>();
        for (EnchantDef def : content.library().catalog()) {
            if (tier.equalsIgnoreCase(def.tier())) {
                pool.add(def);
            }
        }
        if (pool.isEmpty()) {
            return java.util.Optional.empty();
        }
        EnchantDef chosen = pool.get(random.nextInt(pool.size()));
        int level = 1 + random.nextInt(Math.max(1, chosen.maxLevel()));
        int success = carriers.capBookSuccess( // randomised mint respects the global books.max-success ceiling
                cfg.minSuccess() + random.nextInt(cfg.maxSuccess() - cfg.minSuccess() + 1));
        return java.util.Optional.of(new Rolled(carriers.mintBook(chosen.key(), level, success),
                chosen.display(), level, success));
    }

    /** A rolled concrete book plus the details {@link #open}'s reveal message needs. */
    private record Rolled(ItemStack book, String display, int level, int success) {
    }

    private static List<String> renderLore(List<String> lore, String tier) {
        List<String> out = new ArrayList<>(lore.size());
        for (String line : lore) {
            out.add(line.replace("{TIER}", tier));
        }
        return out;
    }
}
