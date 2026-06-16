package tester.suite;

import java.util.OptionalInt;
import schema.spec.HandleCategory;
import platform.resolve.RegistryResolvers;
import tester.harness.Harness;

/**
 * Live checks for the production cross-version resolver (docs/architecture.md §9; cross-version-item-api
 * skill). This is the whole reason the version matrix exists: a token authored in any era
 * ({@code CONFUSION}, {@code DAMAGE_ALL}, {@code SULPHUR}, {@code GENERIC_MAX_HEALTH}, …) must resolve
 * to a handle that genuinely exists on THIS server — across the 1.20.5 spigot&rarr;mojang flip, the
 * 1.21.3 attribute de-prefixing, and the enum&rarr;interface transitions. Each token is its own check
 * so a failure pinpoints the exact (category, token) that did not resolve on the version under test.
 *
 * <p>Both the legacy and modern spelling of representative renames are asserted, so the resolver is
 * proven bidirectional on every version (legacy&rarr;modern on a new server, modern&rarr;legacy on an
 * old one). Pure CPU — resolved synchronously on launch.
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

        check(h, "resolve.particle.VILLAGER_HAPPY", r.particle("VILLAGER_HAPPY")); // → HAPPY_VILLAGER
        check(h, "resolve.particle.HAPPY_VILLAGER", r.particle("HAPPY_VILLAGER"));

        check(h, "resolve.sound.ENTITY_PLAYER_LEVELUP", r.sound("ENTITY_PLAYER_LEVELUP"));

        // The runtime's id→handle reverse lookup must map a resolved id back to a real name.
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
