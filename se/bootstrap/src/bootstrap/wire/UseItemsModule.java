package bootstrap.wire;

import feature.menu.Mintable;
import feature.useitem.UseItemListener;
import feature.useitem.UseItemService;
import item.codec.ItemKeys;
import item.codec.UseItemCodec;
import java.util.List;

/**
 * Right-click use-items (§3.6, ADR-0047, docs/decisions/0048-use-items.md): a content-family item whose
 * abilities fire through the shared pipeline on right-click. The {@code features.use-items} toggle is LIVE (the
 * listener registers regardless and short-circuits on the flag) — a use-item is a content item, so like crystals
 * the gating is read at use time rather than being an accident of where the boolean is read. No menu: use-items
 * are minted from the operator mint console, not browsed.
 */
final class UseItemsModule {

    private final BootCore core;
    final UseItemService useItems;
    final List<Mintable> mints;

    UseItemsModule(BootCore core) {
        this.core = core;
        UseItemCodec codec = new UseItemCodec(ItemKeys.of().useItem(), core.store());
        // The cold-path run reuses the shared TriggerDispatch (its runner/sink wiring), so no second engine spine.
        this.useItems = new UseItemService(core.content(), codec, core.triggerDispatch(), core.vanillaEnchants());
        this.mints = List.of(Mints.useItem(useItems, core.content()));
    }

    FeatureModule module() {
        return FeatureModule.named("use-items")
                .toggle(Toggle.live("features.use-items", () -> core.master().config().features().useItems()))
                .events(new UseItemListener(useItems, core.messages(), core.hands(),
                        () -> core.master().config().features().useItems()))
                .mints(mints)
                .pluginItem(useItems::isUseItem)
                .lang("use-item", "command.give.useitem")
                .build();
    }
}
