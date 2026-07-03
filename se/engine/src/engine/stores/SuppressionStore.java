package engine.stores;

import compile.model.Ability;
import compile.model.ScopeKinds;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import schema.spec.Ranges;

/**
 * Per-player timed suppression: an interned id &rarr; {@link Window} (docs/architecture.md §5.4). Home for
 * the {@code DISABLE_*}-with-duration effects (an enchant/group/type id silenced for a span of ticks). The
 * per-activation transient suppression set is a SEPARATE arbiter, not this; this store holds only
 * suppressions that outlast the activation that created them. Each window also carries the DISABLE_* ability
 * that armed it, so {@code /se why} can name the suppressor (ADR-0045).
 */
public final class SuppressionStore implements PlayerScoped {

    /** Notified whenever a player is freshly suppressed, so a maintained-buff driver can drop the affected
     *  effects immediately (instant DISABLE) and schedule their restore at the window's end. */
    @FunctionalInterface
    public interface SuppressListener {
        void onSuppress(UUID player, int durationTicks);
    }

    /** One timed suppression window: expiry tick + the DISABLE_* ability that armed it ({@code -1} unattributed). */
    private record Window(long expiry, int byDefId) {
    }

    private final Map<UUID, Map<Long, Window>> expiryByPlayer = new ConcurrentHashMap<>();
    /**
     * Per-player suppression-immunity CHANCE in {@code [1,100]} (dragon's Dovahkiin, ADR-0034): each
     * {@link #suppress} rolls it, so {@code 100} is absolute immunity and a lower value ignores that fraction of
     * suppressions. Absent = not immune. A {@code SUPPRESS_IMMUNE} with no chance stores {@code 100}.
     */
    private final Map<UUID, Integer> immuneChance = new ConcurrentHashMap<>();
    private volatile SuppressListener onSuppress = (player, durationTicks) -> { };

    /**
     * Set {@code player}'s suppression-immunity CHANCE (percent), or lift it with {@code chance <= 0}
     * ({@code SUPPRESS_IMMUNE}). A full ({@code >= 100}) immunity also CLEARS any suppression already on the
     * player, so equipping it frees them at once; a partial chance only gates FUTURE suppressions.
     */
    public void setImmune(UUID player, int chance) {
        if (chance <= 0) {
            immuneChance.remove(player);
            return;
        }
        int clamped = Ranges.clampPercent(chance);
        immuneChance.put(player, clamped);
        if (clamped >= 100) {
            expiryByPlayer.remove(player); // absolute immunity drops any DISABLE that landed before it armed
        }
    }

    /** Whether {@code player} is currently ABSOLUTELY immune to suppression (a partial chance is not). */
    public boolean isImmune(UUID player) {
        return immuneChance.getOrDefault(player, 0) >= 100;
    }

    /** Install the listener invoked after each {@link #suppress} (composition root); {@code null} clears it. */
    public void onSuppress(SuppressListener listener) {
        this.onSuppress = listener == null ? (player, durationTicks) -> { } : listener;
    }

    /**
     * Suppress packed scope key {@code id} for {@code durationTicks}, expiring at {@code nowTicks +
     * durationTicks}. The key is {@link CooldownStore#key(int, int)}-packed and shares the gate's
     * cooldown-scope namespace, so a {@code SUPPRESS} keys the same id the suppressed abilities lower their
     * scope to. Non-positive duration is a no-op; re-suppressing only EXTENDS (later expiry wins).
     */
    public void suppress(UUID player, long id, long nowTicks, int durationTicks) {
        suppress(player, id, nowTicks, durationTicks, -1);
    }

    /**
     * As {@link #suppress(UUID, long, long, int)} but attributed to {@code byDefId} (the DISABLE_* ability that
     * armed it, {@code -1} = unattributed), so {@code /se why} can name the suppressor (ADR-0045).
     */
    public void suppress(UUID player, long id, long nowTicks, int durationTicks, int byDefId) {
        if (durationTicks <= 0) {
            return;
        }
        int immunity = immuneChance.getOrDefault(player, 0);
        // Roll the per-player immunity: >=100 is absolute (Dovahkiin), a lower chance ignores that fraction of
        // suppressions (crystals/chaos "4% chance to ignore Silence", ADR-0034). ThreadLocalRandom — no RNG is
        // threaded to this store, and it runs on the firing region thread.
        if (immunity >= 100 || (immunity > 0 && ThreadLocalRandom.current().nextInt(100) < immunity)) {
            return;
        }
        long expiry = nowTicks + durationTicks;
        // Later expiry wins WITH its own defId; an earlier/equal re-suppress keeps the live window (and its defId).
        expiryByPlayer.computeIfAbsent(player, k -> new ConcurrentHashMap<>())
                .merge(id, new Window(expiry, byDefId), (a, b) -> a.expiry() >= b.expiry() ? a : b);
        onSuppress.onSuppress(player, durationTicks); // instant drop + scheduled restore of maintained buffs
    }

    /**
     * @return {@code true} if {@code id} has an active suppression for {@code player} at {@code nowTicks}.
     *     An elapsed one is evicted lazily; the window is half-open {@code [start, expiry)}.
     */
    public boolean isSuppressed(UUID player, long id, long nowTicks) {
        return window(player, id, nowTicks) != null;
    }

    /** The live window for {@code id}, or {@code null} (evicting an elapsed one). The ONE lookup both the gate
     *  check and {@link #blockedDetail} share, so eviction semantics cannot drift between them. */
    private Window window(UUID player, long id, long nowTicks) {
        Map<Long, Window> ids = expiryByPlayer.get(player);
        if (ids == null) {
            return null;
        }
        Window w = ids.get(id);
        if (w == null) {
            return null;
        }
        if (nowTicks >= w.expiry()) {
            ids.remove(id, w); // lazy eviction of an elapsed suppression (only if unchanged, per remove(k, v))
            return null;
        }
        return w;
    }

    /** Whether ANY of {@code ability}'s three cooldown scopes is under an active timed {@code DISABLE_*} — the
     *  one three-scope check gate 5 and the passive-potion driver share, so the gate-5 mirror cannot drift. */
    public boolean suppressesAny(Ability ability, UUID player, long nowTicks) {
        return scopeSuppressed(ability.cdScopeEnchant(), ScopeKinds.ENCHANT, player, nowTicks)
                || scopeSuppressed(ability.cdScopeGroup(), ScopeKinds.GROUP, player, nowTicks)
                || scopeSuppressed(ability.cdScopeType(), ScopeKinds.TYPE, player, nowTicks);
    }

    private boolean scopeSuppressed(int scopeId, int scopeKind, UUID player, long nowTicks) {
        return scopeId >= 0 && isSuppressed(player, CooldownStore.key(scopeKind, scopeId), nowTicks);
    }

    /**
     * Packed detail of the FIRST scope (enchant&rarr;group&rarr;type, mirroring {@link #suppressesAny}) that an
     * active DISABLE_* blocks for {@code ability}: {@code [byDefId:32][scopeKind:2][scopeId:28]} via the
     * {@code detail*()} unpackers, or {@code 0} when none is blocked (a sub-tick race after {@code suppressesAny},
     * or a defensive caller). "Found" is decided by the live window, never by the packed value, so a real
     * (defId 0, ENCHANT, id 0) detail — which also packs to {@code 0} — is reported, not skipped.
     */
    public long blockedDetail(Ability ability, UUID player, long nowTicks) {
        Window w;
        if ((w = scopeWindow(ability.cdScopeEnchant(), ScopeKinds.ENCHANT, player, nowTicks)) != null) {
            return detail(w.byDefId(), ScopeKinds.ENCHANT, ability.cdScopeEnchant());
        }
        if ((w = scopeWindow(ability.cdScopeGroup(), ScopeKinds.GROUP, player, nowTicks)) != null) {
            return detail(w.byDefId(), ScopeKinds.GROUP, ability.cdScopeGroup());
        }
        if ((w = scopeWindow(ability.cdScopeType(), ScopeKinds.TYPE, player, nowTicks)) != null) {
            return detail(w.byDefId(), ScopeKinds.TYPE, ability.cdScopeType());
        }
        return 0;
    }

    private Window scopeWindow(int scopeId, int scopeKind, UUID player, long nowTicks) {
        return scopeId < 0 ? null : window(player, CooldownStore.key(scopeKind, scopeId), nowTicks);
    }

    private static long detail(int byDefId, int scopeKind, int scopeId) {
        return ((long) byDefId << 32) | ((long) (scopeKind & 0x3) << 28) | (scopeId & 0x0FFF_FFFFL);
    }

    public static int detailScopeKind(long d) {
        return (int) ((d >>> 28) & 0x3L);
    }

    public static int detailScopeId(long d) {
        return (int) (d & 0x0FFF_FFFFL);
    }

    /** The suppressor's defId, or {@code -1} when the window was armed unattributed (a 4-arg suppress). */
    public static int detailByDefId(long d) {
        return (int) (d >> 32);
    }

    /** Forget every suppression (and any immunity) for one player (call on quit). */
    public void clear(UUID player) {
        expiryByPlayer.remove(player);
        immuneChance.remove(player);
    }

    /** Forget every suppression (and all immunity) for every player (call on disable). */
    public void clearAll() {
        expiryByPlayer.clear();
        immuneChance.clear();
    }
}
