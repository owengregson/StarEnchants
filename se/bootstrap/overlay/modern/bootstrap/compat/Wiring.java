package bootstrap.compat;

import engine.run.ActorProbe;
import engine.run.ModernActorProbe;
import engine.sink.ModernDispatchSink;
import engine.sink.SinkFactory;
import feature.compat.DropControl;
import feature.compat.Hands;
import feature.compat.ModernDropControl;
import feature.compat.ModernHands;
import feature.compat.ModernProjectiles;
import feature.compat.Projectiles;
import feature.compat.Sounds;
import feature.fx.ParticleFx;
import item.codec.ItemStateStore;
import item.codec.PdcItemStateStore;
import item.worn.EquipSource;
import item.worn.ModernEquipSource;
import java.util.OptionalInt;
import java.util.function.Function;
import org.bukkit.enchantments.Enchantment;
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
        return new ParticleFx(token -> {
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

    /**
     * §6.6 set-piece base enchants: resolve a modern canonical enchant name to a live {@link Enchantment} via
     * the namespaced-key registry ({@code PROTECTION} → {@code minecraft:protection}). Miss → {@code null}.
     */
    public Function<String, Enchantment> enchantResolver() {
        return name -> (Enchantment) handles.resolveByName(HandleCategory.ENCHANTMENT, name);
    }
}
