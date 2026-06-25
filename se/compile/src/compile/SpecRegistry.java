package compile;

import schema.spec.ParamSpec;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves a kind's head name (e.g. {@code DAMAGE}) to its {@link ParamSpec} — the explicit, greppable
 * registration the architecture mandates over annotation-processor codegen (docs/architecture.md §7, §13.2).
 */
public interface SpecRegistry {

    /** The spec registered under {@code head} (case-insensitive), if any. */
    Optional<ParamSpec> lookup(String head);

    /** Every registered head, in canonical (upper-case) form. */
    Set<String> heads();
}
