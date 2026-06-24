package tester.suite;

import compile.load.ContentHolder;
import compile.load.ItemDef;
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
import java.util.Random;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import platform.item.ItemGroups;
import tester.harness.Harness;

/**
 * Live checks for the carrier application economy (ADR-0016) — the things a unit test cannot prove
 * because they mutate real {@link ItemStack}s through the server's item factory: a minted book applies
 * its enchant to gear and is consumed; a destroy-on-fail book shatters the gear when its roll fails; and
 * a protect scroll spares the gear from one such failure. Runs on the global thread over a small temp
 * content tree (its own enchant + carrier defs), with deterministic apply rolls (success-100 vs
 * success-0), so the outcomes are exact.
 */
public final class CarrierSuite implements Harness.Scenario {

    private static final String ZAP = """
            display: "&eZap"
            trigger: ATTACK
            applies-to: [SWORD]
            levels:
              1: { chance: 100, effects: ["IGNITE:40:@Victim"] }
            """;
    private static final String ZAP_BOOK = """
            display: "&eZap Book"
            kind: book
            grants: { enchant: enchants/zap, level: 1 }
            apply: { success-chance: 0, destroy-on-fail: true }
            """;
    private static final String GUARD_SCROLL = """
            display: "&fGuard Scroll"
            kind: scroll
            grants: { role: PROTECT }
            """;
    private static final String BOOST_BOOK = """
            display: "&eBoost Book"
            kind: book
            grants: { enchant: enchants/zap, level: 1 }
            apply: { success-chance: 50 }
            """;
    private static final String WEAK_BOOK = """
            display: "&eWeak Book"
            kind: book
            grants: { enchant: enchants/zap, level: 1 }
            apply: { success-chance: 0 }
            """;
    private static final String DUST_15 = """
            display: "&aSuccess Dust"
            kind: dust
            grants: { success-bonus: 15 }
            """;
    private static final String DUST_100 = """
            display: "&bMega Dust"
            kind: dust
            grants: { success-bonus: 100 }
            """;

    private static final String[] KEYS = {
        "carrier.book.applies", "carrier.book.stackGuard", "carrier.book.destroyOnFail",
        "carrier.scroll.protects", "carrier.dust.boostsBook", "carrier.dust.cappedAndIdempotent",
        "carrier.dust.boostedBookApplies", "carrier.dust.rejectsNonBook", "carrier.dust.gestureEligibility",
        "carrier.topLevel.dustBoostsBook", "carrier.topLevel.whiteScrollProtects",
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
            write(root, "items/book/zapbook.yml", ZAP_BOOK);
            write(root, "items/scroll/guard.yml", GUARD_SCROLL);
            write(root, "items/book/boostbook.yml", BOOST_BOOK);
            write(root, "items/book/weakbook.yml", WEAK_BOOK);
            write(root, "items/dust/dust15.yml", DUST_15);
            write(root, "items/dust/dust100.yml", DUST_100);
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
        ItemKeys keys = ItemKeys.of(plugin);
        CombatCodec combat = new CombatCodec(keys.combat());
        LoreRenderer lore = new LoreRenderer(LoreStyle.DEFAULT, k -> holder.library().displayNameOf(k));
        ItemEnchanter enchanter = new ItemEnchanter(combat, lore, holder, ItemGroups.standard());
        CarrierCodec carrierCodec = new CarrierCodec(keys.carrier(), keys.guarded());
        CarrierService carriers = new CarrierService(carrierCodec, enchanter, holder, new Random(1));

        h.guard("carrier.book.applies", () -> {
            ItemStack book = carriers.mintBook("enchants/zap", 1); // ad-hoc book: default success-100
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
            ItemStack book = carriers.mint(itemDef(lib, "items/book/zapbook")); // success-0 + destroy
            ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
            carriers.applyTo(book, sword);
            if (sword.getAmount() != 0) {
                throw new IllegalStateException("a failed destroy-on-fail apply should shatter the sword");
            }
        });

        h.guard("carrier.scroll.protects", () -> {
            ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
            carriers.applyTo(carriers.mint(itemDef(lib, "items/scroll/guard")), sword); // guard it
            if (!carrierCodec.isGuarded(sword)) {
                throw new IllegalStateException("the protect scroll did not mark the sword guarded");
            }
            carriers.applyTo(carriers.mint(itemDef(lib, "items/book/zapbook")), sword); // failing destroy book
            if (sword.getAmount() != 1) {
                throw new IllegalStateException("the guard should have spared the sword from destruction");
            }
            if (carrierCodec.isGuarded(sword)) {
                throw new IllegalStateException("the guard should be consumed by the failed apply");
            }
        });

        h.guard("carrier.dust.boostsBook", () -> {
            ItemStack book = carriers.mint(itemDef(lib, "items/book/boostbook")); // base 50%
            ItemStack dust = carriers.mint(itemDef(lib, "items/dust/dust15"));     // +15%
            CarrierResult result = carriers.applyTo(dust, book);
            if (!result.consumed() || dust.getAmount() != 0) {
                throw new IllegalStateException("the dust was not consumed onto the book: " + result);
            }
            CarrierData bookData = carrierCodec.read(book);
            if (bookData == null || bookData.successBonus() != 15) {
                throw new IllegalStateException("the book did not record a +15 success bonus: " + bookData);
            }
        });

        h.guard("carrier.dust.cappedAndIdempotent", () -> {
            ItemStack book = carriers.mint(itemDef(lib, "items/book/boostbook")); // base 50%
            carriers.applyTo(carriers.mint(itemDef(lib, "items/dust/dust100")), book); // +100 → caps at +50
            CarrierData bookData = carrierCodec.read(book);
            if (bookData == null || bookData.successBonus() != 50) {
                throw new IllegalStateException("dust bonus must cap so effective ≤ 100% (base 50 → +50): "
                        + bookData);
            }
            ItemStack extra = carriers.mint(itemDef(lib, "items/dust/dust15"));
            CarrierResult result = carriers.applyTo(extra, book); // already 100% → no-op
            if (result.consumed() || extra.getAmount() != 1) {
                throw new IllegalStateException("a dust on a maxed book must be a no-op: " + result);
            }
        });

        h.guard("carrier.dust.boostedBookApplies", () -> {
            ItemStack book = carriers.mint(itemDef(lib, "items/book/weakbook")); // base 0% → always fails
            carriers.applyTo(carriers.mint(itemDef(lib, "items/dust/dust100")), book); // +100 → effective 100%
            ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
            carriers.applyTo(book, sword); // the dust-boosted chance flows into the book→gear roll
            CombatState state = combat.read(sword);
            if (state == null || !state.enchants().containsKey("enchants/zap")) {
                throw new IllegalStateException("a 0%+dust(100%) book should always apply its enchant: " + state);
            }
        });

        h.guard("carrier.dust.rejectsNonBook", () -> {
            ItemStack dust = carriers.mint(itemDef(lib, "items/dust/dust15"));
            ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
            CarrierResult result = carriers.applyTo(dust, sword); // dust onto plain gear, not a book
            if (result.consumed() || dust.getAmount() != 1) {
                throw new IllegalStateException("dust on non-book gear must be a no-op: " + result);
            }
        });

        // The top-level items/dust.yml dust (sentinel-keyed, bonus from live config) boosts a content book
        // exactly like the operator-authored content dust — proving the relocation off the ItemDef path.
        h.guard("carrier.topLevel.dustBoostsBook", () -> {
            ItemStack book = carriers.mint(itemDef(lib, "items/book/boostbook")); // base 50%
            ItemStack dust = carriers.mintDust(); // default likeness: +15%
            CarrierResult result = carriers.applyTo(dust, book);
            if (!result.consumed() || dust.getAmount() != 0) {
                throw new IllegalStateException("the top-level dust was not consumed onto the book: " + result);
            }
            CarrierData bookData = carrierCodec.read(book);
            if (bookData == null || bookData.successBonus() != 15) {
                throw new IllegalStateException("the book did not record the +15 default dust bonus: " + bookData);
            }
            if (!carriers.canCombineDust(dust, book)) {
                // (dust was consumed above; re-mint for the gesture-eligibility check)
                ItemStack fresh = carriers.mintDust();
                if (!carriers.canCombineDust(fresh, carriers.mint(itemDef(lib, "items/book/weakbook")))) {
                    throw new IllegalStateException("a top-level dust must claim the gesture onto a content book");
                }
            }
        });

        // The top-level items/white-scroll.yml scroll (sentinel-keyed) stamps the guard and spares gear from
        // one failed destroy — the protect mechanic relocated off the ItemDef path.
        h.guard("carrier.topLevel.whiteScrollProtects", () -> {
            ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
            carriers.applyTo(carriers.mintWhiteScroll(), sword); // guard it
            if (!carrierCodec.isGuarded(sword)) {
                throw new IllegalStateException("the white scroll did not mark the sword guarded");
            }
            carriers.applyTo(carriers.mint(itemDef(lib, "items/book/zapbook")), sword); // failing destroy book
            if (sword.getAmount() != 1) {
                throw new IllegalStateException("the white scroll should have spared the sword from destruction");
            }
            if (carrierCodec.isGuarded(sword)) {
                throw new IllegalStateException("the guard should be consumed by the failed apply");
            }
        });

        // The interaction-layer gate (ADR-0019): the dust gesture is claimed ONLY onto a content book, so
        // a dust onto a scroll/another dust falls through to the vanilla click (no dead, cancelled no-op).
        h.guard("carrier.dust.gestureEligibility", () -> {
            ItemStack dust = carriers.mint(itemDef(lib, "items/dust/dust15"));
            ItemStack book = carriers.mint(itemDef(lib, "items/book/boostbook"));
            ItemStack scroll = carriers.mint(itemDef(lib, "items/scroll/guard"));
            ItemStack otherDust = carriers.mint(itemDef(lib, "items/dust/dust100"));
            if (!carriers.canCombineDust(dust, book)) {
                throw new IllegalStateException("a dust should be allowed to combine onto a content book");
            }
            if (carriers.canCombineDust(dust, scroll)) {
                throw new IllegalStateException("a dust must NOT claim the gesture onto a scroll (dead click)");
            }
            if (carriers.canCombineDust(dust, otherDust)) {
                throw new IllegalStateException("a dust must NOT claim the gesture onto another dust");
            }
            if (carriers.canCombineDust(book, book)) {
                throw new IllegalStateException("a book is not a dust — no carrier-onto-carrier claim");
            }
        });
    }

    private static ItemDef itemDef(Library lib, String key) {
        return lib.items().stream().filter(d -> d.key().equals(key)).findFirst()
                .orElseThrow(() -> new IllegalStateException("missing item def " + key));
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
