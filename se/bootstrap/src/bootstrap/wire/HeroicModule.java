package bootstrap.wire;

import feature.heroic.HeroicListener;
import feature.heroic.HeroicService;
import item.codec.HeroicUpgradeCodec;
import item.codec.ItemKeys;

/**
 * Heroic upgrades (§F, ADR-0047). The item-damage durability save listener rides here, right after the heroic
 * listener (moved from its old tail position — proven order-insensitive: it handles only item-damage, disjoint
 * from every listener it now precedes; the DURABILITY trigger listener still fires before it cancels the event).
 */
final class HeroicModule {

    private final BootCore core;
    final HeroicService heroics;

    HeroicModule(BootCore core) {
        this.core = core;
        HeroicUpgradeCodec heroicCodec = new HeroicUpgradeCodec(ItemKeys.of().heroicUpgrade(), core.store());
        this.heroics = new HeroicService(heroicCodec, core.codec(), core.lore(),
                () -> core.items().config().heroicOrDefault(), core.rolls(), core.messages(), core.itemGroups(),
                core.bindings().vanillaStats());
    }

    FeatureModule module() {
        return FeatureModule.named("heroic")
                .toggle(Toggle.live("features.heroic", () -> core.master().config().features().heroic()))
                .events(new HeroicListener(heroics, core.messages(), core.sounds()))
                // Heroic durability (§F): the modern per-event save; on 1.8 an inert listener (the gear poll restores).
                .events(core.bindings().heroicDurabilitySave(core.codec(), core.rolls()))
                .pluginItem(heroics::isUpgrade)
                .lang("heroic", "command.give.heroic")
                .build();
    }
}
