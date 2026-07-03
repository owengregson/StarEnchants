package bootstrap.wire;

import engine.stores.PlayerScoped;
import feature.menu.Menu;
import feature.menu.Mintable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

/**
 * One feature's complete wiring manifest (ADR-0047): what it registers, gates, mints, stores, and stops —
 * the feature axis, the way {@code bootstrap.compat.EraBindings} is the era axis (ADR-0044). The composition
 * root folds ONE ordered registry of these; nothing feature-shaped is wired outside a module
 * (ModuleTreeGateTest).
 *
 * <ul>
 *   <li>{@code wires} — ordered registrations; the concatenation of every enabled module's wires IS the
 *       golden listener registration sequence (§2).</li>
 *   <li>{@code commands} — dynamic command-map registrations ({@code /enchants}, {@code /splitsouls}, the
 *       command-trigger).</li>
 *   <li>{@code mintables} — the ONE mint declaration set (menu tiles + {@code /se give} + {@code /se <type>}).</li>
 *   <li>{@code menus} — rank-ordered {@code MenuRegistry} contributions (rank preserves the shipped order).</li>
 *   <li>{@code playerStores} — module-owned per-player state swept on quit (beyond {@code EngineStores}).</li>
 *   <li>{@code pluginItem} — this module's vanilla-guard contribution ({@code PluginItems.NONE} if none).</li>
 *   <li>{@code langRoots} — owned {@code lang.yml} namespaces (introspection only).</li>
 *   <li>{@code boots} — post-wire boot actions (driver starts, timers, arm-online).</li>
 *   <li>{@code stops} — onDisable actions, forward order.</li>
 * </ul>
 */
public record FeatureModule(
        String name,
        Toggle toggle,
        List<Wire> wires,
        List<DynCommand> commands,
        List<Mintable> mintables,
        List<MenuEntry> menus,
        List<PlayerScoped> playerStores,
        Predicate<ItemStack> pluginItem,
        List<String> langRoots,
        List<Runnable> boots,
        List<Stop> stops) {

    public FeatureModule {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(toggle, "toggle");
        Objects.requireNonNull(pluginItem, "pluginItem");
        wires = List.copyOf(wires);
        commands = List.copyOf(commands);
        mintables = List.copyOf(mintables);
        menus = List.copyOf(menus);
        playerStores = List.copyOf(playerStores);
        langRoots = List.copyOf(langRoots);
        boots = List.copyOf(boots);
        stops = List.copyOf(stops);
    }

    /** One onDisable unit: what + how, so {@code /se modules} and the stop log can name it. */
    public record Stop(String what, Runnable run) {
        public Stop {
            Objects.requireNonNull(what, "what");
            Objects.requireNonNull(run, "run");
        }
    }

    /** A rank-ordered menu contribution (rank preserves the shipped {@code /se menu} completion order). */
    public record MenuEntry(int rank, Menu menu) {
        public MenuEntry {
            Objects.requireNonNull(menu, "menu");
        }
    }

    public static Builder named(String name) {
        return new Builder(name);
    }

    /** Trivial accumulation into the immutable record — no logic beyond copying (round-tripped by a unit test). */
    public static final class Builder {

        private final String name;
        private Toggle toggle = Toggle.ALWAYS;
        private final List<Wire> wires = new ArrayList<>();
        private final List<DynCommand> commands = new ArrayList<>();
        private final List<Mintable> mintables = new ArrayList<>();
        private final List<MenuEntry> menus = new ArrayList<>();
        private final List<PlayerScoped> playerStores = new ArrayList<>();
        private Predicate<ItemStack> pluginItem = PluginItems.NONE;
        private final List<String> langRoots = new ArrayList<>();
        private final List<Runnable> boots = new ArrayList<>();
        private final List<Stop> stops = new ArrayList<>();

        private Builder(String name) {
            this.name = name;
        }

        public Builder toggle(Toggle toggle) {
            this.toggle = toggle;
            return this;
        }

        public Builder wire(Wire wire) {
            wires.add(wire);
            return this;
        }

        /** Convenience: a {@link Wire.Events} registration. */
        public Builder events(Listener listener) {
            wires.add(new Wire.Events(listener));
            return this;
        }

        /** Convenience: a self-managing {@link Wire.Install}. */
        public Builder install(String what, Runnable run) {
            wires.add(new Wire.Install(what, run));
            return this;
        }

        /** Convenience: the fold-materialized vanilla-guard slot at this position in the sequence. */
        public Builder pluginItemGuard() {
            wires.add(new Wire.PluginItemGuard());
            return this;
        }

        /** Convenience: the fold-materialized quit-sweep slot at this position in the sequence. */
        public Builder quitSweep() {
            wires.add(new Wire.QuitSweep());
            return this;
        }

        public Builder command(DynCommand command) {
            commands.add(command);
            return this;
        }

        public Builder mint(Mintable mintable) {
            mintables.add(mintable);
            return this;
        }

        public Builder menu(int rank, Menu menu) {
            menus.add(new MenuEntry(rank, menu));
            return this;
        }

        public Builder store(PlayerScoped store) {
            playerStores.add(store);
            return this;
        }

        public Builder pluginItem(Predicate<ItemStack> pluginItem) {
            this.pluginItem = pluginItem;
            return this;
        }

        public Builder lang(String... roots) {
            for (String root : roots) {
                langRoots.add(root);
            }
            return this;
        }

        public Builder boot(Runnable boot) {
            boots.add(boot);
            return this;
        }

        public Builder stop(String what, Runnable run) {
            stops.add(new Stop(what, run));
            return this;
        }

        public FeatureModule build() {
            return new FeatureModule(name, toggle, wires, commands, mintables, menus, playerStores,
                    pluginItem, langRoots, boots, stops);
        }
    }
}
