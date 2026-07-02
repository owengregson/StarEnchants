package platform.resolve;

/**
 * The platform bindings twin (ADR-0044) — one of the only two same-FQN twins in the tree. It exists because the
 * era choice for the handle lookup is constructed <em>below</em> the composition root: {@code new
 * RegistryResolvers()} runs in engine {@code ContentCompiler.production()} and in both tester eras, so those
 * sites must reach the era impl without threading it from {@code bootstrap}. Construction-only by the G1 gate:
 * {@link #current()} just returns the era's impl. This is the modern half — it returns {@link ModernHandleLookup}.
 */
public final class HandleLookups {

    private HandleLookups() {
    }

    public static HandleLookup current() {
        return new ModernHandleLookup();
    }
}
