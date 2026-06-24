package feature.carrier;

import compile.load.ContentHolder;
import compile.load.EnchantDef;
import compile.load.ItemDef;
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
 * The book/scroll/dust application economy (ADR-0016) — the cold path that MINTS carrier items from
 * {@link ItemDef}s and APPLIES them to gear. A book/crystal carrier rolls its {@code success-chance} and,
 * on success, applies its granted enchant/crystal through the shared {@link ItemEnchanter}; on failure it
 * destroys the gear (when {@code destroy-on-fail}) unless a guard scroll spared it. A protect scroll
 * stamps the guard marker. Carrier identity lives in PDC ({@link CarrierCodec}); behaviour comes from the
 * live {@link ItemDef} (looked up each apply, so a reload is honoured) — never decoded on the combat hot
 * path. The apply roll is the only non-determinism, injected as a {@link Random} for testability.
 *
 * <p>Dust (success-bonus combining, ADR-0019) is the one carrier→carrier interaction: dragging a dust
 * onto a content book raises that book's stored {@code successBonus} (clamped so its effective success
 * can never exceed 100%), so a later book→gear apply rolls against {@code clamp(success-chance + bonus)}.
 * Any genuinely unsupported carrier kind remains a no-op with a message, never a silent loss.
 */
public final class CarrierService {

    private final CarrierCodec codec;
    private final ItemEnchanter enchanter;
    private final ContentHolder content;
    private final Random random;
    private final java.util.function.Supplier<compile.load.EnchantBookConfig> bookConfig; // §I general book likeness

    /** Test/fixture form: the built-in enchant-book likeness. */
    public CarrierService(CarrierCodec codec, ItemEnchanter enchanter, ContentHolder content, Random random) {
        this(codec, enchanter, content, random, compile.load.EnchantBookConfig::defaults);
    }

    /** Canonical form (the composition root): {@code bookConfig} is the live general enchant-book likeness. */
    public CarrierService(CarrierCodec codec, ItemEnchanter enchanter, ContentHolder content, Random random,
                          java.util.function.Supplier<compile.load.EnchantBookConfig> bookConfig) {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.enchanter = Objects.requireNonNull(enchanter, "enchanter");
        this.content = Objects.requireNonNull(content, "content");
        this.random = Objects.requireNonNull(random, "random");
        this.bookConfig = Objects.requireNonNull(bookConfig, "bookConfig");
    }

    /** Create one carrier {@link ItemStack} from {@code def} (material, name, lore, carrier PDC). */
    @SuppressWarnings("deprecation") // setDisplayName/setLore(String/List): the floor-stable item-meta path
    public ItemStack mint(ItemDef def) {
        ItemStack stack = new ItemStack(material(def.material()));
        CarrierData data = new CarrierData(def.key(), grantKeyOf(def), grantLevelOf(def));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(def.display()));
            List<String> lore = renderLore(def, data);
            if (!lore.isEmpty()) {
                meta.setLore(lore);
            }
            stack.setItemMeta(meta);
        }
        codec.write(stack, data);
        return stack;
    }

    /**
     * Apply the carrier {@code carrier} to {@code target}, mutating both (the grant lands on the target,
     * one carrier use is consumed) and returning the outcome. A no-op (ineligible target / missing def /
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
        // Mechanics come from the authored ItemDef when present; an ad-hoc book (no def) uses defaults
        // (always succeeds, never destroys). The grant itself always comes from the carrier's PDC.
        ItemDef def = itemDef(data.itemKey());

        // Dust combines onto a book — the one carrier-onto-carrier interaction (ADR-0019).
        if (def != null && isDust(def)) {
            return applyDust(carrier, target, def);
        }
        if (def != null && isProtectScroll(def)) {
            if (codec.isGuarded(target)) {
                return CarrierResult.noop("§7That item is already protected.");
            }
            codec.setGuarded(target, true);
            consume(carrier);
            return CarrierResult.consumed("§aProtected — a failed enchant will spare this item once.");
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

        int base = baseSuccessOf(data, def); // an unopened-book output / randomizer reroll overrides the def base
        int successChance = effectiveSuccess(base, data.successBonus()); // dust-accumulated bonus (ADR-0019)
        boolean destroyOnFail = def != null && def.apply().destroyOnFail();
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

    /**
     * Mint an enchant BOOK for an arbitrary enchant from the GENERAL {@code items/enchant-book.yml} likeness
     * (no per-enchant config) — the enchant's display name fills {@code {ENCHANT}}, the level fills
     * {@code {LEVEL}}. It applies with default mechanics (always succeeds, never destroys). Used by
     * {@code /se give book}, combine, etc.
     */
    public ItemStack mintBook(String enchantKey, int level) {
        ItemStack stack = bookLikeness(enchantKey, level, -1);
        codec.write(stack, new CarrierData("book", enchantKey, level));
        return stack;
    }

    /**
     * Mint an enchant BOOK that applies at an explicit {@code successChance} (§I) — used by the unopened/
     * randomized book. Like {@link #mintBook(String, int)} but the general likeness's {@code success-lore}
     * (with {@code {SUCCESS}}) is appended and the book carries a base-success override.
     */
    public ItemStack mintBook(String enchantKey, int level, int successChance) {
        int chance = clampPercent(successChance);
        ItemStack stack = bookLikeness(enchantKey, level, chance);
        codec.write(stack, new CarrierData("book", enchantKey, level, 0, chance));
        return stack;
    }

    /** Build the visible book item from the general likeness (success {@code < 0} omits the success line). */
    private ItemStack bookLikeness(String enchantKey, int level, int successChance) {
        compile.load.EnchantBookConfig cfg = bookConfig.get();
        String name = content.library().displayNameOf(enchantKey);
        String display = cfg.name()
                .replace("{ENCHANT}", name != null ? name : enchantKey)
                .replace("{LEVEL}", Integer.toString(level));
        List<String> lore = new ArrayList<>();
        for (String line : cfg.lore()) {
            lore.add(line.replace("{ENCHANT}", name != null ? name : enchantKey)
                    .replace("{LEVEL}", Integer.toString(level)));
        }
        if (successChance >= 0) {
            for (String line : cfg.successLore()) {
                lore.add(line.replace("{LEVEL}", Integer.toString(level))
                        .replace("{SUCCESS}", Integer.toString(successChance)));
            }
        }
        return ItemFactory.build(ItemFactory.material(cfg.material(), Material.ENCHANTED_BOOK), display, lore);
    }

    /** The enchant a book grants and at what level, or empty when {@code stack} is not an enchant book. */
    public java.util.Optional<BookContents> bookContents(ItemStack stack) {
        CarrierData data = codec.read(stack);
        if (data == null || !data.grants() || enchantDef(data.grantKey()) == null) {
            return java.util.Optional.empty(); // not a carrier, no grant, or the grant is not a catalog enchant
        }
        return java.util.Optional.of(new BookContents(data.grantKey(), data.grantLevel()));
    }

    /**
     * Combine two enchant books into one of the next level — the Alchemist's upgrade (docs/v3-directives.md
     * §K). Both must grant the SAME enchant at the SAME level, below that enchant's max. Returns the minted
     * level+1 book, or empty when the pair is not combinable (the menu then leaves both inputs untouched). A
     * mint of an existing item type — NOT a new economy data model (cf. ADR-0019, which excludes EE's
     * dust-rarity-tinkering / book↔dust conversion).
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
     * sets an explicit base-success override and clears any accumulated dust bonus, then re-renders the
     * book's lore from state. A no-op (not a content-granting book) leaves the stack untouched.
     */
    public CarrierResult rerollSuccess(ItemStack book, int targetPercent) {
        CarrierData data = codec.read(book);
        if (data == null || !data.grants()) {
            return CarrierResult.noop("§cThe randomizer only works on an enchant book.");
        }
        ItemDef def = itemDef(data.itemKey());
        if (!isContentCarrier(def)) {
            return CarrierResult.noop("§cThe randomizer only works on an enchant book.");
        }
        CarrierData updated = data.withBaseSuccess(clampPercent(targetPercent));
        codec.write(book, updated);
        if (def != null) {
            reRenderLore(book, def, updated);
        }
        return CarrierResult.consumed("§aThe book's success chance is now §f" + clampPercent(targetPercent) + "%§a.");
    }

    /**
     * Whether {@code cursor} is a dust that would LEGALLY combine onto {@code target} — i.e. the cursor is
     * a bonus-carrying dust and the target is a content-granting book/tome/gem. The interaction layer
     * claims the otherwise-forbidden carrier-onto-carrier gesture only when this holds (ADR-0019), so a
     * dust dropped onto a scroll / another dust / a non-content carrier falls through to the vanilla click
     * instead of becoming a dead, cancelled no-op.
     */
    public boolean canCombineDust(ItemStack cursor, ItemStack target) {
        CarrierData cursorData = codec.read(cursor);
        if (cursorData == null) {
            return false;
        }
        ItemDef cursorDef = itemDef(cursorData.itemKey());
        if (cursorDef == null || !isDust(cursorDef) || dustBonus(cursorDef) <= 0) {
            return false;
        }
        CarrierData targetData = codec.read(target);
        if (targetData == null || !targetData.grants()) {
            return false;
        }
        return isContentCarrier(itemDef(targetData.itemKey()));
    }

    /**
     * Combine the {@code dust} onto a {@code book} — the one carrier-onto-carrier interaction (ADR-0019).
     * Raises the book's stored success bonus by the dust's {@code grants.success-bonus}, clamped so the
     * book's effective success can never exceed 100%, re-renders the book's lore from state, and consumes
     * the dust. A no-op (target not a content book, dust confers nothing, or the book is already at 100%)
     * leaves both stacks untouched.
     */
    private CarrierResult applyDust(ItemStack dust, ItemStack book, ItemDef dustDef) {
        CarrierData bookData = codec.read(book);
        ItemDef bookDef = bookData == null ? null : itemDef(bookData.itemKey());
        // A dust only boosts a content-granting book/tome/gem — never another dust, a scroll, or plain gear.
        if (bookData == null || !bookData.grants() || !isContentCarrier(bookDef)) {
            return CarrierResult.noop("§cDust can only boost an enchant book.");
        }
        int base = baseSuccessOf(bookData, bookDef);
        if (effectiveSuccess(base, bookData.successBonus()) >= 100) {
            return CarrierResult.noop("§7That book is already at 100% success.");
        }
        int bonus = dustBonus(dustDef);
        if (bonus <= 0) {
            return CarrierResult.noop("§cThis dust confers no success bonus.");
        }
        int newBonus = Math.max(0, Math.min(bookData.successBonus() + bonus, 100 - base)); // base + bonus ≤ 100
        CarrierData updated = bookData.withSuccessBonus(newBonus);
        codec.write(book, updated);
        if (bookDef != null) {
            reRenderLore(book, bookDef, updated);
        }
        consume(dust);
        // §I: hand the dust's configured sound + particle tokens to the listener (it plays them on the
        // player's own region thread). A dust with no feedback configured falls back to a plain result.
        ItemDef.Grant grant = dustDef.grant();
        return CarrierResult.consumed("§aThe book's success chance is now §f"
                + effectiveSuccess(base, newBonus) + "%§a.",
                grant == null ? null : grant.sound(),
                grant == null ? java.util.List.of() : grant.particles());
    }

    /** Re-render a carrier's lore from its def + current state (lore is rendered from state, never parsed). */
    @SuppressWarnings("deprecation") // setLore(List): the floor-stable item-meta path
    private void reRenderLore(ItemStack stack, ItemDef def, CarrierData data) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        List<String> lore = renderLore(def, data);
        meta.setLore(lore.isEmpty() ? null : lore);
        stack.setItemMeta(meta);
    }

    /**
     * Build a carrier's lore deterministically from its def + on-item state — never parsed back. A dust
     * advertises the success bonus it confers; a content book/tome/gem shows its current effective success
     * chance (so combining a dust has a visible effect).
     */
    private List<String> renderLore(ItemDef def, CarrierData data) {
        List<String> lore = new ArrayList<>();
        if (def != null && !def.description().isBlank()) {
            for (String line : def.description().split("\n")) {
                lore.add(color("&7" + line));
            }
        }
        if (def != null && isDust(def)) {
            lore.add(color("&7Combine onto an enchant book: &a+" + dustBonus(def) + "%&7 success."));
        } else if (data.grants() && isContentCarrier(def)) {
            int base = baseSuccessOf(data, def);
            lore.add(color("&7Success chance: &f" + effectiveSuccess(base, data.successBonus()) + "%"));
        }
        return lore;
    }

    /** Whether {@code def} is a success-bonus dust (ADR-0019). */
    static boolean isDust(ItemDef def) {
        return "dust".equals(def.kind());
    }

    /** Whether {@code def} confers content (book/tome/gem) — the only carriers a dust can boost. A null def
     * is an ad-hoc {@code /se book}, which is content-granting by construction. */
    private static boolean isContentCarrier(ItemDef def) {
        if (def == null) {
            return true;
        }
        return switch (def.kind() == null ? "" : def.kind()) {
            case "book", "tome", "gem" -> true;
            default -> false;
        };
    }

    /** The success-chance bonus a dust confers, or 0 if it carries none. */
    private static int dustBonus(ItemDef def) {
        ItemDef.Grant g = def.grant();
        return g == null || g.successBonus() == null ? 0 : Math.max(0, g.successBonus());
    }

    /** A base success chance plus an accumulated bonus, clamped to {@code [0, 100]}. */
    private static int effectiveSuccess(int base, int bonus) {
        return Math.max(0, Math.min(100, base + Math.max(0, bonus)));
    }

    /**
     * The base success chance for a carrier: its explicit {@link CarrierData#baseSuccess()} override (§I —
     * an unopened-book output or a randomizer reroll) when present, else the def's authored chance, else
     * {@code 100} (an ad-hoc {@code /se book} always succeeds). The bonus is added on top by callers.
     */
    private static int baseSuccessOf(CarrierData data, ItemDef def) {
        if (data.hasBaseSuccess()) {
            return data.baseSuccess();
        }
        return def != null ? clampPercent(def.apply().successChance()) : 100;
    }

    private static String color(String raw) {
        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    /** Whether {@code def} is a protect scroll (stamps the guard marker rather than granting content). */
    static boolean isProtectScroll(ItemDef def) {
        return "scroll".equals(def.kind()) && def.grant() != null
                && "PROTECT".equalsIgnoreCase(def.grant().role());
    }

    /** The resolved grant key for a carrier def: its enchant, else crystal, else set, else {@code ""}. */
    static String grantKeyOf(ItemDef def) {
        ItemDef.Grant g = def.grant();
        if (g == null) {
            return "";
        }
        if (g.enchant() != null) {
            return g.enchant();
        }
        if (g.crystal() != null) {
            return g.crystal();
        }
        return g.set() != null ? g.set() : "";
    }

    private static int grantLevelOf(ItemDef def) {
        return def.grant() == null ? 0 : Math.max(0, def.grant().level());
    }

    private ItemDef itemDef(String key) {
        for (ItemDef def : content.library().items()) {
            if (def.key().equals(key)) {
                return def;
            }
        }
        return null;
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
