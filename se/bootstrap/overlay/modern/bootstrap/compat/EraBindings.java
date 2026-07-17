package bootstrap.compat;

import engine.run.ActorProbe;
import engine.run.ModernActorProbe;
import engine.sink.ModernDispatchSink;
import engine.sink.SinkFactory;
import engine.stores.KnockbackControlStore;
import feature.combat.EquipListener;
import feature.combat.KnockbackListener;
import feature.combat.LegacyKnockbackListener;
import feature.combat.ModernArmourChangeListener;
import feature.combat.ModernHandChangeListener;
import feature.combat.PotionLockListener;
import feature.heroic.HeroicDurabilityListener;
import feature.trigger.DurabilityTriggerListener;
import feature.trigger.TriggerDispatch;
import item.codec.CombatCodec;
import java.util.Random;
import org.bukkit.event.Listener;
import feature.compat.DropControl;
import feature.compat.Hands;
import feature.compat.ModernDropControl;
import feature.compat.ModernHands;
import feature.compat.ModernProjectiles;
import feature.compat.Projectiles;
import feature.compat.Sounds;
import feature.fx.ModernParticleFx;
import feature.fx.ParticleFx;
import feature.heroic.ModernVanillaStats;
import feature.heroic.VanillaStats;
import item.head.EquipmentRepaint;
import item.head.ModernEquipmentRepaint;
import item.head.ModernTexturedHeads;
import item.head.TexturedHeads;
import feature.scroll.AnvilRename;
import feature.scroll.ModernAnvilRename;
import item.codec.ItemStateStore;
import item.codec.PdcItemStateStore;
import item.worn.EquipSource;
import item.worn.ModernEquipSource;
import integrate.Integrations;
import java.util.List;
import java.util.OptionalInt;
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
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import platform.economy.EconomyProvider;
import platform.protect.ProtectionProvider;
import platform.resolve.RegistryResolvers;
import platform.resolve.RuntimeHandles;
import platform.text.Colors;
import schema.spec.HandleCategory;

/**
 * Modern era bindings (ADR-0044): the one same-FQN {@code bootstrap} twin, implementing {@link EraServices}.
 * {@code RuntimeHandles} (id&rarr;live-object) feeds the particle fx / sink factory / enchant resolver; the
 * integration bridges delegate to {@link Integrations}; targeting uses the 1.13+ raytrace API; dynamic commands
 * use {@code Server.getCommandMap()}. The {@code overlay/legacy} counterpart has no {@code RuntimeHandles} (its
 * DispatchSink resolves ids to NMS itself), returns the neutral integration defaults, and owns the 1.8 edges
 * (docs/legacy-1.8.9-codeshare-design.md §4/§6). Every method is construction or one-line delegation.
 */
public final class EraBindings implements EraServices {

    private final RegistryResolvers resolvers;
    private final RuntimeHandles handles;

    public EraBindings(RegistryResolvers resolvers) {
        this.resolvers = resolvers;
        this.handles = new RuntimeHandles(resolvers);
    }

    /** §D/§I particle feedback: token → interner → live Particle, skip-on-miss. */
    @Override
    public ParticleFx particleFx() {
        return new ModernParticleFx(token -> {
            OptionalInt id = resolvers.particle(token);
            return id.isPresent() ? handles.particle(id.getAsInt()) : null;
        });
    }

    @Override
    public SinkFactory sinkFactory() {
        return env -> new ModernDispatchSink(handles, env);
    }

    /** The physical item-data layer (§4.2): modern PDC. Injected into every codec + the lore renderer. */
    @Override
    public ItemStateStore itemStateStore() {
        return new PdcItemStateStore();
    }

    /** The worn/held equipment read (§3.3): modern 6-slot (incl. off-hand). Injected into {@code WornResolver}. */
    @Override
    public EquipSource equipSource() {
        return new ModernEquipSource();
    }

    /** The entity/material fact reads (§3.3): modern swim/glide/isAir/main-hand. Injected into {@code FactPopulator}. */
    @Override
    public ActorProbe actorProbe() {
        return new ModernActorProbe();
    }

    /** Hand/equipment access (§4): modern off-hand-aware. Injected into the feature shells. */
    @Override
    public Hands hands() {
        return new ModernHands();
    }

    /** Block-break drop suppression (§4): modern {@code setDropItems(false)}. Injected into {@code MineDrops}. */
    @Override
    public DropControl dropControl() {
        return new ModernDropControl();
    }

    /** Projectile-type routing (§4): modern {@code Trident}/{@code AbstractArrow}. Injected into the combat router. */
    @Override
    public Projectiles projectiles() {
        return new ModernProjectiles();
    }

    /** Sound playback (§4): the shared resolver + the modern String-overload key-form fallback (1.9.4+). */
    @Override
    public Sounds sounds() {
        return new Sounds((player, at, key, volume, pitch) -> player.playSound(at, key, volume, pitch));
    }

    /** {@code {#RRGGBB}} form (ADR-0062): the whole modern range accepts the 1.16+ {@code §x} legacy-hex escape. */
    @Override
    public Colors.HexMode hexMode() {
        return Colors.HexMode.MODERN;
    }

    /** §F heroic vanilla stats (§4): modern writes real diamond attributes. Injected into {@code HeroicService}. */
    @Override
    public VanillaStats vanillaStats() {
        return new ModernVanillaStats();
    }

    /** ADR-0052 pet heads: Paper profile + textures property (API surface, mapping-flip immune). */
    @Override
    public TexturedHeads texturedHeads() {
        return new ModernTexturedHeads();
    }

    /** ADR-0053 mask repaint: {@code sendEquipmentChange} by name (1.18.2+; 1.17.1 degrades inert in the impl). */
    @Override
    public EquipmentRepaint equipmentRepaint() {
        return new ModernEquipmentRepaint();
    }

    /** 1.8.4 helmet-wearability strip: overrides the {@code equippable} component by name (1.21.2+; below it inert). */
    @Override
    public item.head.HeadEquip headEquip() {
        return new item.head.ModernHeadEquip();
    }

    @Override
    public item.head.HeadAttributes headAttributes() {
        return new item.head.ModernHeadAttributes();
    }

    /** §I nametag rename (§4): modern opens a real anvil + colour preview. Into {@code NametagListener}. */
    @Override
    public AnvilRename anvilRename() {
        return new ModernAnvilRename();
    }

    /**
     * §C KNOCKBACK_CONTROL (§4): register the applier this modern server fires knockback through — the 1.20.6+
     * event (reflective) or Paper's legacy event ({@link LegacyKnockbackListener}); returns the chosen path.
     */
    @Override
    public KnockbackListener.Path registerKnockback(Plugin plugin, KnockbackControlStore store, LongSupplier nowTicks) {
        return KnockbackListener.register(plugin, store, nowTicks, new LegacyKnockbackListener(store, nowTicks));
    }

    /** §B armour-change source (§4/§6): modern Paper {@code PlayerArmorChangeEvent}. Drives {@code EquipListener.refresh}. */
    @Override
    public Listener armourChangeFeeder(EquipListener equip) {
        return new ModernArmourChangeListener(equip);
    }

    /** §B hand-change source (§4/§6): modern off-hand swap + pickup-into-empty-hand. Drives {@code EquipListener.refresh}. */
    @Override
    public Listener handChangeFeeder(EquipListener equip) {
        return new ModernHandChangeListener(equip);
    }

    /** POTION_LOCK guard (§B): modern {@code EntityPotionEffectEvent} cancel of a locked type — genuine prevention. */
    @Override
    public Listener potionLockGuard() {
        return new PotionLockListener();
    }

    /** ITEM_DAMAGE source (§4): modern {@code PlayerItemDamageEvent} listener (1.8 fires it from the gear poll). */
    @Override
    public Listener itemDamageSource(TriggerDispatch dispatch) {
        return new DurabilityTriggerListener(dispatch);
    }

    /** §F heroic durability save (§4): modern per-item-damage cancel (1.8 restores post-hoc via the gear poll). */
    @Override
    public Listener heroicDurabilitySave(CombatCodec codec, Random rolls) {
        return new HeroicDurabilityListener(codec, rolls);
    }

    /** Masked-helmet break (ADR-0053): modern {@code PlayerItemDamageEvent} listener that breaks a spent masked
     *  helmet at zero durability and pops the mask off (1.8 an inert listener — a gear-poll follow-up). */
    @Override
    public Listener maskBreakSource(CombatCodec codec, feature.mask.MaskBreakSalvage salvage) {
        return new feature.mask.MaskDurabilityBreakListener(codec, salvage);
    }

    /** Vanilla-station guard (G04/G05/G06): modern smithing/grindstone/anvil prepare-event guard on set gear. */
    @Override
    public Listener stationGuard(feature.guard.StationGuardRules rules) {
        return new feature.guard.ModernStationGuard(rules);
    }

    /** 1.8.4 dispenser head-equip guard: modern cancels {@code BlockDispenseArmorEvent} for a mask/pet head. */
    @Override
    public Listener dispenseArmorGuard(Predicate<ItemStack> isHeadItem) {
        return new feature.guard.DispenseArmorGuard(isHeadItem);
    }

    /**
     * §6.6 set-piece base enchants: resolve a modern canonical enchant name to a live {@link Enchantment} via
     * the namespaced-key registry ({@code PROTECTION} → {@code minecraft:protection}). Miss → {@code null}.
     */
    @Override
    public Function<String, Enchantment> enchantResolver() {
        return name -> (Enchantment) handles.resolveByName(HandleCategory.ENCHANTMENT, name);
    }

    // ── integration bridges (ADR-0027): delegate to :integrate, modern-only ──
    @Override
    public BiFunction<Player, String, String> placeholderResolver(Plugin plugin, Predicate<String> enabled) {
        return Integrations.placeholderResolver(plugin, enabled);
    }

    @Override
    public void registerPlaceholders(Plugin plugin, Predicate<String> enabled,
                                     Predicate<Player> soulMode, ToIntFunction<Player> souls) {
        Integrations.registerPlaceholders(plugin, enabled, soulMode, souls);
    }

    @Override
    public List<ProtectionProvider> protectionProviders(Plugin plugin, Predicate<String> enabled) {
        return Integrations.protectionProviders(plugin, enabled);
    }

    @Override
    public EconomyProvider economyProvider(Plugin plugin, Predicate<String> enabled) {
        return Integrations.economyProvider(plugin, enabled);
    }

    @Override
    public Consumer<Player> antiCheatExemption(Plugin plugin, Predicate<String> enabled, System.Logger log) {
        return Integrations.antiCheatExemption(plugin, enabled, log);
    }

    @Override
    public BiPredicate<Player, Player> mcmmoFriendlyFire(Plugin plugin, Predicate<String> enabled) {
        return Integrations.mcmmoFriendlyFire(plugin, enabled);
    }

    @Override
    public Function<Entity, String> mythicMobType(Plugin plugin, Predicate<String> enabled) {
        return Integrations.mythicMobType(plugin, enabled);
    }

    @Override
    public Function<String, ItemStack> customItem(Plugin plugin, Predicate<String> enabled) {
        return Integrations.customItem(plugin, enabled);
    }

    // ── targeting (1.13+ raytrace) + dynamic commands (Server.getCommandMap) ──
    @Override
    public Entity targetEntity(Player from, int maxDistance) {
        return from.getTargetEntity(maxDistance);
    }

    @Override
    public Block targetBlock(Player from, int maxDistance) {
        return from.getTargetBlockExact(maxDistance);
    }

    @Override
    public void registerCommand(Server server, String fallbackPrefix, Command command) {
        server.getCommandMap().register(fallbackPrefix, command);
    }
}
