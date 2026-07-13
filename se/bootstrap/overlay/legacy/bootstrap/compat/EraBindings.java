package bootstrap.compat;

import engine.run.ActorProbe;
import engine.run.LegacyActorProbe;
import engine.sink.LegacyDispatchSink;
import engine.sink.SinkFactory;
import engine.run.ActivationContext;
import engine.stores.KnockbackControlStore;
import feature.combat.EquipListener;
import feature.combat.KnockbackListener;
import feature.combat.NmsKnockbackApplier;
import feature.combat.NoopListener;
import feature.compat.DropControl;
import feature.compat.Hands;
import feature.compat.KeySoundFallback;
import feature.compat.LegacyDropControl;
import feature.compat.LegacyHands;
import feature.compat.LegacyProjectiles;
import feature.compat.Projectiles;
import feature.compat.Sounds;
import feature.fx.LegacyParticleFx;
import feature.fx.ParticleFx;
import feature.heroic.LegacyHeroicSave;
import feature.heroic.VanillaStats;
import item.head.EquipmentRepaint;
import item.head.LegacyEquipmentRepaint;
import item.head.LegacyTexturedHeads;
import item.head.TexturedHeads;
import feature.scroll.AnvilRename;
import feature.trigger.LegacyGearPoll;
import feature.trigger.TriggerDispatch;
import item.codec.CombatCodec;
import item.codec.ItemKeys;
import item.codec.ItemStateStore;
import item.codec.NbtItemStateStore;
import item.worn.EquipSource;
import item.worn.LegacyEquipSource;
import java.util.List;
import java.util.Random;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import org.bukkit.Server;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.craftbukkit.v1_8_R3.CraftServer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import platform.economy.EconomyProvider;
import platform.protect.ProtectionProvider;
import platform.resolve.RegistryResolvers;

/**
 * Legacy (1.8.9) era bindings (ADR-0044): the one same-FQN {@code bootstrap} twin, implementing
 * {@link EraServices}. 1.8 has no {@code RuntimeHandles} (its DispatchSink resolves ids to NMS by name) and no
 * {@code :integrate} bridges (their plugin APIs are modern-Bukkit-typed), so the integration methods return the
 * SAME neutral defaults {@code Integrations} yields when a plugin is absent — and every bridged plugin is
 * modern-only, so it can never be installed on 1.8. It owns the 1.8 edges: the one {@link LegacyGearPoll}, the
 * NMS knockback applier, the {@link LegacyTargets} line-of-sight scan, the {@code CraftServer} command-map cast,
 * and the {@link LegacyEnchantResolver} pre-1.13 name mapping (docs/legacy-1.8.9-codeshare-design.md §4/§6).
 */
public final class EraBindings implements EraServices {

    private final RegistryResolvers resolvers;
    // §6 the ONE legacy per-tick gear poll: the three era feeders attach their subscribers to it (ADR-0044).
    private final LegacyGearPoll gearPoll;

    public EraBindings(RegistryResolvers resolvers) {
        this.resolvers = resolvers;
        // Poll identity = the item's combat-state blob, read via the SAME era store + logical key CombatCodec uses,
        // so it pairs with heroicSave's own read without depending on BootCore's codec (not built yet at ctor time).
        ItemStateStore store = new NbtItemStateStore();
        String combatKey = ItemKeys.of().combat();
        this.gearPoll = new LegacyGearPoll(item -> store.read(item, combatKey));
    }

    @Override
    public ParticleFx particleFx() {
        return new LegacyParticleFx();
    }

    @Override
    public SinkFactory sinkFactory() {
        return env -> new LegacyDispatchSink(resolvers, env); // RegistryResolvers is a RenameResolvers
    }

    /** The physical item-data layer (§4.2): 1.8 NMS tags. Injected into every codec + the lore renderer. */
    @Override
    public ItemStateStore itemStateStore() {
        return new NbtItemStateStore();
    }

    /** The worn/held equipment read (§3.3): 1.8 5-slot (main hand only). Injected into {@code WornResolver}. */
    @Override
    public EquipSource equipSource() {
        return new LegacyEquipSource();
    }

    /** The entity/material fact reads (§3.3): 1.8-correct swim/glide/isAir/main-hand. Injected into {@code FactPopulator}. */
    @Override
    public ActorProbe actorProbe() {
        return new LegacyActorProbe();
    }

    /** Hand/equipment access (§4): the single 1.8 hand (no off-hand). Injected into the feature shells. */
    @Override
    public Hands hands() {
        return new LegacyHands();
    }

    /** Block-break drop suppression (§4): 1.8 cancel + clear (no {@code setDropItems}). Injected into {@code MineDrops}. */
    @Override
    public DropControl dropControl() {
        return new LegacyDropControl();
    }

    /** Projectile-type routing (§4): 1.8 {@code Arrow} only. Injected into the combat router. */
    @Override
    public Projectiles projectiles() {
        return new LegacyProjectiles();
    }

    /** Sound playback (§4): the shared resolver; 1.8 has no String overload, so key-form sounds are skipped. */
    @Override
    public Sounds sounds() {
        return new Sounds(KeySoundFallback.NONE);
    }

    /** §F heroic vanilla stats (§4): 1.8 has no attribute API — keep the plugin-maths fold. Into {@code HeroicService}. */
    @Override
    public VanillaStats vanillaStats() {
        return VanillaStats.NONE;
    }

    /** ADR-0052 pet heads: 1.8 SKULL_ITEM:3 + an authlib GameProfile textures property. */
    @Override
    public TexturedHeads texturedHeads() {
        return new LegacyTexturedHeads();
    }

    /** ADR-0053 mask repaint: the direct-NMS 1.8 equipment packet on the recipient's connection. */
    @Override
    public EquipmentRepaint equipmentRepaint() {
        return new LegacyEquipmentRepaint();
    }

    @Override
    public item.head.HeadEquip headEquip() {
        return item.head.HeadEquip.NONE; // 1.8 has no data components — the server-side HeadEquipGuard carries it
    }

    @Override
    public item.head.HeadAttributes headAttributes() {
        return item.head.HeadAttributes.NONE; // 1.8 renders no attribute tooltips — nothing to copy
    }

    /** §I nametag rename (§4): 1.8 has no live anvil-rename field — chat capture. Into {@code NametagListener}. */
    @Override
    public AnvilRename anvilRename() {
        return AnvilRename.UNSUPPORTED;
    }

    /**
     * §C KNOCKBACK_CONTROL (§4): 1.8 fires no Paper knockback event, so the {@link NmsKnockbackApplier}
     * (knockback-resistance at the NMS source) is always the applier. Returns {@link KnockbackListener.Path#LEGACY}.
     */
    @Override
    public KnockbackListener.Path registerKnockback(Plugin plugin, KnockbackControlStore store, LongSupplier nowTicks) {
        plugin.getServer().getPluginManager().registerEvents(new NmsKnockbackApplier(store, nowTicks), plugin);
        return KnockbackListener.Path.LEGACY;
    }

    /** §B armour-change source (§4/§6): the gear-poll armour-signature delta drives refresh. The close-refresh
     *  backup is now the era-neutral {@code EquipListener.onInventoryClose}, so this feeder is otherwise inert. */
    @Override
    public Listener armourChangeFeeder(EquipListener equip) {
        gearPoll.refreshEquip(equip::refresh);
        return NoopListener.INSTANCE;
    }

    /** §B hand-change source (§4/§6): 1.8 has no off-hand and no swap/pickup-into-empty semantics, so inert. */
    @Override
    public Listener handChangeFeeder(EquipListener equip) {
        return NoopListener.INSTANCE;
    }

    /** POTION_LOCK guard (§B): 1.8 has no {@code EntityPotionEffectEvent}, so inert — the Sink's per-tick re-strip
     *  enforces the lock on the legacy lane. */
    @Override
    public Listener potionLockGuard() {
        return NoopListener.INSTANCE;
    }

    /**
     * ITEM_DAMAGE source (§4/§6): the gear-poll durability-rise subscriber fires it; no Bukkit event on 1.8.
     * ADR-0049: carries the durability points as {@code %damage%} and armor-vs-held as {@code %itemdamage.armor%}.
     * The Cancellable is null — legacy observes the loss AFTER the fact, so a SUPPRESS/cancel on ITEM_DAMAGE cannot
     * apply (the heroic restore is the separate {@code heroicDurabilitySave} subscriber; no restore shim here).
     */
    @Override
    public Listener itemDamageSource(TriggerDispatch dispatch) {
        gearPoll.fireItemDamage((player, armor, delta) -> dispatch.fire(player, dispatch.itemDamage,
                new ActivationContext(player, null, null, player.getLocation(), delta, null, 0, "", armor, 0, 0), null));
        return NoopListener.INSTANCE;
    }

    /** §F heroic durability save (§4/§6): the gear-poll heroic subscriber restores the lost durability post-hoc. */
    @Override
    public Listener heroicDurabilitySave(CombatCodec codec, Random rolls) {
        gearPoll.heroicSave(new LegacyHeroicSave(codec, rolls));
        return NoopListener.INSTANCE;
    }

    /** Vanilla-station guard (G06): the anvil is the only guarded station on 1.8 (no grindstone/smithing table). */
    @Override
    public Listener stationGuard(feature.guard.StationGuardRules rules) {
        return new feature.guard.LegacyAnvilGuard(rules);
    }

    /** 1.8.4 dispenser head-equip guard: {@code BlockDispenseArmorEvent} does not exist on 1.8, so inert. */
    @Override
    public Listener dispenseArmorGuard(Predicate<ItemStack> isHeadItem) {
        return NoopListener.INSTANCE;
    }

    /** §6.6 set-piece base enchants: the pre-1.13 name mapping lives in {@link LegacyEnchantResolver}. */
    @Override
    public Function<String, Enchantment> enchantResolver() {
        return new LegacyEnchantResolver();
    }

    // ── integration bridges: :integrate is EXCLUDED from the 1.8 tree, so return the neutral defaults ──
    @Override
    public BiFunction<Player, String, String> placeholderResolver(Plugin plugin, Predicate<String> enabled) {
        return (player, text) -> text; // identity: no PlaceholderAPI on 1.8
    }

    @Override
    public void registerPlaceholders(Plugin plugin, Predicate<String> enabled,
                                     Predicate<Player> soulMode, ToIntFunction<Player> souls) {
        // no PlaceholderAPI on 1.8 — nothing to register
    }

    @Override
    public List<ProtectionProvider> protectionProviders(Plugin plugin, Predicate<String> enabled) {
        return List.of();
    }

    @Override
    public EconomyProvider economyProvider(Plugin plugin, Predicate<String> enabled) {
        return null; // no Vault bridge on 1.8; the ServicesManager discovery path still runs
    }

    @Override
    public Consumer<Player> antiCheatExemption(Plugin plugin, Predicate<String> enabled, System.Logger log) {
        return player -> { };
    }

    @Override
    public BiPredicate<Player, Player> mcmmoFriendlyFire(Plugin plugin, Predicate<String> enabled) {
        return (attacker, victim) -> false;
    }

    @Override
    public Function<Entity, String> mythicMobType(Plugin plugin, Predicate<String> enabled) {
        return entity -> "";
    }

    @Override
    public Function<String, ItemStack> customItem(Plugin plugin, Predicate<String> enabled) {
        return material -> null;
    }

    // ── targeting (1.8 line-of-sight scan) + dynamic commands (CraftServer command map) ──
    @Override
    public Entity targetEntity(Player from, int maxDistance) {
        return LegacyTargets.targetEntity(from, maxDistance);
    }

    @Override
    public Block targetBlock(Player from, int maxDistance) {
        return LegacyTargets.targetBlock(from, maxDistance);
    }

    @Override
    public void registerCommand(Server server, String fallbackPrefix, Command command) {
        ((CraftServer) server).getCommandMap().register(fallbackPrefix, command);
    }
}
