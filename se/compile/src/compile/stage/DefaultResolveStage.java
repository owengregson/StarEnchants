package compile.stage;

import compile.SpecRegistry;
import compile.model.CompiledEffect;
import compile.resolve.PlatformResolvers;
import schema.diag.DiagCode;
import schema.diag.Diagnostics;
import schema.spec.Args;
import schema.spec.HandleCategory;
import schema.spec.Param;
import schema.spec.ParamSpec;
import schema.spec.ParamType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The default {@link ResolveStage}: resolves each effect's {@code HANDLE}-typed params from authored
 * tokens to interned ids via the injected {@link PlatformResolvers} (docs/architecture.md §9). Injection
 * keeps {@code se-compile} Bukkit-free — production wires {@code se-platform}'s resolvers, tests a fake.
 *
 * <p>A handle token may be a {@code A|B|C} FALLBACK CHAIN (any category): the candidates are resolved in
 * order and the FIRST that resolves on this version wins, so {@code ENTITY_BREEZE_WIND_BURST|ENTITY_POLAR_BEAR_HURT}
 * loads the modern sound on 1.21+ and the fallback on the floor — per-version selection at compile time, zero
 * runtime cost. If NONE of the chain resolves the effect is warn-and-skipped ({@link DiagCode#E_UNKNOWN_HANDLE},
 * message carrying the full chain).
 */
public final class DefaultResolveStage implements ResolveStage {

    private final SpecRegistry registry;
    private final PlatformResolvers resolvers;

    public DefaultResolveStage(SpecRegistry registry, PlatformResolvers resolvers) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.resolvers = Objects.requireNonNull(resolvers, "resolvers");
    }

    @Override
    public LoweredAbility resolve(LoweredAbility ability, Diagnostics diags) {
        List<CompiledEffect> out = new ArrayList<>(ability.effects().size());
        for (CompiledEffect effect : ability.effects()) {
            CompiledEffect resolved = resolveEffect(effect, ability, diags);
            if (resolved != null) {
                out.add(resolved); // null ⇒ an unknown handle dropped this one effect
            }
        }
        return new LoweredAbility(
                ability.sourceKind(), ability.stableKey(), ability.defId(), ability.level(),
                ability.baseChance(), ability.cooldownTicks(), ability.soulCost(),
                ability.triggers(), ability.worldBlacklist(), ability.condition(),
                out, ability.suppressKey(), ability.cdScopeEnchant(), ability.cdScopeGroup(),
                ability.cdScopeType(), ability.repeatTicks(), ability.affinity(), ability.source(),
                ability.setPieces());
    }

    /** @return the effect with handle args resolved, or {@code null} if a handle was unknown. */
    private CompiledEffect resolveEffect(CompiledEffect effect, LoweredAbility owner, Diagnostics diags) {
        Optional<ParamSpec> spec = registry.lookup(effect.head());
        if (spec.isEmpty()) {
            return effect; // unknown head was already handled in lowering; leave untouched
        }
        Args args = effect.args();
        for (Param p : spec.get().params()) {
            ParamType type = p.type();
            if (type.kind() != ParamType.Kind.HANDLE || !args.has(p.name())) {
                continue;
            }
            Object current = args.opt(p.name()).orElse(null);
            if (!(current instanceof String token)) {
                continue; // already an int (re-resolved) or otherwise not a token
            }
            OptionalInt id = resolveChain(type.handleCategory(), token);
            if (id.isEmpty()) {
                diags.error(DiagCode.E_UNKNOWN_HANDLE,
                        "unknown " + type.handleCategory().label() + " '" + token
                                + "' for argument '" + p.name() + "' of '" + effect.head() + "'",
                        owner.source(),
                        "use a name valid on the target version, or remove the effect");
                return null; // warn-and-skip this one op (§9)
            }
            args = args.with(p.name(), id.getAsInt());
        }
        return effect.withArgs(args); // keep the stamped kindId (ADR-0039)
    }

    /**
     * Resolve a handle token, honouring the {@code A|B|C} fallback chain: the first candidate that resolves on this
     * version wins (its id is interned by {@link #lookup}). A token with no {@code '|'} is the plain single lookup.
     */
    private OptionalInt resolveChain(HandleCategory category, String token) {
        if (token.indexOf('|') < 0) {
            return lookup(category, token);
        }
        for (String candidate : token.split("\\|")) {
            String trimmed = candidate.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            OptionalInt id = lookup(category, trimmed);
            if (id.isPresent()) {
                return id;
            }
        }
        return OptionalInt.empty();
    }

    private OptionalInt lookup(HandleCategory category, String token) {
        return switch (category) {
            case MATERIAL -> resolvers.material(token);
            case SOUND -> resolvers.sound(token);
            case POTION_EFFECT -> resolvers.potionEffect(token);
            case PARTICLE -> resolvers.particle(token);
            case ENTITY_TYPE -> resolvers.entityType(token);
            case ATTRIBUTE -> resolvers.attribute(token);
            case ENCHANTMENT -> resolvers.enchantment(token);
        };
    }
}
