package bootstrap.wire;

import feature.trak.TrakListener;
import feature.trak.TrakService;

/**
 * Trak gems (§I, ADR-0047): block/mob/soul/fish lifetime counters tracked in the background on eligible gear.
 * Shares the {@code features.scrolls} BOOT gate but with an EMPTY disabled log — the scrolls module logs once
 * for the whole block, so traks skips silently (the fold suppresses an empty disabled log).
 */
final class TraksModule {

    private final BootCore core;
    final TrakService traks;

    TraksModule(BootCore core) {
        this.core = core;
        this.traks = new TrakService(core.trakCodec(), core.appliedSlot(), core.itemGroups(),
                () -> core.items().config().traksOrDefault(), core.messages(), core.recompose(), core.hands());
    }

    FeatureModule module() {
        return FeatureModule.named("traks")
                .toggle(Toggle.boot("features.scrolls",
                        () -> core.master().config().features().scrolls(), ""))
                .events(new TrakListener(traks, core.messages(), core.sounds()))
                .pluginItem(traks::isTrakGem)
                .lang("trak", "command.give.trak")
                .build();
    }
}
