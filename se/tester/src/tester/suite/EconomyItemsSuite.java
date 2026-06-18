package tester.suite;

import compile.load.ContentHolder;
import compile.load.Library;
import compile.load.LibraryLoader;
import compile.load.ScrollsConfig;
import compile.load.SlotConfig;
import compile.load.UnopenedBookConfig;
import engine.boot.ContentCompiler;
import feature.apply.ItemEnchanter;
import feature.book.UnopenedBookService;
import feature.book.UnopenedResult;
import feature.carrier.CarrierService;
import feature.scroll.ScrollResult;
import feature.scroll.ScrollService;
import feature.slot.SlotResult;
import feature.slot.SlotService;
import item.codec.CarrierCodec;
import item.codec.CarrierData;
import item.codec.CombatCodec;
import item.codec.CombatState;
import item.codec.ItemKeys;
import item.codec.ScrollCodec;
import item.codec.SlotItemCodec;
import item.codec.UnopenedBookCodec;
import item.render.LoreRenderer;
import item.render.LoreStyle;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import platform.item.ItemGroups;
import tester.harness.Harness;

/**
 * Live checks for the §I economy items that mutate real {@link ItemStack}s through the server's item
 * factory (what a unit test cannot prove): the slot expander persists {@code CombatState.added} in real
 * PDC and respects the universal cap; the black scroll extracts an enchant off real gear into a real book;
 * the randomizer rerolls a real book's stored base success; the unopened book mints a concrete book of a
 * tier enchant; and the transmog scroll reorders without losing any enchant and stamps the name suffix.
 * No fake player needed — these are gear/book mutations; the holy/nametag player flows live in
 * {@link ScrollPlayerSuite}. Runs on the global thread with deterministic rolls.
 */
public final class EconomyItemsSuite implements Harness.Scenario {

    private static final String SHARP = """
            display: "&eSharp"
            tier: uncommon
            applies-to: [SWORD]
            trigger: ATTACK
            levels:
              1: { chance: 100, effects: ["IGNITE:40:@Victim"] }
              2: { chance: 100, effects: ["IGNITE:40:@Victim"] }
              3: { chance: 100, effects: ["IGNITE:40:@Victim"] }
            """;
    private static final String TOUGH = """
            display: "&aTough"
            tier: uncommon
            applies-to: [SWORD]
            trigger: ATTACK
            levels:
              1: { chance: 100, effects: ["IGNITE:40:@Victim"] }
            """;

    private static final String[] KEYS = {
        "economy.slot.persistsAndCaps", "economy.black.extractsToBook", "economy.randomizer.rerollsBase",
        "economy.unopened.revealsTierBook", "economy.transmog.reordersAndSuffixes",
    };

    private final Plugin plugin;

    public EconomyItemsSuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        for (String key : KEYS) {
            h.expect(key);
        }

        Path root;
        try {
            root = Files.createTempDirectory("se-economy-suite");
            write(root, "enchants/sharp.yml", SHARP);
            write(root, "enchants/tough.yml", TOUGH);
        } catch (IOException e) {
            failAll(h, e.toString());
            return;
        }
        Library lib = LibraryLoader.load(root, ContentCompiler.production(), 0);
        if (lib.hasErrors()) {
            failAll(h, "economy content has errors: " + lib.diagnostics());
            return;
        }
        ContentHolder holder = new ContentHolder(lib);
        ItemKeys keys = ItemKeys.of(plugin);
        CombatCodec combat = new CombatCodec(keys.combat());
        LoreRenderer lore = new LoreRenderer(LoreStyle.DEFAULT, k -> holder.library().displayNameOf(k));
        ItemEnchanter enchanter = new ItemEnchanter(combat, lore, holder, ItemGroups.standard());
        CarrierCodec carrierCodec = new CarrierCodec(keys.carrier(), keys.guarded());
        CarrierService carriers = new CarrierService(carrierCodec, enchanter, holder, new Random(1));
        SlotItemCodec slotCodec = new SlotItemCodec(keys.slotItem());
        SlotService slots = new SlotService(slotCodec, combat, lore, SlotConfig::defaults,
                ItemEnchanter.DEFAULT_BASE_SLOTS);
        ScrollCodec scrollCodec = new ScrollCodec(keys.scroll());
        // A black scroll that always succeeds, so the extraction outcome is exact.
        ScrollsConfig alwaysExtract = withBlackSuccess(ScrollsConfig.defaults(), 100);
        ScrollService scrolls = new ScrollService(scrollCodec, combat, lore, carriers, holder,
                () -> alwaysExtract, new Random(2));
        UnopenedBookCodec unopenedCodec = new UnopenedBookCodec(keys.unopened());
        UnopenedBookService unopened = new UnopenedBookService(unopenedCodec, carriers, holder,
                UnopenedBookConfig::defaults, new Random(3));

        h.guard("economy.slot.persistsAndCaps", () -> {
            // Base 9 + hardCap 15 → maxAdded 6. Reach a non-multiple of the orb's +3 with two +1 gems so the
            // final orb genuinely OVERSHOOTS the cap and must clamp-and-commit (5 + 3 → clamp to 6), then a
            // further item at the cap is a no-op that is not consumed.
            ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
            slots.applyTo(slots.mintOrb(), sword); // +3 → 3
            if (combat.read(sword).added() != 3) {
                throw new IllegalStateException("first orb did not persist +3: " + combat.read(sword).added());
            }
            slots.applyTo(slots.mintGem(), sword); // +1 → 4
            slots.applyTo(slots.mintGem(), sword); // +1 → 5
            if (combat.read(sword).added() != 5) {
                throw new IllegalStateException("gems did not persist to +5: " + combat.read(sword).added());
            }
            ItemStack orb = slots.mintOrb();
            SlotResult capped = slots.applyTo(orb, sword); // 5 + 3 = 8 > cap 6 → clamp to 6, still commits
            if (combat.read(sword).added() != 6) {
                throw new IllegalStateException("clamp did not land on the cap (6): " + combat.read(sword).added());
            }
            if (!capped.commit() || orb.getAmount() != 0) {
                throw new IllegalStateException("the overshooting orb should clamp-and-commit (consuming it): " + capped);
            }
            // A fully-capped item must reject (and NOT consume) a further slot item.
            ItemStack extra = slots.mintGem();
            SlotResult noop = slots.applyTo(extra, sword);
            if (noop.commit() || extra.getAmount() != 1 || combat.read(sword).added() != 6) {
                throw new IllegalStateException("a capped item must not consume a further slot item: " + noop);
            }
        });

        h.guard("economy.black.extractsToBook", () -> {
            ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
            combat.write(sword, new CombatState(Map.of("enchants/sharp", 2), List.of()));
            ItemStack scroll = scrolls.mintBlack();
            ScrollResult result = scrolls.interact(scroll, sword);
            if (!result.commit() || result.produced() == null) {
                throw new IllegalStateException("black scroll did not extract a book: " + result);
            }
            if (combat.read(sword).enchants().containsKey("enchants/sharp")) {
                throw new IllegalStateException("the enchant was not removed from the gear");
            }
            CarrierData book = carrierCodec.read(result.produced());
            if (book == null || !"enchants/sharp".equals(book.grantKey()) || book.grantLevel() != 2) {
                throw new IllegalStateException("the produced book did not grant sharp/2: " + book);
            }
            if (scroll.getAmount() != 0) {
                throw new IllegalStateException("the black scroll was not consumed");
            }
        });

        h.guard("economy.randomizer.rerollsBase", () -> {
            ItemStack book = carriers.mintBook("enchants/sharp", 1); // ad-hoc book, base success 100
            carriers.rerollSuccess(book, 42); // direct, deterministic
            CarrierData data = carrierCodec.read(book);
            if (data == null || data.baseSuccess() != 42 || data.successBonus() != 0) {
                throw new IllegalStateException("randomizer did not set base success 42 / reset bonus: " + data);
            }
        });

        h.guard("economy.unopened.revealsTierBook", () -> {
            ItemStack mystery = unopened.mint("uncommon");
            if (!unopened.isUnopened(mystery)) {
                throw new IllegalStateException("minted unopened book not recognised");
            }
            UnopenedResult result = unopened.open(mystery);
            if (!result.opened() || result.produced() == null) {
                throw new IllegalStateException("unopened book did not reveal a book: " + result);
            }
            CarrierData book = carrierCodec.read(result.produced());
            if (book == null || !book.grants()) {
                throw new IllegalStateException("the revealed item is not a granting book: " + book);
            }
            // The rolled enchant must be one of the uncommon-tier catalog enchants.
            if (!book.grantKey().equals("enchants/sharp") && !book.grantKey().equals("enchants/tough")) {
                throw new IllegalStateException("revealed an enchant outside the tier: " + book.grantKey());
            }
            UnopenedBookConfig cfg = UnopenedBookConfig.defaults();
            if (book.baseSuccess() < cfg.minSuccess() || book.baseSuccess() > cfg.maxSuccess()) {
                throw new IllegalStateException("revealed success out of range: " + book.baseSuccess());
            }
        });

        h.guard("economy.transmog.reordersAndSuffixes", () -> {
            ItemStack sword = named(Material.DIAMOND_SWORD, "§bMy Blade");
            combat.write(sword, new CombatState(
                    new java.util.LinkedHashMap<>(Map.of("enchants/sharp", 1, "enchants/tough", 1)), List.of()));
            // A transmog scroll with the default suffix; the scroll kind drives the reorder + suffix.
            ScrollService transmogScrolls = new ScrollService(scrollCodec, combat, lore, carriers, holder,
                    ScrollsConfig::defaults, new Random(7));
            ItemStack scroll = transmogScrolls.mintTransmog();
            ScrollResult result = transmogScrolls.interact(scroll, sword);
            if (!result.commit()) {
                throw new IllegalStateException("transmog did not apply: " + result);
            }
            CombatState after = combat.read(sword);
            if (!after.enchants().keySet().equals(java.util.Set.of("enchants/sharp", "enchants/tough"))) {
                throw new IllegalStateException("transmog lost or changed enchants: " + after.enchants());
            }
            ItemMeta meta = sword.getItemMeta();
            String suffix = colored(ScrollsConfig.defaults().transmog().nameSuffix());
            if (meta == null || !meta.hasDisplayName() || !meta.getDisplayName().endsWith(suffix)) {
                throw new IllegalStateException("transmog did not append the name suffix: "
                        + (meta == null ? "no meta" : meta.getDisplayName()));
            }
        });
    }

    /** A {@link ScrollsConfig} with the black scroll's success chance overridden (rest unchanged). */
    private static ScrollsConfig withBlackSuccess(ScrollsConfig base, int chance) {
        ScrollsConfig.Black b = base.black();
        return new ScrollsConfig(
                new ScrollsConfig.Black(b.material(), b.name(), b.lore(), chance,
                        b.messageSuccess(), b.messageFail(), b.messageNoEnchants()),
                base.randomizer(), base.transmog(), base.holy(), base.nametag());
    }

    @SuppressWarnings("deprecation") // setDisplayName: the floor-stable item-meta path
    private static ItemStack named(Material material, String name) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static String colored(String raw) {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', raw);
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
