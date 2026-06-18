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
import tester.harness.Harness;

/**
 * The end-to-end combat spine, live (ADR-0014; §3.3, §3.6): a fake player wielding an enchanted weapon
 * hits a victim, and the enchant's effect lands — exercising the WHOLE runtime in one shot, on a real
 * server, on Paper and Folia. The enchant is {@code Venom} (ATTACK, {@code POTION:POISON:1:80:@Victim}),
 * so it also proves the §9 RESOLVER PAIRING: the potion handle is interned at compile time and resolved
 * back to a live {@code PotionEffectType} at runtime through the SAME retained {@code RegistryResolvers}.
 *
 * <p>Flow: compile Venom with a retained resolver → wire the executor + {@link CombatDispatch} with the
 * paired {@link RuntimeHandles} → register {@link CombatListener} → spawn a cow victim and a fake-player
 * attacker holding a Venom sword → resolve the attacker's WornState → {@code victim.damage(1, attacker)}
 * fires a real {@code EntityDamageByEntityEvent} → the listener dispatches → POTION:@Victim routes to the
 * victim's thread → the victim is poisoned. Mojang-mapped only (needs the fake-player attacker).
 */
public final class CombatSuite implements Harness.Scenario {

    private static final String VENOM = """
            display: Venom
            trigger: ATTACK
            levels:
              1: { chance: 100, effects: ["POTION:POISON:1:80:@Victim"] }
            """;

    private final Plugin plugin;

    public CombatSuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        h.expect("combat.enchantFiresOnHit");
        h.expect("combat.enchantActivateEventFired");

        // Wire the runtime with a retained resolver so the runtime handle round-trip pairs.
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
        CombatCodec codec = new CombatCodec(ItemKeys.of(plugin).combat());
        ItemViewCache itemViews = new ItemViewCache(codec, library.snapshot().generation());
        TriggerRegistry triggers = BuiltinTriggers.registry();
        WornStateStore worn = new WornStateStore(
                new WornResolver(itemViews, triggers.count(), triggers.attackTriggers(), triggers.defenseTriggers())::resolve);
        // A probe receives the public EnchantActivateEvent; the executor's activation listener fires it
        // per proc (the §13 api seam) exactly as the composition root does — resolving the stable key
        // against the live snapshot. Firing happens on the firing thread (the entity's region on Folia),
        // so this also proves the event survives a region-thread dispatch.
        EventProbe probe = new EventProbe();
        plugin.getServer().getPluginManager().registerEvents(probe, plugin);
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
        CombatDispatch dispatch = new CombatDispatch(executor, handles, holder, worn,
                triggers.idOf("ATTACK").orElseThrow(), triggers.idOf("DEFENSE").orElseThrow(), tick::incrementAndGet);
        plugin.getServer().getPluginManager().registerEvents(new CombatListener(dispatch), plugin);

        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        codec.write(sword, new CombatState(Map.of("enchants/venom", 1), List.of()));

        World world = plugin.getServer().getWorlds().get(0);
        Location at = world.getSpawnLocation();
        int cx = at.getBlockX() >> 4;
        int cz = at.getBlockZ() >> 4;

        Scheduling.onGlobal(() -> {
            world.setChunkForceLoaded(cx, cz, true);
            Scheduling.onRegion(at, () -> {
                // Attacker = fake player (must carry a WornState). Victim = a passive, non-undead mob
                // (a cow): a normal mob takes the standard LivingEntity hurt path, so a programmatic
                // damage() fires a real EntityDamageByEntityEvent — unlike a player victim (PvP/peaceful
                // gating rejects the hit before the event) or an armour stand (custom hurt handling).
                // A cow is not undead, so POISON actually applies and is observable.
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
                    // Same spawn location ⇒ same region as the victim; the hit fires a real EDBE.
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
                        // The proc fired EnchantActivateEvent synchronously during victim.damage(...),
                        // so by this delayed tick the probe has already received it (one proc → one event).
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
                        org.bukkit.event.HandlerList.unregisterAll(probe);
                        victim.remove();
                        FakePlayers.despawn(attacker);
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
     * A Bukkit listener that records {@link EnchantActivateEvent} deliveries — proves the public api event
     * actually reaches a registered handler end-to-end. Fields are concurrent/volatile because on Folia the
     * event fires on the activating entity's region thread, which may differ from the asserting thread.
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
