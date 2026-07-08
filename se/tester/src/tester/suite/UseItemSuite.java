package tester.suite;

import compile.Compiler;
import compile.load.ContentHolder;
import compile.load.Library;
import compile.load.LibraryLoader;
import engine.boot.ContentCompiler;
import engine.effect.kind.BuiltinEffects;
import engine.interact.SoulSpender;
import engine.pipeline.ActivationPipeline;
import engine.run.AbilityExecutor;
import engine.run.AreaScan;
import engine.selector.kind.BuiltinSelectors;
import engine.stores.CooldownStore;
import engine.trigger.BuiltinTriggers;
import engine.trigger.TriggerRegistry;
import feature.trigger.TriggerDispatch;
import feature.useitem.UseItemListener;
import feature.useitem.UseItemService;
import item.codec.CombatCodec;
import item.codec.ItemKeys;
import item.codec.UseItemCodec;
import item.mint.VanillaEnchants;
import item.view.ItemViewCache;
import item.worn.WornResolver;
import item.worn.WornStateStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;
import platform.lang.Messages;
import platform.resolve.RegistryResolvers;
import platform.resolve.RuntimeHandles;
import platform.sched.Scheduling;
import schema.spec.HandleCategory;
import tester.harness.CombatRig;
import tester.harness.Harness;

/**
 * The §3.6 use-item flow, live (docs/decisions/0048-use-items.md): a right-click use-item runs its abilities
 * through the SAME pipeline every other source uses. Drives real {@link PlayerInteractEvent}s at a
 * {@link UseItemListener} over a minted item and asserts the world-side outcome the unit tests cannot —
 * (a) the abilities' potion effects land, (b) a {@code consumable} item decrements one on a successful use,
 * (c) an immediate second use is cooldown-gated (no consume, no re-fire), and (d) a {@code commands:} def runs
 * a console command (an observable {@code /effect give {PLAYER}} side effect). Clientless fake player.
 */
public final class UseItemSuite implements Harness.Scenario {

    // Strength II + Slowness I for 12s, 60s cooldown — the shipped rage-crystal likeness in engine terms.
    private static final String RAGE = """
            name: "&c&lRage Crystal"
            material: RED_DYE
            consumable: true
            cooldown: 1200
            chance: 100
            effects:
              - { POTION: { effect: "INCREASE_DAMAGE", level: 2, duration: 240, who: "@Self" } }
              - { POTION: { effect: "SLOW", level: 1, duration: 240, who: "@Self" } }
            """;

    // A reusable command tool: no effects, one console command whose {PLAYER} token fills the actor's name, so a
    // successful use is observable as a levitation effect the vanilla /effect command grants the fake player.
    private static final String COMMANDER = """
            name: "&aCommand Crystal"
            material: PAPER
            consumable: false
            cooldown: 0
            chance: 100
            effects: []
            commands:
              - "effect give {PLAYER} minecraft:levitation 60 0"
            """;

    private static final String POTIONS = "useItem.abilityPotionsApply";
    private static final String CONSUME = "useItem.consumableDecrements";
    private static final String COOLDOWN = "useItem.secondUseCooldownGated";
    private static final String COMMAND = "useItem.commandRunsConsoleCommand";

    private final Plugin plugin;

    public UseItemSuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        h.expect(POTIONS);
        h.expect(CONSUME);
        h.expect(COOLDOWN);
        h.expect(COMMAND);

        RegistryResolvers resolvers = new RegistryResolvers();
        Compiler compiler = ContentCompiler.production(resolvers);
        RuntimeHandles handles = new RuntimeHandles(resolvers);

        Library library;
        PotionEffectType strength;
        PotionEffectType slow;
        PotionEffectType levitation;
        try {
            Path root = Files.createTempDirectory("se-useitem-suite");
            write(root, "use-items/rage-crystal.yml", RAGE);
            write(root, "use-items/cmd-crystal.yml", COMMANDER);
            library = LibraryLoader.load(root, compiler, 0);
            if (library.hasErrors()) {
                failAll(h, "use-items failed to compile: " + library.diagnostics());
                return;
            }
            strength = (PotionEffectType) handles.resolveByName(HandleCategory.POTION_EFFECT, "INCREASE_DAMAGE");
            slow = (PotionEffectType) handles.resolveByName(HandleCategory.POTION_EFFECT, "SLOW");
            levitation = (PotionEffectType) handles.resolveByName(HandleCategory.POTION_EFFECT, "LEVITATION");
            if (strength == null || slow == null || levitation == null) {
                failAll(h, "INCREASE_DAMAGE/SLOW/LEVITATION did not resolve on this version");
                return;
            }
        } catch (IOException e) {
            failAll(h, e.toString());
            return;
        }

        ContentHolder holder = new ContentHolder(library);
        ItemKeys keys = ItemKeys.of();
        UseItemCodec codec = new UseItemCodec(keys.useItem(), Stores.state());

        // The USE cold path reuses the shared TriggerDispatch spine (its runner/sink wiring) exactly as the
        // shipped UseItemsModule does — one CooldownStore instance so the second use sees the first's stamp.
        CombatCodec combat = new CombatCodec(keys.combat(), Stores.state());
        ItemViewCache itemViews = new ItemViewCache(combat, library.snapshot().generation());
        TriggerRegistry triggers = BuiltinTriggers.registry();
        WornStateStore worn = new WornStateStore(new WornResolver(Stores.equip(), itemViews, triggers.count(),
                triggers.attackTriggers(), triggers.defenseTriggers())::resolve);
        AbilityExecutor executor = new AbilityExecutor(BuiltinEffects.registry(), BuiltinSelectors.registry(),
                new ActivationPipeline(new CooldownStore(), SoulSpender.NONE), AreaScan.NONE);
        AtomicLong tick = new AtomicLong();
        engine.sink.SinkEnv env = engine.sink.SinkEnv.of(platform.economy.EconomyService.NONE,
                engine.sink.SoulDebit.NONE, engine.stores.EngineStores.fresh(), tick::incrementAndGet);
        TriggerDispatch dispatch = new TriggerDispatch(executor,
                dsEnv -> new engine.sink.ModernDispatchSink(handles, dsEnv), Stores.probe(), holder, worn, triggers,
                actor -> Optional.empty(), env, Stores.hands(), Stores.dropControl());
        UseItemService service = new UseItemService(holder, codec, dispatch, VanillaEnchants.NONE);
        UseItemListener listener = new UseItemListener(service, Messages.defaults(), Stores.hands(), () -> true);

        ItemStack rage = service.mint("rage-crystal");
        ItemStack commander = service.mint("cmd-crystal");
        if (rage == null || commander == null) {
            failAll(h, "use-item mint returned null");
            return;
        }
        rage.setAmount(2); // start above 1 so a decrement leaves a visible remainder rather than emptying the hand

        CombatRig rig = new CombatRig(plugin);
        rig.listen(listener);

        World world = plugin.getServer().getWorlds().get(0);
        Location at = world.getSpawnLocation();
        rig.onArena(at, () -> {
            Player user;
            try {
                user = rig.spawnFake(world, "se_useitem");
            } catch (Throwable t) {
                failAll(h, "fake-player spawn: " + t);
                return;
            }
            Scheduling.onEntity(user, () -> {
                user.getInventory().setItemInMainHand(rage);
                rightClick(user, rage);
                Scheduling.onEntityLater(user, 12L, () -> {
                    h.guard(POTIONS, () -> {
                        if (!user.hasPotionEffect(strength) || !user.hasPotionEffect(slow)) {
                            throw new IllegalStateException("use-item abilities did not apply Strength+Slowness");
                        }
                    });
                    h.guard(CONSUME, () -> {
                        int left = user.getInventory().getItemInMainHand().getAmount();
                        if (left != 1) {
                            throw new IllegalStateException("consumable use did not decrement 2 → 1: " + left);
                        }
                    });
                    // Second use, immediately: the a0 cooldown (1200t) must gate it — no consume, no re-fire.
                    rightClick(user, user.getInventory().getItemInMainHand());
                    Scheduling.onEntityLater(user, 12L, () -> {
                        h.guard(COOLDOWN, () -> {
                            int left = user.getInventory().getItemInMainHand().getAmount();
                            if (left != 1) {
                                throw new IllegalStateException(
                                        "second use was not cooldown-gated (item consumed again): " + left);
                            }
                        });
                        user.getInventory().setItemInMainHand(commander);
                        rightClick(user, commander);
                        Scheduling.onEntityLater(user, 20L, () -> {
                            h.guard(COMMAND, () -> {
                                if (!user.hasPotionEffect(levitation)) {
                                    throw new IllegalStateException(
                                            "commands: def did not run the console /effect command");
                                }
                            });
                            rig.teardown();
                        });
                    });
                });
            });
        });
    }

    /** Fire a synthetic main-hand right-click at the listener; it reads the live main hand, not the event item. */
    private void rightClick(Player player, ItemStack held) {
        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, held, null,
                BlockFace.SELF, EquipmentSlot.HAND);
        plugin.getServer().getPluginManager().callEvent(event);
    }

    private void failAll(Harness h, String message) {
        h.fail(POTIONS, message);
        h.fail(CONSUME, message);
        h.fail(COOLDOWN, message);
        h.fail(COMMAND, message);
    }

    private static void write(Path root, String relative, String yaml) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, yaml, StandardCharsets.UTF_8);
    }
}
