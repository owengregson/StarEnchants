package tester.suite;

import compile.load.ContentHolder;
import compile.load.EnchantBookConfig;
import compile.load.Library;
import compile.load.LibraryLoader;
import engine.boot.ContentCompiler;
import feature.apply.ItemEnchanter;
import feature.carrier.CarrierResult;
import feature.carrier.CarrierService;
import item.codec.CarrierCodec;
import item.codec.CarrierData;
import item.codec.CombatCodec;
import item.codec.CombatState;
import item.codec.ItemKeys;
import item.render.LoreRenderer;
import item.render.LoreStyle;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import platform.item.ItemGroups;
import tester.harness.Harness;

/**
 * Carrier application economy — books, scrolls, success dust over real {@link ItemStack}s (ADR-0016; ADR-0019).
 * Seeded {@link Random} + success-100/success-0 books make the rolls deterministic.
 */
public final class CarrierSuite implements Harness.Scenario {

    private static final String ZAP = """
            display: "&eZap"
            trigger: ATTACK
            applies-to: [SWORD]
            levels:
              1: { chance: 100, effects: ["IGNITE:40:@Victim"] }
            """;

    private static final String[] KEYS = {
        "carrier.book.applies", "carrier.book.stackGuard", "carrier.book.destroyOnFail",
        "carrier.scroll.whiteScrollProtects", "carrier.dust.fixedBoostsBook", "carrier.dust.cappedAndIdempotent",
        "carrier.dust.boostedBookApplies", "carrier.dust.rejectsNonBook", "carrier.dust.gestureEligibility",
        "carrier.dust.randomInRange",
    };

    private final Plugin plugin;

    public CarrierSuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        for (String key : KEYS) {
            h.expect(key);
        }

        Path root;
        try {
            root = Files.createTempDirectory("se-carrier-suite");
            write(root, "enchants/zap.yml", ZAP);
        } catch (IOException e) {
            failAll(h, e.toString());
            return;
        }

        Library lib = LibraryLoader.load(root, ContentCompiler.production(), 0);
        if (lib.hasErrors()) {
            failAll(h, "carrier content has errors: " + lib.diagnostics());
            return;
        }
        ContentHolder holder = new ContentHolder(lib);
        ItemKeys keys = ItemKeys.of();
        CombatCodec combat = new CombatCodec(keys.combat());
        LoreRenderer lore = new LoreRenderer(LoreStyle.DEFAULT, k -> holder.library().displayNameOf(k));
        ItemEnchanter enchanter = new ItemEnchanter(combat, lore, holder, ItemGroups.standard());
        CarrierCodec carrierCodec = new CarrierCodec(keys.carrier(), keys.guarded());
        // Default likeness: books never destroy on fail.
        CarrierService carriers = new CarrierService(carrierCodec, enchanter, holder, new Random(1));
        // destroy-on-fail ON, for the shatter + white-scroll-protect cases.
        EnchantBookConfig destroyLikeness = new EnchantBookConfig(
                "ENCHANTED_BOOK", "{ENCHANT} &7Book", List.of(), List.of(), true);
        CarrierService destroyer = new CarrierService(
                carrierCodec, enchanter, holder, new Random(1), () -> destroyLikeness);

        h.guard("carrier.book.applies", () -> {
            ItemStack book = carriers.mintBook("enchants/zap", 1);
            ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
            carriers.applyTo(book, sword);
            if (book.getAmount() != 0) {
                throw new IllegalStateException("the book was not consumed; amount=" + book.getAmount());
            }
            CombatState state = combat.read(sword);
            if (state == null || !state.enchants().containsKey("enchants/zap")) {
                throw new IllegalStateException("the sword did not gain enchants/zap: " + state);
            }
        });

        h.guard("carrier.book.stackGuard", () -> {
            // One carrier must never affect a whole stack (dupe/mass-loss guard).
            ItemStack book = carriers.mintBook("enchants/zap", 1);
            ItemStack stacked = new ItemStack(Material.DIAMOND_SWORD, 2);
            CarrierResult result = carriers.applyTo(book, stacked);
            if (result.consumed()) {
                throw new IllegalStateException("a stacked target must not consume the carrier");
            }
            if (book.getAmount() != 1) {
                throw new IllegalStateException("the book was wrongly consumed on a stacked target");
            }
            CombatState onStack = combat.read(stacked); // EMPTY (non-null) for an untouched item
            if (onStack != null && onStack.enchants().containsKey("enchants/zap")) {
                throw new IllegalStateException("a stacked target must not be enchanted");
            }
        });

        h.guard("carrier.book.destroyOnFail", () -> {
            ItemStack book = destroyer.mintBook("enchants/zap", 1, 0); // forced-fail roll
            ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
            destroyer.applyTo(book, sword);
            if (sword.getAmount() != 0) {
                throw new IllegalStateException("a failed destroy-on-fail apply should shatter the sword");
            }
        });

        h.guard("carrier.scroll.whiteScrollProtects", () -> {
            ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
            carriers.applyTo(carriers.mintWhiteScroll(), sword);
            if (!carrierCodec.isGuarded(sword)) {
                throw new IllegalStateException("the white scroll did not mark the sword guarded");
            }
            destroyer.applyTo(destroyer.mintBook("enchants/zap", 1, 0), sword); // forced-fail destroy book
            if (sword.getAmount() != 1) {
                throw new IllegalStateException("the white scroll should have spared the sword from destruction");
            }
            if (carrierCodec.isGuarded(sword)) {
                throw new IllegalStateException("the guard should be consumed by the failed apply");
            }
        });

        h.guard("carrier.dust.fixedBoostsBook", () -> {
            ItemStack book = carriers.mintBook("enchants/zap", 1, 50);
            ItemStack dust = carriers.mintDust(15); // fixed +15%
            CarrierResult result = carriers.applyTo(dust, book);
            if (!result.consumed() || dust.getAmount() != 0) {
                throw new IllegalStateException("the fixed dust was not consumed onto the book: " + result);
            }
            CarrierData bookData = carrierCodec.read(book);
            if (bookData == null || bookData.successBonus() != 15) {
                throw new IllegalStateException("the book did not record a +15 success bonus: " + bookData);
            }
        });

        h.guard("carrier.dust.cappedAndIdempotent", () -> {
            ItemStack book = carriers.mintBook("enchants/zap", 1, 50);
            carriers.applyTo(carriers.mintDust(100), book); // +100 caps at +50 so effective stays ≤ 100%
            CarrierData bookData = carrierCodec.read(book);
            if (bookData == null || bookData.successBonus() != 50) {
                throw new IllegalStateException("dust bonus must cap so effective ≤ 100% (base 50 → +50): "
                        + bookData);
            }
            ItemStack extra = carriers.mintDust(15);
            CarrierResult result = carriers.applyTo(extra, book); // maxed book: no-op
            if (result.consumed() || extra.getAmount() != 1) {
                throw new IllegalStateException("a dust on a maxed book must be a no-op: " + result);
            }
        });

        h.guard("carrier.dust.boostedBookApplies", () -> {
            ItemStack book = carriers.mintBook("enchants/zap", 1, 0); // base 0%, always fails
            carriers.applyTo(carriers.mintDust(100), book); // +100 → effective 100%
            ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
            carriers.applyTo(book, sword); // dust-boosted chance must flow into the book→gear roll
            CombatState state = combat.read(sword);
            if (state == null || !state.enchants().containsKey("enchants/zap")) {
                throw new IllegalStateException("a 0%+dust(100%) book should always apply its enchant: " + state);
            }
        });

        h.guard("carrier.dust.rejectsNonBook", () -> {
            ItemStack dust = carriers.mintDust(15);
            ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
            CarrierResult result = carriers.applyTo(dust, sword);
            if (result.consumed() || dust.getAmount() != 1) {
                throw new IllegalStateException("dust on non-book gear must be a no-op: " + result);
            }
        });

        // Interaction-layer gate (ADR-0019): the dust gesture is claimed only onto an enchant book, so a
        // dust onto a scroll/another dust falls through to the vanilla click (no dead cancelled no-op).
        h.guard("carrier.dust.gestureEligibility", () -> {
            ItemStack dust = carriers.mintDust(); // random dust, eligible (max-bonus > 0)
            ItemStack book = carriers.mintBook("enchants/zap", 1, 50);
            ItemStack whiteScroll = carriers.mintWhiteScroll();
            ItemStack otherDust = carriers.mintDust(15);
            if (!carriers.canCombineDust(dust, book)) {
                throw new IllegalStateException("a dust should be allowed to combine onto an enchant book");
            }
            if (carriers.canCombineDust(dust, whiteScroll)) {
                throw new IllegalStateException("a dust must NOT claim the gesture onto a white scroll (dead click)");
            }
            if (carriers.canCombineDust(dust, otherDust)) {
                throw new IllegalStateException("a dust must NOT claim the gesture onto another dust");
            }
            if (carriers.canCombineDust(book, book)) {
                throw new IllegalStateException("a book is not a dust — no carrier-onto-carrier claim");
            }
        });

        // Random dust (no fixed percent) rolls its bonus in the configured [min, max] = [10, 25] each combine.
        h.guard("carrier.dust.randomInRange", () -> {
            for (int i = 0; i < 8; i++) {
                ItemStack book = carriers.mintBook("enchants/zap", 1, 0); // base 0 so recorded bonus == roll
                carriers.applyTo(carriers.mintDust(), book);
                CarrierData data = carrierCodec.read(book);
                int bonus = data == null ? -1 : data.successBonus();
                if (bonus < 10 || bonus > 25) {
                    throw new IllegalStateException("random dust bonus out of [10,25]: " + bonus);
                }
            }
        });
    }

    private static void failAll(Harness h, String message) {
        for (String key : KEYS) {
            h.fail(key, message);
        }
    }

    private static void write(Path root, String relative, String yaml) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, yaml, StandardCharsets.UTF_8);
    }
}
