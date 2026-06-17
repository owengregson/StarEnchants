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
 * The unopened/randomized book cold path (docs/v3-directives.md §I) — MINTS a tier-scoped unopened book
 * and OPENS one into a concrete enchant book of a random enchant from that tier, at a random level and a
 * random success chance. The opened book is minted through {@link CarrierService#mintBook(String, int, int)}
 * so it carries an explicit base success.
 *
 * <p>The roll is the only non-determinism, injected as a {@link Random} for testability. Folia-correct: a
 * right-click interact fires on the player's own region thread, so reading their held item is in-thread.
 */
public final class UnopenedBookService {

    private final UnopenedBookCodec codec;
    private final CarrierService carriers;
    private final ContentHolder content;
    private final Supplier<UnopenedBookConfig> config;
    private final Random random;

    public UnopenedBookService(UnopenedBookCodec codec, CarrierService carriers, ContentHolder content,
                               Supplier<UnopenedBookConfig> config, Random random) {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.carriers = Objects.requireNonNull(carriers, "carriers");
        this.content = Objects.requireNonNull(content, "content");
        this.config = Objects.requireNonNull(config, "config");
        this.random = Objects.requireNonNull(random, "random");
    }

    /** Whether {@code stack} is an unopened book. */
    public boolean isUnopened(ItemStack stack) {
        return codec.isUnopened(stack);
    }

    /** Mint an unopened book scoped to {@code tier} (its likeness rendered from config with {@code {TIER}}). */
    public ItemStack mint(String tier) {
        UnopenedBookConfig cfg = config.get();
        ItemStack stack = ItemFactory.build(
                ItemFactory.material(cfg.material(), Material.BOOK),
                cfg.name().replace("{TIER}", tier),
                renderLore(cfg.lore(), tier));
        codec.mark(stack, tier);
        return stack;
    }

    /**
     * Open the unopened {@code book}: roll a random enchant from its tier, a random level, and a random
     * success chance, and mint the concrete enchant book. A tier with no enchants yields nothing (the
     * unopened book is preserved); otherwise one unopened book is consumed and the rolled book produced.
     */
    public UnopenedResult open(ItemStack book) {
        String tier = codec.tierOf(book);
        if (tier == null) {
            return UnopenedResult.nothing(null); // not an unopened book (defensive)
        }
        UnopenedBookConfig cfg = config.get();
        List<EnchantDef> pool = new ArrayList<>();
        for (EnchantDef def : content.library().catalog()) {
            if (tier.equalsIgnoreCase(def.tier())) {
                pool.add(def);
            }
        }
        if (pool.isEmpty()) {
            return UnopenedResult.nothing(color(cfg.messageEmptyTier()));
        }
        EnchantDef chosen = pool.get(random.nextInt(pool.size()));
        int level = 1 + random.nextInt(Math.max(1, chosen.maxLevel()));
        int success = cfg.minSuccess() + random.nextInt(cfg.maxSuccess() - cfg.minSuccess() + 1);
        ItemStack produced = carriers.mintBook(chosen.key(), level, success);
        String message = color(cfg.messageOpen()
                .replace("{ENCHANT}", chosen.display())
                .replace("{LEVEL}", Integer.toString(level))
                .replace("{PERCENT}", Integer.toString(success)));
        return UnopenedResult.opened(produced, message);
    }

    private static List<String> renderLore(List<String> lore, String tier) {
        List<String> out = new ArrayList<>(lore.size());
        for (String line : lore) {
            out.add(line.replace("{TIER}", tier));
        }
        return out;
    }

    private static String color(String raw) {
        return ItemFactory.color(raw);
    }
}
