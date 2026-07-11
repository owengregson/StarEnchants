package bootstrap.wire;

import compile.load.ContentHolder;
import compile.load.EnchantDef;
import feature.apply.ItemEnchanter;
import feature.book.UnopenedBookService;
import feature.carrier.CarrierService;
import feature.crystal.CrystalService;
import feature.heroic.HeroicService;
import feature.menu.MintCatalog;
import feature.menu.Mintable;
import feature.scroll.HolyScrollService;
import feature.scroll.NametagService;
import feature.scroll.ScrollService;
import feature.slot.SlotService;
import feature.soul.SoulService;
import feature.trak.TrakService;
import feature.useitem.UseItemService;
import compile.load.UseItemDef;
import item.codec.TrakCodec;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import platform.item.Inventories;
import platform.sched.Scheduling;

/**
 * The mint declarations (ADR-0047): the former {@code SeCommand} give/self helper bodies relocated verbatim
 * and closed over their feature services. Each factory returns the ONE {@link Mintable} a module owns; the
 * verbatim-move rule (§16) governs — same messages, same arg parsing, no re-derived logic. The curated tile
 * ranks (§5) reproduce the shipped mint-menu order; {@link Give} holds the delivery/parse helpers the bodies share.
 */
final class Mints {

    private Mints() {
    }

    /** {@code gem} — the soul gem (souls). */
    static Mintable gem(SoulService souls) {
        return Mint.type("gem")
                .give((sender, target, args, io) -> {
                    int amount = 0;
                    if (args.length >= 4) {
                        try {
                            amount = Integer.parseInt(args[3]);
                        } catch (NumberFormatException bad) {
                            sender.sendMessage(io.messages().format("command.error.bad-number", "ARG", args[3]));
                            return;
                        }
                    }
                    io.deliver().to(sender, target, souls.mintGem(amount), "command.give.gem", "soul gem");
                })
                .self((sender, args, io) -> {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage(io.messages().format("command.not-a-player"));
                        return;
                    }
                    int amount = 0;
                    if (args.length >= 2) {
                        try {
                            amount = Math.max(0, Integer.parseInt(args[1]));
                        } catch (NumberFormatException bad) {
                            sender.sendMessage(io.messages().format("command.error.bad-number", "ARG", args[1]));
                            return;
                        }
                    }
                    int startingSouls = amount; // effectively-final for the entity-thread mint
                    Scheduling.onEntity(player, () -> {
                        ItemStack gem = souls.mintGem(startingSouls);
                        Inventories.giveOrDrop(player, gem);
                        player.sendMessage(io.messages().format("command.give.gem"));
                    });
                })
                .tiles(10, library -> List.of(new MintCatalog.Entry("soul gem", souls::mintGem)))
                .build();
    }

    /** {@code heroic} (alias {@code upgrade}) — the heroic upgrade item (heroic). */
    static Mintable heroic(HeroicService heroics) {
        return Mint.type("heroic").aliases("upgrade")
                .give((sender, target, args, io) ->
                        io.deliver().to(sender, target, heroics.mint(), "command.give.heroic", "heroic upgrade"))
                .self((sender, args, io) -> {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage(io.messages().format("command.not-a-player"));
                        return;
                    }
                    Scheduling.onEntity(player, () -> {
                        ItemStack upgrade = heroics.mint();
                        Inventories.giveOrDrop(player, upgrade);
                        player.sendMessage(io.messages().format("command.give.heroic"));
                    });
                })
                .tiles(30, library -> List.of(new MintCatalog.Entry("heroic upgrade", heroics::mint)))
                .build();
    }

    /** {@code orb} — the slot-expander orb (slots). */
    static Mintable orb(SlotService slots) {
        return Mint.type("orb")
                .give((sender, target, args, io) -> {
                    Integer pct = Give.optionalPercent(io.messages(), sender, args);
                    if (pct == null && args.length >= 4) {
                        return; // a bad number was supplied; optionalPercent messaged the sender
                    }
                    ItemStack orb = pct == null ? slots.mintOrb() : slots.mintOrb(pct);
                    io.deliver().to(sender, target, orb, "command.give.slot", "slot expander");
                })
                .self((sender, args, io) -> {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage(io.messages().format("command.not-a-player"));
                        return;
                    }
                    Scheduling.onEntity(player, () -> {
                        ItemStack item = slots.mintOrb();
                        Inventories.giveOrDrop(player, item);
                        player.sendMessage(io.messages().format("command.give.slot", "KIND", "slot expander"));
                    });
                })
                .tiles(20, library -> List.of(new MintCatalog.Entry("slot expander", slots::mintOrb)))
                .build();
    }

    /** {@code crystal} — a socketable crystal by key (crystals). */
    static Mintable crystal(CrystalService crystals, ContentHolder content) {
        return Mint.type("crystal")
                .give((sender, target, args, io) -> {
                    if (args.length < 4) {
                        sender.sendMessage(io.messages().format("command.crystal.usage"));
                        return;
                    }
                    String key = Give.normalize(args[3], "crystals/");
                    if (content.library().crystalDefOf(key) == null) {
                        sender.sendMessage(io.messages().format("command.error.no-such-crystal", "KEY", key));
                        return;
                    }
                    Scheduling.onEntity(target, () -> {
                        Inventories.giveOrDrop(target, crystals.mint(List.of(key)));
                        target.sendMessage(io.messages().format("command.give.crystal", "KEY", key));
                    });
                    if (Give.notSelf(sender, target)) {
                        Give.tell(sender, io.messages().format("command.give.delivered",
                                "ITEM", key, "PLAYER", target.getName()));
                    }
                })
                .self((sender, args, io) -> {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage(io.messages().format("command.not-a-player"));
                        return;
                    }
                    if (args.length < 2) {
                        sender.sendMessage(io.messages().format("command.crystal.usage"));
                        return;
                    }
                    String key = Give.normalize(args[1], "crystals/");
                    if (content.library().crystalDefOf(key) == null) {
                        player.sendMessage(io.messages().format("command.error.no-such-crystal", "KEY", key));
                        return;
                    }
                    Scheduling.onEntity(player, () -> {
                        ItemStack crystal = crystals.mint(List.of(key));
                        Inventories.giveOrDrop(player, crystal);
                        player.sendMessage(io.messages().format("command.give.crystal", "KEY", key));
                    });
                })
                .build();
    }

    /** {@code extractor} — the crystal extractor (crystals); no self row. */
    static Mintable extractor(CrystalService crystals) {
        return Mint.type("extractor")
                .give((sender, target, args, io) -> io.deliver().to(sender, target,
                        crystals.mintExtractor(), "command.give.extractor", "crystal extractor"))
                .tiles(40, library -> List.of(new MintCatalog.Entry("crystal extractor", crystals::mintExtractor)))
                .build();
    }

    /** {@code blackscroll} — the black scroll (scrolls). */
    static Mintable blackscroll(ScrollService scrolls) {
        return Mint.type("blackscroll")
                .give((sender, target, args, io) -> {
                    ItemStack scroll;
                    if (args.length >= 4) {
                        int pct;
                        try {
                            pct = Integer.parseInt(args[3]);
                        } catch (NumberFormatException bad) {
                            sender.sendMessage(io.messages().format("command.error.bad-number", "ARG", args[3]));
                            return;
                        }
                        scroll = scrolls.mintBlack(pct);
                    } else {
                        scroll = scrolls.mintBlack();
                    }
                    io.deliver().to(sender, target, scroll, "command.give.blackscroll", "black scroll");
                })
                .self((sender, args, io) -> {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage(io.messages().format("command.not-a-player"));
                        return;
                    }
                    Scheduling.onEntity(player, () -> {
                        ItemStack scroll = scrolls.mintBlack();
                        Inventories.giveOrDrop(player, scroll);
                        player.sendMessage(io.messages().format("command.give.blackscroll"));
                    });
                })
                .tiles(50, library -> List.of(new MintCatalog.Entry("black scroll", scrolls::mintBlack)))
                .build();
    }

    /** {@code randomizer} — the randomizer scroll (scrolls). */
    static Mintable randomizer(ScrollService scrolls) {
        return Mint.type("randomizer")
                .give((sender, target, args, io) -> io.deliver().to(sender, target,
                        scrolls.mintRandomizer(), "command.give.randomizer", "randomizer scroll"))
                .self((sender, args, io) -> {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage(io.messages().format("command.not-a-player"));
                        return;
                    }
                    Scheduling.onEntity(player, () -> {
                        ItemStack scroll = scrolls.mintRandomizer();
                        Inventories.giveOrDrop(player, scroll);
                        player.sendMessage(io.messages().format("command.give.randomizer"));
                    });
                })
                .tiles(60, library -> List.of(new MintCatalog.Entry("randomizer scroll", scrolls::mintRandomizer)))
                .build();
    }

    /** {@code transmog} — the transmog scroll (scrolls). */
    static Mintable transmog(ScrollService scrolls) {
        return Mint.type("transmog")
                .give((sender, target, args, io) -> io.deliver().to(sender, target,
                        scrolls.mintTransmog(), "command.give.transmog", "transmog scroll"))
                .self((sender, args, io) -> Give.giveSimpleItem(io.messages(), sender,
                        scrolls.mintTransmog(), io.messages().format("command.give.transmog")))
                .tiles(70, library -> List.of(new MintCatalog.Entry("transmog scroll", scrolls::mintTransmog)))
                .build();
    }

    /** {@code godlytransmog} — the godly transmog tool (scrolls); a give type with no give handler (usage). */
    static Mintable godlytransmog(ScrollService scrolls) {
        return Mint.type("godlytransmog")
                .give((sender, target, args, io) -> sender.sendMessage(io.messages().format("command.give.usage")))
                .self((sender, args, io) -> Give.giveSimpleItem(io.messages(), sender,
                        scrolls.mintGodlyTransmog(), io.messages().format("command.give.godlytransmog")))
                .tiles(80, library -> List.of(new MintCatalog.Entry("godly transmog", scrolls::mintGodlyTransmog)))
                .build();
    }

    /** {@code holy} — the holy white scroll (scrolls). */
    static Mintable holy(HolyScrollService holyScrolls) {
        return Mint.type("holy")
                .give((sender, target, args, io) -> io.deliver().to(sender, target,
                        holyScrolls.mint(), "command.give.holy", "holy white scroll"))
                .self((sender, args, io) -> Give.giveSimpleItem(io.messages(), sender,
                        holyScrolls.mint(), io.messages().format("command.give.holy")))
                .tiles(90, library -> List.of(new MintCatalog.Entry("holy white scroll", holyScrolls::mint)))
                .build();
    }

    /** {@code nametag} — the item nametag (scrolls). */
    static Mintable nametag(NametagService nametags) {
        return Mint.type("nametag")
                .give((sender, target, args, io) -> io.deliver().to(sender, target,
                        nametags.mint(), "command.give.nametag", "item nametag"))
                .self((sender, args, io) -> Give.giveSimpleItem(io.messages(), sender,
                        nametags.mint(), io.messages().format("command.give.nametag")))
                .tiles(100, library -> List.of(new MintCatalog.Entry("item nametag", nametags::mint)))
                .build();
    }

    /** {@code dust} — success dust (carriers). */
    static Mintable dust(CarrierService carriers) {
        return Mint.type("dust")
                .give((sender, target, args, io) -> {
                    OptionalInt percent = Give.dustPercent(args, 3);
                    ItemStack dust = percent.isPresent() ? carriers.mintDust(percent.getAsInt()) : carriers.mintDust();
                    io.deliver().to(sender, target, dust, "command.give.dust", "success dust");
                })
                .self((sender, args, io) -> {
                    OptionalInt percent = Give.dustPercent(args, 1);
                    ItemStack dust = percent.isPresent() ? carriers.mintDust(percent.getAsInt()) : carriers.mintDust();
                    Give.giveSimpleItem(io.messages(), sender, dust, io.messages().format("command.give.dust"));
                })
                .tiles(110, library -> List.of(new MintCatalog.Entry("success dust", carriers::mintDust)))
                .build();
    }

    /** {@code whitescroll} — the white scroll (carriers). */
    static Mintable whitescroll(CarrierService carriers) {
        return Mint.type("whitescroll")
                .give((sender, target, args, io) -> {
                    Integer pct = Give.optionalPercent(io.messages(), sender, args);
                    if (pct == null && args.length >= 4) {
                        return;
                    }
                    ItemStack scroll = pct == null ? carriers.mintWhiteScroll() : carriers.mintWhiteScroll(pct);
                    io.deliver().to(sender, target, scroll, "command.give.whitescroll", "white scroll");
                })
                .self((sender, args, io) -> Give.giveSimpleItem(io.messages(), sender,
                        carriers.mintWhiteScroll(), io.messages().format("command.give.whitescroll")))
                .tiles(120, library -> List.of(new MintCatalog.Entry("white scroll", carriers::mintWhiteScroll)))
                .build();
    }

    /** One trak gem ({@code blocktrak}/{@code mobtrak}/{@code soultrak}/{@code fishtrak}); no self row. */
    static Mintable trak(TrakService traks, TrakCodec.Kind kind) {
        String type = kind.name().toLowerCase(Locale.ROOT) + "trak";
        String label = type + " gem";
        return Mint.type(type)
                .give((sender, target, args, io) -> io.deliver().to(sender, target,
                        traks.mint(kind), "command.give.trak", label))
                .tiles(130, library -> List.of(new MintCatalog.Entry(label, () -> traks.mint(kind))))
                .build();
    }

    /** {@code book} — an enchant book by key, or a concrete random-tier book (carriers + unopened books). */
    static Mintable book(CarrierService carriers, UnopenedBookService unopenedBooks, ContentHolder content) {
        return Mint.type("book")
                .give((sender, target, args, io) -> {
                    if (args.length < 4) {
                        sender.sendMessage(io.messages().format("command.book.usage"));
                        return;
                    }
                    if ("random".equalsIgnoreCase(args[3])) {
                        giveRandomBookTo(unopenedBooks, content, sender, target, args, io);
                        return;
                    }
                    String key = Give.normalize(args[3], "enchants/");
                    EnchantDef def = content.library().enchantDefOf(key);
                    if (def == null) {
                        sender.sendMessage(io.messages().format("command.error.no-such-enchant", "KEY", key));
                        return;
                    }
                    int level = 1;
                    if (args.length >= 5) {
                        try {
                            level = Integer.parseInt(args[4]);
                        } catch (NumberFormatException bad) {
                            sender.sendMessage(io.messages().format("command.error.bad-level", "ARG", args[4]));
                            return;
                        }
                    }
                    if (level < 1 || level > def.maxLevel()) {
                        sender.sendMessage(io.messages().format("command.error.level-range",
                                "MAX", def.maxLevel(), "KEY", key));
                        return;
                    }
                    Integer success = null;
                    if (args.length >= 6) {
                        try {
                            success = Integer.parseInt(args[5]);
                        } catch (NumberFormatException bad) {
                            sender.sendMessage(io.messages().format("command.error.bad-number", "ARG", args[5]));
                            return;
                        }
                    }
                    int bookLevel = level;
                    Integer bookSuccess = success;
                    Scheduling.onEntity(target, () -> {
                        ItemStack book = bookSuccess == null ? carriers.mintBook(key, bookLevel)
                                : carriers.mintBook(key, bookLevel, bookSuccess);
                        Inventories.giveOrDrop(target, book);
                        target.sendMessage(io.messages().format("command.give.book", "KEY", key, "LEVEL", bookLevel));
                    });
                    if (Give.notSelf(sender, target)) {
                        Give.tell(sender, io.messages().format("command.give.delivered",
                                "ITEM", key, "PLAYER", target.getName()));
                    }
                })
                .self((sender, args, io) -> {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage(io.messages().format("command.not-a-player"));
                        return;
                    }
                    if (args.length < 2) {
                        sender.sendMessage(io.messages().format("command.book.usage"));
                        return;
                    }
                    String key = Give.normalize(args[1], "enchants/");
                    EnchantDef def = content.library().enchantDefOf(key);
                    if (def == null) {
                        player.sendMessage(io.messages().format("command.error.no-such-enchant", "KEY", key));
                        return;
                    }
                    int level = 1;
                    if (args.length >= 3) {
                        try {
                            level = Integer.parseInt(args[2]);
                        } catch (NumberFormatException bad) {
                            sender.sendMessage(io.messages().format("command.error.bad-level", "ARG", args[2]));
                            return;
                        }
                    }
                    if (level < 1 || level > def.maxLevel()) {
                        player.sendMessage(io.messages().format("command.error.level-range",
                                "MAX", def.maxLevel(), "KEY", key));
                        return;
                    }
                    int bookLevel = level;
                    Scheduling.onEntity(player, () -> {
                        ItemStack book = carriers.mintBook(key, bookLevel);
                        Inventories.giveOrDrop(player, book);
                        player.sendMessage(io.messages().format("command.give.book", "KEY", key, "LEVEL", bookLevel));
                    });
                })
                .build();
    }

    /** {@code /se give book <player> random <tier>} — mint a CONCRETE random enchant book from that tier. */
    private static void giveRandomBookTo(UnopenedBookService unopenedBooks, ContentHolder content,
            org.bukkit.command.CommandSender sender, Player target, String[] args, feature.menu.MintIo io) {
        if (args.length < 5) {
            sender.sendMessage(io.messages().format("command.book.usage"));
            return;
        }
        String tier = args[4];
        if (!content.library().tiers().isTier(tier)) {
            sender.sendMessage(io.messages().format("command.error.no-such-tier", "TIER", tier));
            return;
        }
        Optional<ItemStack> rolled = unopenedBooks.roll(tier);
        if (rolled.isEmpty()) {
            sender.sendMessage(io.messages().format("command.error.no-such-tier", "TIER", tier)); // valid tier, but empty
            return;
        }
        ItemStack book = rolled.get();
        Scheduling.onEntity(target, () -> {
            Inventories.giveOrDrop(target, book);
            target.sendMessage(io.messages().format("command.give.book", "KEY", tier, "LEVEL", 0));
        });
        if (Give.notSelf(sender, target)) {
            Give.tell(sender, io.messages().format("command.give.delivered",
                    "ITEM", "random " + tier + " book", "PLAYER", target.getName()));
        }
    }

    /** {@code unopened} — a tier-scoped unopened book (books). */
    static Mintable unopened(UnopenedBookService unopenedBooks, ContentHolder content) {
        return Mint.type("unopened")
                .give((sender, target, args, io) -> {
                    if (args.length < 4) {
                        sender.sendMessage(io.messages().format("command.unopened.usage"));
                        return;
                    }
                    String tier = args[3];
                    if (!content.library().tiers().isTier(tier)) {
                        sender.sendMessage(io.messages().format("command.error.no-such-tier", "TIER", tier));
                        return;
                    }
                    Scheduling.onEntity(target, () -> {
                        Inventories.giveOrDrop(target, unopenedBooks.mint(tier));
                        target.sendMessage(io.messages().format("command.give.unopened", "TIER", tier));
                    });
                    if (Give.notSelf(sender, target)) {
                        Give.tell(sender, io.messages().format("command.give.delivered",
                                "ITEM", tier, "PLAYER", target.getName()));
                    }
                })
                .self((sender, args, io) -> {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage(io.messages().format("command.not-a-player"));
                        return;
                    }
                    if (args.length < 2) {
                        sender.sendMessage(io.messages().format("command.unopened.usage"));
                        return;
                    }
                    String tier = args[1];
                    if (!content.library().tiers().isTier(tier)) {
                        player.sendMessage(io.messages().format("command.error.no-such-tier", "TIER", tier));
                        return;
                    }
                    Scheduling.onEntity(player, () -> {
                        ItemStack book = unopenedBooks.mint(tier);
                        Inventories.giveOrDrop(player, book);
                        player.sendMessage(io.messages().format("command.give.unopened", "TIER", tier));
                    });
                })
                .tiles(140, library -> {
                    List<MintCatalog.Entry> out = new java.util.ArrayList<>();
                    for (compile.load.TierRegistry.Tier tier : library.tiers().tiers()) {
                        String name = tier.name();
                        out.add(new MintCatalog.Entry(name + " unopened book", () -> unopenedBooks.mint(name)));
                    }
                    return out;
                })
                .build();
    }

    /** {@code pet} — a pet head by key, optional starting level (ADR-0052); no self row. */
    static Mintable pet(feature.pet.PetService pets, ContentHolder content) {
        return Mint.type("pet").aliases("pets")
                .give((sender, target, args, io) -> {
                    if (args.length < 4) {
                        sender.sendMessage(io.messages().format("command.pet.usage"));
                        return;
                    }
                    String key = args[3]; // the codec stores the bare filename stem — no source-prefix normalise
                    if (content.library().petDefOf(key) == null) {
                        sender.sendMessage(io.messages().format("command.error.no-such-pet", "KEY", key));
                        return;
                    }
                    int level = 1;
                    if (args.length >= 5) {
                        try {
                            level = Math.max(1, Integer.parseInt(args[4]));
                        } catch (NumberFormatException bad) {
                            sender.sendMessage(io.messages().format("command.error.bad-number", "ARG", args[4]));
                            return;
                        }
                    }
                    int at = level;
                    Scheduling.onEntity(target, () -> {
                        ItemStack item = pets.mint(key, at);
                        if (item != null) {
                            Inventories.giveOrDrop(target, item);
                        }
                        target.sendMessage(io.messages().format("command.give.pet", "KEY", key));
                    });
                    if (Give.notSelf(sender, target)) {
                        Give.tell(sender, io.messages().format("command.give.delivered",
                                "ITEM", key, "PLAYER", target.getName()));
                    }
                })
                .tiles(160, library -> {
                    List<MintCatalog.Entry> out = new java.util.ArrayList<>();
                    for (compile.load.PetDef def : library.pets()) {
                        String defKey = def.key();
                        out.add(new MintCatalog.Entry(defKey + " pet", () -> pets.mint(defKey, 1)));
                    }
                    return out;
                })
                .build();
    }

    /** {@code petfood} (alias {@code pet-food}) — the +levels apply item (ADR-0052); no self row. */
    static Mintable petFood(feature.pet.PetService pets) {
        return Mint.type("petfood").aliases("pet-food")
                .give((sender, target, args, io) -> {
                    int amount = 1;
                    if (args.length >= 4) {
                        try {
                            amount = Math.max(1, Integer.parseInt(args[3]));
                        } catch (NumberFormatException bad) {
                            sender.sendMessage(io.messages().format("command.error.bad-number", "ARG", args[3]));
                            return;
                        }
                    }
                    int give = amount;
                    Scheduling.onEntity(target, () -> {
                        ItemStack item = pets.mintFood();
                        item.setAmount(give);
                        Inventories.giveOrDrop(target, item);
                        target.sendMessage(io.messages().format("command.give.petfood"));
                    });
                    if (Give.notSelf(sender, target)) {
                        Give.tell(sender, io.messages().format("command.give.delivered",
                                "ITEM", "pet food", "PLAYER", target.getName()));
                    }
                })
                .tiles(161, library -> List.of(new MintCatalog.Entry("pet food", pets::mintFood)))
                .build();
    }

    /** {@code useitem} (alias {@code use-item}) — a right-click use-item by key (§3.6); no self row. */
    static Mintable useItem(UseItemService useItems, ContentHolder content) {
        return Mint.type("useitem").aliases("use-item")
                .give((sender, target, args, io) -> {
                    if (args.length < 4) {
                        sender.sendMessage(io.messages().format("command.useitem.usage"));
                        return;
                    }
                    String key = args[3]; // the codec stores the bare filename stem — no source-prefix normalise
                    if (content.library().useItemDefOf(key) == null) {
                        sender.sendMessage(io.messages().format("command.error.no-such-useitem", "KEY", key));
                        return;
                    }
                    int amount = 1;
                    if (args.length >= 5) {
                        try {
                            amount = Math.max(1, Integer.parseInt(args[4]));
                        } catch (NumberFormatException bad) {
                            sender.sendMessage(io.messages().format("command.error.bad-number", "ARG", args[4]));
                            return;
                        }
                    }
                    int give = amount;
                    Scheduling.onEntity(target, () -> {
                        ItemStack item = useItems.mint(key);
                        if (item != null) {
                            item.setAmount(give);
                            Inventories.giveOrDrop(target, item);
                        }
                        target.sendMessage(io.messages().format("command.give.useitem", "KEY", key));
                    });
                    if (Give.notSelf(sender, target)) {
                        Give.tell(sender, io.messages().format("command.give.delivered",
                                "ITEM", key, "PLAYER", target.getName()));
                    }
                })
                .tiles(150, library -> {
                    List<MintCatalog.Entry> out = new java.util.ArrayList<>();
                    for (UseItemDef def : library.useItems()) {
                        String defKey = def.key();
                        out.add(new MintCatalog.Entry(defKey + " use-item", () -> useItems.mint(defKey)));
                    }
                    return out;
                })
                .build();
    }

    /** {@code set} — a declared set member (sets); no self row, no tile. */
    static Mintable set(ItemEnchanter enchanter, ContentHolder content) {
        return Mint.type("set")
                .give((sender, target, args, io) -> {
                    if (args.length < 5) {
                        sender.sendMessage(io.messages().format("command.set.usage"));
                        return;
                    }
                    String key = Give.normalize(args[3], "sets/");
                    if (content.library().setDefOf(key) == null) {
                        sender.sendMessage(io.messages().format("command.error.no-such-set", "KEY", key));
                        return;
                    }
                    String piece = args[4];
                    Optional<ItemStack> minted = enchanter.mintSetPiece(key, piece);
                    if (minted.isEmpty()) {
                        sender.sendMessage(io.messages().format("command.give.set-piece", "PIECE", piece, "KEY", key));
                        return;
                    }
                    ItemStack item = minted.get();
                    Scheduling.onEntity(target, () -> {
                        Inventories.giveOrDrop(target, item);
                        target.sendMessage(io.messages().format("command.give.set", "KEY", key, "PIECE", piece));
                    });
                    if (Give.notSelf(sender, target)) {
                        Give.tell(sender, io.messages().format("command.give.delivered",
                                "ITEM", key + " " + piece, "PLAYER", target.getName()));
                    }
                })
                .build();
    }
}
