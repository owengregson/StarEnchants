package bootstrap.wire;

import feature.crystal.CrystalListener;
import feature.crystal.CrystalService;
import feature.menu.CrystalsBrowserMenu;
import feature.menu.Mintable;
import item.codec.CrystalExtractorCodec;
import item.codec.CrystalItemCodec;
import item.codec.ItemKeys;
import java.util.List;

/**
 * Physical crystal items (§E, ADR-0047): a multi-crystal is one crystal-slot entry encoding "a+b". The
 * {@code features.crystals} toggle is LIVE (the listener registers regardless; the actual gating is per
 * worn-resolve), now DECLARED rather than an accident of where the boolean is read.
 */
final class CrystalsModule {

    private final BootCore core;
    final CrystalService crystals;
    final List<Mintable> mints;

    CrystalsModule(BootCore core, feature.crystal.ReforgeExtractor reforges) {
        this.core = core;
        CrystalItemCodec crystalItemCodec = new CrystalItemCodec(ItemKeys.of().crystalItem(), core.store());
        CrystalExtractorCodec crystalExtractorCodec =
                new CrystalExtractorCodec(ItemKeys.of().crystalExtractor(), core.store());
        // ADR-0070: the ONE Item Extractor pops a reforge FIRST via this hook, then crystals — the reforge
        // module owns the reforge-side economy while the extractor cursor stays claimed by CrystalListener alone.
        this.crystals = new CrystalService(crystalItemCodec, crystalExtractorCodec, core.enchanter(), core.content(),
                () -> core.items().config().crystalOrDefault(), () -> core.master().config().crystals().maxMerge(),
                reforges, core.messages());
        this.mints = List.of(Mints.crystal(crystals, core.content()), Mints.extractor(crystals));
    }

    FeatureModule module() {
        return FeatureModule.named("crystals")
                .toggle(Toggle.live("features.crystals", () -> core.master().config().features().crystals()))
                .events(new CrystalListener(crystals, core.messages(), core.sounds()))
                .menu(70, new CrystalsBrowserMenu(core.content(), crystals, core.caps(), core.messages(),
                        core.menusHolder()::config, core.vanillaEnchants()))
                .mints(mints)
                .pluginItem(stack -> crystals.isCrystal(stack) || crystals.isExtractor(stack))
                .lang("crystal", "command.give.crystal", "command.give.extractor")
                .build();
    }
}
