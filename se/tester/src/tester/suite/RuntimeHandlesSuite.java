package tester.suite;

import java.util.OptionalInt;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import platform.resolve.RegistryResolvers;
import platform.resolve.RuntimeHandles;
import tester.harness.Harness;

/**
 * Live checks for the runtime handle resolver (docs/architecture.md §9): the full cross-version
 * round-trip <em>token &rarr; interned id (compile) &rarr; live Bukkit object (runtime)</em>. The
 * resolver interns a handle; {@link RuntimeHandles} must hand back the live object the {@code Sink}
 * dispatcher would mutate the world with — on every version, through the enum&rarr;interface and
 * registry-key shifts. Rename targets are spot-checked to a stable constant where one exists
 * (SULPHUR&rarr;GUNPOWDER, ZOMBIE) and to non-null otherwise; cache identity is asserted too.
 */
public final class RuntimeHandlesSuite implements Harness.Scenario {

    @Override
    public void accept(Harness h) {
        RegistryResolvers resolvers = new RegistryResolvers();
        RuntimeHandles handles = new RuntimeHandles(resolvers);

        // SULPHUR resolves to the live GUNPOWDER material (a stable constant across the range).
        h.expect("handles.material.SULPHUR");
        h.guard("handles.material.SULPHUR", () -> {
            Material m = handles.material(internOrThrow(resolvers.material("SULPHUR"), "SULPHUR"));
            if (m != Material.GUNPOWDER) {
                throw new IllegalStateException("SULPHUR resolved to " + m + ", expected GUNPOWDER");
            }
        });

        // PIG_ZOMBIE resolves to a live entity type (ZOMBIFIED_PIGLIN on every supported version).
        h.expect("handles.entity.PIG_ZOMBIE");
        h.guard("handles.entity.PIG_ZOMBIE", () -> {
            EntityType t = handles.entityType(internOrThrow(resolvers.entityType("PIG_ZOMBIE"), "PIG_ZOMBIE"));
            if (t == null) {
                throw new IllegalStateException("PIG_ZOMBIE did not resolve to a live EntityType");
            }
        });

        nonNull(h, "handles.ench.DAMAGE_ALL", () ->
                handles.enchantment(internOrThrow(resolvers.enchantment("DAMAGE_ALL"), "DAMAGE_ALL")));
        nonNull(h, "handles.potion.CONFUSION", () ->
                handles.potionEffect(internOrThrow(resolvers.potionEffect("CONFUSION"), "CONFUSION")));
        nonNull(h, "handles.attr.GENERIC_MAX_HEALTH", () ->
                handles.attribute(internOrThrow(resolvers.attribute("GENERIC_MAX_HEALTH"), "GENERIC_MAX_HEALTH")));
        nonNull(h, "handles.particle.VILLAGER_HAPPY", () ->
                handles.particle(internOrThrow(resolvers.particle("VILLAGER_HAPPY"), "VILLAGER_HAPPY")));
        nonNull(h, "handles.sound.ENTITY_PLAYER_LEVELUP", () ->
                handles.sound(internOrThrow(resolvers.sound("ENTITY_PLAYER_LEVELUP"), "ENTITY_PLAYER_LEVELUP")));

        // Cache identity: the same id resolves to the very same object instance.
        h.expect("handles.cacheIdentity");
        h.guard("handles.cacheIdentity", () -> {
            int id = internOrThrow(resolvers.material("GUNPOWDER"), "GUNPOWDER");
            if (handles.material(id) != handles.material(id)) {
                throw new IllegalStateException("RuntimeHandles did not cache the resolved object");
            }
        });
    }

    private static int internOrThrow(OptionalInt id, String token) {
        if (id.isEmpty()) {
            throw new IllegalStateException(token + " did not resolve to a handle id on this version");
        }
        return id.getAsInt();
    }

    private static void nonNull(Harness h, String name, java.util.function.Supplier<Object> resolve) {
        h.expect(name);
        h.guard(name, () -> {
            if (resolve.get() == null) {
                throw new IllegalStateException(name + " resolved to a null live object");
            }
        });
    }
}
