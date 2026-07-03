package bootstrap;

import bootstrap.wire.BootCore;
import compile.Compiler;
import compile.load.ContentHolder;
import compile.load.ItemsHolder;
import compile.load.ItemsLoader;
import compile.load.LangHolder;
import compile.load.LangLoader;
import compile.load.MasterConfig;
import compile.load.MasterConfigHolder;
import compile.load.MasterConfigLoader;
import compile.load.MenusHolder;
import compile.load.MenusLoader;
import engine.boot.RegistryFingerprint;
import engine.effect.EffectKind;
import engine.effect.EffectRegistry;
import api.StarEnchantsApi;
import api.event.StarEnchantsReloadEvent;
import engine.run.AbilityExecutor;
import engine.stores.RepeatStore;
import engine.stores.SoulModeStore;
import engine.trigger.TriggerRegistry;
import feature.apply.ItemEnchanter;
import feature.carrier.CarrierListener;
import feature.carrier.CarrierService;
import feature.combat.CombatDispatch;
import feature.combat.CombatListener;
import feature.combat.EquipListener;
import feature.combat.ImmuneListener;
import feature.combat.KeepOnDeathListener;
import feature.combat.KnockbackListener;
import feature.combat.MentalKnockbackBridge;
import feature.combat.TeleblockListener;
import feature.crystal.CrystalListener;
import feature.crystal.CrystalService;
import feature.heroic.HeroicListener;
import feature.heroic.HeroicService;
import feature.book.UnopenedBookListener;
import feature.book.UnopenedBookService;
import feature.menu.AdminBrowserMenu;
import feature.menu.AlchemistMenu;
import feature.menu.CrystalsBrowserMenu;
import feature.menu.EnchantMenu;
import feature.menu.EnchanterMenu;
import feature.menu.EnchantsBrowserMenu;
import feature.menu.GodlyTransmogMenu;
import feature.menu.MenuRegistry;
import feature.menu.MintCatalog;
import feature.menu.MintMenu;
import feature.menu.OperatorConsoleMenu;
import feature.menu.ReferenceBrowserMenu;
import feature.menu.SetsBrowserMenu;
import feature.menu.TinkererMenu;
import feature.menu.UserHubMenu;
import feature.menu.UserMenuCommand;
import feature.scroll.HolyScrollListener;
import feature.scroll.HolyScrollService;
import feature.scroll.NametagListener;
import feature.scroll.NametagService;
import feature.scroll.ScrollListener;
import feature.scroll.ScrollService;
import feature.slot.SlotListener;
import feature.slot.SlotService;
import feature.menu.MenuListener;
import feature.soul.SoulInteractListener;
import feature.soul.SoulInventoryListener;
import feature.soul.SoulListener;
import feature.soul.SoulService;
import feature.trigger.EngineStoreListener;
import feature.trigger.CommandTriggerCommand;
import feature.trigger.LifecycleDriver;
import feature.trigger.RepeatingDriver;
import feature.trigger.TriggerDispatch;
import feature.trigger.TriggerListeners;
import item.codec.CarrierCodec;
import item.codec.CombatCodec;
import item.codec.CrystalItemCodec;
import item.codec.HeroicUpgradeCodec;
import item.codec.ItemKeys;
import item.codec.ScrollCodec;
import item.codec.SlotItemCodec;
import item.codec.UnopenedBookCodec;
import platform.lang.Messages;
import item.render.LoreRenderer;
import item.view.ItemViewCache;
import item.worn.WornStateStore;
import pack.PackStore;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.logging.Level;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import platform.caps.Capabilities;
import platform.content.ContentReloader;
import platform.content.ReloadResult;
import platform.item.ItemGroups;
import platform.sched.Scheduling;
import platform.sched.TaskHandle;
import schema.diag.Diagnostic;

/**
 * The composition root (ADR-0014; §3): probe → install scheduling → load content → wire the combat
 * spine and feature listeners. One retained {@link RegistryResolvers} pairs compile-time interning with
 * the runtime resolver (§9; the modern/legacy split lives behind the {@code bootstrap.compat.EraBindings} seam);
 * reusing one compiler across reloads is safe — reload is single-flight.
 */
public final class StarEnchantsPlugin extends JavaPlugin {

    private BootCore core;                 // ADR-0047 the feature-neutral substrate + engine spine
    private RepeatingDriver passives;     // §B REPEATING lifecycle
    private LifecycleDriver lifecycle;    // §B HELD/PASSIVE lifecycle
    private feature.trigger.PassiveEffectDriver passiveEffects; // §B maintained passive POTION buffs (permanent + suppression-aware)

    /** §B passive-potion maintenance sweep period (ticks): the safety-net re-derive cadence; instant paths handle the rest. */
    private static final long PASSIVE_SWEEP_TICKS = 40L;
    private feature.soul.SoulParticleDriver soulParticles; // §D while-active soul aura

    @Override
    public void onEnable() {
        core = BootCore.boot(this);

        // Commit-4 scaffold (ADR-0047): the still-inline feature wiring reads the substrate as locals aliasing
        // core.* — commits 5-7 dissolve these into modules. The reordering lemma (no event fires during enable)
        // makes the relocation of construction free; the registration order below is unchanged.
        AtomicLong tick = core.tick();
        bootstrap.compat.EraServices bindings = core.bindings();
        feature.fx.ParticleFx particleFx = core.particleFx();
        item.codec.ItemStateStore store = core.store();
        feature.compat.Hands hands = core.hands();
        feature.compat.Sounds sounds = core.sounds();
        feature.scroll.AnvilRename anvilRename = core.anvilRename();
        Supplier<EffectRegistry> effectRegistry = core.effectRegistry();
        Supplier<Compiler> compilerFactory = core.compilerFactory();
        CopyOnWriteArrayList<EffectKind> addonKinds = core.addonKinds();
        ContentHolder content = core.content();
        ItemsHolder items = core.items();
        MasterConfigHolder master = core.master();
        LangHolder lang = core.lang();
        Messages messages = core.messages();
        MenusHolder menusHolder = core.menusHolder();
        Path contentRoot = core.contentRoot();
        Path itemsRoot = core.itemsRoot();
        Path configFile = core.configFile();
        Path langFile = core.langFile();
        Path menusRoot = core.menusRoot();
        Capabilities caps = core.caps();
        CombatCodec codec = core.codec();
        ItemViewCache itemViews = core.itemViews();
        TriggerRegistry triggers = core.triggers();
        WornStateStore worn = core.worn();
        item.codec.AppliedSlot appliedSlot = core.appliedSlot();
        CarrierCodec carrierCodec = core.carrierCodec();
        item.codec.TrakCodec trakCodec = core.trakCodec();
        LoreRenderer lore = core.lore();
        ItemGroups itemGroups = core.itemGroups();
        java.util.function.Consumer<org.bukkit.inventory.ItemStack> recompose = core.recompose();
        item.mint.VanillaEnchants vanillaEnchants = core.vanillaEnchants();
        ItemEnchanter enchanter = core.enchanter();
        java.util.Random rolls = core.rolls();
        SoulModeStore soulModes = core.soulModes();
        SoulService soulService = core.soulService();
        engine.stores.EngineStores stores = core.stores();
        AbilityExecutor executor = core.executor();
        compile.SpecRegistry migrateSpecs = core.migrateSpecs();
        CombatDispatch dispatch = core.dispatch();
        TriggerDispatch triggerDispatch = core.triggerDispatch();

        // Carrier economy (ADR-0016) — carrierCodec/appliedSlot built above (the lore PROTECTED-line reader uses them).
        CarrierService carriers = new CarrierService(carrierCodec, enchanter, content, rolls,
                () -> items.config().enchantBookOrDefault(),   // §I enchant book
                () -> items.config().dustOrDefault(),          // §I success dust
                () -> items.config().whiteScrollOrDefault(),   // §I white scroll
                () -> master.config().lore().roman(),          // book level numeral style (lore.roman, live)
                () -> master.config().books().maxSuccess(),    // §I global success ceiling (books.max-success, live)
                appliedSlot,                                   // §I white scroll occupies this
                recompose,                                     // ADR-0040 recompose gear lore after a guard toggle
                itemGroups,                                    // §I white-scroll applies-to gate
                messages);                                     // §I applies reject reads common.wrong-applies

        // Physical crystal items (§E). A multi-crystal is one crystal-slot entry encoding "a+b".
        CrystalItemCodec crystalItemCodec = new CrystalItemCodec(ItemKeys.of().crystalItem(), store);
        item.codec.CrystalExtractorCodec crystalExtractorCodec =
                new item.codec.CrystalExtractorCodec(ItemKeys.of().crystalExtractor(), store);
        CrystalService crystals = new CrystalService(crystalItemCodec, crystalExtractorCodec, enchanter, content,
                () -> items.config().crystalOrDefault(), () -> master.config().crystals().maxMerge(), messages);

        // Heroic upgrades (§F).
        HeroicUpgradeCodec heroicCodec = new HeroicUpgradeCodec(ItemKeys.of().heroicUpgrade(), store);
        HeroicService heroics = new HeroicService(heroicCodec, codec, lore,
                () -> items.config().heroicOrDefault(), rolls, messages, itemGroups, bindings.vanillaStats());

        // Slot economy (§H). base MUST match the ItemEnchanter default so the cap is computed off the same base.
        SlotItemCodec slotItemCodec = new SlotItemCodec(ItemKeys.of().slotItem(), ItemKeys.of().slotSuccess(), store);
        SlotService slots = new SlotService(slotItemCodec, codec, lore,
                () -> items.config().slotsOrDefault(),
                (java.util.function.IntSupplier) () -> master.config().slots().base(), messages, itemGroups, rolls);

        // Book-economy scrolls (§I). Distinct 'scroll' PDC tag, off the combat hot path.
        ScrollCodec scrollCodec = new ScrollCodec(ItemKeys.of().scroll(), ItemKeys.of().scrollConvert(), store);
        item.codec.GodlyTransmogCodec godlyTransmogCodec =
                new item.codec.GodlyTransmogCodec(ItemKeys.of().godlyTransmog(), store);
        ScrollService scrolls = new ScrollService(scrollCodec, codec, lore, carriers, content,
                () -> items.config().scrollsOrDefault(), rolls, messages, godlyTransmogCodec, itemGroups);

        // Unopened/randomized book (§I).
        UnopenedBookCodec unopenedCodec = new UnopenedBookCodec(ItemKeys.of().unopened(), store);
        UnopenedBookService unopenedBooks = new UnopenedBookService(unopenedCodec, carriers, content,
                () -> items.config().unopenedBookOrDefault(), rolls, messages);

        // Survival + cosmetic scrolls (§I) — both share the 'scroll' PDC tag + scrolls config.
        HolyScrollService holyScrolls = new HolyScrollService(scrollCodec, appliedSlot,
                () -> items.config().scrollsOrDefault(), rolls, messages, recompose, itemGroups);
        feature.scroll.KeptItemsStore keptItems = new feature.scroll.KeptItemsStore(); // §I holy death→respawn stash
        NametagService nametags = new NametagService(scrollCodec, () -> items.config().scrollsOrDefault(),
                messages, codec); // §I codec → re-append the enchant-count suffix on rename + preview

        // Trak gems (§I): block/mob/soul lifetime counters tracked in the background on eligible gear (trakCodec
        // built above with the other codecs). Applying/bumping a trak recomposes the gear's lore from state.
        feature.trak.TrakService traks = new feature.trak.TrakService(trakCodec, appliedSlot, itemGroups,
                () -> items.config().traksOrDefault(), messages, recompose, hands);

        // §N PlaceholderAPI expansion (ADR-0027). Accessors are plain JDK-typed, so PAPI never loads internals.
        bindings.registerPlaceholders(this, master.config().integrations()::enabled,
                player -> soulModes.isActive(player.getUniqueId()),
                // §D total souls across ALL carried gems (cached on the holder thread each tick — thread-safe here)
                player -> soulService.soulTotal(player.getUniqueId()));
        // §D soul-mode tick: one global task that auto-disables soul mode when a player's active gem is gone or
        // drained to zero, then spawns the configured while-active aura at players still in soul mode.
        soulParticles = new feature.soul.SoulParticleDriver(
                soulService, soulModes, () -> items.config().soulGemOrDefault(), particleFx);
        soulParticles.start();

        // §B REPEATING: one entity-owned repeating task per (player, ability), armed/torn-down by EquipListener.
        passives = new RepeatingDriver(triggerDispatch, content, triggers.idOf("REPEATING").orElse(-1),
                new RepeatStore<TaskHandle>());
        // §B HELD/PASSIVE buffs that flip on/off at equip/unequip via EquipListener's worn-ability diff (ADR-0022).
        lifecycle = new LifecycleDriver(triggerDispatch, content,
                triggers.idOf("HELD").orElse(-1), triggers.idOf("PASSIVE").orElse(-1));
        // §B maintained passive POTION buffs: permanent-while-worn + suppression-aware + self-healing. The
        // authority for passive potions (runs after the lifecycle diff); re-derives from live worn state each
        // refresh, so a DISABLE_ENCHANT drops exactly the right effects and the correct set is restored after.
        passiveEffects = new feature.trigger.PassiveEffectDriver(triggerDispatch, content, worn, stores.suppression(),
                tick::get, triggers.idOf("HELD").orElse(-1), triggers.idOf("PASSIVE").orElse(-1));
        // §6.6 set equip/remove: the authored per-set message on a completion transition PLUS the universal
        // equip/unequip sound+particle (one config for all sets; the dust takes the set's own colour).
        feature.trigger.SetMessageDriver setMessages = new feature.trigger.SetMessageDriver(content,
                (player, msg) -> { // split on \n (keep trailing empties) so a leading AND trailing blank line both render
                    for (String line : platform.text.Colors.translate(msg).split("\n", -1)) {
                        player.sendMessage(line);
                    }
                },
                () -> master.config().sets().messageUppercase(), // read live so a reload can flip it
                new feature.trigger.SetEquipEffects(() -> master.config().sets(), particleFx, sounds));

        // §L feature toggles gate listener registration at BOOT: handlers can't be cleanly re-bound mid-run,
        // so a toggle change needs a restart.
        MasterConfig.FeaturesSection features = master.config().features();

        getServer().getPluginManager().registerEvents(new CombatListener(dispatch), this);
        // The shared worn-state refresher (join/held/respawn/quit) + the era armour-change feeder that drives its
        // refresh (modern PlayerArmorChangeEvent; 1.8 the gear-poll signature delta + an InventoryClose backup).
        EquipListener equipListener =
                new EquipListener(worn, content, passives, lifecycle, passiveEffects, setMessages);
        getServer().getPluginManager().registerEvents(equipListener, this);
        getServer().getPluginManager().registerEvents(bindings.armourChangeFeeder(equipListener), this);
        // §B instant DISABLE: when a player is suppressed, drop their now-disabled passive buffs at once and
        // schedule their restore at the window's end (the periodic sweep is only the safety net).
        stores.suppression().onSuppress((playerId, durationTicks) -> {
            Player target = getServer().getPlayer(playerId);
            if (target != null) {
                Scheduling.onEntity(target, () -> passiveEffects.refresh(target));
                Scheduling.onEntityLater(target, durationTicks + 1L, () -> passiveEffects.refresh(target));
            }
        });
        if (features.souls()) {
            getServer().getPluginManager().registerEvents(new SoulListener(soulService), this);
            getServer().getPluginManager().registerEvents(new SoulInteractListener(soulService, hands), this);
            getServer().getPluginManager().registerEvents(new SoulInventoryListener(soulService), this);
        } else {
            getLogger().info("souls feature disabled (config.yml features.souls) — soul listeners not registered");
        }
        getServer().getPluginManager().registerEvents(new TriggerListeners(triggerDispatch,
                () -> "ALL".equalsIgnoreCase(items.config().heroicOrDefault().reductionScope()), hands), this); // §F reduction-scope
        // ITEM_DAMAGE source (§4): the modern PlayerItemDamageEvent listener; on 1.8 an inert listener (the gear
        // poll fires ITEM_DAMAGE off-event).
        getServer().getPluginManager().registerEvents(bindings.itemDamageSource(triggerDispatch), this);
        // A landing FALLING_BLOCK fires the IMPACT trigger on whoever it hit (druid Terrablender grass rain).
        getServer().getPluginManager().registerEvents(
                new feature.combat.FallingBlockListener(triggerDispatch), this);
        // EQUIP_SWAP (spooky's pumpkin helmet) — keep death/quit normal: restore the real piece, never the placeholder.
        getServer().getPluginManager().registerEvents(new feature.combat.TempEquipListener(), this);
        // Magma floor (devil's Hell's Kitchen) scorches the scene, not the health: cancel HOT_FLOOR in a hellfire zone.
        getServer().getPluginManager().registerEvents(new feature.combat.HellfireFloorListener(), this);
        getServer().getPluginManager().registerEvents(
                new EngineStoreListener(stores, soulService), this);
        // §C KEEP_ON_DEATH at NORMAL priority — earlier than HolyScrollListener (HIGH) — so an enchant-kept
        // death never spends a holy scroll.
        getServer().getPluginManager().registerEvents(new KeepOnDeathListener(stores.keepOnDeath(), tick::get), this);
        // Cosmic Enchants exotic-effect ports: TELEBLOCK cancels teleport, IMMUNE cancels damage while flagged.
        getServer().getPluginManager().registerEvents(new TeleblockListener(stores.teleblock(), tick::get), this);
        getServer().getPluginManager().registerEvents(new ImmuneListener(stores.immune(), tick::get, hands), this);
        // §C KNOCKBACK_CONTROL: capability-probed onto modern-bukkit or legacy destroystokyo; inert on neither.
        KnockbackListener.Path knockbackPath = bindings.registerKnockback(this, stores.knockback(), tick::get);
        getLogger().info("KNOCKBACK_CONTROL applier: " + knockbackPath);
        // §N (ADR-0026): Mental OWNS player knockback, so the vanilla applier is discarded for players; bind
        // its KnockbackApplyEvent so KNOCKBACK_CONTROL composes onto Mental's vector instead of being lost.
        MentalKnockbackBridge.Path mentalPath = MentalKnockbackBridge.register(
                this, stores.knockback(), tick::get, master.config().integrations().enabled("mental"));
        getLogger().info("Mental knockback coordination: " + mentalPath);
        // §I custom items do ONLY their intended action — suppress their vanilla mechanics (the orb's ender-eye
        // throw, a nametag renaming a mob, a food/potion-material item being consumed). Material-agnostic: keyed
        // off the OR of every economy/utility codec, NOT the material. Real enchanted GEAR is excluded (swords swing).
        java.util.function.Predicate<org.bukkit.inventory.ItemStack> isPluginItem = stack -> {
            if (stack == null || stack.getType() == org.bukkit.Material.AIR) {
                return false;
            }
            return soulService.isGem(stack) || scrolls.isScroll(stack) || scrolls.isGodlyTransmog(stack)
                    || holyScrolls.isHolyScroll(stack) || nametags.isNametag(stack) || slots.isSlotItem(stack)
                    || crystals.isCrystal(stack) || crystals.isExtractor(stack) || traks.isTrakGem(stack)
                    || heroics.isUpgrade(stack) || unopenedBooks.isUnopened(stack)
                    || carrierCodec.read(stack) != null; // enchant books, magic dust, white scroll
        };
        getServer().getPluginManager().registerEvents(new feature.guard.VanillaGuardListener(isPluginItem, hands), this);
        getServer().getPluginManager().registerEvents(new CarrierListener(carriers, carrierCodec, particleFx, messages, sounds), this);
        getServer().getPluginManager().registerEvents(new CrystalListener(crystals, messages, sounds), this);
        getServer().getPluginManager().registerEvents(new HeroicListener(heroics, messages, sounds), this);
        if (features.slots()) {
            getServer().getPluginManager().registerEvents(new SlotListener(slots, messages, sounds), this);
        } else {
            getLogger().info("slots feature disabled (config.yml features.slots) — slot-expander apply not registered");
        }
        getServer().getPluginManager().registerEvents(new UnopenedBookListener(unopenedBooks, messages, hands), this);
        // §L scrolls feature gate.
        if (features.scrolls()) {
            getServer().getPluginManager().registerEvents(new ScrollListener(scrolls, messages, sounds), this);
            getServer().getPluginManager().registerEvents(new HolyScrollListener(holyScrolls, keptItems, messages, sounds), this);
            getServer().getPluginManager().registerEvents(new NametagListener(nametags, messages, sounds, anvilRename), this);
            anvilRename.installPreview(this, nametags); // modern: colour the anvil result preview (no-op on 1.8.9)
            getServer().getPluginManager().registerEvents(new feature.trak.TrakListener(traks, messages, sounds), this);
        } else {
            getLogger().info("scrolls feature disabled (config.yml features.scrolls) — scroll listeners not registered");
        }
        // Heroic durability (§F): a heroic item's per-item durability chance cancels item-damage events (§4: the
        // modern per-event save; on 1.8 an inert listener — the gear poll restores the lost durability post-hoc).
        getServer().getPluginManager().registerEvents(bindings.heroicDurabilitySave(codec, rolls), this);

        // Arm players already online (a plugin /reload with players on); a fresh boot has none. Normal joins
        // are armed by EquipListener via PlayerJoinEvent.
        for (Player player : getServer().getOnlinePlayers()) {
            Scheduling.onEntity(player, () -> {
                var state = worn.refresh(player, content.snapshot());
                passives.arm(player, state);     // §B REPEATING
                lifecycle.refresh(player, state); // §B HELD/PASSIVE
                passiveEffects.refresh(player);   // §B maintained passive potions
            });
        }

        // §B passive-potion maintenance sweep: re-derive every online player's permanent passive buffs so they
        // never lapse and self-heal after a death/milk/other clear. The time-critical paths (equip, respawn,
        // suppression) refresh instantly; this is the safety net. The global task only DISPATCHES per-entity
        // work (Folia-correct) — it touches no entity itself.
        Scheduling.repeatingGlobal(PASSIVE_SWEEP_TICKS, PASSIVE_SWEEP_TICKS, () -> {
            for (Player player : getServer().getOnlinePlayers()) {
                Scheduling.onEntity(player, () -> passiveEffects.refresh(player));
            }
        });

        // The §L config sources reload in the SAME transaction as content (§L-4): each parses off-thread,
        // and the reloader commits all-or-nothing — any error keeps the previous state of EVERYTHING.
        List<platform.content.ReloadStep> reloadSteps = List.of(
                () -> { var c = ItemsLoader.load(itemsRoot); return new platform.content.ReloadStep.Built(
                        c.diagnostics(), () -> items.publish(c)); },
                () -> { var c = MasterConfigLoader.load(configFile); return new platform.content.ReloadStep.Built(
                        c.diagnostics(), () -> master.publish(c)); },
                () -> { var c = LangLoader.load(langFile); return new platform.content.ReloadStep.Built(
                        c.diagnostics(), () -> lang.publish(c)); },
                () -> { var c = MenusLoader.load(menusRoot); return new platform.content.ReloadStep.Built(
                        c.diagnostics(), () -> menusHolder.publish(c)); });

        // On a clean swap this hook advances the gen-keyed caches and re-resolves every online player.
        // The compiler is rebuilt per reload (not constant) so a newly registered add-on head compiles;
        // the resolver is reused, so the §9 handle round-trip holds (ADR-0038). The ADR-0046 pack gate
        // dry-runs through the SAME factory (same resolvers instance — the §9 invariant), so its compile
        // sees exactly what the reloader would.
        ContentReloader reloader = new ContentReloader(content, compilerFactory,
                contentRoot, 0, published -> {
            itemViews.reload(published.snapshot().generation());
            executor.bindQuarantine(BootCore.quarantineFor(published.snapshot())); // §10 fresh per snapshot — a fixed edit clears the block
            stores.why().generation(published.snapshot().generation()); // ADR-0045: rebind gen so post-reload records resolve
            executor.bindContent(effectRegistry.get()); // ADR-0038/0039: atomic effect+selector kind pair, add-on kinds included
            getServer().getPluginManager().callEvent(new StarEnchantsReloadEvent(
                    published.snapshot().generation(), published.snapshot().abilityCount()));
            if (master.config().reload().reResolvePlayers()) { // §L config.yml reload.re-resolve-players
                for (Player player : getServer().getOnlinePlayers()) {
                    // Re-arm against the new snapshot per player (a repeating task's period may have changed).
                    Scheduling.onEntity(player, () -> {
                        var state = worn.refresh(player, published.snapshot());
                        passives.arm(player, state);
                        lifecycle.refresh(player, state);
                        passiveEffects.refresh(player);
                    });
                }
            }
        }, reloadSteps);

        // ADR-0038: the public add-on service. Registered with Bukkit's ServicesManager (ServiceLoader is
        // unreliable across plugin classloaders) so an add-on looks it up in its own onEnable and registers
        // effect kinds / queries item state; registerEffect appends to addonKinds and triggers the reload above.
        StarEnchantsApi apiService = new ApiService(addonKinds, effectRegistry, reloader, content, itemViews,
                () -> master.config().slots().base());
        getServer().getServicesManager().register(StarEnchantsApi.class, apiService, this, ServicePriority.Normal);

        // §L auto-reload (config.yml reload.auto-seconds; ≤ 0 = off). Armed once at boot — interval change needs a restart.
        int autoSeconds = master.config().reload().autoSeconds();
        if (autoSeconds > 0) {
            long period = autoSeconds * 20L;
            Scheduling.repeatingGlobal(period, period, () -> reloader.reload(this::logAutoReload));
            getLogger().info("auto-reload armed: every " + autoSeconds + "s");
        }

        // GUIs on the shared menu framework (§K). Menus open on the player's region thread (Folia open-hop).
        // Enchant-icon names are styled by the enchant-book name template, so a menu name matches the book.
        java.util.function.Supplier<String> bookName = () -> items.config().enchantBookOrDefault().name();
        EnchantMenu applyMenu = new EnchantMenu(content, enchanter,
                player -> worn.refresh(player, content.snapshot()), caps, menusHolder::config, bookName, messages, hands,
                vanillaEnchants);
        // Hoisted so the physical godly-transmog gesture listener can open it bound to a clicked piece (§I/§K).
        GodlyTransmogMenu transmogMenu = new GodlyTransmogMenu(content, codec, scrolls, caps, menusHolder::config, hands,
                vanillaEnchants);
        // The operator "mint anything" catalogue (ADR-0030) — driven by the live tier list + trak kinds.
        MintCatalog mintCatalog = new MintCatalog(content, soulService, slots, heroics, crystals, scrolls,
                holyScrolls, nametags, carriers, traks, unopenedBooks);
        // The hubs look siblings up live from the registry, so registration order is irrelevant.
        MenuRegistry menus = new MenuRegistry();
        menus.register(new UserHubMenu(menus, caps, menusHolder::config, vanillaEnchants))  // /enchants player landing
                .register(new OperatorConsoleMenu(menus, reloader, messages, caps, menusHolder::config, vanillaEnchants)) // /se menu
                .register(new MintMenu(mintCatalog, caps, messages, menusHolder::config, vanillaEnchants))  // operator: mint anything
                .register(applyMenu)
                .register(new EnchantsBrowserMenu(content, caps, menusHolder::config, bookName, vanillaEnchants)) // tier → enchant catalog
                .register(new SetsBrowserMenu(content, enchanter, caps, messages, menusHolder::config, vanillaEnchants)) // sets → pieces → mint
                .register(new CrystalsBrowserMenu(content, crystals, caps, messages, menusHolder::config, vanillaEnchants)) // browse + mint
                .register(new ReferenceBrowserMenu(caps, menusHolder::config, vanillaEnchants))             // effects/selectors/…
                .register(transmogMenu)                                                    // reorder lore (held or bound)
                .register(new EnchanterMenu(content, unopenedBooks, caps, messages, menusHolder::config, vanillaEnchants)) // buy books
                .register(new AlchemistMenu(carriers, caps, messages, menusHolder::config, vanillaEnchants)) // combine books → +1
                .register(new TinkererMenu(carriers, caps, messages, menusHolder::config, vanillaEnchants))  // salvage book → XP
                .register(new AdminBrowserMenu(content, carriers, caps, messages, menusHolder::config, vanillaEnchants)); // admin grant
        getServer().getPluginManager().registerEvents(new MenuListener(hands), this);
        // §I/§K physical godly-transmog gesture — scroll family, so it shares the features.scrolls() boot gate.
        if (features.scrolls()) {
            getServer().getPluginManager().registerEvents(
                    new feature.menu.GodlyTransmogListener(scrolls, transmogMenu, codec, messages, sounds), this);
        }
        // ADR-0030 user entry: /enchants opens the player hub (open to all; the hub's targets are perm-free).
        // Registered on the server command map like /splitsouls, so it needs no plugin.yml command entry.
        try {
            bindings.registerCommand(getServer(), "starenchants",
                    new UserMenuCommand("enchants", menus, messages));
        } catch (Throwable t) {
            getLogger().log(Level.WARNING, "could not register /enchants (use /se menu hub instead)", t);
        }

        // Config packs (ADR-0023). /se pack apply pairs the on-disk swap with the transactional
        // reloader; the ADR-0046 gate pre-flights a pack against the live authoring surface first.
        PackStore packs = new PackStore(getDataFolder().toPath());
        PackGate packGate = new PackGate(
                compilerFactory, // the same factory the reloader uses (§9 resolver reuse)
                () -> RegistryFingerprint.hash(effectRegistry.get()),
                () -> RegistryFingerprint.summary(effectRegistry.get()));

        PluginCommand command = getCommand("se");
        if (command != null) {
            SeCommand seCommand = new SeCommand(reloader, enchanter,
                    player -> worn.refresh(player, content.snapshot()), soulService,
                    getDataFolder().toPath().resolve("migrated"), menus, content,
                    head -> migrateSpecs.lookup(head).orElse(null), carriers, crystals, heroics, slots,
                    scrolls, unopenedBooks, holyScrolls, nametags, traks, packs, codec, carrierCodec,
                    () -> master.config().slots().base(), messages, contentRoot, store, hands, packGate,
                    stores.why(), executor::quarantinedKeys, worn, tick::get); // ADR-0046 pack gate + ADR-0045 /se why
            command.setExecutor(seCommand);
            command.setTabCompleter(seCommand);
        }

        // §D /splitsouls — a top-level alias for /se split (the soul gem's lore advertises it). Registered on
        // the server command map like the command-trigger, so it needs no plugin.yml entry.
        try {
            bindings.registerCommand(getServer(), "starenchants",
                    new feature.soul.SplitSoulsCommand("splitsouls", soulService, messages));
        } catch (Throwable t) {
            getLogger().log(Level.WARNING, "could not register /splitsouls (use /se split instead)", t);
        }

        // §B COMMAND trigger: dynamic name can't live in plugin.yml, so register on the server command map
        // (guarded — an inaccessible map just leaves it unfireable). A name change needs a restart.
        var commandTrigger = master.config().commandTrigger();
        if (commandTrigger.enabled()) {
            try {
                bindings.registerCommand(getServer(), "starenchants", new CommandTriggerCommand(
                        commandTrigger.name(), commandTrigger.description(), triggerDispatch,
                        messages.format("command.not-a-player")));
                getLogger().info("command-trigger registered: /" + commandTrigger.name());
            } catch (Throwable t) {
                getLogger().log(Level.WARNING, "could not register the command-trigger '/" + commandTrigger.name()
                        + "' (COMMAND enchants will not be fireable by command)", t);
            }
        }
    }

    @Override
    public void onDisable() {
        // §B: repeating tasks outlive a /reload (other stores self-evict by TTL), so cancel them explicitly.
        if (passives != null) {
            passives.disarmAll();
        }
        if (lifecycle != null) {
            lifecycle.clearAll(); // forget started HELD/PASSIVE buffs (the driver is discarded across a reload)
        }
        if (passiveEffects != null) {
            passiveEffects.clearAll(); // forget the maintained-passive owned ledger (re-derived on next sweep)
        }
        if (soulParticles != null) {
            soulParticles.stop(); // cancel the §D while-active soul aura task
        }
        engine.sink.FallingBlockCasts.clearAll(); // forget any in-flight falling-block impact bindings
        engine.sink.CombatTag.clearAll(); // forget combat tags (supreme's out-of-combat fly)
        engine.sink.DamageMarks.clearAll(); // forget damage marks (reaper's Mark of the Reaper)
        engine.sink.OwnerZones.clearAll(); // forget owner zones (devil's Hell's Kitchen hellfire zones)
        engine.sink.TempEquip.clearAll(); // forget temporary equipment swaps (spooky's pumpkin helmet)
        if (core != null) {
            for (bootstrap.wire.FeatureModule.Stop stop : core.coreStops()) {
                stop.run().run(); // §B bStats shutdown (the sink statics + drivers above are stopped in place)
            }
        }
    }

    public ContentHolder content() {
        return core.content();
    }

    private static final int AUTO_RELOAD_DIAG_PREVIEW = 3;

    /**
     * ADR-0042: the timed reload's result used to be discarded — a failing auto-reload was silent while
     * {@code /se reload} reported the same faults. (busy → FINE, not WARNING: a build outlasting the period
     * would otherwise self-spam every cycle.)
     */
    private void logAutoReload(ReloadResult result) {
        if (result.failure() != null) {
            getLogger().log(Level.WARNING, "auto-reload build failed; previous content kept", result.failure());
            return;
        }
        if (result.isBusy()) {
            getLogger().fine("auto-reload skipped: another reload is in flight");
            return;
        }
        if (result.errorCount() > 0) {
            getLogger().warning("auto-reload rejected: " + result.errorCount()
                    + " blocking diagnostic(s); previous content kept (run /se problems)");
            result.diagnostics().stream().filter(Diagnostic::blocking).limit(AUTO_RELOAD_DIAG_PREVIEW)
                    .forEach(d -> getLogger().warning("  " + d.render()));
            return;
        }
        getLogger().fine("auto-reload: published generation " + result.generation()
                + " (" + result.abilityCount() + " abilities)");
    }
}
