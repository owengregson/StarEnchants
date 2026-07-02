package api;

import api.spi.AddonEffect;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.bukkit.inventory.ItemStack;

/**
 * The service third-party add-ons look up to extend and query StarEnchants (ADR-0038). Obtain the singleton
 * from Bukkit's {@code ServicesManager} in your own {@code onEnable} (declare {@code depend: [StarEnchants]}
 * so it is registered first):
 *
 * <pre>{@code
 * RegisteredServiceProvider<StarEnchantsApi> rsp =
 *     getServer().getServicesManager().getRegistration(StarEnchantsApi.class);
 * StarEnchantsApi se = rsp.getProvider();
 * se.registerEffect(new MyEffect());
 * }</pre>
 *
 * <p>ServiceLoader was considered and rejected for discovery: it is unreliable across Bukkit's per-plugin
 * classloaders. The {@code ServicesManager} is the Bukkit-idiomatic equivalent (ADR-0038).
 *
 * <p>The read-only queries mirror the engine's own item-data view. Keys are the version-stable content keys
 * (e.g. {@code enchants/venom}), never dense ids, so they are safe to persist and compare across reloads.
 */
public interface StarEnchantsApi {

    /**
     * Register an add-on effect kind so its {@link AddonEffect#spec() head} becomes authorable in content
     * YAML. May be called at any time (typically from an add-on's {@code onEnable}); it triggers a
     * transactional off-thread content reload so newly-registered heads become compilable and any content
     * using them takes effect. Re-registering a head already present is rejected by the underlying registry.
     *
     * @return the reload triggered by this registration — {@code true} once content republished cleanly
     */
    CompletableFuture<Boolean> registerEffect(AddonEffect effect);

    /**
     * Trigger a transactional content reload (the same off-thread build + atomic swap {@code /se reload}
     * runs). Mirrors the engine's {@code ContentReloader}: the future completes {@code true} on a clean
     * publish and {@code false} when the build had blocking diagnostics (the previous content stays live).
     */
    CompletableFuture<Boolean> reloadContent();

    /** The custom enchants on {@code stack}, as stable key &rarr; level; empty if none. Never null. */
    Map<String, Integer> enchantsOf(ItemStack stack);

    /** The applied crystals on {@code stack}, as stable keys in order (crystals stack); empty if none. Never null. */
    List<String> crystalsOf(ItemStack stack);

    /** The armour-set {@code stack} belongs to as a member (stable key), or empty if none. */
    Optional<String> setOf(ItemStack stack);

    /** The remaining free enchant slots on {@code stack} (base + purchased − used), or empty if the item carries no combat state. */
    OptionalInt slotsOf(ItemStack stack);

    /** Every registered enchant's stable key, from the live content. Never null. */
    Set<String> enchantKeys();
}
