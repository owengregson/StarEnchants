package bootstrap.wire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import engine.stores.PlayerScoped;
import feature.menu.MenuRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

/**
 * The fold's mechanics with fake modules through the {@link ModuleFold.Registrar} seam — order, gating,
 * guard/sweep materialization, command semantics, stop order + isolation, and the report (ADR-0047). The
 * real registry's golden orders are pinned separately by RegistryWiringTest.
 */
final class ModuleFoldTest {

    // ── Named fakes so listener order is assertable by simple name ────────────────────────────────
    static final class Alpha implements Listener { }

    static final class Beta implements Listener { }

    static final class Gamma implements Listener { }

    static final class Delta implements Listener { }

    static final class GuardListener implements Listener { }

    static final class SweepListener implements Listener { }

    static final class RecordingRegistrar implements ModuleFold.Registrar {
        final List<String> events = new ArrayList<>();
        final List<String> logs = new ArrayList<>();
        final List<String> warns = new ArrayList<>();

        @Override
        public void events(Listener listener) {
            events.add(listener.getClass().getSimpleName());
        }

        @Override
        public void log(String line) {
            logs.add(line);
        }

        @Override
        public void warn(String line, Throwable error) {
            warns.add(line);
        }
    }

    static final class RecordingCommands implements ModuleFold.CommandRegistrar {
        final List<String> registered = new ArrayList<>();
        boolean throwOnRegister;

        @Override
        public void register(Command command) {
            if (throwOnRegister) {
                throw new RuntimeException("map unavailable");
            }
            registered.add(command.getName());
        }
    }

    static final class FakeCommand extends Command {
        FakeCommand(String name) {
            super(name);
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            return true;
        }
    }

    private static final Function<Predicate<ItemStack>, Listener> GUARD = predicate -> new GuardListener();
    private static final Function<List<PlayerScoped>, Listener> SWEEP = stores -> new SweepListener();

    private static ModuleFold wire(RecordingRegistrar reg, RecordingCommands commands, List<FeatureModule> registry) {
        return ModuleFold.wire(reg, registry, GUARD, SWEEP, commands, new MenuRegistry(), List.of());
    }

    @Test
    void wiresConcatenateInRegistryOrder() {
        RecordingRegistrar reg = new RecordingRegistrar();
        wire(reg, new RecordingCommands(), List.of(
                FeatureModule.named("a").events(new Alpha()).events(new Beta()).build(),
                FeatureModule.named("b").events(new Gamma()).build()));
        assertEquals(List.of("Alpha", "Beta", "Gamma"), reg.events);
    }

    @Test
    void bootGatedOffSkipsOnlyItsWiresAndLogsItsMessageOnce() {
        RecordingRegistrar reg = new RecordingRegistrar();
        ModuleFold fold = wire(reg, new RecordingCommands(), List.of(
                FeatureModule.named("first").events(new Alpha()).build(),
                FeatureModule.named("souls")
                        .toggle(Toggle.boot("features.souls", () -> false, "souls disabled"))
                        .events(new Beta()).events(new Gamma()).build(),
                FeatureModule.named("last").events(new Delta()).build()));

        assertEquals(List.of("Alpha", "Delta"), reg.events); // the gated module's wires are dropped
        assertEquals(1, reg.logs.stream().filter("souls disabled"::equals).count());

        ModuleFold.ModuleReport souls = fold.report().modules().get(1);
        assertFalse(souls.wired());
        assertEquals(List.of("Beta", "Gamma"), souls.skipped());
        assertTrue(souls.listeners().isEmpty());
    }

    @Test
    void anEmptyDisabledLogSkipsSilently() {
        RecordingRegistrar reg = new RecordingRegistrar();
        wire(reg, new RecordingCommands(), List.of(
                FeatureModule.named("traks")
                        .toggle(Toggle.boot("features.scrolls", () -> false, ""))
                        .events(new Alpha()).build()));
        assertTrue(reg.events.isEmpty());
        assertTrue(reg.logs.isEmpty());
    }

    @Test
    void installRunsInSequenceAndIsRecorded() {
        RecordingRegistrar reg = new RecordingRegistrar();
        AtomicBoolean ran = new AtomicBoolean(false);
        ModuleFold fold = wire(reg, new RecordingCommands(), List.of(
                FeatureModule.named("controls").install("knockback applier", () -> ran.set(true)).build()));
        assertTrue(ran.get());
        assertEquals(List.of("knockback applier"), fold.report().modules().get(0).installs());
    }

    @Test
    void pluginItemGuardMaterializesOverTheOrOfEveryContribution() {
        ItemStack a = new ItemStack(Material.PAPER);
        ItemStack b = new ItemStack(Material.STONE);
        ItemStack other = new ItemStack(Material.DIRT);
        AtomicReference<Predicate<ItemStack>> captured = new AtomicReference<>();
        Function<Predicate<ItemStack>, Listener> guard = predicate -> {
            captured.set(predicate);
            return new GuardListener();
        };
        RecordingRegistrar reg = new RecordingRegistrar();
        ModuleFold.wire(reg, List.of(
                        FeatureModule.named("scrolls").pluginItem(stack -> stack == a).build(),
                        FeatureModule.named("crystals").pluginItem(stack -> stack == b).build(),
                        FeatureModule.named("guard").pluginItemGuard().build()),
                guard, SWEEP, new RecordingCommands(), new MenuRegistry(), List.of());

        assertEquals(List.of("GuardListener"), reg.events); // materialized at the guard module's position
        Predicate<ItemStack> orChain = captured.get();
        assertTrue(orChain.test(a));
        assertTrue(orChain.test(b));
        assertFalse(orChain.test(other));
    }

    @Test
    void quitSweepMaterializesOverTheConcatenatedDeclaredStores() {
        PlayerScoped s1 = id -> { };
        PlayerScoped s2 = id -> { };
        AtomicReference<List<PlayerScoped>> captured = new AtomicReference<>();
        Function<List<PlayerScoped>, Listener> sweep = stores -> {
            captured.set(stores);
            return new SweepListener();
        };
        ModuleFold.wire(new RecordingRegistrar(), List.of(
                        FeatureModule.named("souls").store(s1).build(),
                        FeatureModule.named("scrolls").store(s2).build(),
                        FeatureModule.named("stores").quitSweep().build()),
                GUARD, sweep, new RecordingCommands(), new MenuRegistry(), List.of());
        assertEquals(List.of(s1, s2), captured.get());
    }

    @Test
    void commandRegistersOnSuccessAndLogsItsSuccessLog() {
        RecordingRegistrar reg = new RecordingRegistrar();
        RecordingCommands commands = new RecordingCommands();
        wire(reg, commands, List.of(FeatureModule.named("triggers")
                .command(new DynCommand("trigcmd", () -> true, () -> new FakeCommand("trigcmd"),
                        "fail line", "command-trigger registered: /trigcmd"))
                .build()));
        assertEquals(List.of("trigcmd"), commands.registered);
        assertTrue(reg.logs.contains("command-trigger registered: /trigcmd"));
    }

    @Test
    void aDisabledCommandIsSkipped() {
        RecordingCommands commands = new RecordingCommands();
        wire(new RecordingRegistrar(), commands, List.of(FeatureModule.named("m")
                .command(new DynCommand("x", () -> false, () -> new FakeCommand("x"), "fail", ""))
                .build()));
        assertTrue(commands.registered.isEmpty());
    }

    @Test
    void aThrowingRegisterLogsTheFailLineAtWarning() {
        RecordingRegistrar reg = new RecordingRegistrar();
        RecordingCommands commands = new RecordingCommands();
        commands.throwOnRegister = true;
        wire(reg, commands, List.of(FeatureModule.named("m")
                .command(DynCommand.always("enchants", () -> new FakeCommand("enchants"),
                        "could not register /enchants (use /se menu hub instead)"))
                .build()));
        assertTrue(commands.registered.isEmpty());
        assertTrue(reg.warns.contains("could not register /enchants (use /se menu hub instead)"));
    }

    @Test
    void stopRunsModuleStopsThenCoreStopsInForwardOrderIsolatingFailures() {
        List<String> order = new ArrayList<>();
        FeatureModule equip = FeatureModule.named("equip")
                .stop("repeating tasks", () -> order.add("repeating"))
                .stop("held buffs", () -> {
                    throw new RuntimeException("boom"); // must not skip the rest
                })
                .build();
        FeatureModule souls = FeatureModule.named("souls").stop("soul aura", () -> order.add("aura")).build();
        FeatureModule.Stop core = new FeatureModule.Stop("bStats", () -> order.add("bstats"));

        ModuleFold fold = ModuleFold.wire(new RecordingRegistrar(), List.of(equip, souls),
                GUARD, SWEEP, new RecordingCommands(), new MenuRegistry(), List.of(core));
        List<String> logged = new ArrayList<>();
        fold.stop(logged::add);

        assertEquals(List.of("repeating", "aura", "bstats"), order); // the throwing stop did not abort the rest
        assertEquals(4, logged.size()); // every stop logs, including the failed one
    }

    @Test
    void reportCapturesToggleWiredStateAndMintTypes() {
        RecordingRegistrar reg = new RecordingRegistrar();
        ModuleFold fold = wire(reg, new RecordingCommands(), List.of(
                FeatureModule.named("heroic")
                        .toggle(Toggle.live("features.heroic", () -> true))
                        .events(new Alpha())
                        .mint(new feature.menu.Mintable() {
                            @Override
                            public String type() {
                                return "heroic";
                            }

                            @Override
                            public java.util.List<String> aliases() {
                                return java.util.List.of("upgrade");
                            }

                            @Override
                            public void give(CommandSender sender, org.bukkit.entity.Player target,
                                             String[] args, feature.menu.MintIo io) {
                            }
                        })
                        .build()));
        ModuleFold.ModuleReport heroic = fold.report().modules().get(0);
        assertEquals("heroic", heroic.name());
        assertTrue(heroic.wired());
        assertEquals(Toggle.Depth.LIVE, heroic.toggle().depth());
        assertEquals(List.of("Alpha"), heroic.listeners());
        assertEquals(List.of("heroic", "upgrade"), heroic.mintTypes());
    }
}
