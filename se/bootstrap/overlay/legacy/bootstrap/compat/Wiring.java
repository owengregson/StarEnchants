package bootstrap.compat;

import engine.run.ActorProbe;
import engine.run.LegacyActorProbe;
import engine.sink.LegacyDispatchSink;
import engine.sink.SinkFactory;
import engine.run.ActivationContext;
import engine.stores.KnockbackControlStore;
import feature.combat.EquipListener;
import feature.combat.KnockbackListener;
import feature.combat.LegacyArmourCloseListener;
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
import feature.scroll.AnvilRename;
import feature.trigger.LegacyGearPoll;
import feature.trigger.TriggerDispatch;
import item.codec.CombatCodec;
import item.codec.ItemStateStore;
import item.codec.NbtItemStateStore;
import item.worn.EquipSource;
import item.worn.LegacyEquipSource;
import java.util.Locale;
import java.util.Random;
import java.util.function.Function;
import java.util.function.LongSupplier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import platform.resolve.RegistryResolvers;

/**
 * Legacy (1.8.9) composition wiring — same-FQN counterpart to the {@code overlay/modern} impl. 1.8 has no
 * {@code RuntimeHandles} (it casts to Particle/Attribute): the legacy {@code DispatchSinkFactory} takes the
 * {@code RenameResolvers} directly (the legacy DispatchSink resolves ids to NMS by name), and the legacy
 * {@code ParticleFx} is the no-arg NMS-packet impl (docs/legacy-1.8.9-codeshare-design.md §4).
 */
public final class Wiring {

    private final RegistryResolvers resolvers;
    // §6 the ONE legacy per-tick gear poll: the three era feeders attach their subscribers to it (ADR-0044).
    private final LegacyGearPoll gearPoll = new LegacyGearPoll();

    public Wiring(RegistryResolvers resolvers) {
        this.resolvers = resolvers;
    }

    public ParticleFx particleFx() {
        return new LegacyParticleFx();
    }

    public SinkFactory sinkFactory() {
        return env -> new LegacyDispatchSink(resolvers, env); // RegistryResolvers is a RenameResolvers
    }

    /** The physical item-data layer (§4.2): 1.8 NMS tags. Injected into every codec + the lore renderer. */
    public ItemStateStore itemStateStore() {
        return new NbtItemStateStore();
    }

    /** The worn/held equipment read (§3.3): 1.8 5-slot (main hand only). Injected into {@code WornResolver}. */
    public EquipSource equipSource() {
        return new LegacyEquipSource();
    }

    /** The entity/material fact reads (§3.3): 1.8-correct swim/glide/isAir/main-hand. Injected into {@code FactPopulator}. */
    public ActorProbe actorProbe() {
        return new LegacyActorProbe();
    }

    /** Hand/equipment access (§4): the single 1.8 hand (no off-hand). Injected into the feature shells. */
    public Hands hands() {
        return new LegacyHands();
    }

    /** Block-break drop suppression (§4): 1.8 cancel + clear (no {@code setDropItems}). Injected into {@code MineDrops}. */
    public DropControl dropControl() {
        return new LegacyDropControl();
    }

    /** Projectile-type routing (§4): 1.8 {@code Arrow} only. Injected into the combat router. */
    public Projectiles projectiles() {
        return new LegacyProjectiles();
    }

    /** Sound playback (§4): the shared resolver; 1.8 has no String overload, so key-form sounds are skipped. */
    public Sounds sounds() {
        return new Sounds(KeySoundFallback.NONE);
    }

    /** §F heroic vanilla stats (§4): 1.8 has no attribute API — keep the plugin-maths fold. Into {@code HeroicService}. */
    public VanillaStats vanillaStats() {
        return VanillaStats.NONE;
    }

    /** §I nametag rename (§4): 1.8 has no live anvil-rename field — chat capture. Into {@code NametagListener}. */
    public AnvilRename anvilRename() {
        return AnvilRename.UNSUPPORTED;
    }

    /**
     * §C KNOCKBACK_CONTROL (§4): 1.8 fires no Paper knockback event, so the {@link NmsKnockbackApplier}
     * (knockback-resistance at the NMS source) is always the applier. Returns {@link KnockbackListener.Path#LEGACY}.
     */
    public KnockbackListener.Path registerKnockback(Plugin plugin, KnockbackControlStore store, LongSupplier nowTicks) {
        plugin.getServer().getPluginManager().registerEvents(new NmsKnockbackApplier(store, nowTicks), plugin);
        return KnockbackListener.Path.LEGACY;
    }

    /** §B armour-change source (§4/§6): the gear-poll armour-signature delta drives refresh; close is the backup. */
    public Listener armourChangeFeeder(EquipListener equip) {
        gearPoll.refreshEquip(equip::refresh);
        return new LegacyArmourCloseListener(equip);
    }

    /** ITEM_DAMAGE source (§4/§6): the gear-poll durability-rise subscriber fires it; no Bukkit event on 1.8. */
    public Listener itemDamageSource(TriggerDispatch dispatch) {
        gearPoll.fireItemDamage(player -> dispatch.fire(player, dispatch.itemDamage,
                new ActivationContext(player, null, null, player.getLocation()), null));
        return NoopListener.INSTANCE;
    }

    /** §F heroic durability save (§4/§6): the gear-poll heroic subscriber restores the lost durability post-hoc. */
    public Listener heroicDurabilitySave(CombatCodec codec, Random rolls) {
        gearPoll.heroicSave(new LegacyHeroicSave(codec, rolls));
        return NoopListener.INSTANCE;
    }

    /**
     * §6.6 set-piece base enchants: resolve a modern canonical enchant name to a 1.8 {@link Enchantment}. 1.8
     * uses the pre-1.13 spelling ({@code PROTECTION} → {@code PROTECTION_ENVIRONMENTAL}, {@code UNBREAKING} →
     * {@code DURABILITY}, {@code SHARPNESS} → {@code DAMAGE_ALL}), so the modern name is mapped before the
     * {@code getByName} lookup. Miss → {@code null}.
     */
    @SuppressWarnings("deprecation") // Enchantment.getByName is the 1.8 lookup (deprecated-not-removed)
    public Function<String, Enchantment> enchantResolver() {
        return name -> {
            String n = name == null ? "" : name.toUpperCase(Locale.ROOT);
            String legacy = switch (n) {
                case "PROTECTION" -> "PROTECTION_ENVIRONMENTAL";
                case "UNBREAKING" -> "DURABILITY";
                case "SHARPNESS" -> "DAMAGE_ALL";
                default -> n;
            };
            Enchantment byLegacy = Enchantment.getByName(legacy);
            return byLegacy != null ? byLegacy : Enchantment.getByName(n);
        };
    }
}
