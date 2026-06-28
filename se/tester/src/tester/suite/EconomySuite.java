package tester.suite;

import compile.Compiler;
import compile.load.ContentHolder;
import compile.load.Library;
import compile.load.LibraryLoader;
import engine.boot.ContentCompiler;
import engine.effect.kind.BuiltinEffects;
import engine.pipeline.ActivationPipeline;
import engine.interact.SoulLedger;
import engine.run.AbilityExecutor;
import engine.run.AreaScan;
import engine.selector.kind.BuiltinSelectors;
import engine.stores.CooldownStore;
import engine.trigger.BuiltinTriggers;
import engine.trigger.TriggerRegistry;
import feature.combat.CombatDispatch;
import feature.combat.CombatListener;
import item.codec.CombatCodec;
import item.codec.CombatState;
import item.codec.ItemKeys;
import item.view.ItemViewCache;
import item.worn.WornResolver;
import item.worn.WornStateStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import platform.economy.EconomyProvider;
import platform.economy.EconomyService;
import platform.resolve.RegistryResolvers;
import platform.resolve.RuntimeHandles;
import platform.sched.Scheduling;
import tester.fake.FakePlayers;
import tester.harness.CombatRig;
import tester.harness.Harness;

/**
 * The economy seam, live (§2, §7): a fake {@link EconomyProvider} registered + discovered via
 * {@link EconomyService#discover}, driven by a MODIFY_MONEY ATTACK enchant — discovery → service → effect →
 * sink's global-thread money intent. @Self not @Victim: a money target must be a player, and a player victim
 * can't take a programmatic hit (PvP/peaceful gating, see CombatSuite). Mojang-mapped only.
 */
public final class EconomySuite implements Harness.Scenario {

    private static final String BOUNTY = """
            display: Bounty
            trigger: ATTACK
            levels:
              1: { chance: 100, effects: [{ MODIFY_MONEY: { amount: 100, mode: give } }] }
            """;

    private final Plugin plugin;

    public EconomySuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        h.expect("economy.giveMoneyDepositsToActor");

        RegistryResolvers resolvers = new RegistryResolvers();
        Compiler compiler = ContentCompiler.production(resolvers);
        RuntimeHandles handles = new RuntimeHandles(resolvers);

        Library library;
        try {
            Path root = Files.createTempDirectory("se-economy-suite");
            write(root, "enchants/bounty.yml", BOUNTY);
            library = LibraryLoader.load(root, compiler, 0);
            if (library.hasErrors()) {
                h.fail("economy.giveMoneyDepositsToActor", "bounty failed to compile: " + library.diagnostics());
                return;
            }
        } catch (IOException e) {
            h.fail("economy.giveMoneyDepositsToActor", e.toString());
            return;
        }

        // Same ServicesManager path the bootstrap uses, so this exercises discovery too.
        FakeEconomy bank = new FakeEconomy();
        plugin.getServer().getServicesManager().register(EconomyProvider.class, bank, plugin, ServicePriority.Normal);
        EconomyService economy = EconomyService.discover(plugin.getServer(), System.getLogger("se.test.economy"));
        if (!economy.present()) {
            h.fail("economy.giveMoneyDepositsToActor", "EconomyService.discover did not find the registered provider");
            plugin.getServer().getServicesManager().unregister(EconomyProvider.class, bank);
            return;
        }

        ContentHolder holder = new ContentHolder(library);
        CombatCodec codec = new CombatCodec(ItemKeys.of().combat());
        ItemViewCache itemViews = new ItemViewCache(codec, library.snapshot().generation());
        TriggerRegistry triggers = BuiltinTriggers.registry();
        WornStateStore worn = new WornStateStore(
                new WornResolver(itemViews, triggers.count(), triggers.attackTriggers(), triggers.defenseTriggers())::resolve);
        AtomicLong tick = new AtomicLong();
        AbilityExecutor executor = new AbilityExecutor(BuiltinEffects.registry(), BuiltinSelectors.registry(),
                new ActivationPipeline(new CooldownStore(), new SoulLedger()), AreaScan.NONE);
        CombatDispatch dispatch = new CombatDispatch(executor, new engine.sink.DispatchSinkFactory(handles), holder, worn,
                triggers.idOf("ATTACK").orElseThrow(), triggers.idOf("DEFENSE").orElseThrow(),
                tick::incrementAndGet, actor -> Optional.empty(), economy);
        CombatRig rig = new CombatRig(plugin);
        rig.listen(new CombatListener(dispatch));

        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        codec.write(sword, new CombatState(Map.of("enchants/bounty", 1), List.of()));

        World world = plugin.getServer().getWorlds().get(0);
        Location at = world.getSpawnLocation();
        int cx = at.getBlockX() >> 4;
        int cz = at.getBlockZ() >> 4;

        Scheduling.onGlobal(() -> {
            world.setChunkForceLoaded(cx, cz, true);
            Scheduling.onRegion(at, () -> {
                Player attacker;
                LivingEntity victim;
                try {
                    attacker = FakePlayers.spawn(world, "se_econ_atk");
                    victim = (LivingEntity) world.spawnEntity(at, EntityType.COW);
                } catch (Throwable t) {
                    h.fail("economy.giveMoneyDepositsToActor", "spawn: " + t);
                    plugin.getServer().getServicesManager().unregister(EconomyProvider.class, bank);
                    return;
                }
                UUID attackerId = attacker.getUniqueId();
                Scheduling.onEntity(attacker, () -> {
                    attacker.getInventory().setItemInMainHand(sword);
                    worn.refresh(attacker, library.snapshot());
                    victim.damage(1.0, attacker);
                    // Deposit routes to the global thread; 10 ticks is ample for it to land.
                    Scheduling.onEntityLater(victim, 10L, () -> {
                        h.guard("economy.giveMoneyDepositsToActor", () -> {
                            double balance = bank.balance(attackerId);
                            if (balance != 100.0) {
                                throw new IllegalStateException("expected attacker balance 100 after MODIFY_MONEY, got "
                                        + balance + " (deposits=" + bank.deposits.get() + ")");
                            }
                        });
                        plugin.getServer().getServicesManager().unregister(EconomyProvider.class, bank);
                        victim.remove();
                        FakePlayers.despawn(attacker);
                        rig.teardown();
                    });
                });
            });
        });
    }

    private static void write(Path root, String relative, String yaml) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, yaml, StandardCharsets.UTF_8);
    }

    /** Thread-safe: deposits land on the global thread, balance is read on the entity thread. */
    private static final class FakeEconomy implements EconomyProvider {
        private final ConcurrentHashMap<UUID, Double> balances = new ConcurrentHashMap<>();
        final java.util.concurrent.atomic.AtomicInteger deposits = new java.util.concurrent.atomic.AtomicInteger();

        @Override
        public double balance(UUID player) {
            return balances.getOrDefault(player, 0.0);
        }

        @Override
        public boolean withdraw(UUID player, double amount) {
            return balances.merge(player, -amount, Double::sum) >= 0; // simplistic: allows going negative in the test
        }

        @Override
        public void deposit(UUID player, double amount) {
            balances.merge(player, amount, Double::sum);
            deposits.incrementAndGet();
        }

        @Override
        public String name() {
            return "fake";
        }
    }
}
