package bootstrap;

import bootstrap.wire.BootCore;
import bootstrap.wire.ModuleFold;
import bootstrap.wire.Modules;
import compile.load.ContentHolder;
import feature.guard.VanillaGuardListener;
import feature.trigger.EngineStoreListener;
import java.util.logging.Level;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The composition root (ADR-0014, ADR-0047): {@code onEnable} builds the feature-neutral substrate
 * ({@link BootCore}), constructs the ordered {@link Modules} registry, then folds it — the fold's wire pass
 * reproduces the shipped listener registration sequence, materializes the vanilla guard over the OR of every
 * module's plugin-item contribution and the quit sweeper over every module's declared player stores, registers
 * the dynamic commands and rank-ordered menus, and runs the boot actions. {@code onDisable} is the fold's
 * {@code stop()}. Every feature-shaped registration lives in a {@code FeatureModule}, not here
 * (ModuleTreeGateTest); this class is the ONE Bukkit event/command edge.
 */
public final class StarEnchantsPlugin extends JavaPlugin {

    private BootCore core;
    private volatile ModuleFold fold;

    @Override
    public void onEnable() {
        core = BootCore.boot(this);
        // /se modules reads the fold report through this supplier; the fold is assigned just below, before any
        // command can run (commands execute only after onEnable returns), so EMPTY is a never-observed placeholder.
        Modules modules = new Modules(core, () -> fold == null ? ModuleFold.Report.EMPTY : fold.report());
        fold = ModuleFold.wire(
                new PluginRegistrar(),
                modules.registry(),
                // §I the guard predicate: null/AIR short-circuit, then the OR of every module's plugin-item probe.
                pluginItem -> new VanillaGuardListener(
                        stack -> stack != null && stack.getType() != Material.AIR && pluginItem.test(stack),
                        core.hands()),
                // §5.4 the quit sweeper over EngineStores + every module's declared per-player stores; the
                // monotonic tick lets it RETAIN combat windows across a relog (only elapsed entries are shed).
                extra -> new EngineStoreListener(core.stores(), extra, core.tick()::get),
                new BindingsCommandRegistrar(),
                modules.menuRegistry(),
                core.coreStops());
    }

    @Override
    public void onDisable() {
        if (fold != null) {
            fold.stop(getLogger()::fine); // module stops (drivers, sink statics) then core stops (bStats), forward order
        }
    }

    public ContentHolder content() {
        return core.content();
    }

    /** The ONE Bukkit event/log edge for the fold (ADR-0047): the sole {@code registerEvents} site (G2-a). */
    private final class PluginRegistrar implements ModuleFold.Registrar {

        @Override
        public void events(Listener listener) {
            getServer().getPluginManager().registerEvents(listener, StarEnchantsPlugin.this);
        }

        @Override
        public void log(String line) {
            getLogger().info(line);
        }

        @Override
        public void warn(String line, Throwable error) {
            getLogger().log(Level.WARNING, line, error); // ADR-0042: the Throwable rides as the log parameter
        }
    }

    /** The command-map edge (era seam): a dynamic command registration on the server command map; may throw. */
    private final class BindingsCommandRegistrar implements ModuleFold.CommandRegistrar {

        @Override
        public void register(Command command) {
            core.bindings().registerCommand(getServer(), "starenchants", command);
        }
    }
}
