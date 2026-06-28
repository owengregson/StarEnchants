package tester.suite;

import api.event.EnchantActivateEvent;
import compile.Compiler;
import compile.load.ContentHolder;
import compile.load.Library;
import compile.load.LibraryLoader;
import engine.boot.ContentCompiler;
import engine.effect.kind.BuiltinEffects;
import engine.interact.SoulLedger;
import engine.pipeline.ActivationPipeline;
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
import item.worn.WornState;
import item.worn.WornStateStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;
import platform.resolve.RegistryResolvers;
import platform.resolve.RuntimeHandles;
import platform.sched.Scheduling;
import tester.fake.FakePlayers;
import tester.harness.CombatRig;
import tester.harness.Harness;

/**
 * End-to-end combat spine, live (ADR-0014; §3.3, §3.6): a fake player's Venom sword poisons a cow. Also
 * proves §9 resolver pairing — the compile-interned POTION handle resolves back through the SAME retained
 * {@link RegistryResolvers}. Mojang-mapped only (fake-player attacker).
 */
public final class CombatSuite implements Harness.Scenario {

    private static final String VENOM = """
            display: Venom
            trigger: ATTACK
            levels:
              1: { chance: 100, effects: [{ POTION: { effect: POISON, level: 1, duration: 80, who: "@Victim" } }] }
            """;

    private final Plugin plugin;

    public CombatSuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        h.expect("combat.enchantFiresOnHit");
        h.expect("combat.enchantActivateEventFired");

        // Retained resolver so the compile→runtime handle round-trip pairs.
        RegistryResolvers resolvers = new RegistryResolvers();
        Compiler compiler = ContentCompiler.production(resolvers);
        RuntimeHandles handles = new RuntimeHandles(resolvers);

        Library library;
        PotionEffectType poison;
        try {
            Path root = Files.createTempDirectory("se-combat-suite");
            write(root, "enchants/venom.yml", VENOM);
            library = LibraryLoader.load(root, compiler, 0);
            if (library.hasErrors()) {
                h.fail("combat.enchantFiresOnHit", "venom failed to compile: " + library.diagnostics());
                return;
            }
            poison = (PotionEffectType) handles.resolveByName(schema.spec.HandleCategory.POTION_EFFECT, "POISON");
            if (poison == null) {
                h.fail("combat.enchantFiresOnHit", "POISON did not resolve on this version");
                return;
            }
        } catch (IOException e) {
            h.fail("combat.enchantFiresOnHit", e.toString());
            return;
        }

        ContentHolder holder = new ContentHolder(library);
        CombatCodec codec = new CombatCodec(ItemKeys.of().combat());
        ItemViewCache itemViews = new ItemViewCache(codec, library.snapshot().generation());
        TriggerRegistry triggers = BuiltinTriggers.registry();
        WornStateStore worn = new WornStateStore(
                new WornResolver(itemViews, triggers.count(), triggers.attackTriggers(), triggers.defenseTriggers())::resolve);
        // §13 api seam: the executor fires EnchantActivateEvent per proc on the entity's region thread (Folia),
        // so this also proves region-thread dispatch survives.
        CombatRig rig = new CombatRig(plugin);
        EventProbe probe = rig.listen(new EventProbe());
        AbilityExecutor executor = new AbilityExecutor(BuiltinEffects.registry(), BuiltinSelectors.registry(),
                new ActivationPipeline(new CooldownStore(), new SoulLedger()), AreaScan.NONE,
                (key, ability, ctx) -> {
                    Player actor = ctx.actor();
                    if (actor == null || key == null) {
                        return;
                    }
                    plugin.getServer().getPluginManager().callEvent(
                            new EnchantActivateEvent(actor, key, ability.level()));
                });
        AtomicLong tick = new AtomicLong();
        CombatDispatch dispatch = new CombatDispatch(executor, new engine.sink.DispatchSinkFactory(handles), holder, worn,
                triggers.idOf("ATTACK").orElseThrow(), triggers.idOf("DEFENSE").orElseThrow(), tick::incrementAndGet);
        rig.listen(new CombatListener(dispatch));

        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        codec.write(sword, new CombatState(Map.of("enchants/venom", 1), List.of()));

        World world = plugin.getServer().getWorlds().get(0);
        Location at = world.getSpawnLocation();
        int cx = at.getBlockX() >> 4;
        int cz = at.getBlockZ() >> 4;

        Scheduling.onGlobal(() -> {
            world.setChunkForceLoaded(cx, cz, true);
            Scheduling.onRegion(at, () -> {
                // Cow victim: a normal mob's hurt path fires a real EntityDamageByEntityEvent (a player
                // victim is gated by PvP/peaceful; an armour stand has custom hurt handling), and it's not
                // undead so POISON actually applies and is observable.
                Player attacker;
                LivingEntity victim;
                try {
                    attacker = FakePlayers.spawn(world, "se_combat_atk");
                    victim = (LivingEntity) world.spawnEntity(at, EntityType.COW);
                } catch (Throwable t) {
                    h.fail("combat.enchantFiresOnHit", "victim/attacker spawn: " + t);
                    return;
                }
                int attackId = triggers.idOf("ATTACK").orElseThrow();
                Scheduling.onEntity(attacker, () -> {
                    attacker.getInventory().setItemInMainHand(sword);
                    worn.refresh(attacker, library.snapshot());
                    WornState wornState = worn.get(attacker.getUniqueId());
                    int candidates = wornState == null ? -1 : wornState.byTrigger(attackId).length;
                    plugin.getLogger().info("[combat-suite] venom candidates for attacker = " + candidates);
                    // Same spawn location, so same region as the victim; the hit fires a real EDBE.
                    victim.damage(1.0, attacker);
                    Scheduling.onEntityLater(victim, 10L, () -> {
                        h.guard("combat.enchantFiresOnHit", () -> {
                            if (candidates <= 0) {
                                throw new IllegalStateException("no Venom candidate resolved (candidates=" + candidates
                                        + ") — worn resolve/equip issue");
                            }
                            if (!victim.hasPotionEffect(poison)) {
                                throw new IllegalStateException("candidates=" + candidates
                                        + " but victim not poisoned — EDBE/dispatch issue");
                            }
                        });
                        // The proc fired the event synchronously during victim.damage(...), so by this
                        // delayed tick the probe has it (one proc → one event).
                        h.guard("combat.enchantActivateEventFired", () -> {
                            if (probe.count() != 1) {
                                throw new IllegalStateException("expected exactly 1 EnchantActivateEvent, got "
                                        + probe.count());
                            }
                            if (!"enchants/venom".equals(probe.lastKey())) {
                                throw new IllegalStateException("event carried wrong key: " + probe.lastKey());
                            }
                            if (probe.lastLevel() != 1) {
                                throw new IllegalStateException("event carried wrong level: " + probe.lastLevel());
                            }
                        });
                        victim.remove();
                        FakePlayers.despawn(attacker);
                        rig.teardown(); // unregisters the probe AND the formerly-leaked CombatListener
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

    /**
     * Records {@link EnchantActivateEvent} deliveries. Fields are concurrent/volatile: on Folia the event
     * fires on the activating entity's region thread, which may differ from the asserting thread.
     */
    private static final class EventProbe implements org.bukkit.event.Listener {
        private final AtomicInteger count = new AtomicInteger();
        private volatile String lastKey;
        private volatile int lastLevel = -1;

        @org.bukkit.event.EventHandler
        public void onActivate(EnchantActivateEvent event) {
            lastKey = event.getEnchantKey();
            lastLevel = event.getLevel();
            count.incrementAndGet();
        }

        int count() {
            return count.get();
        }

        String lastKey() {
            return lastKey;
        }

        int lastLevel() {
            return lastLevel;
        }
    }
}
