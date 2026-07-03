package bootstrap.wire;

import feature.crystal.CrystalListener;
import feature.crystal.CrystalService;
import feature.menu.CrystalsBrowserMenu;
import item.codec.CrystalExtractorCodec;
import item.codec.CrystalItemCodec;
import item.codec.ItemKeys;

/**
 * Physical crystal items (§E, ADR-0047): a multi-crystal is one crystal-slot entry encoding "a+b". The
 * {@code features.crystals} toggle is LIVE (the listener registers regardless; the actual gating is per
 * worn-resolve), now DECLARED rather than an accident of where the boolean is read.
 */
final class CrystalsModule {

    private final BootCore core;
    final CrystalService crystals;

    CrystalsModule(BootCore core) {
        this.core = core;
        CrystalItemCodec crystalItemCodec = new CrystalItemCodec(ItemKeys.of().crystalItem(), core.store());
        CrystalExtractorCodec crystalExtractorCodec =
                new CrystalExtractorCodec(ItemKeys.of().crystalExtractor(), core.store());
        this.crystals = new CrystalService(crystalItemCodec, crystalExtractorCodec, core.enchanter(), core.content(),
                () -> core.items().config().crystalOrDefault(), () -> core.master().config().crystals().maxMerge(),
                core.messages());
    }

    FeatureModule module() {
        return FeatureModule.named("crystals")
                .toggle(Toggle.live("features.crystals", () -> core.master().config().features().crystals()))
                .events(new CrystalListener(crystals, core.messages(), core.sounds()))
                .menu(70, new CrystalsBrowserMenu(core.content(), crystals, core.caps(), core.messages(),
                        core.menusHolder()::config, core.vanillaEnchants()))
                .pluginItem(stack -> crystals.isCrystal(stack) || crystals.isExtractor(stack))
                .lang("crystal", "command.give.crystal", "command.give.extractor")
                .build();
    }
}
