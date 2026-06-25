package tester.suite;

import java.util.OptionalInt;
import schema.spec.HandleCategory;
import platform.resolve.RegistryResolvers;
import tester.harness.Harness;

/**
 * Production cross-version resolver, live (docs/architecture.md §9; cross-version-item-api skill) — the
 * reason the version matrix exists: a token authored in any era must resolve to a handle that exists on
 * THIS server, across the 1.20.5 spigot&rarr;mojang flip, 1.21.3 attribute de-prefixing, and the
 * enum&rarr;interface transitions. One check per token pinpoints the exact (category, token) that fails.
 * Both legacy and modern spellings of representative renames are asserted, proving the resolver
 * bidirectional on every version.
 */
public final class ResolverSuite implements Harness.Scenario {

    @Override
    public void accept(Harness h) {
        RegistryResolvers r = new RegistryResolvers();

        check(h, "resolve.material.SULPHUR", r.material("SULPHUR"));         // → GUNPOWDER
        check(h, "resolve.material.GUNPOWDER", r.material("GUNPOWDER"));

        check(h, "resolve.potion.CONFUSION", r.potionEffect("CONFUSION"));   // → NAUSEA
        check(h, "resolve.potion.NAUSEA", r.potionEffect("NAUSEA"));
        check(h, "resolve.potion.INCREASE_DAMAGE", r.potionEffect("INCREASE_DAMAGE")); // → STRENGTH

        check(h, "resolve.ench.DAMAGE_ALL", r.enchantment("DAMAGE_ALL"));    // → SHARPNESS
        check(h, "resolve.ench.SHARPNESS", r.enchantment("SHARPNESS"));
        check(h, "resolve.ench.PROTECTION_ENVIRONMENTAL", r.enchantment("PROTECTION_ENVIRONMENTAL")); // → PROTECTION

        check(h, "resolve.attr.GENERIC_MAX_HEALTH", r.attribute("GENERIC_MAX_HEALTH")); // → MAX_HEALTH
        check(h, "resolve.attr.MAX_HEALTH", r.attribute("MAX_HEALTH"));

        check(h, "resolve.entity.PIG_ZOMBIE", r.entityType("PIG_ZOMBIE"));   // → ZOMBIFIED_PIGLIN
        check(h, "resolve.entity.ZOMBIE", r.entityType("ZOMBIE"));
        // PRIMED_TNT (floor) / TNT (modern) — both must resolve; SPAWN_ENTITY's TNT path needs the alias.
        check(h, "resolve.entity.PRIMED_TNT", r.entityType("PRIMED_TNT"));
        check(h, "resolve.entity.TNT", r.entityType("TNT"));

        check(h, "resolve.particle.VILLAGER_HAPPY", r.particle("VILLAGER_HAPPY")); // → HAPPY_VILLAGER
        check(h, "resolve.particle.HAPPY_VILLAGER", r.particle("HAPPY_VILLAGER"));

        check(h, "resolve.sound.ENTITY_PLAYER_LEVELUP", r.sound("ENTITY_PLAYER_LEVELUP"));
        // Multi-word segments (lightning_bolt, ender_dragon): a naive '_'→'.' registry key mangles the
        // boundary, so on 26.1.x (registry-backed Sound) these resolve only via the static-field path.
        check(h, "resolve.sound.ENTITY_LIGHTNING_BOLT_THUNDER", r.sound("ENTITY_LIGHTNING_BOLT_THUNDER"));
        check(h, "resolve.sound.ENTITY_ENDER_DRAGON_GROWL", r.sound("ENTITY_ENDER_DRAGON_GROWL"));

        // The runtime's id→name reverse lookup must round-trip a resolved id back to a real name.
        h.expect("resolve.reverseLookup");
        h.guard("resolve.reverseLookup", () -> {
            OptionalInt id = r.potionEffect("CONFUSION");
            if (id.isEmpty()) {
                throw new IllegalStateException("CONFUSION did not resolve, cannot reverse-look-up");
            }
            String name = r.nameOf(HandleCategory.POTION_EFFECT, id.getAsInt());
            if (name == null || name.isEmpty()) {
                throw new IllegalStateException("resolved id " + id.getAsInt() + " has no canonical name");
            }
        });
    }

    private static void check(Harness h, String name, OptionalInt result) {
        h.expect(name);
        if (result.isPresent()) {
            h.pass(name);
        } else {
            h.fail(name, "did not resolve on this version");
        }
    }
}
