package feature.trigger;

import compile.load.ContentHolder;
import compile.model.Ability;
import compile.model.CompiledEffect;
import compile.model.Snapshot;
import engine.stores.CooldownStore;
import engine.stores.SuppressionStore;
import item.worn.WornState;
import item.worn.WornStateStore;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import org.bukkit.entity.Player;

/**
 * Maintains PASSIVE/HELD <em>potion</em> buffs as TRULY permanent-while-worn (bug fix, §B): a passive potion
 * is re-derived from live {@link WornState} on every refresh and (re)applied with a permanent duration, so it
 * never expires on its own (the migrated likenesses author a short duration the engine would otherwise let
 * lapse). It is the authority for these effects — it runs AFTER the {@link LifecycleDriver}'s equip diff, so a
 * value it computes always wins.
 *
 * <p><strong>Suppression-correct (DISABLE_ENCHANT).</strong> Each refresh excludes abilities currently under a
 * timed {@code DISABLE_*} (the same per-scope check the gate pipeline uses), so a disabled passive's effect is
 * dropped; when the window ends it is re-derived and restored. Because the desired set is recomputed from the
 * live worn state + live suppression every time — never replayed from a snapshot — a debuff or gear change
 * during the window can never leave a player with the wrong set.
 *
 * <p><strong>Owned-effect ledger.</strong> Only the potion TYPES this driver applied are ever removed (tracked
 * per player), so a vanilla potion or an enemy's same-or-other-type debuff the plugin did not apply is never
 * clobbered. Re-derivation also self-heals the buff after a death/milk/other clear (the periodic sweep + the
 * respawn refresh re-apply it).
 *
 * <p>Folia-correct: {@link #refresh} runs on the player's own region thread (driven by the equip listener, a
 * respawn, the periodic sweep, and the suppression hooks); the per-player owned map is a {@link ConcurrentHashMap}.
 */
public final class PassiveEffectDriver {

    /** Cooldown-scope kinds (mirror {@code ActivationPipeline}): a {@code DISABLE_*} keys the same packed id. */
    private static final int SCOPE_ENCHANT = 0;
    private static final int SCOPE_GROUP = 1;
    private static final int SCOPE_TYPE = 2;

    /** "Permanent" duration in ticks (~13.9h) — long enough to never lapse in a session; matches the authored
     *  permanent-buff convention. Re-applied each refresh, so it also returns within a sweep after a clear. */
    public static final int PERMANENT_TICKS = 1_000_000;

    private final TriggerDispatch dispatch;
    private final ContentHolder content;
    private final WornStateStore worn;
    private final SuppressionStore suppression;
    private final LongSupplier nowTicks;
    private final int held;    // -1 if HELD is absent from the vocabulary
    private final int passive; // -1 if PASSIVE is absent from the vocabulary

    /** player → (potionEffect handle id → amplifier) currently applied by THIS driver. */
    private final Map<UUID, Map<Integer, Integer>> owned = new ConcurrentHashMap<>();

    public PassiveEffectDriver(TriggerDispatch dispatch, ContentHolder content, WornStateStore worn,
                               SuppressionStore suppression, LongSupplier nowTicks, int held, int passive) {
        this.dispatch = Objects.requireNonNull(dispatch, "dispatch");
        this.content = Objects.requireNonNull(content, "content");
        this.worn = Objects.requireNonNull(worn, "worn");
        this.suppression = Objects.requireNonNull(suppression, "suppression");
        this.nowTicks = Objects.requireNonNull(nowTicks, "nowTicks");
        this.held = held;
        this.passive = passive;
    }

    /**
     * Reconcile {@code player}'s passive potion buffs with their live worn state + suppression. Must run on the
     * player's own thread. Applies/renews every desired effect at {@link #PERMANENT_TICKS} and removes only the
     * driver-owned effects that are no longer desired (suppressed / unworn).
     */
    public void refresh(Player player) {
        if (held < 0 && passive < 0) {
            return;
        }
        UUID id = player.getUniqueId();
        Snapshot snapshot = content.snapshot();
        WornState state = worn.get(id);
        Desired computed = computeDesired(state, snapshot, suppression, id, nowTicks.getAsLong(), held, passive);
        Map<Integer, Integer> desired = computed.apply();

        // Remove: anything we previously applied that is no longer desired, PLUS any potion type a worn-but-
        // SUPPRESSED ability would grant (so a passive re-applied by the lifecycle diff on a re-equip mid-window
        // is force-cleared) — minus whatever is still desired from another active source.
        Set<Integer> toRemove = new HashSet<>(owned.getOrDefault(id, Map.of()).keySet());
        toRemove.addAll(computed.suppressed());
        toRemove.removeAll(desired.keySet());

        dispatch.applyPassivePotions(player, desired, toRemove);

        if (desired.isEmpty()) {
            owned.remove(id);
        } else {
            owned.put(id, desired);
        }
    }

    /** Forget a player's owned set on quit (the entity is gone — no removal needed). */
    public void clear(UUID player) {
        owned.remove(player);
    }

    public void clearAll() {
        owned.clear();
    }

    /** The result of a re-derivation: the potions to maintain ({@code apply}) and the potion types that worn but
     *  SUPPRESSED abilities would otherwise grant ({@code suppressed}, force-removed unless still applied). */
    record Desired(Map<Integer, Integer> apply, Set<Integer> suppressed) {
    }

    /**
     * What a player's worn PASSIVE and HELD abilities currently imply for self-buff potions: {@code apply} is
     * (handle id → max amplifier) over the NON-suppressed abilities; {@code suppressed} is the set of handle ids
     * a worn-but-suppressed ability grants. Pure (no Bukkit) — unit-testable. A stale worn state (different
     * snapshot generation) yields nothing; the next equip/sweep re-derives against the live one.
     */
    static Desired computeDesired(WornState state, Snapshot snapshot, SuppressionStore suppression,
                                  UUID player, long now, int held, int passive) {
        Map<Integer, Integer> apply = new HashMap<>();
        Set<Integer> suppressed = new HashSet<>();
        if (state == null || state.gen() != snapshot.generation()) {
            return new Desired(apply, suppressed);
        }
        Ability[] abilities = snapshot.abilities();
        collect(apply, suppressed, state, held, abilities, suppression, player, now);
        collect(apply, suppressed, state, passive, abilities, suppression, player, now);
        return new Desired(apply, suppressed);
    }

    private static void collect(Map<Integer, Integer> apply, Set<Integer> suppressed, WornState state, int trigger,
                                Ability[] abilities, SuppressionStore suppression, UUID player, long now) {
        if (trigger < 0) {
            return;
        }
        for (int abilityId : state.byTrigger(trigger)) {
            if (abilityId < 0 || abilityId >= abilities.length) {
                continue;
            }
            Ability ability = abilities[abilityId];
            boolean disabled = isSuppressed(ability, suppression, player, now);
            for (CompiledEffect effect : ability.effects()) {
                if (!"POTION".equals(effect.head()) || !"SELF".equals(effect.target().head())) {
                    continue; // only self-buff potions are maintained; other effect kinds keep their own lifecycle
                }
                int handle = effect.args().integer("effect");
                if (disabled) {
                    suppressed.add(handle); // a DISABLE'd passive grants nothing — and is force-cleared
                    continue;
                }
                int amplifier = Math.max(0, effect.args().integer("level") - 1); // §C 1-based level → 0-based amplifier
                apply.merge(handle, amplifier, Math::max); // strongest wins when two sources grant the same type
            }
        }
    }

    /** Whether any of {@code ability}'s three scopes is under an active timed {@code DISABLE_*} (gate-5 mirror). */
    private static boolean isSuppressed(Ability ability, SuppressionStore suppression, UUID player, long now) {
        return scopeSuppressed(ability.cdScopeEnchant(), SCOPE_ENCHANT, suppression, player, now)
                || scopeSuppressed(ability.cdScopeGroup(), SCOPE_GROUP, suppression, player, now)
                || scopeSuppressed(ability.cdScopeType(), SCOPE_TYPE, suppression, player, now);
    }

    private static boolean scopeSuppressed(int scopeId, int scopeKind, SuppressionStore suppression,
                                           UUID player, long now) {
        return scopeId >= 0 && suppression.isSuppressed(player, CooldownStore.key(scopeKind, scopeId), now);
    }
}
