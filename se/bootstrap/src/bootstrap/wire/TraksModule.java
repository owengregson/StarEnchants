package bootstrap.wire;

import feature.menu.Mintable;
import feature.trak.TrakListener;
import feature.trak.TrakService;
import item.codec.TrakCodec;
import java.util.ArrayList;
import java.util.List;

/**
 * Trak gems (§I, ADR-0047): block/mob/soul/fish lifetime counters tracked in the background on eligible gear.
 * Shares the {@code features.scrolls} BOOT gate but with an EMPTY disabled log — the scrolls module logs once
 * for the whole block, so traks skips silently (the fold suppresses an empty disabled log).
 */
final class TraksModule {

    private final BootCore core;
    final TrakService traks;
    final List<Mintable> mints;

    TraksModule(BootCore core) {
        this.core = core;
        this.traks = new TrakService(core.trakCodec(), core.appliedSlot(), core.itemGroups(),
                () -> core.items().config().traksOrDefault(), core.messages(), core.recompose(), core.hands());
        // One Mintable per trak kind, in Kind order (blocktrak/mobtrak/soultrak/fishtrak) — all tile-rank 130.
        List<Mintable> trakMints = new ArrayList<>();
        for (TrakCodec.Kind kind : TrakCodec.Kind.values()) {
            trakMints.add(Mints.trak(traks, kind));
        }
        this.mints = List.copyOf(trakMints);
    }

    FeatureModule module() {
        return FeatureModule.named("traks")
                .toggle(Toggle.boot("features.scrolls",
                        () -> core.master().config().features().scrolls(), ""))
                .events(new TrakListener(traks, core.messages(), core.sounds()))
                .mints(mints)
                .pluginItem(traks::isTrakGem)
                .lang("trak", "command.give.trak")
                .build();
    }
}
