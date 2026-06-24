package feature.carrier;

import compile.load.ContentHolder;
import compile.load.EnchantDef;
import feature.apply.ApplyResult;
import feature.apply.ItemEnchanter;
import item.mint.ItemFactory;
import item.codec.CarrierCodec;
import item.codec.CarrierData;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * The book/dust/white-scroll application economy (ADR-0016; ADR-0019) — the cold path that MINTS these
 * carrier items and APPLIES them to gear. Every carrier is built from a top-level {@code items/*.yml}
 * likeness (the general enchant book, the success dust, the white scroll), read LIVE so a {@code /se reload}
 * re-tunes it. Carrier identity lives in PDC ({@link CarrierCodec}) — never decoded on the combat hot path.
 *
 * <p>A book rolls its success chance and, on success, applies its granted enchant through the shared
 * {@link ItemEnchanter}; on failure it destroys the gear (when the enchant-book likeness sets
 * {@code destroy-on-fail}) unless a white-scroll guard spared it. The WHITE SCROLL stamps that guard. The
 * SUCCESS DUST is the one carrier→carrier interaction: dragging it onto an enchant book raises the book's
 * stored bonus (clamped so effective success never exceeds 100%); a normal dust rolls a random bonus in the
 * configured {@code [min, max]} range, a fixed-percent dust (minted via {@code /se dust <percent>}) confers
 * exactly its baked amount. The apply/dust rolls are the only non-determinism, injected as a {@link Random}.
 */
public final class CarrierService {

    /** Stable PDC item-key for the top-level success dust (items/dust.yml). */
    public static final String DUST_KEY = "dust";
    /** Stable PDC item-key for the top-level white scroll (items/white-scroll.yml) — the enchant-protect guard. */
    public static final String WHITE_SCROLL_KEY = "white-scroll";
    /** Stable PDC item-key every minted enchant book carries. */
    public static final String BOOK_KEY = "book";

    private final CarrierCodec codec;
    private final ItemEnchanter enchanter;
    private final ContentHolder content;
    private final Random random;
    private final java.util.function.Supplier<compile.load.EnchantBookConfig> bookConfig; // §I general book likeness
    private final java.util.function.Supplier<compile.load.DustConfig> dustConfig; // §I top-level success dust
    private final java.util.function.Supplier<compile.load.WhiteScrollConfig> whiteScrollConfig; // §I white scroll

    /** Test/fixture form: every top-level item at its built-in likeness. */
    public CarrierService(CarrierCodec codec, ItemEnchanter enchanter, ContentHolder content, Random random) {
        this(codec, enchanter, content, random, compile.load.EnchantBookConfig::defaults,
                compile.load.DustConfig::defaults, compile.load.WhiteScrollConfig::defaults);
    }

    /** Book-config form: the general enchant-book likeness supplied; dust/white-scroll at their defaults. */
    public CarrierService(CarrierCodec codec, ItemEnchanter enchanter, ContentHolder content, Random random,
                          java.util.function.Supplier<compile.load.EnchantBookConfig> bookConfig) {
        this(codec, enchanter, content, random, bookConfig,
                compile.load.DustConfig::defaults, compile.load.WhiteScrollConfig::defaults);
    }

    /**
     * Canonical form (the composition root): the live likeness suppliers for the general enchant book, the
     * top-level success dust, and the white scroll — each re-read on use so a {@code /se reload} re-tunes them.
     */
    public CarrierService(CarrierCodec codec, ItemEnchanter enchanter, ContentHolder content, Random random,
                          java.util.function.Supplier<compile.load.EnchantBookConfig> bookConfig,
                          java.util.function.Supplier<compile.load.DustConfig> dustConfig,
                          java.util.function.Supplier<compile.load.WhiteScrollConfig> whiteScrollConfig) {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.enchanter = Objects.requireNonNull(enchanter, "enchanter");
        this.content = Objects.requireNonNull(content, "content");
        this.random = Objects.requireNonNull(random, "random");
        this.bookConfig = Objects.requireNonNull(bookConfig, "bookConfig");
        this.dustConfig = Objects.requireNonNull(dustConfig, "dustConfig");
        this.whiteScrollConfig = Objects.requireNonNull(whiteScrollConfig, "whiteScrollConfig");
    }

    /**
     * Mint a RANDOM-bonus SUCCESS DUST from the {@code items/dust.yml} likeness (§I; ADR-0019) — when combined
     * onto an enchant book it rolls a bonus in the configured {@code [min, max]} range. {@code {BONUS}} renders
     * as that range. Used by {@code /se dust} (no percent), drops, etc.
     */
    public ItemStack mintDust() {
        return buildDust(0); // 0 baked → roll [min, max] from config at apply time
    }

    /**
     * Mint a FIXED-percent SUCCESS DUST that confers exactly {@code fixedPercent}, bypassing the random roll
     * (§I) — used by {@code /se dust <percent>}. The percent is baked onto the item (so it is reload-stable).
     */
    public ItemStack mintDust(int fixedPercent) {
        return buildDust(clampPercent(fixedPercent));
    }

    /** Build a dust item; {@code fixedBonus > 0} bakes a fixed conferred bonus, {@code 0} = roll at apply. */
    private ItemStack buildDust(int fixedBonus) {
        compile.load.DustConfig cfg = dustConfig.get();
        String label = fixedBonus > 0 ? Integer.toString(fixedBonus) : cfg.bonusLabel();
        String min = Integer.toString(cfg.minBonus());
        String max = Integer.toString(cfg.maxBonus());
        List<String> lore = new ArrayList<>();
        for (String line : cfg.lore()) {
            lore.add(subDust(line, label, min, max));
        }
        ItemStack stack = ItemFactory.build(ItemFactory.material(cfg.material(), Material.GLOWSTONE_DUST),
                subDust(cfg.name(), label, min, max), lore);
        // The dust carries its FIXED bonus in the successBonus field (0 = random — roll from config on apply).
        codec.write(stack, new CarrierData(DUST_KEY, "", 0, fixedBonus));
        return stack;
    }

    private static String subDust(String s, String bonus, String min, String max) {
        return s.replace("{BONUS}", bonus).replace("{MIN}", min).replace("{MAX}", max);
    }

    /**
     * Mint a WHITE SCROLL from the {@code items/white-scroll.yml} likeness (§I) — drag onto gear to protect it
     * from enchant destruction once. Grants no content; it stamps the guard marker on apply.
     */
    public ItemStack mintWhiteScroll() {
        compile.load.WhiteScrollConfig cfg = whiteScrollConfig.get();
        ItemStack stack = ItemFactory.build(material(cfg.material()), cfg.name(), cfg.lore());
        codec.write(stack, new CarrierData(WHITE_SCROLL_KEY, "", 0));
        return stack;
    }

    /**
     * Apply the carrier {@code carrier} to {@code target}, mutating both (the grant lands on the target, one
     * carrier use is consumed) and returning the outcome. A no-op (not a carrier / ineligible target /
     * unsupported kind) leaves both stacks untouched.
     */
    public CarrierResult applyTo(ItemStack carrier, ItemStack target) {
        CarrierData data = codec.read(carrier);
        if (data == null) {
            return CarrierResult.noop("§cThat is not an enchant carrier.");
        }
        if (target == null || target.getType() == Material.AIR) {
            return CarrierResult.noop("§cApply the carrier onto an item.");
        }
        // ONE carrier affects ONE item — never a whole stack (a single book must not enchant or destroy
        // 64 swords at once: that would be a dupe / mass-loss exploit). Split the stack first.
        if (target.getAmount() > 1) {
            return CarrierResult.noop("§cApply the carrier to a single item — split the stack first.");
        }

        // Success dust: the one carrier-onto-carrier interaction (ADR-0019). A fixed dust confers its baked
        // bonus; a random dust rolls [min, max] from the live config.
        if (DUST_KEY.equals(data.itemKey())) {
            compile.load.DustConfig cfg = dustConfig.get();
            int bonus = data.successBonus() > 0 ? data.successBonus() : rolledDustBonus(cfg);
            return applyDustBonus(carrier, target, bonus, cfg.sound(), cfg.particles());
        }
        // White scroll: stamp the one-shot protect guard.
        if (WHITE_SCROLL_KEY.equals(data.itemKey())) {
            return applyProtect(carrier, target);
        }
        if (!data.grants()) {
            return CarrierResult.noop("§cThis carrier grants nothing applicable.");
        }

        String grant = data.grantKey();
        boolean crystal = grant.startsWith("crystals/");
        ApplyResult check = crystal
                ? enchanter.checkCrystalEntry(target, java.util.List.of(grant)) // §E crystal-slot gate
                : enchanter.checkApplicable(target, grant, data.grantLevel()); // §G/§H gate before consuming
        if (!check.ok()) {
            return CarrierResult.noop(check.message()); // ineligible target → don't waste the carrier
        }

        int base = baseSuccessOf(data); // an unopened-book output / randomizer reroll overrides the default 100
        int successChance = effectiveSuccess(base, data.successBonus()); // dust-accumulated bonus (ADR-0019)
        boolean destroyOnFail = bookConfig.get().destroyOnFail(); // §I enchant-book likeness, read live
        consume(carrier); // a use is spent whether the roll succeeds or fails
        if (random.nextInt(100) < successChance) {
            ApplyResult applied = crystal
                    ? enchanter.applyCrystal(target, grant)
                    : enchanter.applyEnchant(target, grant, data.grantLevel());
            return CarrierResult.consumed(applied.message());
        }
        if (codec.isGuarded(target)) {
            codec.setGuarded(target, false);
            return CarrierResult.consumed("§eThe enchant failed — but your protection saved the item.");
        }
        if (destroyOnFail) {
            target.setAmount(0);
            return CarrierResult.consumed("§cThe enchant failed — the item shattered.");
        }
        return CarrierResult.consumed("§eThe enchant failed — the item is unharmed.");
    }

    /** A random success-chance bonus in the dust's configured {@code [min, max]} range. */
    private int rolledDustBonus(compile.load.DustConfig cfg) {
        int span = cfg.maxBonus() - cfg.minBonus();
        return span <= 0 ? cfg.minBonus() : cfg.minBonus() + random.nextInt(span + 1);
    }

    /**
     * Mint an enchant BOOK for an arbitrary enchant from the GENERAL {@code items/enchant-book.yml} likeness —
     * the enchant's display name fills {@code {ENCHANT}}, the level fills {@code {LEVEL}}. It applies with the
     * default success (always succeeds, never destroys). Used by {@code /se give book}, combine, etc.
     */
    public ItemStack mintBook(String enchantKey, int level) {
        ItemStack stack = bookLikeness(enchantKey, level, -1);
        codec.write(stack, new CarrierData(BOOK_KEY, enchantKey, level));
        return stack;
    }

    /**
     * Mint an enchant BOOK that applies at an explicit {@code successChance} (§I) — used by the unopened/
     * randomized book. Like {@link #mintBook(String, int)} but the likeness's {@code success-lore} (with
     * {@code {SUCCESS}}) is appended and the book carries a base-success override.
     */
    public ItemStack mintBook(String enchantKey, int level, int successChance) {
        int chance = clampPercent(successChance);
        ItemStack stack = bookLikeness(enchantKey, level, chance);
        codec.write(stack, new CarrierData(BOOK_KEY, enchantKey, level, 0, chance));
        return stack;
    }

    /** Build the visible book item from the general likeness (success {@code < 0} omits the success line). */
    private ItemStack bookLikeness(String enchantKey, int level, int successChance) {
        compile.load.EnchantBookConfig cfg = bookConfig.get();
        String enchant = displayOf(enchantKey);
        String display = cfg.name()
                .replace("{ENCHANT}", enchant)
                .replace("{LEVEL}", Integer.toString(level));
        List<String> lore = bookLore(cfg, enchant, level, successChance);
        return ItemFactory.build(ItemFactory.material(cfg.material(), Material.ENCHANTED_BOOK), display, lore);
    }

    /** The raw (untranslated) book lore from the likeness; {@code success < 0} omits the success-lore lines. */
    private static List<String> bookLore(compile.load.EnchantBookConfig cfg, String enchant, int level, int success) {
        List<String> lore = new ArrayList<>();
        for (String line : cfg.lore()) {
            lore.add(line.replace("{ENCHANT}", enchant).replace("{LEVEL}", Integer.toString(level)));
        }
        if (success >= 0) {
            for (String line : cfg.successLore()) {
                lore.add(line.replace("{LEVEL}", Integer.toString(level))
                        .replace("{SUCCESS}", Integer.toString(success)));
            }
        }
        return lore;
    }

    /** The enchant a book grants and at what level, or empty when {@code stack} is not an enchant book. */
    public java.util.Optional<BookContents> bookContents(ItemStack stack) {
        CarrierData data = codec.read(stack);
        if (data == null || !isEnchantBook(data)) {
            return java.util.Optional.empty(); // not a carrier, no grant, or the grant is not a catalog enchant
        }
        return java.util.Optional.of(new BookContents(data.grantKey(), data.grantLevel()));
    }

    /**
     * Combine two enchant books into one of the next level — the Alchemist's upgrade (docs/v3-directives.md
     * §K). Both must grant the SAME enchant at the SAME level, below that enchant's max. Returns the minted
     * level+1 book, or empty when the pair is not combinable (the menu then leaves both inputs untouched). A
     * mint of an existing item type — NOT a new economy data model (cf. ADR-0019).
     */
    public java.util.Optional<ItemStack> combineBooks(ItemStack a, ItemStack b) {
        java.util.Optional<BookContents> ca = bookContents(a);
        java.util.Optional<BookContents> cb = bookContents(b);
        if (ca.isEmpty() || cb.isEmpty()) {
            return java.util.Optional.empty();
        }
        BookContents x = ca.get();
        BookContents y = cb.get();
        EnchantDef def = enchantDef(x.enchantKey());
        if (def == null || !combinable(x.enchantKey(), x.level(), y.enchantKey(), y.level(), def.maxLevel())) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(mintBook(x.enchantKey(), x.level() + 1));
    }

    /** EXP-level refund for salvaging a book of {@code level} (the Tinkerer): the book's level, at least one. */
    public java.util.Optional<Integer> salvageLevels(ItemStack book) {
        return bookContents(book).map(c -> salvageLevels(c.level()));
    }

    /** Whether two books combine: same enchant, same level, below that enchant's max. Pure. */
    public static boolean combinable(String keyA, int levelA, String keyB, int levelB, int maxLevel) {
        return keyA.equals(keyB) && levelA == levelB && levelA >= 1 && levelA < maxLevel;
    }

    /** EXP levels refunded for salvaging a book of {@code bookLevel} — its level, at least one. Pure. */
    public static int salvageLevels(int bookLevel) {
        return Math.max(1, bookLevel);
    }

    /** The catalog {@link EnchantDef} for {@code key}, or {@code null} if no such enchant. */
    private EnchantDef enchantDef(String key) {
        for (EnchantDef def : content.library().catalog()) {
            if (def.key().equals(key)) {
                return def;
            }
        }
        return null;
    }

    /** What an enchant book grants: the enchant key and the level it applies. */
    public record BookContents(String enchantKey, int level) {
    }

    /**
     * Reroll the success chance of an enchant {@code book} to {@code targetPercent} (§I randomizer scroll) —
     * sets an explicit base-success override, clears any accumulated dust bonus, and re-renders the book's lore
     * from state. A no-op (not an enchant book) leaves the stack untouched.
     */
    public CarrierResult rerollSuccess(ItemStack book, int targetPercent) {
        CarrierData data = codec.read(book);
        if (data == null || !isEnchantBook(data)) {
            return CarrierResult.noop("§cThe randomizer only works on an enchant book.");
        }
        CarrierData updated = data.withBaseSuccess(clampPercent(targetPercent));
        codec.write(book, updated);
        reRenderBookLore(book, updated);
        return CarrierResult.consumed("§aThe book's success chance is now §f" + clampPercent(targetPercent) + "%§a.");
    }

    /**
     * Whether {@code cursor} is a dust that would LEGALLY combine onto {@code target} — a success dust onto an
     * enchant book. The interaction layer claims the otherwise-forbidden carrier-onto-carrier gesture only when
     * this holds (ADR-0019), so a dust dropped onto a scroll / another dust / non-content carrier falls through
     * to the vanilla click instead of becoming a dead, cancelled no-op.
     */
    public boolean canCombineDust(ItemStack cursor, ItemStack target) {
        CarrierData cursorData = codec.read(cursor);
        if (cursorData == null || !DUST_KEY.equals(cursorData.itemKey())) {
            return false;
        }
        // A fixed dust's potential is its baked bonus; a random dust's is its configured max.
        int potential = cursorData.successBonus() > 0 ? cursorData.successBonus() : dustConfig.get().maxBonus();
        if (potential <= 0) {
            return false;
        }
        CarrierData targetData = codec.read(target);
        return targetData != null && isEnchantBook(targetData);
    }

    /**
     * Raise {@code book}'s stored success bonus by {@code bonus}, clamped so its effective success can never
     * exceed 100%, re-render its lore from state, and consume the {@code dust}. A no-op (target not an enchant
     * book, no bonus, or the book already at 100%) leaves both stacks untouched.
     */
    private CarrierResult applyDustBonus(ItemStack dust, ItemStack book, int bonus, String sound,
                                         java.util.List<String> particles) {
        CarrierData bookData = codec.read(book);
        if (bookData == null || !isEnchantBook(bookData)) {
            return CarrierResult.noop("§cDust can only boost an enchant book.");
        }
        int base = baseSuccessOf(bookData);
        if (effectiveSuccess(base, bookData.successBonus()) >= 100) {
            return CarrierResult.noop("§7That book is already at 100% success.");
        }
        if (bonus <= 0) {
            return CarrierResult.noop("§cThis dust confers no success bonus.");
        }
        int newBonus = Math.max(0, Math.min(bookData.successBonus() + bonus, 100 - base)); // base + bonus ≤ 100
        CarrierData updated = bookData.withSuccessBonus(newBonus);
        codec.write(book, updated);
        reRenderBookLore(book, updated);
        consume(dust);
        return CarrierResult.consumed("§aThe book's success chance is now §f"
                + effectiveSuccess(base, newBonus) + "%§a.", sound, particles);
    }

    /** Stamp the one-shot guard marker on {@code target} (white scroll), consuming the carrier. */
    private CarrierResult applyProtect(ItemStack carrier, ItemStack target) {
        if (codec.isGuarded(target)) {
            return CarrierResult.noop("§7That item is already protected.");
        }
        codec.setGuarded(target, true);
        consume(carrier);
        return CarrierResult.consumed("§aProtected — a failed enchant will spare this item once.");
    }

    /**
     * Re-render an enchant book's lore from the general likeness + the book's current state — so a dust combine
     * or a randomizer reroll visibly updates the shown success chance (lore is rendered from state, never parsed
     * back). Always shows the success line (the book now has a meaningful effective success).
     */
    @SuppressWarnings("deprecation") // setLore(List): the floor-stable item-meta path
    private void reRenderBookLore(ItemStack book, CarrierData data) {
        ItemMeta meta = book.getItemMeta();
        if (meta == null) {
            return;
        }
        int effective = effectiveSuccess(baseSuccessOf(data), data.successBonus());
        List<String> raw = bookLore(bookConfig.get(), displayOf(data.grantKey()), data.grantLevel(), effective);
        List<String> lore = new ArrayList<>(raw.size());
        for (String line : raw) {
            lore.add(color(line));
        }
        meta.setLore(lore.isEmpty() ? null : lore);
        book.setItemMeta(meta);
    }

    /** Whether {@code data} is an enchant book — it grants a key that names a catalog enchant. */
    private boolean isEnchantBook(CarrierData data) {
        return data.grants() && enchantDef(data.grantKey()) != null;
    }

    /** The display name for an enchant key, or the key itself if no content defines it. */
    private String displayOf(String enchantKey) {
        String name = content.library().displayNameOf(enchantKey);
        return name != null ? name : enchantKey;
    }

    /** A base success chance plus an accumulated bonus, clamped to {@code [0, 100]}. */
    private static int effectiveSuccess(int base, int bonus) {
        return Math.max(0, Math.min(100, base + Math.max(0, bonus)));
    }

    /**
     * The base success chance for a book: its explicit {@link CarrierData#baseSuccess()} override (§I — an
     * unopened-book output or a randomizer reroll) when present, else {@code 100} (a plain {@code /se book}
     * always succeeds). The bonus is added on top by callers.
     */
    private static int baseSuccessOf(CarrierData data) {
        return data.hasBaseSuccess() ? data.baseSuccess() : 100;
    }

    private static String color(String raw) {
        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    private static void consume(ItemStack carrier) {
        carrier.setAmount(carrier.getAmount() - 1);
    }

    private static int clampPercent(int pct) {
        return Math.max(0, Math.min(100, pct));
    }

    private static Material material(String token) {
        return ItemFactory.material(token, Material.PAPER); // cross-version resolve lives in one place
    }
}
