package bootstrap.wire;

import feature.combat.ReforgeStrikeListener;
import feature.combat.ReforgeTempoGuardListener;
import feature.menu.Mintable;
import feature.reforge.CastlingService;
import feature.reforge.GrappleService;
import feature.reforge.GravityWellService;
import feature.reforge.JavelinService;
import feature.reforge.ReforgeListener;
import feature.reforge.ReforgeMachines;
import feature.reforge.ReforgeMessenger;
import feature.reforge.ReforgeRunner;
import feature.reforge.ReforgeService;
import feature.reforge.ReforgeUseListener;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Weapon Reforges (ADR-0070/0071): a weapon-socket content family on the shared engine — one reforge per weapon,
 * applied by drag gesture, its signature ability fired by SHIFT + right-click while held, removed by the ONE
 * Item Extractor (crystal-module-owned; this module hands it the {@code ReforgeExtractor} hook). The
 * {@code features.reforges} toggle is LIVE (the crystal/mask rule): listeners register regardless; the worn
 * resolver + use listener short-circuit on the flag, and an apply onto gear is a harmless content mutation.
 *
 * <p>The five movement/space kinds are service-owned MARKERS (ADR-0071 Plan B): their effect {@code run} bodies
 * emit nothing, so {@link ReforgeMachines} — routed by {@link ReforgeRunner} at the ACTIVATED outcome — starts
 * them. Gravity-well / castling / javelin hold live per-player state (dropped on quit + disable); GRAPPLE and
 * BLINK are stateless. The two combat-state listeners (ADR-0071 Plan C) ride the shared {@code EngineStores}, so
 * they declare no module store — only their listener registrations live here, after the use listener.
 */
final class ReforgesModule {

    private final BootCore core;
    final ReforgeService reforges;
    final List<Mintable> mints;

    private final ReforgeListener applyListener;
    private final ReforgeUseListener useListener;
    private final ReforgeStrikeListener strikeListener;
    private final ReforgeTempoGuardListener tempoGuardListener;

    ReforgesModule(BootCore core) {
        this.core = core;
        BooleanSupplier enabled = enabled();
        this.reforges = new ReforgeService(core.reforgeCodec(), core.codec(), core.enchanter(), core.content(),
                () -> core.items().config().reforgeOrDefault(),
                () -> core.master().config().reforges().weaponGroups(), core.messages(), core.vanillaEnchants());
        this.applyListener = new ReforgeListener(reforges, core.messages(), core.sounds());

        // ADR-0071 Plan B: the four service-owned movement/space machines + the dispatcher the runner hands the
        // ACTIVATED def to. Raycasts ride the era-split targetBlock/targetEntity refs (composition-only seam); the
        // Javelin swing reads the WeaponDamage era leaf.
        bootstrap.compat.EraServices bindings = core.bindings();
        GravityWellService gravityWell = new GravityWellService(core.triggerDispatch(), bindings::targetBlock,
                core.resolvers());
        GrappleService grapple = new GrappleService(core.triggerDispatch(), bindings::targetEntity,
                bindings::targetBlock, core.messages());
        CastlingService castling = new CastlingService(core.triggerDispatch(), bindings::targetEntity,
                core.messages(), core.resolvers());
        JavelinService javelin = new JavelinService(core.triggerDispatch(), bindings.weaponDamage(), core.resolvers(),
                () -> core.master().config().combat().pvp(), () -> core.master().config().combat().pve(),
                feature.combat.CombatDispatch::friendly);
        ReforgeMachines machines = new ReforgeMachines(core.content(), gravityWell, grapple, castling, javelin);

        // The pets-convention universal messages (activated/ended/cooldown/fail) + the window-ENDED reads.
        ReforgeMessenger reforgeMessages = new ReforgeMessenger(core.messages(),
                () -> core.master().config().reforges());
        ReforgeRunner runner = new ReforgeRunner(core.content(), core.triggerDispatch(), reforgeMessages,
                machines, core.stores(), core.tick()::get);
        this.useListener = new ReforgeUseListener(runner, core.codec(), core.hands(), enabled);

        // ADR-0071 Plan C: the two combat-state listeners — the strike relay (tempo/battery/disarm commit at
        // MONITOR + battery bank) and the third-party i-frame fairness gate (LOWEST). Both read the shared stores.
        this.strikeListener = new ReforgeStrikeListener(core.stores(), core.worn(), core.content(), core.messages(),
                core.sounds(), core.tick()::get,
                () -> core.master().config().combat().pvp(), () -> core.master().config().combat().pve(),
                new feature.combat.MentalTimingBridge()); // Mental's window service, capability-detected per hit
        this.tempoGuardListener = new ReforgeTempoGuardListener(core.stores().hitTempo(), core.tick()::get);

        this.mints = List.of(Mints.reforge(reforges, core.content()));
    }

    private BooleanSupplier enabled() {
        return () -> core.master().config().features().reforges();
    }

    FeatureModule module() {
        return FeatureModule.named("reforges")
                .toggle(Toggle.live("features.reforges", enabled()))
                .events(applyListener)
                .events(useListener)
                .events(strikeListener)
                .events(tempoGuardListener)
                .menu(77, new feature.menu.ReforgesBrowserMenu(core.content(), reforges, core.caps(),
                        core.messages(), core.menusHolder()::config, core.vanillaEnchants()))
                .mints(mints)
                // ADR-0071 Plan B machines (order: stops mirror the §2 head order). GRAPPLE + BLINK are stateless
                // (no store/stop); the three below drop live state on quit and clear on disable.
                .store(GravityWellService.scope())   // owner quits → their live wells keep running but drop the owner handle
                .store(CastlingService.scope())      // caster quits → channel cancelled
                .store(JavelinService.scope())       // thrower quits → live flights lose attribution; victim holds self-cancel
                .pluginItem(stack -> core.reforgeCodec().isReforge(stack))
                .lang("reforge", "command.reforge", "command.give.reforge", "command.error.no-such-reforge")
                .stop("gravity wells", GravityWellService::clearAll)
                .stop("castling channels", CastlingService::clearAll)
                .stop("javelin flights", JavelinService::clearAll)
                .build();
    }
}
