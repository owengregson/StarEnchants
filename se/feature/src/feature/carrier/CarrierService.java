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
 * The book/dust/white-scroll application economy (ADR-0016; ADR-0019) — the cold path that mints these
 * carriers and applies them to gear. Each is built from a top-level {@code items/*.yml} likeness read LIVE
 * so a {@code /se reload} re-tunes it. The success dust is the one carrier&rarr;carrier interaction
 * (dragging it onto a book raises the book's bonus). The apply/dust rolls are the only non-determinism,
 * injected as a {@link Random}.
 */
public final class CarrierService {

    public static final String DUST_KEY = "dust";
    /** The white scroll — a one-shot enchant-protect guard, grants no content. */
    public static final String WHITE_SCROLL_KEY = "white-scroll";
    public static final String BOOK_KEY = "book";

    private final CarrierCodec codec;
    private final ItemEnchanter enchanter;
    private final ContentHolder content;
    private final Random random;
    private final java.util.function.Supplier<compile.load.EnchantBookConfig> bookConfig;
    private final java.util.function.Supplier<compile.load.DustConfig> dustConfig;
    private final java.util.function.Supplier<compile.load.WhiteScrollConfig> whiteScrollConfig;
    private final java.util.function.BooleanSupplier roman; // §L lore.roman — book level numeral style, read live
    private final java.util.function.IntSupplier maxBookSuccess; // §I books.max-success — global success ceiling, live
    private final item.codec.AppliedSlot slot; // §I exclusive applied-utility slot a white scroll occupies

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

    /** Book-numeral-default form: likeness suppliers supplied; the book level numeral defaults to Roman. */
    public CarrierService(CarrierCodec codec, ItemEnchanter enchanter, ContentHolder content, Random random,
                          java.util.function.Supplier<compile.load.EnchantBookConfig> bookConfig,
                          java.util.function.Supplier<compile.load.DustConfig> dustConfig,
                          java.util.function.Supplier<compile.load.WhiteScrollConfig> whiteScrollConfig) {
        this(codec, enchanter, content, random, bookConfig, dustConfig, whiteScrollConfig, () -> true, () -> 100,
                new item.codec.AppliedSlot("appliedslot"));
    }

    /**
     * Canonical form (composition root): likeness suppliers re-read on use so a {@code /se reload} re-tunes
     * them; {@code roman} (the live {@code lore.roman} setting) chooses the book level numeral style;
     * {@code maxBookSuccess} (the live {@code books.max-success} setting) is the global success ceiling that
     * binds randomised minting and dust (guaranteed/admin books are exempt — see {@link #capBookSuccess});
     * {@code slot} is the shared exclusive applied-utility slot a white scroll occupies (§I).
     */
    public CarrierService(CarrierCodec codec, ItemEnchanter enchanter, ContentHolder content, Random random,
                          java.util.function.Supplier<compile.load.EnchantBookConfig> bookConfig,
                          java.util.function.Supplier<compile.load.DustConfig> dustConfig,
                          java.util.function.Supplier<compile.load.WhiteScrollConfig> whiteScrollConfig,
                          java.util.function.BooleanSupplier roman,
                          java.util.function.IntSupplier maxBookSuccess,
                          item.codec.AppliedSlot slot) {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.enchanter = Objects.requireNonNull(enchanter, "enchanter");
        this.content = Objects.requireNonNull(content, "content");
        this.random = Objects.requireNonNull(random, "random");
        this.bookConfig = Objects.requireNonNull(bookConfig, "bookConfig");
        this.dustConfig = Objects.requireNonNull(dustConfig, "dustConfig");
        this.whiteScrollConfig = Objects.requireNonNull(whiteScrollConfig, "whiteScrollConfig");
        this.roman = Objects.requireNonNull(roman, "roman");
        this.maxBookSuccess = Objects.requireNonNull(maxBookSuccess, "maxBookSuccess");
        this.slot = Objects.requireNonNull(slot, "slot");
    }

    /** Mint a RANDOM-bonus SUCCESS DUST (§I; ADR-0019) — combined onto a book it rolls a bonus in {@code [min, max]}. */
    public ItemStack mintDust() {
        return buildDust(0); // 0 baked → roll [min, max] from config at apply time
    }

    /** Mint a FIXED-percent SUCCESS DUST conferring exactly {@code fixedPercent} (§I) — baked, so reload-stable. */
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
        ItemStack stack = ItemFactory.buildItem(ItemFactory.material(cfg.material(), Material.GLOWSTONE_DUST),
                subDust(cfg.name(), label, min, max), lore);
        // successBonus carries the FIXED bonus; 0 = random, rolled from config on apply.
        codec.write(stack, new CarrierData(DUST_KEY, "", 0, fixedBonus));
        return stack;
    }

    /**
     * Substitute the dust placeholders. {@code {BONUS}} = the conferred success bonus (a fixed number, or the
     * {@code min–max} range label for a random dust); {@code {MIN}}/{@code {MAX}} = the range bounds;
     * {@code {MAXSUCCESS}} = the live global {@code books.max-success} ceiling the boost is clamped to.
     */
    private String subDust(String s, String bonus, String min, String max) {
        String cap = Integer.toString(clampPercent(maxBookSuccess.getAsInt()));
        return s.replace("{BONUS}", bonus).replace("{MIN}", min).replace("{MAX}", max)
                .replace("{MAXSUCCESS}", cap);
    }

    /** Mint a WHITE SCROLL with a RANDOM apply-success rolled in the config {@code [min, max]} range (§I). */
    public ItemStack mintWhiteScroll() {
        compile.load.WhiteScrollConfig cfg = whiteScrollConfig.get();
        int span = cfg.maxSuccess() - cfg.minSuccess();
        return buildWhiteScroll(span <= 0 ? cfg.minSuccess() : cfg.minSuccess() + random.nextInt(span + 1));
    }

    /** Mint a WHITE SCROLL at an EXPLICIT apply-success (§J {@code /se give whitescroll <player> <percent>}). */
    public ItemStack mintWhiteScroll(int fixedSuccess) {
        return buildWhiteScroll(clampPercent(fixedSuccess));
    }

    private ItemStack buildWhiteScroll(int success) {
        compile.load.WhiteScrollConfig cfg = whiteScrollConfig.get();
        ItemStack stack = ItemFactory.buildItem(material(cfg.material()),
                subPercent(cfg.name(), success), subPercentLore(cfg.lore(), success));
        // store the rolled success in the carrier's baseSuccess so applyProtect can roll against it
        codec.write(stack, new CarrierData(WHITE_SCROLL_KEY, "", 0, 0, success));
        return stack;
    }

    /**
     * Substitute the white-scroll percent placeholders: {@code {SUCCESS}} = the rolled apply success,
     * {@code {FAILURE}} = its complement.
     */
    private static String subPercent(String s, int success) {
        return s.replace("{SUCCESS}", Integer.toString(success)).replace("{FAILURE}", Integer.toString(100 - success));
    }

    private static List<String> subPercentLore(List<String> lore, int success) {
        List<String> out = new ArrayList<>(lore.size());
        for (String line : lore) {
            out.add(subPercent(line, success));
        }
        return out;
    }

    /**
     * Apply {@code carrier} to {@code target}, mutating both. A no-op (not a carrier / ineligible target /
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
        // ONE carrier affects ONE item — applying to a stack would be a dupe / mass-loss exploit.
        if (target.getAmount() > 1) {
            return CarrierResult.noop("§cApply the carrier to a single item — split the stack first.");
        }

        // Success dust: the one carrier-onto-carrier interaction (ADR-0019). Fixed dust confers its baked
        // bonus; random dust rolls [min, max] from the live config.
        if (DUST_KEY.equals(data.itemKey())) {
            compile.load.DustConfig cfg = dustConfig.get();
            int bonus = data.successBonus() > 0 ? data.successBonus() : rolledDustBonus(cfg);
            return applyDustBonus(carrier, target, bonus, cfg.sound(), cfg.particles());
        }
        if (WHITE_SCROLL_KEY.equals(data.itemKey())) {
            return applyProtect(carrier, target, data);
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
            slot.release(target, item.codec.AppliedSlot.WHITE_SCROLL); // §I the white scroll's guard is spent
            enchanter.reRender(target); // drop the PROTECTED line now the guard is gone
            return CarrierResult.consumed("§eThe enchant failed — but your protection saved the item.");
        }
        if (destroyOnFail) {
            target.setAmount(0);
            return CarrierResult.consumed("§cThe enchant failed — the item shattered.");
        }
        return CarrierResult.consumed("§eThe enchant failed — the item is unharmed.");
    }

    private int rolledDustBonus(compile.load.DustConfig cfg) {
        int span = cfg.maxBonus() - cfg.minBonus();
        return span <= 0 ? cfg.minBonus() : cfg.minBonus() + random.nextInt(span + 1);
    }

    /** Mint an enchant BOOK from the general likeness. Default success — always succeeds, never destroys. */
    public ItemStack mintBook(String enchantKey, int level) {
        ItemStack stack = bookLikeness(enchantKey, level, -1);
        codec.write(stack, new CarrierData(BOOK_KEY, enchantKey, level));
        return stack;
    }

    /**
     * Mint an enchant BOOK that applies at an explicit {@code successChance} (§I, the unopened/randomized
     * book) — the lore shows that success/failure rate and the book carries a base-success override.
     */
    public ItemStack mintBook(String enchantKey, int level, int successChance) {
        int chance = clampPercent(successChance);
        ItemStack stack = bookLikeness(enchantKey, level, chance);
        codec.write(stack, new CarrierData(BOOK_KEY, enchantKey, level, 0, chance));
        return stack;
    }

    /**
     * Build the visible book item from the configured spec (§I). {@code successChance < 0} (a plain guaranteed
     * book) shows a 100% success rate; an explicit chance shows that. The name is the tier-coloured bold
     * enchant + level; the lore is the word-wrapped description, the success/failure rate, the grammatically-
     * joined applies-to kinds, and the drag-and-drop footer.
     */
    private ItemStack bookLikeness(String enchantKey, int level, int successChance) {
        compile.load.EnchantBookConfig cfg = bookConfig.get();
        EnchantDef def = enchantDef(enchantKey);
        String enchant = displayOf(enchantKey);
        String tierColor = tierColorOf(enchantKey);
        String levelText = levelNumeral(level);
        int shown = successChance < 0 ? 100 : clampPercent(successChance);
        String display = subBook(cfg.name(), enchant, levelText, tierColor, shown, def);
        List<String> lore = bookLore(cfg, def, enchant, descriptionOf(enchantKey), tierColor, levelText,
                successChance, shown);
        return ItemFactory.build(ItemFactory.material(cfg.material(), Material.ENCHANTED_BOOK), display, lore);
    }

    /**
     * The raw (untranslated) book lore from the likeness. A {@code {DESCRIPTION}} line expands to the enchant's
     * description word-wrapped to {@code cfg.wrap()} visible chars — one lore entry per wrapped line, so the
     * full description renders (never one entry with embedded newlines). {@code {SUCCESS}}/{@code {FAILURE}} show
     * {@code shown}/(100−{@code shown}); the legacy {@code success-lore} block (empty by default) is appended
     * only for an explicit-success book ({@code successChance >= 0}).
     */
    private static List<String> bookLore(compile.load.EnchantBookConfig cfg, EnchantDef def, String enchant,
                                         String description, String tierColor, String levelText,
                                         int successChance, int shown) {
        List<String> lore = new ArrayList<>();
        for (String line : cfg.lore()) {
            if (line.contains("{DESCRIPTION}")) {
                // Authored newlines are obeyed as hard breaks; a single authored line word-wraps to cfg.wrap().
                for (String descLine : item.render.TextWrap.wrap(description, cfg.wrap())) {
                    lore.add(subBook(line.replace("{DESCRIPTION}", descLine), enchant, levelText, tierColor, shown, def));
                }
            } else {
                lore.add(subBook(line, enchant, levelText, tierColor, shown, def));
            }
        }
        if (successChance >= 0) { // back-compat: a configured success-lore block still appends for explicit books
            for (String line : cfg.successLore()) {
                lore.add(subBook(line, enchant, levelText, tierColor, shown, def));
            }
        }
        return lore;
    }

    /** Substitute every book placeholder in {@code line}. {@code def} may be {@code null} (unknown enchant). */
    private static String subBook(String line, String enchant, String levelText, String tierColor, int shown,
                                  EnchantDef def) {
        String kinds = def == null ? "" : platform.item.ItemGroups.kindsLabel(def.appliesTo());
        return line.replace("{ENCHANT}", enchant)
                .replace("{LEVEL}", levelText)
                .replace("{TIER_COLOR}", tierColor)
                .replace("{TIER-COLOR}", tierColor) // tolerate either spelling
                .replace("{SUCCESS}", Integer.toString(shown))
                .replace("{FAILURE}", Integer.toString(100 - shown))
                .replace("{KINDS}", kinds);
    }

    /**
     * The styled display name an enchant book carries (tier-coloured, with any bold/underline from the
     * likeness {@code name:} template), at {@code level}; {@code level <= 0} renders a level-less name (the
     * trailing {@code " {LEVEL}"} slot is dropped). Lets a menu icon match the unapplied book ({@code &}-coded;
     * the caller translates).
     */
    public String bookDisplayName(String enchantKey, int level) {
        String tierColor = tierColorOf(enchantKey);
        String name = bookConfig.get().name()
                .replace("{TIER_COLOR}", tierColor)
                .replace("{TIER-COLOR}", tierColor)
                .replace("{ENCHANT}", displayOf(enchantKey));
        return level <= 0
                ? name.replace(" {LEVEL}", "").replace("{LEVEL}", "")
                : name.replace("{LEVEL}", levelNumeral(level));
    }

    /** The enchant's rarity-tier colour code (e.g. {@code &e}), or grey ({@code &7}) for no/unknown tier. */
    private String tierColorOf(String enchantKey) {
        String tier = content.library().tierOf(enchantKey);
        if (tier == null) {
            return "&7";
        }
        compile.load.TierRegistry.Tier t = content.library().tiers().tier(tier);
        return t != null && !t.color().isBlank() ? t.color() : "&7";
    }

    /** Render a level as a Roman numeral or Arabic number per the live {@code lore.roman} setting. */
    private String levelNumeral(int level) {
        return roman.getAsBoolean() ? item.render.Numerals.roman(level) : Integer.toString(level);
    }

    /** The enchant a book grants and at what level, or empty when {@code stack} is not an enchant book. */
    public java.util.Optional<BookContents> bookContents(ItemStack stack) {
        CarrierData data = codec.read(stack);
        if (data == null || !isEnchantBook(data)) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new BookContents(data.grantKey(), data.grantLevel()));
    }

    /**
     * Combine two enchant books into one of the next level — the Alchemist's upgrade (§K). Both must grant
     * the SAME enchant at the SAME level, below its max; empty otherwise. A mint of an existing item type —
     * NOT a new economy data model (cf. ADR-0019).
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

    private EnchantDef enchantDef(String key) {
        for (EnchantDef def : content.library().catalog()) {
            if (def.key().equals(key)) {
                return def;
            }
        }
        return null;
    }

    public record BookContents(String enchantKey, int level) {
    }

    /**
     * Reroll {@code book}'s success to {@code targetPercent} (§I randomizer scroll) — sets a base-success
     * override, clears any dust bonus, re-renders lore. No-op (not an enchant book) leaves it untouched.
     */
    public CarrierResult rerollSuccess(ItemStack book, int targetPercent) {
        CarrierData data = codec.read(book);
        if (data == null || !isEnchantBook(data)) {
            return CarrierResult.noop("§cThe randomizer only works on an enchant book.");
        }
        int target = capBookSuccess(targetPercent); // randomizer respects the global ceiling (§I)
        CarrierData updated = data.withBaseSuccess(target);
        codec.write(book, updated);
        reRenderBookLore(book, updated);
        return CarrierResult.consumed("§aThe book's success chance is now §f" + target + "%§a.");
    }

    /**
     * Whether {@code cursor} is a success dust that would LEGALLY combine onto {@code target} (an enchant
     * book). The listener claims the otherwise-forbidden carrier-onto-carrier gesture only when this holds
     * (ADR-0019), else falls through to the vanilla click.
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
     * Raise {@code book}'s stored bonus by {@code bonus} (clamped so effective success never exceeds 100%),
     * re-render lore, and consume the {@code dust}. No-op (not a book, no bonus, already at 100%) leaves both
     * untouched.
     */
    private CarrierResult applyDustBonus(ItemStack dust, ItemStack book, int bonus, String sound,
                                         java.util.List<String> particles) {
        CarrierData bookData = codec.read(book);
        if (bookData == null || !isEnchantBook(bookData)) {
            return CarrierResult.noop("§cDust can only boost an enchant book.");
        }
        int base = baseSuccessOf(bookData);
        int cap = capBookSuccess(100); // global ceiling dust may lift a book TO — it snaps, never overflows (§I)
        if (effectiveSuccess(base, bookData.successBonus()) >= cap) {
            return CarrierResult.noop("§7That book is already at the §f" + cap + "%§7 success ceiling.");
        }
        if (bonus <= 0) {
            return CarrierResult.noop("§cThis dust confers no success bonus.");
        }
        int newBonus = Math.max(0, Math.min(bookData.successBonus() + bonus, cap - base)); // base + bonus ≤ cap
        CarrierData updated = bookData.withSuccessBonus(newBonus);
        codec.write(book, updated);
        reRenderBookLore(book, updated);
        consume(dust);
        return CarrierResult.consumed("§aThe book's success chance is now §f"
                + effectiveSuccess(base, newBonus) + "%§a.", sound, particles);
    }

    /**
     * Stamp the one-shot guard marker on {@code target} (white scroll), rolling the scroll's own success first
     * (§I): a failed roll spends the scroll WITHOUT protecting and never destroys the gear (only books destroy).
     */
    private CarrierResult applyProtect(ItemStack carrier, ItemStack target, CarrierData data) {
        if (codec.isGuarded(target)) {
            return CarrierResult.noop("§7That item is already protected.");
        }
        int success = data.hasBaseSuccess() ? data.baseSuccess() : 100;
        consume(carrier); // a use is spent whether the roll succeeds or fails
        if (random.nextInt(100) >= success) {
            return CarrierResult.consumed("§eThe White Scroll failed — the item is not protected.");
        }
        codec.setGuarded(target, true);
        slot.occupy(target, item.codec.AppliedSlot.WHITE_SCROLL); // §I add the white-scroll marker (coexists with traks/holy)
        enchanter.reRender(target); // stamp the PROTECTED line from the new guard state
        return CarrierResult.consumed("§aProtected — a failed enchant will spare this item once.");
    }

    /** Re-render an enchant book's lore from state (never parsed back), so a dust combine or reroll shows the new success. */
    @SuppressWarnings("deprecation") // setLore(List): the floor-stable item-meta path
    private void reRenderBookLore(ItemStack book, CarrierData data) {
        ItemMeta meta = book.getItemMeta();
        if (meta == null) {
            return;
        }
        int effective = effectiveSuccess(baseSuccessOf(data), data.successBonus());
        // A re-rendered book always has an explicit (effective) success — pass it as both the explicit chance
        // and the shown value so the success/failure rate updates after a dust combine or reroll.
        List<String> raw = bookLore(bookConfig.get(), enchantDef(data.grantKey()), displayOf(data.grantKey()),
                descriptionOf(data.grantKey()), tierColorOf(data.grantKey()), levelNumeral(data.grantLevel()),
                effective, effective);
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

    /** The description for an enchant key (for a book's {@code {DESCRIPTION}} line), or empty if none/unknown. */
    private String descriptionOf(String enchantKey) {
        EnchantDef def = enchantDef(enchantKey);
        return def != null ? def.description() : "";
    }

    /** A base success chance plus an accumulated bonus, clamped to {@code [0, 100]}. */
    private static int effectiveSuccess(int base, int bonus) {
        return Math.max(0, Math.min(100, base + Math.max(0, bonus)));
    }

    /** A book's base success: its explicit {@code baseSuccess} override (§I) when present, else 100 (always succeeds). */
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

    /**
     * Clamp {@code pct} to the global {@code books.max-success} ceiling (§I, live) and {@code [0, 100]} — the
     * cap that binds RANDOMISED minting (unopened book / randomizer scroll), the black scroll conversion, and
     * dust. Guaranteed books (no base-success override → 100) and admin explicit-success mints are never
     * routed through here, so they stay uncapped.
     */
    public int capBookSuccess(int pct) {
        return Math.min(clampPercent(pct), clampPercent(maxBookSuccess.getAsInt()));
    }

    private static Material material(String token) {
        return ItemFactory.material(token, Material.PAPER); // cross-version resolve lives in one place
    }
}
