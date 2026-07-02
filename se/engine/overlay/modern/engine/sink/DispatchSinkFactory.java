package engine.sink;

import platform.resolve.RuntimeHandles;

/**
 * Modern {@link SinkFactory}: wraps {@code RuntimeHandles} (the id&rarr;live-object resolver) and builds the
 * modern {@link DispatchSink}. Same-FQN counterpart to the {@code overlay/legacy} impl (which wraps
 * {@code RenameResolvers}); selected at build assembly.
 */
public final class DispatchSinkFactory implements SinkFactory {

    private final RuntimeHandles handles;

    public DispatchSinkFactory(RuntimeHandles handles) {
        this.handles = handles;
    }

    @Override
    public SinkReadback create(SinkEnv env) {
        return new DispatchSink(handles, env);
    }
}
