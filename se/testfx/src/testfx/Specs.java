package testfx;

import compile.SpecRegistry;
import java.util.function.Function;
import schema.spec.ParamSpec;

/** The one canonical {@link SpecRegistry} &rarr; nullable head&rarr;{@link ParamSpec} lookup the migrator writers take. */
public final class Specs {

    private Specs() {
    }

    /** {@code head ->} the registry's {@link ParamSpec} for it, or {@code null} when unknown. */
    public static Function<String, ParamSpec> lookup(SpecRegistry registry) {
        return head -> registry.lookup(head).orElse(null);
    }
}
