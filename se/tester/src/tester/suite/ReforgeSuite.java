package tester.suite;

import compile.Compiler;
import compile.load.ContentHolder;
import compile.load.CrystalConfig;
import compile.load.Library;
import compile.load.LibraryLoader;
import compile.load.MasterConfig;
import compile.load.PetFoodConfig;
import compile.load.PetItemConfig;
import compile.load.ReforgeItemConfig;
import engine.boot.ContentCompiler;
import engine.effect.kind.BuiltinEffects;
import engine.interact.SoulSpender;
import engine.pipeline.ActivationPipeline;
import engine.run.AbilityExecutor;
import engine.run.AreaScan;
import engine.selector.kind.BuiltinSelectors;
import engine.stores.CooldownStore;
import engine.stores.EngineStores;
import engine.trigger.BuiltinTriggers;
import engine.trigger.TriggerRegistry;
import feature.apply.ItemEnchanter;
import feature.crystal.CrystalListener;
import feature.crystal.CrystalService;
import feature.fx.ParticleFx;
import feature.pet.PetArmedStore;
import feature.pet.PetHomeStore;
import feature.pet.PetHomeVisuals;
import feature.pet.PetLevelCue;
import feature.pet.PetMessenger;
import feature.pet.PetService;
import feature.pet.PetSharedUseStore;
import feature.pet.PetWornSource;
import feature.reforge.ReforgeListener;
import feature.reforge.ReforgeMessenger;
import feature.reforge.ReforgeRunner;
import feature.reforge.ReforgeService;
import feature.reforge.ReforgeUseListener;
import feature.trigger.TriggerDispatch;
import item.codec.CombatCodec;
import item.codec.CombatState;
import item.codec.CrystalExtractorCodec;
import item.codec.CrystalItemCodec;
import item.codec.ItemKeys;
import item.codec.PetCodec;
import item.codec.ReforgeCodec;
import item.head.HeadEquip;
import item.head.TexturedHeads;
import item.mint.VanillaEnchants;
import item.render.LoreRenderer;
import item.render.LoreStyle;
import item.view.ItemViewCache;
import item.worn.WornResolver;
import item.worn.WornStateStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;
import platform.item.ItemGroups;
import platform.lang.Messages;
import platform.resolve.RegistryResolvers;
import platform.resolve.RuntimeHandles;
import platform.sched.Scheduling;
import platform.sched.TaskHandle;
import schema.spec.HandleCategory;
import tester.harness.CombatRig;
import tester.harness.Harness;

/**
 * The weapon-reforge family live, end-to-end (ADR-0070): the seams only a booted server proves. Own wired stack
 * over the REAL PDC store — the tester installs NO production wiring — with one test-owned reforge
 * ({@code reforges/testforge}, a Speed-II active), a crystal, and four pet defs, all compiled into one temp
 * library. Clientless fake players; sneak staged with {@code setSneaking}; every wait tick-anchored.
 *
 * <p>Proves: (a) a real {@link InventoryClickEvent} drag-apply stamps the weapon's combat blob and the occupied
 * socket / non-weapon target are refused without consuming the cursor; (b) the ONE Item Extractor pops the
 * reforge FIRST, intact, ahead of a co-resident crystal; (c) the on-weapon reforge lore line renders exactly
 * once (compared against the production renderer's own output); (d) SHIFT + right-click fires the active
 * through the shared pipeline (Speed lands, event cancelled) while a plain right-click falls through, and an
 * immediate second use is cooldown-gated (no re-fire); and (e) the folded-in 2s shared pet gate blocks a burst
 * of DIFFERENT pet actives while EXEMPTING a Mole's own home recall (owner ruling 2026-07-18).
 */
@SuppressWarnings("deprecation") // getCursor/setCursor/setItem/getLore: the floor-stable view/meta path the base uses
public final class ReforgeSuite implements Harness.Scenario {

    private static final String TESTFORGE = """
            display: Testforge
            color: "&6"
            icon: SUGAR
            cooldown: 200
            chance: 100
            effects:
              - { POTION: { effect: SPEED, level: 2, duration: 200, who: "@Self" } }
            """;

    private static final String SPARK = """
            display: Spark
            applies-to: [ALL]
            trigger: ATTACK
            chance: 100
            effects: [{ MODIFY_HEALTH: { amount: 1 } }]
            """;

    // Two ACTIVE potion pets for the shared-gate burst — distinct effects so "A fired / B blocked" is a clean read.
    private static final String PET_ALPHA = """
            display: PetAlpha
            type: ACTIVE
            levels:
              1: { cooldown: 0, chance: 100, effects: [ { POTION: { effect: SPEED, level: 1, duration: 200, who: "@Self" } } ] }
            """;
    private static final String PET_BETA = """
            display: PetBeta
            type: ACTIVE
            levels:
              1: { cooldown: 0, chance: 100, effects: [ { POTION: { effect: SLOW, level: 1, duration: 200, who: "@Self" } } ] }
            """;

    // A digger (window 600 ≫ 40t gate) + a different potion pet for the recall-exempt check.
    private static final String MOLE_FORGE = """
            display: MoleForge
            type: ACTIVE
            levels:
              1: { cooldown: 0, condition: "%sneaking%", effects: [ { DIG_HOME: { window: 600, range: 50 } } ] }
            """;
    private static final String PET_GAMMA = """
            display: PetGamma
            type: ACTIVE
            levels:
              1: { cooldown: 0, chance: 100, effects: [ { POTION: { effect: SPEED, level: 1, duration: 200, who: "@Self" } } ] }
            """;

    private static final String REFORGE_KEY = "reforges/testforge";
    private static final String CRYSTAL_KEY = "crystals/spark";

    private static final String APPLY = "reforge.applyStampsWeapon";
    private static final String OCCUPIED = "reforge.occupiedSocketRejected";
    private static final String NON_WEAPON = "reforge.nonWeaponRejected";
    private static final String EXTRACT_FIRST = "reforge.extractorPopsReforgeFirst";
    private static final String LORE = "reforge.loreLineRendered";
    private static final String ACTIVATION = "reforge.activationFires";
    private static final String COOLDOWN = "reforge.secondUseCooldownGated";
    private static final String BURST = "reforge.petSharedGateBlocksBurst";
    private static final String RECALL_EXEMPT = "reforge.petSharedGateExemptsRecall";

    /** Ticks to let a @Self potion (scheduled through the sink) land before asserting it. */
    private static final long POTION_SETTLE = 12L;
    /** Ticks to let an async recall teleport settle before reading position. */
    private static final long HOP_SETTLE = 10L;
    /** > SHARED_USE_GATE_TICKS (40): re-firing the burst pet after the shared gate has re-opened. */
    private static final long GATE_REOPEN = 55L;

    private final Plugin plugin;

    public ReforgeSuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        h.expect(APPLY);
        h.expect(OCCUPIED);
        h.expect(NON_WEAPON);
        h.expect(EXTRACT_FIRST);
        h.expect(LORE);
        h.expect(ACTIVATION);
        h.expect(COOLDOWN);
        h.expect(BURST);
        h.expect(RECALL_EXEMPT);

        RegistryResolvers resolvers = new RegistryResolvers();
        Compiler compiler = ContentCompiler.production(resolvers);
        RuntimeHandles handles = new RuntimeHandles(resolvers);

        Library library;
        PotionEffectType speed;
        PotionEffectType slow;
        try {
            Path root = Files.createTempDirectory("se-reforge-suite");
            write(root, "reforges/testforge.yml", TESTFORGE);
            write(root, "crystals/spark.yml", SPARK);
            write(root, "pets/pet-alpha.yml", PET_ALPHA);
            write(root, "pets/pet-beta.yml", PET_BETA);
            write(root, "pets/mole-forge.yml", MOLE_FORGE);
            write(root, "pets/pet-gamma.yml", PET_GAMMA);
            library = LibraryLoader.load(root, compiler, 0);
            if (library.hasErrors()) {
                failAll(h, "reforge content failed to compile: " + library.diagnostics());
                return;
            }
            speed = (PotionEffectType) handles.resolveByName(HandleCategory.POTION_EFFECT, "SPEED");
            slow = (PotionEffectType) handles.resolveByName(HandleCategory.POTION_EFFECT, "SLOW");
            if (speed == null || slow == null) {
                failAll(h, "SPEED/SLOW did not resolve on this version");
                return;
            }
        } catch (IOException e) {
            failAll(h, e.toString());
            return;
        }

        ContentHolder holder = new ContentHolder(library);
        ItemKeys keys = ItemKeys.of();
        CombatCodec combat = new CombatCodec(keys.combat(), Stores.state());
        ReforgeCodec reforgeCodec = new ReforgeCodec(keys, Stores.state());
        PetCodec petCodec = new PetCodec(keys, Stores.state());
        CrystalItemCodec crystalItemCodec = new CrystalItemCodec(keys.crystalItem(), Stores.state());
        CrystalExtractorCodec crystalExtractorCodec = new CrystalExtractorCodec(keys.crystalExtractor(), Stores.state());

        ItemViewCache itemViews = new ItemViewCache(combat, library.snapshot().generation());
        TriggerRegistry triggers = BuiltinTriggers.registry();
        PetArmedStore armed = new PetArmedStore();
        PetHomeStore homes = new PetHomeStore();
        PetSharedUseStore sharedGate = new PetSharedUseStore();
        EngineStores stores = EngineStores.fresh();

        AtomicLong tick = new AtomicLong();  // the SinkEnv counter (advances per sink op — drives the reforge cooldown)
        AtomicLong clock = new AtomicLong(); // the pet/home/gate clock, advanced by the real 1-tick repeater below

        PetWornSource petSource = new PetWornSource(() -> true, petCodec, holder::library, armed, clock::get);
        WornStateStore worn = new WornStateStore(new WornResolver(Stores.equip(), itemViews, triggers.count(),
                triggers.attackTriggers(), triggers.defenseTriggers(), () -> WornResolver.Features.ALL, Set::of,
                petSource)::resolve);
        AbilityExecutor executor = new AbilityExecutor(BuiltinEffects.registry(), BuiltinSelectors.registry(),
                new ActivationPipeline(new CooldownStore(), SoulSpender.NONE), AreaScan.NONE);
        engine.sink.SinkEnv env = engine.sink.SinkEnv.of(platform.economy.EconomyService.NONE,
                engine.sink.SoulDebit.NONE, stores, tick::incrementAndGet);
        TriggerDispatch dispatch = new TriggerDispatch(executor,
                dsEnv -> new engine.sink.ModernDispatchSink(handles, dsEnv), Stores.probe(), holder, worn, triggers,
                actor -> Optional.empty(), env, Stores.hands(), Stores.dropControl());

        Messages messages = Messages.defaults();
        // ADR-0070: mirror BootCore's on-weapon {NAME} lookup — a reforges/<stem> key resolves to the reforge's
        // PLAIN display (owner spec "{NAME} = reforge display"), not the enchant/crystal/set chain (which would
        // fall to the unknown-enchant label). Plain, NOT colour-styled: the `&r{NAME}` frame's reset stays
        // meaningful, so the composed line is round-trip-stable through setLore/getLore.
        LoreRenderer lore = Stores.lore(LoreRenderer.Config
                .of(LoreStyle.DEFAULT, key -> {
                    compile.load.ReforgeDef rd = holder.library().reforgeDefOf(key);
                    return rd != null ? rd.display() : holder.library().displayNameOf(key);
                })
                .withReforgeLine(() -> ReforgeItemConfig.defaults().loreWhileOnItem()) // ADR-0070 on-weapon line
                .withCrystalLine(() -> "&8Crystal: {CRYSTAL}"));
        ItemEnchanter enchanter = new ItemEnchanter(combat, lore, holder, ItemGroups.standard(),
                () -> ItemEnchanter.DEFAULT_BASE_SLOTS, () -> ItemEnchanter.DEFAULT_CRYSTAL_SLOTS,
                () -> ItemEnchanter.DEFAULT_MAX_MERGE, () -> List.of("SWORD", "AXE"), messages, VanillaEnchants.NONE);
        ReforgeService reforgeService = new ReforgeService(reforgeCodec, combat, enchanter, holder,
                ReforgeItemConfig::defaults, () -> List.of("SWORD", "AXE"), messages, VanillaEnchants.NONE);
        ReforgeRunner runner = new ReforgeRunner(holder, dispatch,
                new ReforgeMessenger(messages, compile.load.MasterConfig.ReforgesSection::defaults), (p, d) -> {
                }, stores, () -> 0L); // testforge's POTION runs in-engine; no ReforgeMachines marker to route
        ReforgeUseListener reforgeUse = new ReforgeUseListener(runner, combat, Stores.hands(), () -> true);
        ReforgeListener reforgeApply = new ReforgeListener(reforgeService, messages, Stores.sounds());
        CrystalService crystals = new CrystalService(crystalItemCodec, crystalExtractorCodec, enchanter, holder,
                CrystalConfig::defaults, () -> 4, reforgeService, messages);
        CrystalListener crystalApply = new CrystalListener(crystals, messages, Stores.sounds());

        PetMessenger messenger = new PetMessenger(messages, MasterConfig.PetsSection::defaults);
        PetLevelCue cue = new PetLevelCue(MasterConfig.PetsSection::defaults, ParticleFx.NONE, feature.compat.Sounds.NONE);
        PetHomeVisuals visuals = new PetHomeVisuals(dispatch, homes, clock::get, resolvers);
        PetService pets = new PetService(holder, petCodec, dispatch, TexturedHeads.NONE, HeadEquip.NONE,
                VanillaEnchants.NONE, messenger, armed, sharedGate, MasterConfig.PetsSection::defaults,
                PetItemConfig::defaults, PetFoodConfig::defaults, p -> worn.refresh(p, holder.snapshot()),
                clock::get, m -> m == Material.AIR, cue, new Random(42), homes, stores.teleblock(), visuals);

        CombatRig rig = new CombatRig(plugin);
        TaskHandle[] clockTask = new TaskHandle[1];
        clockTask[0] = Scheduling.repeatingGlobal(1L, 1L, clock::incrementAndGet);

        // Three independent flows (isolated fake players → no potion/position cross-talk); the last to finish
        // cancels the clock and tears the rig down.
        AtomicInteger remaining = new AtomicInteger(3);
        Runnable onDone = () -> {
            if (remaining.decrementAndGet() == 0) {
                if (clockTask[0] != null) {
                    clockTask[0].cancel();
                }
                rig.teardown();
            }
        };

        Consumer<Player> reforgeItemFlow = player ->
                runReforgeItemFlow(h, player, combat, reforgeCodec, reforgeService, crystals, crystalApply,
                        reforgeApply, reforgeUse, lore, speed, onDone);
        Consumer<Player> burstFlow = player ->
                runBurstFlow(h, player, petCodec, pets, speed, slow, onDone);
        Consumer<Player> recallFlow = player ->
                runRecallFlow(h, player, petCodec, pets, homes, clock, speed, onDone);

        World world = plugin.getServer().getWorlds().get(0);
        Location at = world.getSpawnLocation();
        rig.onArena(at, () -> {
            Player pr;
            Player p1;
            Player p2;
            try {
                pr = rig.spawnFake(world, "se_reforge");
                p1 = rig.spawnFake(world, "se_reforge_p1");
                p2 = rig.spawnFake(world, "se_reforge_p2");
            } catch (Throwable t) {
                failAll(h, "fake-player spawn: " + t);
                return;
            }
            Scheduling.onEntity(pr, () -> reforgeItemFlow.accept(pr));
            Scheduling.onEntity(p1, () -> burstFlow.accept(p1));
            Scheduling.onEntity(p2, () -> recallFlow.accept(p2));
        });
    }

    // ── Flow 1: apply / occupied / non-weapon / extractor-first / lore / activation / cooldown (one player) ──

    private void runReforgeItemFlow(Harness h, Player player, CombatCodec combat, ReforgeCodec reforgeCodec,
                                    ReforgeService reforgeService, CrystalService crystals, CrystalListener crystalApply,
                                    ReforgeListener reforgeApply, ReforgeUseListener reforgeUse, LoreRenderer lore,
                                    PotionEffectType speed, Runnable onDone) {
        h.guard(APPLY, () -> {
            player.getInventory().clear();
            InventoryView view = openChest(player);
            int raw = view.getTopInventory().getSize();
            view.setItem(raw, new ItemStack(Material.DIAMOND_SWORD));
            view.setCursor(reforgeService.mint(REFORGE_KEY));
            InventoryClickEvent event = click(view, raw);
            reforgeApply.onGestureClick(event);

            if (!event.isCancelled()) {
                throw new IllegalStateException("the apply click was not cancelled");
            }
            ItemStack after = view.getItem(raw);
            if (after == null || !REFORGE_KEY.equals(combat.read(after).reforgeKey())) {
                throw new IllegalStateException("the sword did not gain the reforge key");
            }
            if (notEmpty(view.getCursor())) {
                throw new IllegalStateException("the reforge on the cursor was not consumed");
            }
        });

        h.guard(OCCUPIED, () -> {
            player.getInventory().clear();
            InventoryView view = openChest(player);
            int raw = view.getTopInventory().getSize();
            ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
            reforgeService.apply(reforgeService.mint(REFORGE_KEY), sword); // pre-occupy the socket
            view.setItem(raw, sword);
            view.setCursor(reforgeService.mint(REFORGE_KEY));
            InventoryClickEvent event = click(view, raw);
            reforgeApply.onGestureClick(event);

            // Occupied → noop: the cursor is kept and the socket is unchanged (a second reforge never lands).
            if (!notEmpty(view.getCursor())) {
                throw new IllegalStateException("an occupied-socket apply still consumed the cursor");
            }
            if (!REFORGE_KEY.equals(combat.read(view.getItem(raw)).reforgeKey())) {
                throw new IllegalStateException("the occupied socket's reforge key changed");
            }
        });

        h.guard(NON_WEAPON, () -> {
            player.getInventory().clear();
            InventoryView view = openChest(player);
            int raw = view.getTopInventory().getSize();
            view.setItem(raw, new ItemStack(Material.DIAMOND_CHESTPLATE));
            view.setCursor(reforgeService.mint(REFORGE_KEY));
            InventoryClickEvent event = click(view, raw);
            reforgeApply.onGestureClick(event);

            if (!notEmpty(view.getCursor())) {
                throw new IllegalStateException("a non-weapon apply still consumed the cursor");
            }
            if (combat.read(view.getItem(raw)).reforgeKey() != null) {
                throw new IllegalStateException("a chestplate was stamped with a reforge");
            }
        });

        h.guard(EXTRACT_FIRST, () -> {
            player.getInventory().clear();
            InventoryView view = openChest(player);
            int raw = view.getTopInventory().getSize();
            ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
            crystals.interact(crystals.mint(List.of(CRYSTAL_KEY)), sword); // gear now carries a crystal
            reforgeService.apply(reforgeService.mint(REFORGE_KEY), sword);  // AND a reforge
            if (combat.read(sword).reforgeKey() == null || combat.read(sword).crystals().isEmpty()) {
                throw new IllegalStateException("setup: the sword did not carry both a reforge and a crystal");
            }
            List<String> crystalsBefore = List.copyOf(combat.read(sword).crystals());
            view.setItem(raw, sword);
            view.setCursor(crystals.mintExtractor());
            InventoryClickEvent event = click(view, raw);
            crystalApply.onGestureClick(event);

            ItemStack after = view.getItem(raw);
            if (after == null || combat.read(after).reforgeKey() != null) {
                throw new IllegalStateException("the extractor did not pop the reforge first");
            }
            if (!combat.read(after).crystals().equals(crystalsBefore)) {
                throw new IllegalStateException("the extractor disturbed the crystal stack");
            }
            boolean produced = false;
            for (ItemStack it : player.getInventory().getContents()) {
                if (it != null && REFORGE_KEY.equals(reforgeCodec.keyOf(it))) {
                    produced = true;
                    break;
                }
            }
            if (!produced) {
                throw new IllegalStateException("the popped reforge was not handed back as a reforge item");
            }
        });

        h.guard(LORE, () -> {
            ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
            reforgeService.apply(reforgeService.mint(REFORGE_KEY), sword);
            CombatState state = combat.read(sword);
            if (state.reforgeKey() == null) {
                throw new IllegalStateException("setup: the reforge did not apply");
            }
            // The reforge line = the ONE line the production renderer adds when the reforge is present. Both sides
            // are LoreRenderer output (never a rebuilt string, contracts §11.4).
            List<String> withLine = lore.lines(state);
            List<String> withoutLine = lore.lines(state.withReforge(null));
            List<String> diff = new ArrayList<>(withLine);
            diff.removeAll(withoutLine);
            if (diff.size() != 1) {
                throw new IllegalStateException("the reforge line is not the single added lore line: " + diff);
            }
            String reforgeLine = diff.get(0);
            List<String> actual = sword.getItemMeta() == null ? null : sword.getItemMeta().getLore();
            long count = actual == null ? 0 : actual.stream().filter(reforgeLine::equals).count();
            if (count != 1) {
                throw new IllegalStateException("the weapon lore carries the reforge line " + count + " time(s): " + actual);
            }
        });

        // Activation: a reforged weapon held in the main hand; a plain right-click falls through, sneak fires.
        player.closeInventory();
        player.getInventory().clear();
        ItemStack weapon = new ItemStack(Material.DIAMOND_SWORD);
        reforgeService.apply(reforgeService.mint(REFORGE_KEY), weapon);
        player.getInventory().setItemInMainHand(weapon);

        // A synthetic RIGHT_CLICK_AIR event has no clicked block, so useInteractedBlock()==DENY and thus
        // isCancelled()==true FROM CONSTRUCTION (paper-api PlayerInteractEvent contract) — regardless of any
        // listener. So isCancelled() cannot tell "fell through untouched" from "claimed". The listener claims by
        // setCancelled(true), which flips useItemInHand() to DENY; an untouched event keeps it DEFAULT. Read that.
        player.setSneaking(false);
        PlayerInteractEvent plain = interact(player, weapon);
        reforgeUse.onInteract(plain);
        boolean plainClaimed = plain.useItemInHand() == org.bukkit.event.Event.Result.DENY;

        player.setSneaking(true);
        PlayerInteractEvent sneak = interact(player, weapon);
        reforgeUse.onInteract(sneak);
        boolean sneakClaimed = sneak.useItemInHand() == org.bukkit.event.Event.Result.DENY;
        player.setSneaking(false);

        Scheduling.onEntityLater(player, POTION_SETTLE, () -> {
            h.guard(ACTIVATION, () -> {
                if (plainClaimed) {
                    throw new IllegalStateException("a non-sneak right-click was claimed (event cancelled)");
                }
                if (!sneakClaimed) {
                    throw new IllegalStateException("the sneak right-click did not cancel the event");
                }
                if (!player.hasPotionEffect(speed)) {
                    throw new IllegalStateException("the reforge active did not apply Speed");
                }
            });
            // Cooldown: strip Speed, then sneak-click again while still on cooldown — it must NOT be re-applied.
            player.removePotionEffect(speed);
            player.setSneaking(true);
            PlayerInteractEvent second = interact(player, weapon);
            reforgeUse.onInteract(second);
            player.setSneaking(false);
            Scheduling.onEntityLater(player, POTION_SETTLE, () -> {
                h.guard(COOLDOWN, () -> {
                    if (player.hasPotionEffect(speed)) {
                        throw new IllegalStateException("a second use re-fired while on cooldown (Speed re-applied)");
                    }
                });
                onDone.run();
            });
        });
    }

    // ── Flow 2: the shared gate blocks a burst of DIFFERENT pet actives ──

    private void runBurstFlow(Harness h, Player player, PetCodec petCodec, PetService pets,
                              PotionEffectType speed, PotionEffectType slow, Runnable onDone) {
        ItemStack alpha = maxLevelPet(petCodec, "pet-alpha");
        ItemStack beta = maxLevelPet(petCodec, "pet-beta");

        pets.use(player, alpha); // activates → arms the shared gate (Speed)
        pets.use(player, beta);  // a DIFFERENT pet within the same tick → gate-blocked (no Slow)

        Scheduling.onEntityLater(player, POTION_SETTLE, () -> {
            h.guard(BURST, () -> {
                if (!player.hasPotionEffect(speed)) {
                    throw new IllegalStateException("pet A did not activate (Speed absent)");
                }
                if (player.hasPotionEffect(slow)) {
                    throw new IllegalStateException("pet B fired inside the shared gate window (Slow present)");
                }
            });
            // Past the 40t gate: pet B now fires.
            Scheduling.onEntityLater(player, GATE_REOPEN, () -> {
                pets.use(player, beta);
                Scheduling.onEntityLater(player, POTION_SETTLE, () -> {
                    h.guard(BURST, () -> {
                        if (!player.hasPotionEffect(slow)) {
                            throw new IllegalStateException("pet B did not fire after the shared gate re-opened (Slow absent)");
                        }
                    });
                    onDone.run();
                });
            });
        });
    }

    // ── Flow 3: the shared gate EXEMPTS a Mole's own home recall (owner ruling 2026-07-18, R1) ──

    private void runRecallFlow(Harness h, Player player, PetCodec petCodec, PetService pets, PetHomeStore homes,
                               AtomicLong clock, PotionEffectType speed, Runnable onDone) {
        ItemStack mole = maxLevelPet(petCodec, "mole-forge");
        ItemStack gamma = maxLevelPet(petCodec, "pet-gamma");

        Location dug = player.getLocation().clone();
        player.setSneaking(true);
        pets.use(player, mole); // DIG → arms the shared gate AND the home window
        player.setSneaking(false);
        boolean homeArmed = homes.get(player.getUniqueId(), clock.get()) != null;

        // A DIFFERENT pet inside the gate window is refused (no Speed) — the gate still guards fresh uses.
        pets.use(player, gamma);

        Scheduling.onEntityLater(player, POTION_SETTLE, () -> {
            boolean gammaRefused = !player.hasPotionEffect(speed);
            // The SAME pet's recall is EXEMPT: even inside the gate window it teleports home. Hop away first
            // (within range 50) so the return trip is observable as a position change.
            hop(player, dug.clone().add(5, 0, 0), () -> {
                pets.use(player, mole); // recall — resolved BEFORE the gate check (R1)
                Scheduling.onEntityLater(player, HOP_SETTLE, () -> {
                    h.guard(RECALL_EXEMPT, () -> {
                        if (!homeArmed) {
                            throw new IllegalStateException("the dig did not arm the home window");
                        }
                        if (!gammaRefused) {
                            throw new IllegalStateException("a different pet was NOT refused inside the shared gate");
                        }
                        double d = player.getLocation().distanceSquared(dug);
                        if (d > 2.25) {
                            throw new IllegalStateException("the same pet's recall did not teleport home: d²=" + d);
                        }
                        if (homes.get(player.getUniqueId(), clock.get()) != null) {
                            throw new IllegalStateException("the recall did not consume the home window");
                        }
                    });
                    onDone.run();
                });
            });
        });
    }

    private ItemStack maxLevelPet(PetCodec petCodec, String key) {
        ItemStack stack = new ItemStack(Material.PAPER);
        petCodec.stamp(stack, key, Integer.MAX_VALUE); // clamps to the universal max → no exp churn / writeback
        return stack;
    }

    /**
     * Folia-safe staging hop: a synchronous teleport throws under region threading, so hop with
     * {@code teleportAsync} and continue once it lands. Always continues (a refused hop surfaces as a resolved
     * guard FAIL, never a stall).
     */
    private static void hop(Player player, Location to, Runnable then) {
        player.teleportAsync(to).whenComplete((landed, err) -> Scheduling.onEntityLater(player, HOP_SETTLE, then));
    }

    private InventoryView openChest(Player player) {
        Inventory chest = plugin.getServer().createInventory(null, 27);
        return player.openInventory(chest);
    }

    /** A LEFT swap-with-cursor click at {@code rawSlot} — the drag-onto-gear gesture the base claims. */
    private InventoryClickEvent click(InventoryView view, int rawSlot) {
        return new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, rawSlot, ClickType.LEFT,
                InventoryAction.SWAP_WITH_CURSOR);
    }

    /** A synthetic main-hand right-click; the listener reads the live main hand, not the event item. */
    private PlayerInteractEvent interact(Player player, ItemStack held) {
        return new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, held, null, BlockFace.SELF, EquipmentSlot.HAND);
    }

    private static boolean notEmpty(ItemStack stack) {
        return stack != null && stack.getType() != Material.AIR && stack.getAmount() > 0;
    }

    private void failAll(Harness h, String reason) {
        h.fail(APPLY, reason);
        h.fail(OCCUPIED, reason);
        h.fail(NON_WEAPON, reason);
        h.fail(EXTRACT_FIRST, reason);
        h.fail(LORE, reason);
        h.fail(ACTIVATION, reason);
        h.fail(COOLDOWN, reason);
        h.fail(BURST, reason);
        h.fail(RECALL_EXEMPT, reason);
    }

    private static void write(Path root, String relative, String yaml) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, yaml, StandardCharsets.UTF_8);
    }
}
