package bootstrap.wire;

import api.StarEnchantsApi;
import api.event.StarEnchantsReloadEvent;
import bootstrap.ApiService;
import compile.load.ItemsLoader;
import compile.load.LangLoader;
import compile.load.MasterConfigLoader;
import compile.load.MenusLoader;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import platform.content.ContentReloader;
import platform.content.ReloadResult;
import platform.content.ReloadStep;
import platform.sched.Scheduling;
import schema.diag.Diagnostic;

/**
 * Transactional reload + the public add-on service (ADR-0038/0047). The publish callback advances the gen-keyed
 * caches, rebinds quarantine + effect kinds, fires the reload event, and re-arms every online player through the
 * equip module's drivers — so this module takes the equip module. The reloader is exposed for the operator
 * console menu and {@code /se}.
 */
final class ReloadModule {

    private static final int AUTO_RELOAD_DIAG_PREVIEW = 3;

    private final BootCore core;
    final ContentReloader reloader;

    ReloadModule(BootCore core, EquipModule equip) {
        this.core = core;
        // The §L config sources reload in the SAME transaction as content: each parses off-thread, the reloader
        // commits all-or-nothing — any error keeps the previous state of EVERYTHING.
        List<ReloadStep> reloadSteps = List.of(
                () -> { var c = ItemsLoader.load(core.itemsRoot()); return new ReloadStep.Built(
                        c.diagnostics(), () -> core.items().publish(c)); },
                () -> { var c = MasterConfigLoader.load(core.configFile()); return new ReloadStep.Built(
                        c.diagnostics(), () -> core.master().publish(c)); },
                () -> { var c = LangLoader.load(core.langFile()); return new ReloadStep.Built(
                        c.diagnostics(), () -> core.lang().publish(c)); },
                () -> { var c = MenusLoader.load(core.menusRoot()); return new ReloadStep.Built(
                        c.diagnostics(), () -> core.menusHolder().publish(c)); });

        // On a clean swap this hook advances the gen-keyed caches and re-resolves every online player. The compiler
        // is rebuilt per reload (not constant) so a newly registered add-on head compiles; the resolver is reused.
        this.reloader = new ContentReloader(core.content(), core.compilerFactory(),
                core.contentRoot(), 0, published -> {
            core.itemViews().reload(published.snapshot().generation());
            core.executor().bindQuarantine(BootCore.quarantineFor(published.snapshot())); // §10 fresh per snapshot
            core.stores().why().generation(published.snapshot().generation()); // ADR-0045: rebind gen post-reload
            // R-QC58: the same moment, for the same reason — the four IMPACT-scope carriers stamp this, and a
            // cast armed against the OLD interner is dropped at consume rather than firing an unscoped payload.
            engine.sink.CastGeneration.generation(published.snapshot().generation());
            core.executor().bindContent(core.effectRegistry().get()); // ADR-0038/0039: atomic effect+selector pair
            core.dispatch().bindTiers(BootCore.tierWeightsFor(published)); // PROC_REBOUND tier index, per snapshot
            core.plugin().getServer().getPluginManager().callEvent(new StarEnchantsReloadEvent(
                    published.snapshot().generation(), published.snapshot().abilityCount()));
            if (core.master().config().reload().reResolvePlayers()) { // §L config.yml reload.re-resolve-players
                for (Player player : core.plugin().getServer().getOnlinePlayers()) {
                    // Re-arm against the new snapshot per player (a repeating task's period may have changed).
                    Scheduling.onEntity(player, () -> {
                        var state = core.worn().refresh(player, published.snapshot());
                        equip.passives.arm(player, state);
                        equip.lifecycle.refresh(player, state);
                        equip.passiveEffects.refresh(player);
                        equip.maxHealth.refresh(player);
                    });
                }
            }
        }, reloadSteps);

        // ADR-0038: the public add-on service, registered with Bukkit's ServicesManager so an add-on looks it up.
        StarEnchantsApi apiService = new ApiService(core.addonKinds(), core.effectRegistry(), reloader,
                core.content(), core.itemViews(), () -> core.master().config().slots().base());
        core.plugin().getServer().getServicesManager().register(StarEnchantsApi.class, apiService, core.plugin(),
                ServicePriority.Normal);
    }

    FeatureModule module() {
        return FeatureModule.named("reload")
                // §L auto-reload (config.yml reload.auto-seconds; ≤ 0 = off). Armed once at boot.
                .boot(() -> {
                    int autoSeconds = core.master().config().reload().autoSeconds();
                    if (autoSeconds > 0) {
                        long period = autoSeconds * 20L;
                        Scheduling.repeatingGlobal(period, period, () -> reloader.reload(this::logAutoReload));
                        core.plugin().getLogger().info("auto-reload armed: every " + autoSeconds + "s");
                    }
                })
                .lang("command.reload")
                .build();
    }

    /**
     * ADR-0042: the timed reload's result used to be discarded — a failing auto-reload was silent while
     * {@code /se reload} reported the same faults. (busy → FINE, not WARNING: a build outlasting the period
     * would otherwise self-spam every cycle.)
     */
    private void logAutoReload(ReloadResult result) {
        if (result.failure() != null) {
            core.plugin().getLogger().log(Level.WARNING, "auto-reload build failed; previous content kept",
                    result.failure());
            return;
        }
        if (result.isBusy()) {
            core.plugin().getLogger().fine("auto-reload skipped: another reload is in flight");
            return;
        }
        if (result.errorCount() > 0) {
            core.plugin().getLogger().warning("auto-reload rejected: " + result.errorCount()
                    + " blocking diagnostic(s); previous content kept (run /se problems)");
            result.diagnostics().stream().filter(Diagnostic::blocking).limit(AUTO_RELOAD_DIAG_PREVIEW)
                    .forEach(d -> core.plugin().getLogger().warning("  " + d.render()));
            return;
        }
        core.plugin().getLogger().fine("auto-reload: published generation " + result.generation()
                + " (" + result.abilityCount() + " abilities)");
    }
}
