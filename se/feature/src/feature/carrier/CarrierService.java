package feature.carrier;

import compile.load.ContentHolder;
import compile.load.ItemDef;
import feature.apply.ApplyResult;
import feature.apply.ItemEnchanter;
import item.codec.CarrierCodec;
import item.codec.CarrierData;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
 * <p>Dust (success-bonus combining) is a documented follow-up; an unsupported carrier kind is a no-op
 * with a message, never a silent loss.
 */
public final class CarrierService {

    private final CarrierCodec codec;
    private final ItemEnchanter enchanter;
    private final ContentHolder content;
    private final Random random;

    public CarrierService(CarrierCodec codec, ItemEnchanter enchanter, ContentHolder content, Random random) {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.enchanter = Objects.requireNonNull(enchanter, "enchanter");
        this.content = Objects.requireNonNull(content, "content");
        this.random = Objects.requireNonNull(random, "random");
    }

    /** Create one carrier {@link ItemStack} from {@code def} (material, name, lore, carrier PDC). */
    @SuppressWarnings("deprecation") // setDisplayName/setLore(String/List): the floor-stable item-meta path
    public ItemStack mint(ItemDef def) {
        ItemStack stack = new ItemStack(material(def.material()));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', def.display()));
            List<String> lore = new ArrayList<>();
            if (!def.description().isBlank()) {
                for (String line : def.description().split("\n")) {
                    lore.add(ChatColor.translateAlternateColorCodes('&', "&7" + line));
                }
            }
            if (!lore.isEmpty()) {
                meta.setLore(lore);
            }
            stack.setItemMeta(meta);
        }
        codec.write(stack, new CarrierData(def.key(), grantKeyOf(def), grantLevelOf(def)));
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
                ? enchanter.checkCrystal(target.getType(), grant)
                : enchanter.checkEnchant(target.getType(), grant, data.grantLevel());
        if (!check.ok()) {
            return CarrierResult.noop(check.message()); // ineligible target → don't waste the carrier
        }

        int successChance = def != null ? clampPercent(def.apply().successChance()) : 100;
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
     * Mint an ad-hoc enchant BOOK for an arbitrary enchant (no authored {@code ItemDef} needed) — an
     * ENCHANTED_BOOK with the enchant's display name + a hint, carrying the grant in PDC. It applies with
     * default mechanics (always succeeds, never destroys). Used by {@code /se book} for admins/testing.
     */
    @SuppressWarnings("deprecation") // setDisplayName/setLore(String/List): the floor-stable item-meta path
    public ItemStack mintBook(String enchantKey, int level) {
        ItemStack stack = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            String name = content.library().displayNameOf(enchantKey);
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&',
                    (name != null ? name : enchantKey) + " &7Book"));
            meta.setLore(List.of(ChatColor.translateAlternateColorCodes('&',
                    "&7Drag onto a held/worn item to apply &flevel " + level + "&7.")));
            stack.setItemMeta(meta);
        }
        // itemKey "book" resolves to no ItemDef → applyTo uses default mechanics.
        codec.write(stack, new CarrierData("book", enchantKey, level));
        return stack;
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
        Material m = token == null ? null : Material.getMaterial(token.toUpperCase(Locale.ROOT));
        return m != null ? m : Material.PAPER;
    }
}
