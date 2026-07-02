package bootstrap.compat;

import engine.sink.DispatchSinkFactory;
import engine.sink.SinkFactory;
import feature.fx.ParticleFx;
import item.codec.ItemStateStore;
import item.codec.NbtItemStateStore;
import item.worn.EquipSource;
import item.worn.LegacyEquipSource;
import java.util.Locale;
import java.util.function.Function;
import org.bukkit.enchantments.Enchantment;
import platform.resolve.RegistryResolvers;

/**
 * Legacy (1.8.9) composition wiring — same-FQN counterpart to the {@code overlay/modern} impl. 1.8 has no
 * {@code RuntimeHandles} (it casts to Particle/Attribute): the legacy {@code DispatchSinkFactory} takes the
 * {@code RenameResolvers} directly (the legacy DispatchSink resolves ids to NMS by name), and the legacy
 * {@code ParticleFx} is the no-arg NMS-packet impl (docs/legacy-1.8.9-codeshare-design.md §4).
 */
public final class Wiring {

    private final RegistryResolvers resolvers;

    public Wiring(RegistryResolvers resolvers) {
        this.resolvers = resolvers;
    }

    public ParticleFx particleFx() {
        return new ParticleFx();
    }

    public SinkFactory sinkFactory() {
        return new DispatchSinkFactory(resolvers); // RegistryResolvers is a RenameResolvers
    }

    /** The physical item-data layer (§4.2): 1.8 NMS tags. Injected into every codec + the lore renderer. */
    public ItemStateStore itemStateStore() {
        return new NbtItemStateStore();
    }

    /** The worn/held equipment read (§3.3): 1.8 5-slot (main hand only). Injected into {@code WornResolver}. */
    public EquipSource equipSource() {
        return new LegacyEquipSource();
    }

    /**
     * §6.6 set-piece base enchants: resolve a modern canonical enchant name to a 1.8 {@link Enchantment}. 1.8
     * uses the pre-1.13 spelling ({@code PROTECTION} → {@code PROTECTION_ENVIRONMENTAL}, {@code UNBREAKING} →
     * {@code DURABILITY}, {@code SHARPNESS} → {@code DAMAGE_ALL}), so the modern name is mapped before the
     * {@code getByName} lookup. Miss → {@code null}.
     */
    @SuppressWarnings("deprecation") // Enchantment.getByName is the 1.8 lookup (deprecated-not-removed)
    public Function<String, Enchantment> enchantResolver() {
        return name -> {
            String n = name == null ? "" : name.toUpperCase(Locale.ROOT);
            String legacy = switch (n) {
                case "PROTECTION" -> "PROTECTION_ENVIRONMENTAL";
                case "UNBREAKING" -> "DURABILITY";
                case "SHARPNESS" -> "DAMAGE_ALL";
                default -> n;
            };
            Enchantment byLegacy = Enchantment.getByName(legacy);
            return byLegacy != null ? byLegacy : Enchantment.getByName(n);
        };
    }
}
