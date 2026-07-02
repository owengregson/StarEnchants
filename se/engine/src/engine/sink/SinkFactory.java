package engine.sink;

/**
 * Builds a per-event {@link SinkReadback} from the per-boot {@link SinkEnv}. The seam that keeps the feature
 * dispatchers free of the version-specific resolver type: the modern impl wraps {@code RuntimeHandles}, the
 * legacy impl wraps {@code RenameResolvers} (the modern {@code RuntimeHandles} does not exist on 1.8 —
 * docs/legacy-1.8.9-codeshare-design.md §3.5/§4). The dispatchers hold a {@code SinkFactory} and call
 * {@link #create} with the shared env; the composition root supplies the right impl for the target.
 */
public interface SinkFactory {

    SinkReadback create(SinkEnv env);
}
