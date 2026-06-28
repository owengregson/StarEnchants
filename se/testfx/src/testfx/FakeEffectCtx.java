package testfx;

import engine.effect.EffectCtx;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import schema.spec.Args;

/**
 * A real, fluent {@link EffectCtx} for effect-kind unit tests — the replacement for the per-test Mockito
 * stub. Two reasons it beats {@code mock(EffectCtx.class)}:
 *
 * <ol>
 *   <li>A param the test never sets <em>throws</em> when read (faithful to the production {@link Args}),
 *       instead of a Mockito mock's silent {@code 0}/{@code null} — so a kind reading the wrong param key
 *       fails loudly instead of passing vacuously.</li>
 *   <li>It carries no Mockito hardcoding of the param names, so {@link SpecDrivenCtx} can fill it straight
 *       from the kind's {@code EffectSpec} and the spec becomes the single source of those names.</li>
 * </ol>
 *
 * Scalars are stored in one bag (mirroring {@link Args}); target slots and locations are stored per name.
 */
public final class FakeEffectCtx implements EffectCtx {

    private final Map<String, Object> scalars = new LinkedHashMap<>();
    private final Map<String, List<LivingEntity>> targets = new LinkedHashMap<>();
    private final Map<String, List<Location>> locations = new LinkedHashMap<>();
    private Player actor;
    private LivingEntity victim;
    private Location location;
    private int level;
    private UUID activeGem;

    public static FakeEffectCtx create() {
        return new FakeEffectCtx();
    }

    // ── fluent setup ─────────────────────────────────────────────────────────────────────────────────
    public FakeEffectCtx with(String name, int value) {
        scalars.put(name, value);
        return this;
    }

    public FakeEffectCtx with(String name, long value) {
        scalars.put(name, value);
        return this;
    }

    public FakeEffectCtx with(String name, double value) {
        scalars.put(name, value);
        return this;
    }

    public FakeEffectCtx with(String name, boolean value) {
        scalars.put(name, value);
        return this;
    }

    public FakeEffectCtx with(String name, String value) {
        scalars.put(name, value);
        return this;
    }

    public FakeEffectCtx targets(String slot, LivingEntity... entities) {
        this.targets.put(slot, new ArrayList<>(List.of(entities)));
        return this;
    }

    public FakeEffectCtx locations(String slot, Location... locs) {
        this.locations.put(slot, new ArrayList<>(List.of(locs)));
        return this;
    }

    public FakeEffectCtx actor(Player actor) {
        this.actor = actor;
        return this;
    }

    public FakeEffectCtx victim(LivingEntity victim) {
        this.victim = victim;
        return this;
    }

    public FakeEffectCtx location(Location location) {
        this.location = location;
        return this;
    }

    public FakeEffectCtx level(int level) {
        this.level = level;
        return this;
    }

    public FakeEffectCtx activeGem(UUID activeGem) {
        this.activeGem = activeGem;
        return this;
    }

    // ── EffectCtx ────────────────────────────────────────────────────────────────────────────────────
    @Override
    public double dbl(String name) {
        return ((Number) require(name)).doubleValue();
    }

    @Override
    public int integer(String name) {
        return ((Number) require(name)).intValue();
    }

    @Override
    public long lng(String name) {
        return ((Number) require(name)).longValue();
    }

    @Override
    public boolean bool(String name) {
        return (Boolean) require(name);
    }

    @Override
    public String str(String name) {
        return String.valueOf(require(name));
    }

    @Override
    public Args args() {
        Args args = Args.empty();
        for (Map.Entry<String, Object> e : scalars.entrySet()) {
            args = args.with(e.getKey(), e.getValue());
        }
        return args;
    }

    @Override
    public Player actor() {
        return actor;
    }

    @Override
    public LivingEntity victim() {
        return victim;
    }

    @Override
    public Location location() {
        return location;
    }

    @Override
    public Iterable<LivingEntity> targets(String selectorName) {
        return targets.getOrDefault(selectorName, List.of());
    }

    @Override
    public Iterable<Location> targetLocations(String selectorName) {
        return locations.getOrDefault(selectorName, List.of());
    }

    @Override
    public int level() {
        return level;
    }

    @Override
    public UUID activeGem() {
        return activeGem;
    }

    private Object require(String name) {
        Object v = scalars.get(name);
        if (v == null) {
            throw new IllegalArgumentException(
                    "FakeEffectCtx has no scalar '" + name + "' (set: " + scalars.keySet() + ")");
        }
        return v;
    }
}
