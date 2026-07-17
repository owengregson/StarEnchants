package bootstrap.wire;

import engine.stores.WardStore;
import feature.guard.IllusionCanonGuard;
import feature.mask.InvseeGuard;
import feature.mask.MaskBreakSalvage;
import feature.mask.MaskIllusionListener;
import feature.mask.MaskIllusionService;
import feature.mask.MaskIllusionStore;
import feature.mask.MaskListener;
import feature.mask.MaskProvocationStore;
import feature.mask.MaskRemoveListener;
import feature.mask.MaskService;
import org.bukkit.event.Listener;
import feature.mask.MobTargetGuard;
import feature.mask.NearGuard;
import feature.mask.SplashHealGuard;
import feature.menu.Mintable;
import item.head.IllusionMark;
import java.util.List;
import java.util.function.BooleanSupplier;
import platform.sched.Scheduling;

/**
 * Masks (ADR-0053): a helmet-only applicable head-likeness content family on the shared engine. The
 * {@code features.masks} toggle is LIVE (listeners register regardless and short-circuit on the flag — a mask is
 * a content item, the crystal/pets rule). Wires the apply gesture, the cursor-less remove gesture, the worn-
 * illusion re-assertion (join/respawn/teleport plus the equip-refresh seam), the four ward guards (mob-target
 * calm, /invsee shield, owned /near, splash-heal boost), the 2s illusion sweep, and the mask mint. The illusion +
 * provocation stores are per-player state cleared by the quit sweep and dropped on disable; the mask wards live
 * in the shared {@code EngineStores}, so they are NOT re-declared here.
 */
final class MasksModule {

    // 2s — bounds the tracker-flash window (ADR-0053 §5): vanilla re-broadcasts true equipment when an observer
    // starts tracking a masked wearer (1.8 on its own cadence), so a short sweep re-asserts the override.
    private static final long SWEEP_TICKS = 40L;

    private final BootCore core;
    final MaskService masks;
    final List<Mintable> mints;

    private final IllusionMark illusionMark;
    private final IllusionCanonGuard canonGuard;
    private final MaskIllusionStore illusionStore;
    private final MaskProvocationStore provocation;
    private final MaskIllusionService illusion;
    private final MaskListener applyListener;
    private final MaskRemoveListener removeListener;
    private final Listener breakListener; // era seam: modern PlayerItemDamageEvent break-salvage, inert on 1.8
    private final MaskIllusionListener illusionListener;
    private final MobTargetGuard mobTargetGuard;
    private final InvseeGuard invseeGuard;
    private final NearGuard nearGuard;
    private final SplashHealGuard splashHealGuard;

    MasksModule(BootCore core, EquipModule equip) {
        this.core = core;
        BooleanSupplier enabled = enabled();
        // The mask item economy: mint / apply-onto-helmet / pop-off, over the ONE shared codec + textured-head seam.
        this.masks = new MaskService(core.maskCodec(), core.enchanter(), core.content(),
                () -> core.items().config().maskOrDefault(), core.bindings().texturedHeads(),
                core.bindings().headEquip(), core.messages());
        this.applyListener = new MaskListener(masks, core.messages(), core.sounds());
        this.removeListener = new MaskRemoveListener(masks, core.codec(), core.messages(), core.sounds(), enabled);
        // A spent masked helmet breaks whole (like vanilla) and pops the mask back off intact, into the wearer's
        // inventory or dropped — never destroyed with the helmet (ADR-0053 follow-up). The break is era-detected:
        // modern PlayerItemDamageEvent, 1.8 inert for now. The listener is always-on like the guard family: a
        // lingering masked helmet still yields its mask on break even with masks toggled off.
        this.breakListener = core.bindings().maskBreakSource(core.codec(), new MaskBreakSalvage(core.codec(), masks::mint));

        // The worn illusion (ADR-0053 §5): snapshot-out, recipient-thread-in. It re-derives on the SAME player-thread
        // refresh that re-resolves worn state — refresh() runs on the player's own region thread, exactly
        // MaskIllusionService.refresh's contract — so the equip-refresh seam feeds it directly (no extra listener).
        this.illusionStore = new MaskIllusionStore();
        // ADR-0064: the shown head is stamped (real helmet as payload) so a client write-back is detectable
        // and losslessly reversible everywhere; the canon guard denies/undresses at the inventory gates.
        this.illusionMark = new IllusionMark(item.codec.ItemKeys.of(), core.store(), core.bindings().itemBytes());
        this.canonGuard = new IllusionCanonGuard(illusionMark);
        this.illusion = new MaskIllusionService(core.bindings().equipmentRepaint(), core.bindings().texturedHeads(),
                core.bindings().headAttributes(), illusionMark, () -> core.content().library(), core.itemViews(),
                enabled, illusionStore);
        equip.onRefresh(illusion::refresh);
        this.illusionListener = new MaskIllusionListener(illusion, enabled);

        // The four ward guards read the shared WardStore the WARD effect arms; provocation is a per-wearer TTL store.
        this.provocation = new MaskProvocationStore();
        WardStore wards = core.stores().ward();
        this.mobTargetGuard = new MobTargetGuard(wards, provocation, core.tick()::get);
        this.invseeGuard = new InvseeGuard(wards, core.tick()::get, core.messages());
        this.nearGuard = new NearGuard(wards, core.tick()::get, core.messages(),
                () -> core.master().config().masks(), enabled);
        this.splashHealGuard = new SplashHealGuard(wards, core.tick()::get);

        this.mints = List.of(Mints.mask(masks, core.content()));
    }

    private BooleanSupplier enabled() {
        return () -> core.master().config().features().masks();
    }

    FeatureModule module() {
        BooleanSupplier enabled = enabled();
        return FeatureModule.named("masks")
                .toggle(Toggle.live("features.masks", enabled))
                .events(canonGuard)
                .events(breakListener)
                .events(applyListener)
                .events(removeListener)
                .events(illusionListener)
                .events(mobTargetGuard)
                .events(invseeGuard)
                .events(nearGuard)
                .events(splashHealGuard)
                .menu(76, new feature.menu.MasksBrowserMenu(core.content(), masks, core.caps(), core.messages(),
                        core.menusHolder()::config, core.vanillaEnchants()))
                .mints(mints)
                .store(illusionStore)  // per-player shown-head cache (WardStore is EngineStores-owned, not here)
                .store(provocation)    // per-wearer mob provocations
                .pluginItem(stack -> core.maskCodec().isMask(stack))
                .lang("mask", "command.mask", "command.give.mask", "command.error.no-such-mask")
                // The 2s illusion sweep: a dumb push of stored state. sweep() itself enumerates online players and
                // fans out per-recipient via Scheduling.onEntity, so it MUST run on the global thread — which
                // repeatingGlobal provides (ADR-0053 §5).
                .boot(() -> Scheduling.repeatingGlobal(SWEEP_TICKS, SWEEP_TICKS, illusion::sweep))
                .stop("mask illusions", illusionStore::clearAll)
                .stop("mask provocations", provocation::clearAll)
                .build();
    }
}
