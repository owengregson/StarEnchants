package compile.stage;

import compile.SpecRegistry;
import compile.model.CompiledEffect;
import compile.model.CompiledSelector;
import compile.resolve.PlatformResolvers;
import schema.diag.DiagCode;
import schema.diag.Diagnostics;
import schema.spec.Args;
import schema.spec.HandleCategory;
import schema.spec.Param;
import schema.spec.ParamSpec;
import schema.spec.ParamType;
import schema.spec.PotionLoadout;
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
 * runtime cost. If NONE of the chain resolves, the op is dropped from the erased content AND an
 * {@link DiagCode#E_UNKNOWN_HANDLE} error (message carrying the full chain) BLOCKS the whole publish — the
 * old content stays live, so a typo can never ship as a silently shorter loadout.
 */
public final class DefaultResolveStage implements ResolveStage {

    private final SpecRegistry registry;
    private final SpecRegistry selectors;
    private final PlatformResolvers resolvers;

    /** No selector registry: a selector's own HANDLE args stay unresolved tokens (the head-fallback wiring). */
    public DefaultResolveStage(SpecRegistry registry, PlatformResolvers resolvers) {
        this(registry, compile.MapSpecRegistry.of(), resolvers);
    }

    public DefaultResolveStage(SpecRegistry registry, SpecRegistry selectors, PlatformResolvers resolvers) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.selectors = Objects.requireNonNull(selectors, "selectors");
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
                ability.setPieces(), ability.suppressImmune(), ability.chanceExpr(), ability.noSoulsMessage(),
                ability.soulCostCarried(), ability.noSoulsSound(), ability.noSoulsParticle(),
                ability.soulCostGrowth(), ability.soulCostCap(), ability.soulCostDecayPeriod(),
                ability.cooldownPerVictim());
    }

    /** @return the effect (and its selector) with handle args resolved, or {@code null} if a handle was unknown. */
    private CompiledEffect resolveEffect(CompiledEffect effect, LoweredAbility owner, Diagnostics diags) {
        CompiledSelector target = resolveSelector(effect, owner, diags);
        if (target == null) {
            return null;
        }
        Optional<ParamSpec> spec = registry.lookup(effect.head());
        if (spec.isEmpty()) {
            return effect.withTarget(target); // unknown head was already handled in lowering; leave args untouched
        }
        Args args = resolveHandles(spec.get(), effect.args(), effect.head(), owner, diags);
        return args == null ? null : effect.withArgs(args).withTarget(target); // keep the stamped kindId (ADR-0039)
    }

    /**
     * The effect's target selector with its own HANDLE args interned (a block selector's {@code materials}).
     * Selector args reach the runtime through the same {@link Args} bag effect args do, so they need the same
     * intern pass — without it a {@code materials} list would arrive as a raw token and filter nothing.
     */
    private CompiledSelector resolveSelector(CompiledEffect effect, LoweredAbility owner, Diagnostics diags) {
        CompiledSelector target = effect.target();
        Optional<ParamSpec> spec = selectors.lookup(target.head());
        if (spec.isEmpty()) {
            return target;
        }
        Args args = resolveHandles(spec.get(), target.args(), "@" + target.head(), owner, diags);
        return args == null ? null : target.withArgs(args);
    }

    /** @return {@code args} with every HANDLE param interned, or {@code null} if one resolved on no version. */
    private Args resolveHandles(ParamSpec spec, Args original, String head,
                                LoweredAbility owner, Diagnostics diags) {
        Args args = original;
        for (Param p : spec.params()) {
            ParamType type = p.type();
            if (type.kind() != ParamType.Kind.HANDLE || !args.has(p.name())) {
                continue;
            }
            Object current = args.opt(p.name()).orElse(null);
            if (!(current instanceof String token)) {
                continue; // already an int / id list (re-resolved) or otherwise not a token
            }
            if (type.isList()) {
                List<Integer> ids = resolveList(type.handleCategory(), token, p, head, owner, diags);
                if (ids == null) {
                    return null; // an unknown entry drops the whole op, as a single handle does — and blocks the publish
                }
                args = args.with(p.name(), ids);
                continue;
            }
            OptionalInt id = resolveChain(type.handleCategory(), token);
            if (id.isEmpty()) {
                diags.error(DiagCode.E_UNKNOWN_HANDLE,
                        "unknown " + type.handleCategory().label() + " '" + token
                                + "' for argument '" + p.name() + "' of '" + head + "'",
                        owner.source(),
                        "use a name valid on the target version, or remove the effect");
                return null; // drop this op; the ERROR above blocks the publish outright (§9, §10)
            }
            args = args.with(p.name(), id.getAsInt());
        }
        return args;
    }

    /**
     * Resolve a COMMA-separated handle set to its interned ids, in authored order. Each entry is an ordinary
     * token, so a {@code A|B} fallback chain still works per entry; an empty token list (the usual default) is an
     * empty result, not a fault. Returns {@code null} when an entry resolves on no version — the same
     * publish-blocking error an unknown single handle raises, so a typo cannot silently ship a shorter loadout.
     *
     * <p>A POTION_EFFECT entry may carry a {@code NAME*LEVEL} suffix; the level is stripped here and packed
     * into the id ({@link PotionLoadout}). The suffix is potion-loadout grammar ONLY — on any other category
     * {@code *} stays part of the token, so a stray one fails as the unknown handle it is.
     */
    private List<Integer> resolveList(HandleCategory category, String token, Param p,
                                      String head, LoweredAbility owner, Diagnostics diags) {
        boolean loadout = category == HandleCategory.POTION_EFFECT;
        List<Integer> ids = new ArrayList<>();
        for (String entry : token.split(",")) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int amplifier = 0;
            int star = loadout ? trimmed.lastIndexOf('*') : -1;
            if (star >= 0) {
                OptionalInt level = level(trimmed.substring(star + 1).trim(), p, head, owner, diags);
                if (level.isEmpty()) {
                    return null;
                }
                amplifier = level.getAsInt() - 1;
                trimmed = trimmed.substring(0, star).trim();
            }
            OptionalInt id = resolveChain(category, trimmed);
            if (id.isEmpty()) {
                diags.error(DiagCode.E_UNKNOWN_HANDLE,
                        "unknown " + category.label() + " '" + trimmed
                                + "' in argument '" + p.name() + "' of '" + head + "'",
                        owner.source(),
                        "use a name valid on the target version, or drop it from the list");
                return null;
            }
            ids.add(loadout ? PotionLoadout.pack(id.getAsInt(), amplifier) : id.getAsInt());
        }
        return List.copyOf(ids);
    }

    /** The {@code *LEVEL} suffix as a 1-based potion level, or empty (diagnostic recorded) if it names none. */
    private static OptionalInt level(String raw, Param p, String head, LoweredAbility owner, Diagnostics diags) {
        int level;
        try {
            level = Integer.parseInt(raw);
        } catch (NumberFormatException notANumber) {
            diags.error(DiagCode.E_TYPE,
                    "expected a potion level after '*' but got '" + raw
                            + "' in argument '" + p.name() + "' of '" + head + "'",
                    owner.source(), "write SPEED*3 for level 3, or drop the suffix for level 1");
            return OptionalInt.empty();
        }
        if (level < 1 || level > PotionLoadout.MAX_LEVEL) {
            diags.error(DiagCode.E_RANGE,
                    "potion level " + level + " is outside 1.." + PotionLoadout.MAX_LEVEL
                            + " in argument '" + p.name() + "' of '" + head + "'",
                    owner.source());
            return OptionalInt.empty();
        }
        return OptionalInt.of(level);
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
