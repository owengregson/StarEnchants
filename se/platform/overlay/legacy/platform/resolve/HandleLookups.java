package platform.resolve;

/**
 * The platform bindings twin (ADR-0044) — the legacy half, same FQN as the {@code overlay/modern} copy. The era
 * choice for the handle lookup is constructed below the composition root ({@code new RegistryResolvers()} in
 * engine {@code ContentCompiler.production()} and both tester eras), so this shadowing factory hands those sites
 * the 1.8 impl. Construction-only by the G1 gate: {@link #current()} returns {@link LegacyHandleLookup}.
 */
public final class HandleLookups {

    private HandleLookups() {
    }

    public static HandleLookup current() {
        return new LegacyHandleLookup();
    }
}
