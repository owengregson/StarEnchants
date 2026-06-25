package engine.selector.kind;

import engine.selector.SelectorRegistry;

/**
 * Every built-in selector kind, wired in one place (docs/architecture.md §7). Adding a selector is
 * implementing {@link engine.selector.SelectorKind} plus one {@code .register(new ...)} line here — no
 * annotation scan, no generated table.
 */
public final class BuiltinSelectors {

    private BuiltinSelectors() {
    }

    public static SelectorRegistry registry() {
        return SelectorRegistry.builder()
                .register(new SelfSelector())
                .register(new VictimSelector())
                .register(new AttackerSelector())
                .register(new NearestSelector())
                .register(new AoeSelector())
                // v3.1 §A — Cosmic Enchants-style entity selectors.
                .register(new AllPlayersSelector())
                .register(new NearestPlayerSelector())
                .register(new PlayerFromNameSelector())
                .register(new EntityInSightSelector())
                // §A block/location selectors — resolve to LOCATIONS.
                .register(new HereSelector())
                .register(new AddSelector())
                .register(new EyeHeightSelector())
                .register(new BlockSelector())
                .register(new BlockInDistanceSelector())
                .register(new TrenchSelector())
                .register(new TunnelSelector())
                .register(new VeinSelector())
                .build();
    }
}
