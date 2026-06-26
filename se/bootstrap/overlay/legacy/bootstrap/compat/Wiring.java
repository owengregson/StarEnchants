package bootstrap.compat;

import engine.sink.DispatchSinkFactory;
import engine.sink.SinkFactory;
import feature.fx.ParticleFx;
import platform.resolve.RegistryResolvers;

/**
 * Legacy (1.8.9) composition wiring — same-FQN counterpart to the {@code overlay/modern} impl. 1.8 has no
 * {@code RuntimeHandles} (it casts to Particle/Attribute): the legacy {@code DispatchSinkFactory} takes the
 * {@code RenameResolvers} directly (the legacy DispatchSink resolves ids to NMS by name), and the legacy
 * {@code ParticleFx} is the no-arg NMS-packet impl (docs/legacy-1.8.9-codeshare-design.md §4).
 */
public final class Wiring {

    private final RegistryResolvers resolvers;

    public Wiring(RegistryResolvers resolvers) {
        this.resolvers = resolvers;
    }

    public ParticleFx particleFx() {
        return new ParticleFx();
    }

    public SinkFactory sinkFactory() {
        return new DispatchSinkFactory(resolvers); // RegistryResolvers is a RenameResolvers
    }
}
