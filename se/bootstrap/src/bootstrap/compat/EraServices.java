package bootstrap.compat;

import engine.run.ActorProbe;
import engine.sink.SinkFactory;
import engine.stores.KnockbackControlStore;
import feature.combat.EquipListener;
import feature.combat.KnockbackListener;
import feature.compat.DropControl;
import feature.compat.Hands;
import feature.compat.Projectiles;
import feature.compat.Sounds;
import feature.fx.ParticleFx;
import feature.heroic.VanillaStats;
import feature.scroll.AnvilRename;
import feature.trigger.TriggerDispatch;
import item.codec.CombatCodec;
import item.codec.ItemStateStore;
import item.head.EquipmentRepaint;
import item.head.TexturedHeads;
import item.worn.EquipSource;
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
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import platform.economy.EconomyProvider;
import platform.protect.ProtectionProvider;

/**
 * The era-services surface (ADR-0044): every version-specific service the composition root needs, so the whole
 * era wiring hangs off ONE seam. The two {@code EraBindings} twins (one per overlay) implement it — the ONLY
 * remaining same-FQN twin in {@code bootstrap} — so cross-era API parity is a per-era {@code javac} fact. It
 * absorbs the former Wiring / Bridges / Targets / Commands seams; each impl method is construction or one-line
 * delegation only (the composition-only rule enforced by the era-tree gate). The modern impl delegates to
 * {@code RuntimeHandles} / {@code Integrations} / the raytrace API; the legacy impl returns the neutral defaults
 * and the 1.8 edges (NMS, line-of-sight scan, the one {@code LegacyGearPoll}).
 */
public interface EraServices {

    // ── runtime resolver pieces (id → live object / NMS) ──
    SinkFactory sinkFactory();

    ParticleFx particleFx();

    ItemStateStore itemStateStore();

    EquipSource equipSource();

    ActorProbe actorProbe();

    /** Resolve a canonical enchant name to a live {@link Enchantment} (cross-version spelling), or {@code null}. */
    Function<String, Enchantment> enchantResolver();

    // ── feature.compat seams ──
    Hands hands();

    DropControl dropControl();

    Projectiles projectiles();

    Sounds sounds();

    VanillaStats vanillaStats();

    /** Base64-textured player-head minting (ADR-0052 pets): modern Paper profiles / 1.8 SKULL_ITEM+authlib. */
    TexturedHeads texturedHeads();

    /** Client-only worn-helmet repaint (ADR-0053 masks): modern {@code sendEquipmentChange} / 1.8 equipment packet. */
    EquipmentRepaint equipmentRepaint();

    /** Client-side helmet-wearability strip for mask/pet heads (1.8.4): modern overrides the {@code equippable}
     *  component (1.21.2+) so the client refuses the slot itself; {@code NONE} below that era and on 1.8. */
    item.head.HeadEquip headEquip();

    /** Worn-tooltip attribute copier for the mask illusion's shown head (1.8.1) — {@code NONE} on 1.8. */
    item.head.HeadAttributes headAttributes();

    AnvilRename anvilRename();

    // ── era listeners / registration ──
    /** §B armour-change feeder that drives {@link EquipListener#refresh} (modern event / 1.8 poll). */
    Listener armourChangeFeeder(EquipListener equip);

    /** §B hand-change feeder that drives {@link EquipListener#refresh} on off-hand swap / pickup-into-empty-hand
     *  (modern only; 1.8 has no off-hand, so an inert listener — G02/F09). */
    Listener handChangeFeeder(EquipListener equip);

    /** POTION_LOCK guard: modern {@code EntityPotionEffectEvent} cancel of a locked type (genuine prevention); 1.8
     *  has no such event, so an inert listener — the Sink's per-tick re-strip is the sole enforcer there. */
    Listener potionLockGuard();

    /** ITEM_DAMAGE source (modern {@code PlayerItemDamageEvent}; 1.8 an inert listener — the poll fires it). */
    Listener itemDamageSource(TriggerDispatch dispatch);

    /** §F heroic durability save (modern per-event cancel; 1.8 an inert listener — the poll restores post-hoc). */
    Listener heroicDurabilitySave(CombatCodec codec, Random rolls);

    /** Vanilla-station guard (G04/G05/G06): modern smithing/grindstone/anvil prepare-event guard; 1.8 the
     *  anvil-click twin (the only station that exists there). */
    Listener stationGuard(feature.guard.StationGuardRules rules);

    /** Dispenser head-equip guard (1.8.4): modern cancels {@code BlockDispenseArmorEvent} for a mask/pet head; 1.8
     *  an inert listener — the event does not exist on that floor for a dispenser to fire. */
    Listener dispenseArmorGuard(Predicate<ItemStack> isHeadItem);

    /** §C KNOCKBACK_CONTROL: register the applier this server fires knockback through; returns the chosen path. */
    KnockbackListener.Path registerKnockback(Plugin plugin, KnockbackControlStore store, LongSupplier nowTicks);

    // ── third-party integration bridges (ADR-0027; :integrate is modern-only) ──
    BiFunction<Player, String, String> placeholderResolver(Plugin plugin, Predicate<String> enabled);

    void registerPlaceholders(Plugin plugin, Predicate<String> enabled,
                              Predicate<Player> soulMode, ToIntFunction<Player> souls);

    List<ProtectionProvider> protectionProviders(Plugin plugin, Predicate<String> enabled);

    EconomyProvider economyProvider(Plugin plugin, Predicate<String> enabled);

    Consumer<Player> antiCheatExemption(Plugin plugin, Predicate<String> enabled, System.Logger log);

    BiPredicate<Player, Player> mcmmoFriendlyFire(Plugin plugin, Predicate<String> enabled);

    Function<Entity, String> mythicMobType(Plugin plugin, Predicate<String> enabled);

    Function<String, ItemStack> customItem(Plugin plugin, Predicate<String> enabled);

    // ── targeting + dynamic commands ──
    Entity targetEntity(Player from, int maxDistance);

    Block targetBlock(Player from, int maxDistance);

    void registerCommand(Server server, String fallbackPrefix, Command command);
}
