package engine.sink;

import platform.resolve.RenameResolvers;

/**
 * Legacy (1.8.9) {@link SinkFactory}: wraps {@code RenameResolvers} (the legacy {@link DispatchSink}
 * resolves interned ids to 1.8 names/NMS itself, since the modern {@code RuntimeHandles} — which casts to
 * {@code Particle}/{@code Attribute} — does not exist on 1.8). Same-FQN counterpart to the
 * {@code overlay/modern} impl (docs/legacy-1.8.9-codeshare-design.md §3.5/§4).
 */
public final class DispatchSinkFactory implements SinkFactory {

    private final RenameResolvers resolvers;

    public DispatchSinkFactory(RenameResolvers resolvers) {
        this.resolvers = resolvers;
    }

    @Override
    public SinkReadback create(SinkEnv env) {
        return new DispatchSink(resolvers, env);
    }
}
