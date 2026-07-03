package bootstrap.wire;

import engine.stores.PlayerScoped;
import feature.menu.MenuRegistry;
import feature.menu.Mintable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.bukkit.command.Command;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

/**
 * The fold (ADR-0047): iterate the ordered registry once per axis. Pure mechanics — every decision (order,
 * gating, contributions) lives in the module declarations, so the fold itself is unit-tested with fake
 * modules through the {@link Registrar} seam. The wire pass reproduces the shipped registration sequence
 * (RegistryWiringTest pins it golden); {@link #stop} reproduces the shipped onDisable sequence.
 */
public final class ModuleFold {

    /** The Bukkit-touching event/log edge, injected so fold logic is unit-testable server-free. */
    public interface Registrar {
        /** plugin-manager {@code registerEvents}. */
        void events(Listener listener);

        /** plugin logger INFO. */
        void log(String line);

        /** plugin logger WARNING with the stack attached (ADR-0042). */
        void warn(String line, Throwable error);
    }

    /** The command-map edge (era seam), injected for the same reason: raw register, may throw. */
    public interface CommandRegistrar {
        void register(Command command);
    }

    private final Report report;
    private final List<FeatureModule> registry;
    private final List<FeatureModule.Stop> coreStops;

    private ModuleFold(Report report, List<FeatureModule> registry, List<FeatureModule.Stop> coreStops) {
        this.report = report;
        this.registry = registry;
        this.coreStops = coreStops;
    }

    /**
     * Fold the registry once per axis in the normative order: plugin-item OR, player-store concat, wires,
     * commands, menus, boots — then freeze the report.
     */
    public static ModuleFold wire(Registrar reg, List<FeatureModule> registry,
                                  Function<Predicate<ItemStack>, Listener> guardFactory,
                                  Function<List<PlayerScoped>, Listener> sweepFactory,
                                  CommandRegistrar commands, MenuRegistry menus,
                                  List<FeatureModule.Stop> coreStops) {
        // 1. plugin-item guard predicate = OR over every module's contribution, unconditionally (a disabled
        //    scrolls feature still guard-suppresses a scroll item's vanilla use — today's behaviour preserved).
        Predicate<ItemStack> pluginItem = PluginItems.NONE;
        for (FeatureModule module : registry) {
            pluginItem = pluginItem.or(module.pluginItem());
        }
        // 2. quit-sweep set = concat of every module's declared player stores, unconditionally.
        List<PlayerScoped> playerScoped = new ArrayList<>();
        for (FeatureModule module : registry) {
            playerScoped.addAll(module.playerStores());
        }
        // 3. wires pass, registry order — the concatenation IS the golden listener sequence.
        List<ModuleReport> reports = new ArrayList<>();
        for (FeatureModule module : registry) {
            Toggle toggle = module.toggle();
            boolean bootGatedOff = toggle.gatesBoot() && !toggle.enabled().getAsBoolean();
            List<String> listeners = new ArrayList<>();
            List<String> installs = new ArrayList<>();
            List<String> skipped = new ArrayList<>();
            if (bootGatedOff) {
                if (!toggle.disabledLog().isEmpty()) {
                    reg.log(toggle.disabledLog());
                }
                for (Wire wire : module.wires()) {
                    skipped.add(describe(wire));
                }
            } else {
                for (Wire wire : module.wires()) {
                    if (wire instanceof Wire.Events events) {
                        reg.events(events.listener());
                        listeners.add(simpleName(events.listener()));
                    } else if (wire instanceof Wire.Install install) {
                        install.run().run();
                        installs.add(install.what());
                    } else if (wire instanceof Wire.PluginItemGuard) {
                        Listener guard = guardFactory.apply(pluginItem);
                        reg.events(guard);
                        listeners.add(simpleName(guard));
                    } else if (wire instanceof Wire.QuitSweep) {
                        Listener sweep = sweepFactory.apply(playerScoped);
                        reg.events(sweep);
                        listeners.add(simpleName(sweep));
                    }
                }
            }
            reports.add(new ModuleReport(module.name(), toggle, !bootGatedOff, listeners, installs, skipped,
                    labels(module), mintTypes(module), menuNames(module), module.playerStores().size(),
                    stopNames(module), module.langRoots()));
        }
        // 4. commands pass, registry order — today's exact guarded-register + WARNING-fallback semantics.
        for (FeatureModule module : registry) {
            for (DynCommand command : module.commands()) {
                if (!command.enabled().getAsBoolean()) {
                    continue;
                }
                try {
                    commands.register(command.command().get());
                    if (!command.successLog().isEmpty()) {
                        reg.log(command.successLog());
                    }
                } catch (Throwable failure) {
                    reg.warn(command.failLog(), failure);
                }
            }
        }
        // 5. menus pass — collect all, stable-sort by rank (preserving the shipped completion order), register.
        List<FeatureModule.MenuEntry> allMenus = new ArrayList<>();
        for (FeatureModule module : registry) {
            allMenus.addAll(module.menus());
        }
        allMenus.sort(Comparator.comparingInt(FeatureModule.MenuEntry::rank));
        for (FeatureModule.MenuEntry entry : allMenus) {
            menus.register(entry.menu());
        }
        // 6. boots pass, registry order — unconditional (today the boot actions sit outside the toggle blocks).
        for (FeatureModule module : registry) {
            for (Runnable boot : module.boots()) {
                boot.run();
            }
        }
        return new ModuleFold(new Report(reports), registry, List.copyOf(coreStops));
    }

    /**
     * onDisable: forward module order, each {@link FeatureModule.Stop} in declared order, then the core stops
     * — the concatenation reproduces today's onDisable sequence exactly. One failing stop is caught-and-logged
     * so it can't skip the rest (a strict improvement; behaviourally invisible in the no-throw case).
     */
    public void stop(Consumer<String> log) {
        for (FeatureModule module : registry) {
            for (FeatureModule.Stop step : module.stops()) {
                runStop(step, log);
            }
        }
        for (FeatureModule.Stop step : coreStops) {
            runStop(step, log);
        }
    }

    private static void runStop(FeatureModule.Stop step, Consumer<String> log) {
        try {
            step.run().run();
            log.accept(step.what());
        } catch (Throwable failure) {
            log.accept(step.what() + " — stop failed: " + failure);
        }
    }

    /** The frozen wire-time report — {@code /se modules} renders it (listeners cannot re-register mid-run). */
    public Report report() {
        return report;
    }

    private static String simpleName(Listener listener) {
        return listener.getClass().getSimpleName();
    }

    private static String describe(Wire wire) {
        if (wire instanceof Wire.Events events) {
            return simpleName(events.listener());
        }
        if (wire instanceof Wire.Install install) {
            return install.what();
        }
        if (wire instanceof Wire.PluginItemGuard) {
            return "VanillaGuardListener";
        }
        return "EngineStoreListener"; // QuitSweep
    }

    private static List<String> labels(FeatureModule module) {
        List<String> out = new ArrayList<>();
        for (DynCommand command : module.commands()) {
            out.add(command.label());
        }
        return out;
    }

    private static List<String> mintTypes(FeatureModule module) {
        List<String> out = new ArrayList<>();
        for (Mintable mintable : module.mintables()) {
            out.add(mintable.type());
            out.addAll(mintable.aliases());
        }
        return out;
    }

    private static List<String> menuNames(FeatureModule module) {
        List<String> out = new ArrayList<>();
        for (FeatureModule.MenuEntry entry : module.menus()) {
            out.add(entry.menu().name());
        }
        return out;
    }

    private static List<String> stopNames(FeatureModule module) {
        List<String> out = new ArrayList<>();
        for (FeatureModule.Stop step : module.stops()) {
            out.add(step.what());
        }
        return out;
    }

    /** What actually got wired, per module — the {@code /se modules} truth, frozen at wire time. */
    public record Report(List<ModuleReport> modules) {

        /** The pre-wire placeholder (before onEnable has folded). */
        public static final Report EMPTY = new Report(List.of());

        public Report {
            modules = List.copyOf(modules);
        }
    }

    public record ModuleReport(String name, Toggle toggle, boolean wired,
                               List<String> listeners, List<String> installs, List<String> skipped,
                               List<String> commands, List<String> mintTypes,
                               List<String> menuNames, int playerStores, List<String> stops,
                               List<String> langRoots) {

        public ModuleReport {
            listeners = List.copyOf(listeners);
            installs = List.copyOf(installs);
            skipped = List.copyOf(skipped);
            commands = List.copyOf(commands);
            mintTypes = List.copyOf(mintTypes);
            menuNames = List.copyOf(menuNames);
            stops = List.copyOf(stops);
            langRoots = List.copyOf(langRoots);
        }
    }
}
