package bootstrap.compat;

import engine.sink.DispatchSinkFactory;
import engine.sink.SinkFactory;
import feature.fx.ParticleFx;
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
        return new DispatchSinkFactory(handles);
    }

    /**
     * §6.6 set-piece base enchants: resolve a modern canonical enchant name to a live {@link Enchantment} via
     * the namespaced-key registry ({@code PROTECTION} → {@code minecraft:protection}). Miss → {@code null}.
     */
    public Function<String, Enchantment> enchantResolver() {
        return name -> (Enchantment) handles.resolveByName(HandleCategory.ENCHANTMENT, name);
    }
}
