package bootstrap.wire;

import feature.menu.GodlyTransmogMenu;
import feature.scroll.HolyScrollListener;
import feature.scroll.HolyScrollService;
import feature.scroll.KeptItemsStore;
import feature.scroll.NametagListener;
import feature.scroll.NametagService;
import feature.scroll.ScrollListener;
import feature.scroll.ScrollService;
import item.codec.GodlyTransmogCodec;
import item.codec.ItemKeys;
import item.codec.ScrollCodec;

/**
 * Book-economy + cosmetic scrolls (§I, ADR-0047): black/randomizer/transmog/godly scrolls, the holy white
 * scroll, and the item nametag. BOOT-gated on {@code features.scrolls}. The kept-items stash and the pending
 * nametag capture are declared player stores swept by the fold's quit authority (retiring their own onQuit
 * handlers). The godly-transmog menu is hoisted here so the menus module's gesture listener can bind it.
 */
final class ScrollsModule {

    private final BootCore core;
    final ScrollService scrolls;
    final HolyScrollService holyScrolls;
    final NametagService nametags;
    final KeptItemsStore keptItems;
    final GodlyTransmogMenu transmogMenu;

    ScrollsModule(BootCore core, CarriersModule carriers) {
        this.core = core;
        ScrollCodec scrollCodec = new ScrollCodec(ItemKeys.of().scroll(), ItemKeys.of().scrollConvert(), core.store());
        GodlyTransmogCodec godlyTransmogCodec = new GodlyTransmogCodec(ItemKeys.of().godlyTransmog(), core.store());
        this.scrolls = new ScrollService(scrollCodec, core.codec(), core.lore(), carriers.carriers, core.content(),
                () -> core.items().config().scrollsOrDefault(), core.rolls(), core.messages(), godlyTransmogCodec,
                core.itemGroups());
        this.holyScrolls = new HolyScrollService(scrollCodec, core.appliedSlot(),
                () -> core.items().config().scrollsOrDefault(), core.rolls(), core.messages(), core.recompose(),
                core.itemGroups());
        this.keptItems = new KeptItemsStore(); // §I holy death→respawn stash
        this.nametags = new NametagService(scrollCodec, () -> core.items().config().scrollsOrDefault(),
                core.messages(), core.codec()); // §I codec → re-append the enchant-count suffix on rename + preview
        // Hoisted so the physical godly-transmog gesture listener can open it bound to a clicked piece (§I/§K).
        this.transmogMenu = new GodlyTransmogMenu(core.content(), core.codec(), scrolls, core.caps(),
                core.menusHolder()::config, core.hands(), core.vanillaEnchants());
    }

    FeatureModule module() {
        return FeatureModule.named("scrolls")
                .toggle(Toggle.boot("features.scrolls",
                        () -> core.master().config().features().scrolls(),
                        "scrolls feature disabled (config.yml features.scrolls) — scroll listeners not registered"))
                .events(new ScrollListener(scrolls, core.messages(), core.sounds()))
                .events(new HolyScrollListener(holyScrolls, keptItems, core.messages(), core.sounds()))
                .events(new NametagListener(nametags, core.messages(), core.sounds(), core.anvilRename()))
                // modern: colour the anvil result preview (no-op on 1.8.9).
                .install("anvil rename preview", () -> core.anvilRename().installPreview(core.plugin(), nametags))
                .store(keptItems)
                .store(nametags)
                .menu(90, transmogMenu)
                .pluginItem(stack -> scrolls.isScroll(stack) || scrolls.isGodlyTransmog(stack)
                        || holyScrolls.isHolyScroll(stack) || nametags.isNametag(stack))
                .lang("scroll", "command.give.blackscroll", "command.give.randomizer", "command.give.transmog",
                        "command.give.godlytransmog", "command.give.holy", "command.give.nametag")
                .build();
    }
}
