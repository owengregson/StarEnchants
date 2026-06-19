package engine.selector.kind;

import engine.selector.SelectorRegistry;

/**
 * The explicit, greppable list of every built-in selector kind (docs/architecture.md
 * §7, the {@code Self/Victim/Attacker/Aoe/Nearest} family). Adding a selector is:
 * implement {@link engine.selector.SelectorKind}, then add one
 * {@code .register(new ...)} line here — the same one-file wiring as
 * {@link engine.effect.kind.BuiltinEffects}, with no annotation scan and no generated
 * table.
 */
public final class BuiltinSelectors {

    private BuiltinSelectors() {
    }

    /** A registry of all built-in selector kinds. */
    public static SelectorRegistry registry() {
        return SelectorRegistry.builder()
                .register(new SelfSelector())
                .register(new VictimSelector())
                .register(new AttackerSelector())
                .register(new NearestSelector())
                .register(new AoeSelector())
                // v3.1 §A — the AE entity-selector set (the block/location selectors await a location-target seam).
                .register(new AllPlayersSelector())
                .register(new NearestPlayerSelector())
                .register(new PlayerFromNameSelector())
                .register(new EntityInSightSelector())
                .build();
    }
}
