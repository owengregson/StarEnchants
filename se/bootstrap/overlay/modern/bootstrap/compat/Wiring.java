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
import feature.scroll.AnvilRename;
import feature.scroll.ModernAnvilRename;
import item.codec.ItemStateStore;
import item.codec.PdcItemStateStore;
import item.worn.EquipSource;
import item.worn.ModernEquipSource;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.function.LongSupplier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.plugin.Plugin;
import platform.resolve.RegistryResolvers;
import platform.resolve.RuntimeHandles;
import schema.spec.HandleCategory;

/**
 * Modern composition wiring for the version-specific runtime resolver pieces: {@code RuntimeHandles}
 * (id&rarr;live-object) feeds both the particle fx and the sink factory. Same-FQN counterpart to the
 * {@code overlay/legacy} impl, which has no {@code RuntimeHandles} (the legacy DispatchSink resolves ids to
 * NMS itself, and the legacy {@code ParticleFx} sends NMS packets) — docs/legacy-1.8.9-codeshare-design.md §4.
 */
public final class Wiring {

    private final RegistryResolvers resolvers;
    private final RuntimeHandles handles;

    public Wiring(RegistryResolvers resolvers) {
        this.resolvers = resolvers;
        this.handles = new RuntimeHandles(resolvers);
    }

    /** §D/§I particle feedback: token → interner → live Particle, skip-on-miss. */
    public ParticleFx particleFx() {
        return new ModernParticleFx(token -> {
            OptionalInt id = resolvers.particle(token);
            return id.isPresent() ? handles.particle(id.getAsInt()) : null;
        });
    }

    public SinkFactory sinkFactory() {
        return env -> new ModernDispatchSink(handles, env);
    }

    /** The physical item-data layer (§4.2): modern PDC. Injected into every codec + the lore renderer. */
    public ItemStateStore itemStateStore() {
        return new PdcItemStateStore();
    }

    /** The worn/held equipment read (§3.3): modern 6-slot (incl. off-hand). Injected into {@code WornResolver}. */
    public EquipSource equipSource() {
        return new ModernEquipSource();
    }

    /** The entity/material fact reads (§3.3): modern swim/glide/isAir/main-hand. Injected into {@code FactPopulator}. */
    public ActorProbe actorProbe() {
        return new ModernActorProbe();
    }

    /** Hand/equipment access (§4): modern off-hand-aware. Injected into the feature shells. */
    public Hands hands() {
        return new ModernHands();
    }

    /** Block-break drop suppression (§4): modern {@code setDropItems(false)}. Injected into {@code MineDrops}. */
    public DropControl dropControl() {
        return new ModernDropControl();
    }

    /** Projectile-type routing (§4): modern {@code Trident}/{@code AbstractArrow}. Injected into the combat router. */
    public Projectiles projectiles() {
        return new ModernProjectiles();
    }

    /** Sound playback (§4): the shared resolver + the modern String-overload key-form fallback (1.9.4+). */
    public Sounds sounds() {
        return new Sounds((player, at, key, volume, pitch) -> player.playSound(at, key, volume, pitch));
    }

    /** §F heroic vanilla stats (§4): modern writes real diamond attributes. Injected into {@code HeroicService}. */
    public VanillaStats vanillaStats() {
        return new ModernVanillaStats();
    }

    /** §I nametag rename (§4): modern opens a real anvil + colour preview. Into {@code NametagListener}. */
    public AnvilRename anvilRename() {
        return new ModernAnvilRename();
    }

    /**
     * §C KNOCKBACK_CONTROL (§4): register the applier this modern server fires knockback through — the 1.20.6+
     * event (reflective) or Paper's legacy event ({@link LegacyKnockbackListener}); returns the chosen path.
     */
    public KnockbackListener.Path registerKnockback(Plugin plugin, KnockbackControlStore store, LongSupplier nowTicks) {
        return KnockbackListener.register(plugin, store, nowTicks, new LegacyKnockbackListener(store, nowTicks));
    }

    /** §B armour-change source (§4/§6): modern Paper {@code PlayerArmorChangeEvent}. Drives {@code EquipListener.refresh}. */
    public Listener armourChangeFeeder(EquipListener equip) {
        return new ModernArmourChangeListener(equip);
    }

    /** ITEM_DAMAGE source (§4): modern {@code PlayerItemDamageEvent} listener (1.8 fires it from the gear poll). */
    public Listener itemDamageSource(TriggerDispatch dispatch) {
        return new DurabilityTriggerListener(dispatch);
    }

    /** §F heroic durability save (§4): modern per-item-damage cancel (1.8 restores post-hoc via the gear poll). */
    public Listener heroicDurabilitySave(CombatCodec codec, Random rolls) {
        return new HeroicDurabilityListener(codec, rolls);
    }

    /**
     * §6.6 set-piece base enchants: resolve a modern canonical enchant name to a live {@link Enchantment} via
     * the namespaced-key registry ({@code PROTECTION} → {@code minecraft:protection}). Miss → {@code null}.
     */
    public Function<String, Enchantment> enchantResolver() {
        return name -> (Enchantment) handles.resolveByName(HandleCategory.ENCHANTMENT, name);
    }
}
