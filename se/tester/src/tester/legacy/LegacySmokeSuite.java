package tester.legacy;

import api.event.EnchantActivateEvent;
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
import engine.sink.DispatchSinkFactory;
import engine.stores.CooldownStore;
import engine.trigger.BuiltinTriggers;
import engine.trigger.TriggerRegistry;
import feature.apply.ItemEnchanter;
import feature.combat.CombatDispatch;
import feature.combat.CombatListener;
import feature.menu.EnchantMenu;
import feature.menu.MenuHolder;
import feature.menu.MenuListener;
import item.codec.CombatCodec;
import item.codec.CombatState;
import item.codec.ItemKeys;
import item.mint.ItemFactory;
import item.render.LoreRenderer;
import item.render.LoreStyle;
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
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;
import platform.caps.Capabilities;
import platform.item.ItemGroups;
import platform.resolve.RegistryResolvers;
import platform.sched.Scheduling;
import tester.fake.FakePlayers;
import tester.harness.Harness;

/**
 * The reduced smoke suite for the OPTIONAL 1.8.9 lane (docs/legacy-1.8.9-codeshare-design.md §11). The full
 * {@link tester.SeTesterPlugin} harness starts on {@code ServerLoadEvent} and registers ~38 modern-API suites,
 * many of which reference modern-only seams ({@code RuntimeHandles}, the {@code RuntimeHandles}-arg sink
 * factory) that do not exist in the legacy overlay — so neither the entry nor those suites even COMPILE against
 * craftbukkit-1.8.8. The legacy build (tester/build.gradle.kts) therefore compiles ONLY this package, driven by
 * {@link LegacySmokePlugin} instead, and the legacy seams are used directly here (the legacy
 * {@code DispatchSinkFactory} takes a {@code RenameResolvers}; potions resolve via {@code PotionEffectType}).
 *
 * <p>Every check is written 1.8-safe by hand (the tester compiles against the floor, not dual-compiled against
 * 1.8.8, so javac is NOT the net here — the live boot is): {@code getItemInHand}, never {@code getItemInMainHand};
 * no {@code World#setChunkForceLoaded} (1.13+); no off-hand / {@code Particle} / {@code Attribute} types. The
 * item checks need no player; the combat + GUI checks exercise the v1_8_R3 fake-player path (the one genuinely
 * risky NMS edge — ADR-0018), so a spawn failure isolates to those two while the item checks still report.
 *
 * <p>It drives the REAL production code (compiler, {@link ItemEnchanter}, {@link CombatDispatch}, the menus,
 * {@link ItemFactory}) so the legacy overlays are what runs. Coverage is deliberately curated, not exhaustive:
 * the combat check transitively exercises the {@code Sink} potion path, so there is no separate per-family sink
 * check; the modern matrix (run-matrix.sh) remains the exhaustive gate for 1.17.1 → 26.1.x.
 */
public final class LegacySmokeSuite implements Harness.Scenario {

    private static final String KEEN = """
            display: "&bKeen"
            applies-to: [SWORD]
            trigger: ATTACK
            levels:
              1: { chance: 100, effects: ["MODIFY_HEALTH:1"] }
              2: { chance: 100, effects: ["MODIFY_HEALTH:2"] }
            """;

    // §6.6 set with NETHERITE armour: on 1.8 those materials do not exist, so minting must DEGRADE them to the
    // DIAMOND equivalents (the item-2 fix, ItemFactory#LEGACY_FALLBACK) rather than drop to the generic fallback.
    private static final String VANGUARD = """
            display: "&bVanguard"
            complete: 4
            armor:
              pieces:
                helmet:     { material: NETHERITE_HELMET,     name: "&bVanguard Helm" }
                chestplate: { material: NETHERITE_CHESTPLATE, name: "&bVanguard Chestplate" }
                leggings:   { material: NETHERITE_LEGGINGS,   name: "&bVanguard Leggings" }
                boots:      { material: NETHERITE_BOOTS,      name: "&bVanguard Boots" }
              trigger: DEFENSE
              effects: ["MODIFY_HEALTH:1"]
            """;

    private static final String VENOM = """
            display: Venom
            trigger: ATTACK
            levels:
              1: { chance: 100, effects: ["POTION:POISON:1:80:@Victim"] }
            """;

    private final Plugin plugin;

    public LegacySmokeSuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        itemChecks(h);
        combatCheck(h);
        guiCheck(h);
    }

    // ── Item path (no player): compile → apply → render → blob survives setItemMeta → 1.8 material degrade ──

    @SuppressWarnings("deprecation") // setDisplayName(String): the floor-stable, 1.8-present item-meta path
    private void itemChecks(Harness h) {
        h.expect("legacy.item.applyAndRender");
        h.expect("legacy.item.blobSurvivesSetItemMeta");
        h.expect("legacy.item.degradesNetherite");

        CombatCodec codec = new CombatCodec(ItemKeys.of().combat());
        ItemEnchanter enchanter;
        try {
            Path root = Files.createTempDirectory("se-legacy-item");
            write(root, "enchants/keen.yml", KEEN);
            write(root, "sets/vanguard.yml", VANGUARD);
            Library library = LibraryLoader.load(root, ContentCompiler.production(), 0);
            if (library.hasErrors()) {
                h.fail("legacy.item.applyAndRender", "content failed to compile on 1.8: " + library.diagnostics());
                return;
            }
            ContentHolder holder = new ContentHolder(library);
            LoreRenderer lore = new LoreRenderer(LoreStyle.DEFAULT, key -> holder.library().displayNameOf(key));
            enchanter = new ItemEnchanter(codec, lore, holder, ItemGroups.standard());
        } catch (IOException e) {
            h.fail("legacy.item.applyAndRender", e.toString());
            return;
        }

        h.guard("legacy.item.applyAndRender", () -> {
            ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
            if (!enchanter.applyEnchant(sword, "enchants/keen", 2).ok()) {
                throw new IllegalStateException("apply of a valid enchant was rejected on 1.8");
            }
            Integer level = codec.read(sword).enchants().get("enchants/keen");
            if (level == null || level != 2) {
                throw new IllegalStateException("blob did not record the enchant at level 2 on 1.8: " + level);
            }
            if (!renderedLoreMentions(sword, "Keen")) {
                throw new IllegalStateException("lore was not rendered with the enchant name on 1.8");
            }
        });

        // THE classic 1.8 trap: there is no PDC, so the blob lives in the item's NMS NBT, and a later
        // setItemMeta() (e.g. a rename scroll) reconstructs the tag and can WIPE unknown data. The legacy
        // ItemBlobStore parks it in CraftMetaItem's unhandled-tags map, which setItemMeta serialises back —
        // this check fails loudly if that ever regresses (a modern PDC server can never exercise it).
        h.guard("legacy.item.blobSurvivesSetItemMeta", () -> {
            ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
            if (!enchanter.applyEnchant(sword, "enchants/keen", 1).ok()) {
                throw new IllegalStateException("setup: enchant did not apply");
            }
            String blobBefore = codec.readBlob(sword);
            if (blobBefore == null || blobBefore.isEmpty()) {
                throw new IllegalStateException("no blob was written by apply on 1.8");
            }
            ItemMeta meta = sword.getItemMeta();
            if (meta == null) {
                throw new IllegalStateException("diamond sword had no ItemMeta");
            }
            meta.setDisplayName("renamed after enchant"); // a mutation through the meta API, as a rename does
            sword.setItemMeta(meta);
            String blobAfter = codec.readBlob(sword);
            // Byte-identity, not just key-presence: catches partial NBT corruption, not only a full wipe.
            if (!blobBefore.equals(blobAfter)) {
                throw new IllegalStateException("blob changed across setItemMeta on 1.8 (NBT-survival regression): '"
                        + blobBefore + "' -> '" + blobAfter + "'");
            }
            if (!codec.read(sword).enchants().containsKey("enchants/keen")) {
                throw new IllegalStateException("enchant lost after setItemMeta on 1.8 (NBT-wipe trap)");
            }
        });

        // The item-2 degradation: NETHERITE_* armour has no 1.8 material, so a set mint must fall to the
        // DIAMOND equivalent, NOT the generic LEATHER_HELMET caller fallback.
        h.guard("legacy.item.degradesNetherite", () -> {
            Material degraded = ItemFactory.material("NETHERITE_HELMET", Material.LEATHER_HELMET);
            if (degraded == Material.LEATHER_HELMET) {
                throw new IllegalStateException("NETHERITE_HELMET fell to the generic LEATHER fallback, not DIAMOND");
            }
            if (degraded != Material.DIAMOND_HELMET) {
                throw new IllegalStateException("NETHERITE_HELMET did not degrade to DIAMOND_HELMET on 1.8: " + degraded);
            }
            var minted = enchanter.mintSetPiece("sets/vanguard", "helmet");
            if (minted.isEmpty() || !minted.get().getType().name().endsWith("HELMET")) {
                throw new IllegalStateException("the Vanguard helmet member did not mint to a helmet on 1.8");
            }
        });
    }

    // ── Combat spine (fake player): a Venom sword poisons a cow + fires EnchantActivateEvent (ADR-0014) ──

    private void combatCheck(Harness h) {
        h.expect("legacy.combat.enchantFiresOnHit");
        h.expect("legacy.combat.eventFired");

        // Legacy seam: the sink resolves interned handles through the SAME RegistryResolvers used to compile, so
        // the compile→runtime potion id pairs. (Modern wraps this in RuntimeHandles, which the 1.8 overlay omits.)
        RegistryResolvers resolvers = new RegistryResolvers();
        Library library;
        PotionEffectType poison = poisonType();
        try {
            Path root = Files.createTempDirectory("se-legacy-combat");
            write(root, "enchants/venom.yml", VENOM);
            library = LibraryLoader.load(root, ContentCompiler.production(resolvers), 0);
            if (library.hasErrors()) {
                h.fail("legacy.combat.enchantFiresOnHit", "venom failed to compile on 1.8: " + library.diagnostics());
                return;
            }
            if (poison == null) {
                h.fail("legacy.combat.enchantFiresOnHit", "POISON did not resolve on 1.8");
                return;
            }
        } catch (IOException e) {
            h.fail("legacy.combat.enchantFiresOnHit", e.toString());
            return;
        }

        ContentHolder holder = new ContentHolder(library);
        CombatCodec codec = new CombatCodec(ItemKeys.of().combat());
        ItemViewCache itemViews = new ItemViewCache(codec, library.snapshot().generation());
        TriggerRegistry triggers = BuiltinTriggers.registry();
        WornStateStore worn = new WornStateStore(
                new WornResolver(itemViews, triggers.count(), triggers.attackTriggers(), triggers.defenseTriggers())::resolve);
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
        CombatDispatch dispatch = new CombatDispatch(executor, new DispatchSinkFactory(resolvers), holder, worn,
                triggers.idOf("ATTACK").orElseThrow(), triggers.idOf("DEFENSE").orElseThrow(), tick::incrementAndGet);
        plugin.getServer().getPluginManager().registerEvents(new CombatListener(dispatch), plugin);

        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        codec.write(sword, new CombatState(Map.of("enchants/venom", 1), List.of()));
        int attackId = triggers.idOf("ATTACK").orElseThrow();

        World world = plugin.getServer().getWorlds().get(0);
        Location at = world.getSpawnLocation();
        // No setChunkForceLoaded here: it is 1.13+ (absent on 1.8) and the spawn chunk is kept loaded anyway.
        Scheduling.onRegion(at, () -> {
            Player attacker;
            LivingEntity victim;
            try {
                attacker = FakePlayers.spawn(world, "se_lc_atk");
                victim = (LivingEntity) world.spawnEntity(at, EntityType.COW);
            } catch (Throwable t) {
                h.fail("legacy.combat.enchantFiresOnHit", "victim/attacker spawn on 1.8: " + t);
                HandlerList.unregisterAll(probe);
                return;
            }
            Scheduling.onEntity(attacker, () -> {
                setHand(attacker, sword); // 1.8: setItemInHand, never setItemInMainHand
                worn.refresh(attacker, library.snapshot());
                WornState wornState = worn.get(attacker.getUniqueId());
                int candidates = wornState == null ? -1 : wornState.byTrigger(attackId).length;
                victim.damage(1.0, attacker);
                Scheduling.onEntityLater(victim, 10L, () -> {
                    h.guard("legacy.combat.enchantFiresOnHit", () -> {
                        if (candidates <= 0) {
                            throw new IllegalStateException("no Venom candidate resolved (candidates=" + candidates
                                    + ") — worn resolve/equip issue on 1.8");
                        }
                        if (!victim.hasPotionEffect(poison)) {
                            throw new IllegalStateException("candidates=" + candidates
                                    + " but cow not poisoned — EDBE/dispatch issue on 1.8");
                        }
                    });
                    h.guard("legacy.combat.eventFired", () -> {
                        if (probe.count() != 1) {
                            throw new IllegalStateException("expected exactly 1 EnchantActivateEvent, got " + probe.count());
                        }
                        if (!"enchants/venom".equals(probe.lastKey())) {
                            throw new IllegalStateException("event carried wrong key: " + probe.lastKey());
                        }
                    });
                    HandlerList.unregisterAll(probe);
                    victim.remove();
                    FakePlayers.despawn(attacker);
                });
            });
        });
    }

    // ── GUI (fake player): the enchant menu opens, the click routes, and the held item gains the enchant ──

    private void guiCheck(Harness h) {
        h.expect("legacy.gui.menuApplies");

        CombatCodec codec = new CombatCodec(ItemKeys.of().combat());
        EnchantMenu menu;
        try {
            Path root = Files.createTempDirectory("se-legacy-gui");
            write(root, "enchants/keen.yml", KEEN);
            Library library = LibraryLoader.load(root, ContentCompiler.production(), 0);
            if (library.hasErrors()) {
                h.fail("legacy.gui.menuApplies", "content failed to compile on 1.8: " + library.diagnostics());
                return;
            }
            ContentHolder holder = new ContentHolder(library);
            LoreRenderer lore = new LoreRenderer(LoreStyle.DEFAULT, key -> holder.library().displayNameOf(key));
            ItemEnchanter enchanter = new ItemEnchanter(codec, lore, holder, ItemGroups.standard());
            // caps drives the cross-version title cap (1.8 rejects titles > 32 chars) — already version-aware.
            menu = new EnchantMenu(holder, enchanter, player -> { }, Capabilities.probe(plugin.getServer()));
        } catch (IOException e) {
            h.fail("legacy.gui.menuApplies", e.toString());
            return;
        }

        MenuListener listener = new MenuListener();
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);

        World world = plugin.getServer().getWorlds().get(0);
        Location at = world.getSpawnLocation();
        Scheduling.onRegion(at, () -> {
            Player player;
            try {
                player = FakePlayers.spawn(world, "se_lc_gui");
            } catch (Throwable t) {
                h.fail("legacy.gui.menuApplies", "spawn on 1.8: " + t);
                HandlerList.unregisterAll(listener);
                return;
            }
            Scheduling.onEntity(player, () -> {
                setHand(player, new ItemStack(Material.DIAMOND_SWORD));
                MenuHolder menuHolder = new MenuHolder(menu);
                menu.render(menuHolder);
                InventoryView view = player.openInventory(menuHolder.getInventory());
                InventoryClickEvent click = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER,
                        0, ClickType.LEFT, InventoryAction.PICKUP_ALL);
                plugin.getServer().getPluginManager().callEvent(click);
                h.guard("legacy.gui.menuApplies", () -> {
                    if (!click.isCancelled()) {
                        throw new IllegalStateException("menu click was not cancelled on 1.8 — items could be moved");
                    }
                    CombatState state = codec.read(handItem(player));
                    if (!state.enchants().containsKey("enchants/keen")) {
                        throw new IllegalStateException("held item did not gain enchants/keen after the click on 1.8; "
                                + "enchants=" + state.enchants());
                    }
                });
                player.closeInventory();
                HandlerList.unregisterAll(listener);
                FakePlayers.despawn(player);
            });
        });
    }

    // ── 1.8-safe helpers (the floor's deprecated-not-removed API is the only one 1.8 has) ──

    @SuppressWarnings("deprecation")
    private static PotionEffectType poisonType() {
        return PotionEffectType.getByName("POISON");
    }

    @SuppressWarnings("deprecation")
    private static void setHand(Player player, ItemStack stack) {
        player.getInventory().setItemInHand(stack);
    }

    @SuppressWarnings("deprecation")
    private static ItemStack handItem(Player player) {
        return player.getInventory().getItemInHand();
    }

    @SuppressWarnings("deprecation") // getLore(): deprecated-not-removed across the whole range.
    private static boolean renderedLoreMentions(ItemStack stack, String fragment) {
        ItemMeta meta = stack.getItemMeta();
        List<String> lore = meta == null ? null : meta.getLore();
        return lore != null && lore.stream().anyMatch(line -> line.contains(fragment));
    }

    private static void write(Path root, String relative, String yaml) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, yaml, StandardCharsets.UTF_8);
    }

    /** Records {@link EnchantActivateEvent} deliveries; volatile/atomic since the proc fires on the hit thread. */
    private static final class EventProbe implements org.bukkit.event.Listener {
        private final AtomicInteger count = new AtomicInteger();
        private volatile String lastKey;

        @org.bukkit.event.EventHandler
        public void onActivate(EnchantActivateEvent event) {
            lastKey = event.getEnchantKey();
            count.incrementAndGet();
        }

        int count() {
            return count.get();
        }

        String lastKey() {
            return lastKey;
        }
    }
}
