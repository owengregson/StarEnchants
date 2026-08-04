package engine.stores;

import compile.model.Ability;
import compile.model.CompiledEffect;
import compile.model.ScopeKinds;
import java.util.Iterator;
import java.util.LinkedHashMap;
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
public final class SuppressionStore implements RetainedStore {

    /** Notified whenever a player is freshly suppressed, so a maintained-buff driver can drop the affected
     *  effects immediately (instant DISABLE) and schedule their restore at the window's end. */
    @FunctionalInterface
    public interface SuppressListener {
        void onSuppress(UUID player, int durationTicks);
    }

    /** One timed suppression window: expiry tick + the DISABLE_* ability that armed it ({@code -1} unattributed). */
    /**
     * The consume-time feedback a TIMED window carries (SUPPRESS's {@code consumed-*} params), emitted at the
     * gate-5 block by the DISPATCH layer — the pipeline itself is Bukkit-free and holds no player handle.
     * {@code by} is the player who armed it, so "actor"/"victim" mean what they meant on the SUPPRESS line.
     */
    public record Feedback(UUID by, String actorMessage, String victimMessage, int soundId) {

        /** {@code soundId} sentinel for "no cue" — an absent HANDLE arg never interns to a real id. */
        public static final int NO_SOUND = -1;
    }

    private record Window(long expiry, int byDefId, Feedback feedback) {
    }

    /** A defender-keyed window: a {@link Window} plus the per-incoming-activation chance rolled at the consult. */
    private record Defender(long expiry, int chance, int byDefId, Feedback feedback) {
    }

    /** One armed one-shot (Neutralize, ADR-0049): remaining charges + the ability that armed it. Event-scoped, never timed. */
    private record Charge(int charges, int byDefId) {
    }

    /** Defensive cap on how many distinct one-shot scope keys a single player can hold armed (drop oldest beyond this). */
    private static final int MAX_ARMED = 16;

    private final Map<UUID, Map<Long, Window>> expiryByPlayer = new ConcurrentHashMap<>();
    /**
     * Parallel armed one-shot suppressions ({@code SUPPRESS} mode {@code next-hit}, ADR-0049 Neutralize): a
     * packed scope key &rarr; remaining charges, consumed by {@link #consumeEventScoped} at the end of a hit —
     * NEVER by time. The inner map is an insertion-ordered {@link LinkedHashMap} (synchronised) so a runaway
     * arming drops the OLDEST beyond {@value #MAX_ARMED}.
     */
    private final Map<UUID, Map<Long, Charge>> armedByPlayer = new ConcurrentHashMap<>();
    /**
     * Per-player suppression-immunity CHANCE in {@code [1,100]} (dragon's Dovahkiin, ADR-0034): each
     * {@link #suppress} rolls it, so {@code 100} is absolute immunity and a lower value ignores that fraction of
     * suppressions. Absent = not immune. A {@code SUPPRESS_IMMUNE} with no chance stores {@code 100}.
     */
    private final Map<UUID, Integer> immuneChance = new ConcurrentHashMap<>();
    /**
     * KIND-scoped windows (ADR-0053): dense effect kindId &rarr; {@link Window}, SEPARATE from the packed
     * cooldown-scope maps so gate 5's per-activation check can fast-path on map emptiness — when no KIND
     * window exists anywhere, the ability's effects are never walked.
     */
    private final Map<UUID, Map<Integer, Window>> kindExpiryByPlayer = new ConcurrentHashMap<>();
    /** KIND-scoped one-shots ({@code SUPPRESS scope: KIND, mode: next-hit}), burned by {@link #consumeEventScoped}. */
    private final Map<UUID, Map<Integer, Charge>> kindArmedByPlayer = new ConcurrentHashMap<>();
    /**
     * DEFENDER-KEYED windows (wave 2d): the incoming-direction complement of everything above. These live on
     * the WEARER and gate 5 consults them with the ability's VICTIM id, so they silence what other people aim
     * at their holder rather than what their holder does. Kept in their own maps for {@code kindExpiryByPlayer}'s
     * reason — gate 5 fast-paths on emptiness, so a server with nobody wearing one pays nothing.
     *
     * <p>The chance is rolled at the CONSULT, not the arm, which is the whole reason the activator-side
     * {@link #immuneChance} could not be reused: an ability's own chance gate rolls once when the window is
     * created, and "50% of incoming mastery procs" needs a roll per incoming proc.
     */
    private final Map<UUID, Map<Long, Defender>> defenderByPlayer = new ConcurrentHashMap<>();
    /** The KIND-scoped half of {@link #defenderByPlayer}, split for the same fast-path reason. */
    private final Map<UUID, Map<Integer, Defender>> defenderKindByPlayer = new ConcurrentHashMap<>();
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
            kindExpiryByPlayer.remove(player); // KIND windows are DISABLE windows too (ADR-0053)
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
        suppress(player, id, nowTicks, durationTicks, byDefId, null);
    }

    /** As above, carrying the window's consume-time {@link Feedback} ({@code null} = silent, the usual case). */
    public void suppress(UUID player, long id, long nowTicks, int durationTicks, int byDefId, Feedback feedback) {
        if (durationTicks <= 0 || vetoedByImmunity(player)) {
            return;
        }
        long expiry = nowTicks + durationTicks;
        // Later expiry wins WITH its own defId; an earlier/equal re-suppress keeps the live window (and its defId).
        expiryByPlayer.computeIfAbsent(player, k -> new ConcurrentHashMap<>())
                .merge(id, new Window(expiry, byDefId, feedback), (a, b) -> a.expiry() >= b.expiry() ? a : b);
        onSuppress.onSuppress(player, durationTicks); // instant drop + scheduled restore of maintained buffs
    }

    /**
     * Suppress every ability carrying effect kind {@code kindId} for {@code player} for {@code durationTicks}
     * ({@code SUPPRESS scope: KIND}, ADR-0053 — e.g. Chef silencing any {@code MODIFY_FOOD}). Same immunity
     * roll, extend-never-shorten merge, and maintained-buff notification as {@link #suppress}, but keyed by the
     * dense effect kindId in its own map so gate 5's fast-path stays an emptiness test.
     */
    public void suppressKind(UUID player, int kindId, long nowTicks, int durationTicks, int byDefId) {
        suppressKind(player, kindId, nowTicks, durationTicks, byDefId, null);
    }

    /** As above, carrying the window's consume-time {@link Feedback} ({@code null} = silent, the usual case). */
    public void suppressKind(UUID player, int kindId, long nowTicks, int durationTicks, int byDefId,
                             Feedback feedback) {
        if (kindId < 0 || durationTicks <= 0 || vetoedByImmunity(player)) {
            return;
        }
        long expiry = nowTicks + durationTicks;
        kindExpiryByPlayer.computeIfAbsent(player, k -> new ConcurrentHashMap<>())
                .merge(kindId, new Window(expiry, byDefId, feedback), (a, b) -> a.expiry() >= b.expiry() ? a : b);
        onSuppress.onSuppress(player, durationTicks); // a KIND-suppressed maintained POTION must drop too
    }

    /** Roll the per-player immunity: >=100 is absolute (Dovahkiin), a lower chance ignores that fraction of
     *  suppressions (crystals/chaos "4% chance to ignore Silence", ADR-0034). ThreadLocalRandom — no RNG is
     *  threaded to this store, and it runs on the firing region thread. */
    private boolean vetoedByImmunity(UUID player) {
        int immunity = immuneChance.getOrDefault(player, 0);
        return immunity >= 100 || (immunity > 0 && ThreadLocalRandom.current().nextInt(100) < immunity);
    }

    /**
     * Arm a one-shot suppression of packed scope key {@code id} for {@code player} with {@code charges} charges
     * (ADR-0049 Neutralize, {@code SUPPRESS} mode {@code next-hit}): each of the player's next {@code charges}
     * incoming hits resolves with that scope suppressed, then it clears. Immunity is NOT rolled (the one-shot is
     * an offensive neutralise, not a durable DISABLE window). Re-arming refreshes to the larger charge count; the
     * map is capped at {@value #MAX_ARMED} keys, dropping the oldest.
     */
    public void armOneShot(UUID player, long id, int charges, int byDefId) {
        if (charges <= 0) {
            return;
        }
        arm(armedByPlayer, player, id, charges, byDefId);
        onSuppress.onSuppress(player, 0); // instant drop of maintained buffs; the restore is event-scoped, not timed
    }

    /** As {@link #armOneShot} but KIND-scoped (ADR-0053): {@code kindId} is the dense effect kindId, kept in its
     *  own map so gate 5's KIND fast-path stays an emptiness test. Burned by the same {@link #consumeEventScoped}. */
    public void armOneShotKind(UUID player, int kindId, int charges, int byDefId) {
        if (kindId < 0 || charges <= 0) {
            return;
        }
        arm(kindArmedByPlayer, player, kindId, charges, byDefId);
        onSuppress.onSuppress(player, 0);
    }

    /** The shared arm: extend-to-larger-charges merge + the {@value #MAX_ARMED} oldest-out cap. */
    private static <K> void arm(Map<UUID, Map<K, Charge>> byPlayer, UUID player, K key, int charges, int byDefId) {
        Map<K, Charge> armed = byPlayer.computeIfAbsent(player, k -> new LinkedHashMap<>());
        synchronized (armed) {
            armed.merge(key, new Charge(charges, byDefId), (a, b) -> new Charge(Math.max(a.charges(), b.charges()), b.byDefId()));
            if (armed.size() > MAX_ARMED) {
                Iterator<K> it = armed.keySet().iterator();
                it.next(); // the eldest (LinkedHashMap insertion order)
                it.remove();
            }
        }
    }

    /**
     * Burn one charge off EVERY armed one-shot for {@code player}, removing exhausted entries — called once at the
     * end of a hit against them (event-scoped, ADR-0049). A no-op when nothing is armed.
     */
    public void consumeEventScoped(UUID player) {
        burn(armedByPlayer, player);
        burn(kindArmedByPlayer, player); // KIND one-shots are event-scoped too (ADR-0053)
    }

    private static <K> void burn(Map<UUID, Map<K, Charge>> byPlayer, UUID player) {
        Map<K, Charge> armed = byPlayer.get(player);
        if (armed == null) {
            return;
        }
        synchronized (armed) {
            Iterator<Map.Entry<K, Charge>> it = armed.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<K, Charge> e = it.next();
                Charge c = e.getValue();
                if (c.charges() <= 1) {
                    it.remove();
                } else {
                    e.setValue(new Charge(c.charges() - 1, c.byDefId()));
                }
            }
            if (armed.isEmpty()) {
                byPlayer.remove(player, armed);
            }
        }
    }

    /** The armed one-shot charge for {@code id}, or {@code null} — the read both the gate mirror and detail share. */
    private Charge armed(UUID player, long id) {
        return armed(armedByPlayer, player, id);
    }

    private static <K> Charge armed(Map<UUID, Map<K, Charge>> byPlayer, UUID player, K key) {
        Map<K, Charge> armed = byPlayer.get(player);
        if (armed == null) {
            return null;
        }
        synchronized (armed) {
            return armed.get(key);
        }
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

    /** Whether ANY of {@code ability}'s three cooldown scopes — or any of its compiled effect kind ids
     *  (ADR-0053 {@code scope: KIND}) — is under an active {@code DISABLE_*}: the ONE check gate 5 and the
     *  passive-potion driver share, so the gate-5 mirror cannot drift. */
    public boolean suppressesAny(Ability ability, UUID player, long nowTicks) {
        if (ability.suppressImmune()) {
            return false; // per-enchant immunity (suppress-immune: true) — Silence & derivatives no-op against it,
                          // so a permanent buff survives while the wearer's OTHER enchants are still silenced.
        }
        return scopeSuppressed(ability.cdScopeEnchant(), ScopeKinds.ENCHANT, player, nowTicks)
                || scopeSuppressed(ability.cdScopeGroup(), ScopeKinds.GROUP, player, nowTicks)
                || scopeSuppressed(ability.cdScopeType(), ScopeKinds.TYPE, player, nowTicks)
                || kindSuppressed(ability, player, nowTicks);
    }

    /**
     * Whether any of {@code ability}'s compiled effect kind ids is KIND-suppressed for {@code player}
     * (ADR-0053). Hot-path rule: the two map-emptiness tests short-circuit before the effects walk, so an
     * activation costs ~nothing while no KIND window exists anywhere. Unstamped effects (kindId −1, the
     * head-fallback test path) never match.
     */
    private boolean kindSuppressed(Ability ability, UUID player, long nowTicks) {
        if (kindExpiryByPlayer.isEmpty() && kindArmedByPlayer.isEmpty()) {
            return false; // fast path: no KIND suppression on the whole server
        }
        Map<Integer, Window> windows = kindExpiryByPlayer.get(player);
        boolean anyArmed = kindArmedByPlayer.containsKey(player);
        if (windows == null && !anyArmed) {
            return false;
        }
        for (CompiledEffect effect : ability.effects()) {
            int kid = effect.kindId();
            if (kid < 0) {
                continue;
            }
            if (windows != null && kindWindow(windows, kid, nowTicks) != null) {
                return true;
            }
            if (anyArmed && armed(kindArmedByPlayer, player, kid) != null) {
                return true;
            }
        }
        return false;
    }

    /** Whether effect kind {@code kindId} is KIND-suppressed for {@code player} (window or one-shot) — the
     *  single-kind read for tests and detail rendering. */
    public boolean isKindSuppressed(UUID player, int kindId, long nowTicks) {
        Map<Integer, Window> windows = kindExpiryByPlayer.get(player);
        return (windows != null && kindWindow(windows, kindId, nowTicks) != null)
                || armed(kindArmedByPlayer, player, kindId) != null;
    }

    /** The live KIND window for {@code kindId} in {@code windows}, or {@code null} (same lazy eviction as {@link #window}). */
    private static Window kindWindow(Map<Integer, Window> windows, int kindId, long nowTicks) {
        Window w = windows.get(kindId);
        if (w == null) {
            return null;
        }
        if (nowTicks >= w.expiry()) {
            windows.remove(kindId, w);
            return null;
        }
        return w;
    }

    private boolean scopeSuppressed(int scopeId, int scopeKind, UUID player, long nowTicks) {
        if (scopeId < 0) {
            return false;
        }
        long key = CooldownStore.key(scopeKind, scopeId);
        // A scope is suppressed by an active timed DISABLE_* window OR an armed one-shot (Neutralize) — gate 5 reads
        // both as a pure check; the one-shot is burned by consumeEventScoped after the hit, never here (ADR-0049).
        return isSuppressed(player, key, nowTicks) || armed(player, key) != null;
    }

    /**
     * Packed detail of the FIRST scope (enchant&rarr;group&rarr;type, mirroring {@link #suppressesAny}) that an
     * active DISABLE_* blocks for {@code ability}: {@code [byDefId:32][scopeKind:2][scopeId:28]} via the
     * {@code detail*()} unpackers, or {@code 0} when none is blocked (a sub-tick race after {@code suppressesAny},
     * or a defensive caller). "Found" is decided by the live window, never by the packed value, so a real
     * (defId 0, ENCHANT, id 0) detail — which also packs to {@code 0} — is reported, not skipped.
     */
    public long blockedDetail(Ability ability, UUID player, long nowTicks) {
        int d;
        if ((d = scopeByDefId(ability.cdScopeEnchant(), ScopeKinds.ENCHANT, player, nowTicks)) != NONE) {
            return detail(d, ScopeKinds.ENCHANT, ability.cdScopeEnchant());
        }
        if ((d = scopeByDefId(ability.cdScopeGroup(), ScopeKinds.GROUP, player, nowTicks)) != NONE) {
            return detail(d, ScopeKinds.GROUP, ability.cdScopeGroup());
        }
        if ((d = scopeByDefId(ability.cdScopeType(), ScopeKinds.TYPE, player, nowTicks)) != NONE) {
            return detail(d, ScopeKinds.TYPE, ability.cdScopeType());
        }
        // KIND arm last, mirroring suppressesAny's order: the first blocked effect kindId names the suppressor.
        Map<Integer, Window> windows = kindExpiryByPlayer.get(player);
        boolean anyArmed = kindArmedByPlayer.containsKey(player);
        if (windows != null || anyArmed) {
            for (CompiledEffect effect : ability.effects()) {
                int kid = effect.kindId();
                if (kid < 0) {
                    continue;
                }
                Window w = windows != null ? kindWindow(windows, kid, nowTicks) : null;
                if (w != null) {
                    return detail(w.byDefId(), ScopeKinds.KIND, kid);
                }
                Charge c = anyArmed ? armed(kindArmedByPlayer, player, kid) : null;
                if (c != null) {
                    return detail(c.byDefId(), ScopeKinds.KIND, kid);
                }
            }
        }
        return 0;
    }

    /**
     * The {@link Feedback} of the TIMED window blocking {@code ability}, or {@code null} — the dispatch layer's
     * read-back after a {@code SUPPRESSED} verdict, the shape {@code remainingCooldownTicks} already sets for
     * {@code ON_COOLDOWN}. Walks {@link #blockedDetail}'s exact order so the emitted line names the same
     * suppressor {@code /se why} does. ONE-SHOTS ARE EXCLUDED: a one-shot is burned blind after the hit, by
     * every armed key at once, so it cannot say which ability it actually spent itself on. Allocation-free
     * when nothing is armed, and on the ordinary silent window it returns the stored {@code null}.
     */
    public Feedback blockedFeedback(Ability ability, UUID player, long nowTicks) {
        Feedback f;
        if ((f = scopeFeedback(ability.cdScopeEnchant(), ScopeKinds.ENCHANT, player, nowTicks)) != null) {
            return f;
        }
        if ((f = scopeFeedback(ability.cdScopeGroup(), ScopeKinds.GROUP, player, nowTicks)) != null) {
            return f;
        }
        if ((f = scopeFeedback(ability.cdScopeType(), ScopeKinds.TYPE, player, nowTicks)) != null) {
            return f;
        }
        Map<Integer, Window> windows = kindExpiryByPlayer.get(player);
        if (windows == null) {
            return null;
        }
        for (CompiledEffect effect : ability.effects()) {
            int kid = effect.kindId();
            if (kid < 0) {
                continue;
            }
            Window w = kindWindow(windows, kid, nowTicks);
            if (w != null && w.feedback() != null) {
                return w.feedback();
            }
        }
        return null;
    }

    private Feedback scopeFeedback(int scopeId, int scopeKind, UUID player, long nowTicks) {
        if (scopeId < 0) {
            return null;
        }
        Window w = window(player, CooldownStore.key(scopeKind, scopeId), nowTicks);
        return w == null ? null : w.feedback();
    }

    /** Sentinel "no blocking window/one-shot" (a real byDefId can be {@code -1}, so {@code NONE} must differ). */
    private static final int NONE = Integer.MIN_VALUE;

    /** The suppressor defId for a blocked scope (timed window first, else armed one-shot), or {@link #NONE} if unblocked. */
    private int scopeByDefId(int scopeId, int scopeKind, UUID player, long nowTicks) {
        if (scopeId < 0) {
            return NONE;
        }
        long key = CooldownStore.key(scopeKind, scopeId);
        Window w = window(player, key, nowTicks);
        if (w != null) {
            return w.byDefId();
        }
        Charge c = armed(player, key);
        return c != null ? c.byDefId() : NONE;
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

    /** Forget every suppression (timed, one-shot, KIND, and immunity) for one player (a full clear — NOT the quit sweep). */
    public void clear(UUID player) {
        expiryByPlayer.remove(player);
        armedByPlayer.remove(player);
        kindExpiryByPlayer.remove(player);
        kindArmedByPlayer.remove(player);
        immuneChance.remove(player);
        defenderByPlayer.remove(player);
        defenderKindByPlayer.remove(player);
    }

    /**
     * Remove only {@code player}'s suppression-immunity CHANCE, leaving timed suppression WINDOWS intact — the
     * quit sweep half of the relog fix. The immunity is self-state re-derived from worn gear by the passive
     * lifecycle within ~2s of rejoin (dragon's {@code SUPPRESS_IMMUNE}), so dropping it on quit is safe, while a
     * DISABLE_* window an opponent landed must survive the relog.
     */
    public void clearImmunity(UUID player) {
        immuneChance.remove(player);
    }

    /** Drop {@code player}'s elapsed suppression windows at {@code nowTicks}, keeping live ones; drop an emptied map. */
    @Override
    public void evictElapsed(UUID player, long nowTicks) {
        // Windows only (never immunity, which the quit sweep clears separately). Atomic per key, like CooldownStore.
        expiryByPlayer.computeIfPresent(player, (id, ids) -> {
            ids.values().removeIf(w -> nowTicks >= w.expiry());
            return ids.isEmpty() ? null : ids;
        });
        kindExpiryByPlayer.computeIfPresent(player, (id, ids) -> {
            ids.values().removeIf(w -> nowTicks >= w.expiry());
            return ids.isEmpty() ? null : ids;
        });
        defenderByPlayer.computeIfPresent(player, (id, ids) -> {
            ids.values().removeIf(w -> nowTicks >= w.expiry());
            return ids.isEmpty() ? null : ids;
        });
        defenderKindByPlayer.computeIfPresent(player, (id, ids) -> {
            ids.values().removeIf(w -> nowTicks >= w.expiry());
            return ids.isEmpty() ? null : ids;
        });
    }

    /** Drop every player's elapsed suppression windows at {@code nowTicks} (the periodic offline-state sweep). */
    @Override
    public void evictElapsed(long nowTicks) {
        for (UUID player : expiryByPlayer.keySet()) {
            evictElapsed(player, nowTicks);
        }
        for (UUID player : kindExpiryByPlayer.keySet()) {
            evictElapsed(player, nowTicks);
        }
        for (UUID player : defenderByPlayer.keySet()) {
            evictElapsed(player, nowTicks);
        }
        for (UUID player : defenderKindByPlayer.keySet()) {
            evictElapsed(player, nowTicks);
        }
    }

    /** Forget every suppression (timed, one-shot, KIND, and all immunity) for every player (call on disable). */
    public void clearAll() {
        expiryByPlayer.clear();
        armedByPlayer.clear();
        kindExpiryByPlayer.clear();
        kindArmedByPlayer.clear();
        immuneChance.clear();
        defenderByPlayer.clear();
        defenderKindByPlayer.clear();
    }

    // ── Defender-keyed suppression: the incoming direction ─────────────────────────────

    /**
     * Arm (or extend) a defender-keyed window on {@code defender} for the packed cooldown-scope {@code id}.
     * {@code chance} is the percentage rolled at each CONSULT (clamped to 100; {@code >= 100} is absolute).
     * Maintained-while-worn is the intended shape, so a re-arm only ever EXTENDS — the same merge the
     * activator side uses, which lets a PASSIVE re-arm on every lifecycle tick without churning the window.
     *
     * <p>Deliberately does NOT fire {@link SuppressListener}: that hook exists so a maintained buff on the
     * SUPPRESSED player drops instantly, and a defender window suppresses none of its holder's own abilities.
     */
    public void defend(UUID defender, long id, long nowTicks, int durationTicks, int chance, int byDefId,
                       Feedback feedback) {
        if (defender == null || durationTicks <= 0 || chance <= 0) {
            return;
        }
        Defender fresh = new Defender(nowTicks + durationTicks, Math.min(chance, 100), byDefId, feedback);
        defenderByPlayer.computeIfAbsent(defender, k -> new ConcurrentHashMap<>())
                .merge(id, fresh, SuppressionStore::laterDefender);
    }

    /** {@link #defend} for a dense effect kindId ({@code scope: KIND}). */
    public void defendKind(UUID defender, int kindId, long nowTicks, int durationTicks, int chance, int byDefId,
                           Feedback feedback) {
        if (defender == null || durationTicks <= 0 || chance <= 0 || kindId < 0) {
            return;
        }
        Defender fresh = new Defender(nowTicks + durationTicks, Math.min(chance, 100), byDefId, feedback);
        defenderKindByPlayer.computeIfAbsent(defender, k -> new ConcurrentHashMap<>())
                .merge(kindId, fresh, SuppressionStore::laterDefender);
    }

    private static Defender laterDefender(Defender a, Defender b) {
        return b.expiry() > a.expiry() ? b : a;
    }

    /** What a defender-keyed block reports back: the naming scope, the arming ability, and the cue to emit. */
    public record Matched(int scopeKind, int scopeId, int byDefId, Feedback feedback) {
    }

    /**
     * The defender-keyed window on {@code defender} that silences {@code ability}, or {@code null}. Each
     * candidate window rolls its OWN chance through {@code roll} (a {@code [0,100)} draw) — per incoming
     * activation, which is exactly what the activator-side immunity cannot express, since an ability's chance
     * gate rolls once when the window is armed.
     *
     * <p>Walks {@link #suppressesAny}'s scope order (enchant, group, type, then effect kinds) so both
     * directions agree on which key names a block. Returning the matched window rather than a bare boolean is
     * what lets gate 5 attribute and cue the block without a second lookup that could race the eviction.
     */
    public Matched defenderBlocks(Ability ability, UUID defender, long nowTicks,
                                  java.util.function.DoubleSupplier roll) {
        if (defender == null || ability.suppressImmune()
                || (defenderByPlayer.isEmpty() && defenderKindByPlayer.isEmpty())) {
            return null; // fast path: no defender window anywhere on the server
        }
        Matched hit = defenderScope(ability.cdScopeEnchant(), ScopeKinds.ENCHANT, defender, nowTicks, roll);
        if (hit == null) {
            hit = defenderScope(ability.cdScopeGroup(), ScopeKinds.GROUP, defender, nowTicks, roll);
        }
        if (hit == null) {
            hit = defenderScope(ability.cdScopeType(), ScopeKinds.TYPE, defender, nowTicks, roll);
        }
        return hit != null ? hit : defenderKind(ability, defender, nowTicks, roll);
    }

    private Matched defenderScope(int scopeId, int scopeKind, UUID defender, long nowTicks,
                                  java.util.function.DoubleSupplier roll) {
        if (scopeId < 0) {
            return null;
        }
        Map<Long, Defender> windows = defenderByPlayer.get(defender);
        if (windows == null) {
            return null;
        }
        long key = CooldownStore.key(scopeKind, scopeId);
        Defender w = windows.get(key);
        if (w == null) {
            return null;
        }
        if (nowTicks >= w.expiry()) {
            windows.remove(key, w);
            return null;
        }
        return rolls(w, roll) ? new Matched(scopeKind, scopeId, w.byDefId(), w.feedback()) : null;
    }

    private Matched defenderKind(Ability ability, UUID defender, long nowTicks,
                                 java.util.function.DoubleSupplier roll) {
        Map<Integer, Defender> windows = defenderKindByPlayer.get(defender);
        if (windows == null) {
            return null;
        }
        for (CompiledEffect effect : ability.effects()) {
            int kindId = effect.kindId();
            if (kindId < 0) {
                continue;
            }
            Defender w = windows.get(kindId);
            if (w == null) {
                continue;
            }
            if (nowTicks >= w.expiry()) {
                windows.remove(kindId, w);
                continue;
            }
            if (rolls(w, roll)) {
                return new Matched(ScopeKinds.KIND, kindId, w.byDefId(), w.feedback());
            }
        }
        return null;
    }

    /**
     * The cue carried by the first live defender-keyed window matching {@code ability}, or {@code null} — the
     * read-back after a defender block, in {@link #defenderBlocks}'s own scope order. It does NOT roll: the
     * roll was spent deciding the block, and a second draw could name a different window or none.
     */
    public Feedback defenderFeedback(Ability ability, UUID defender, long nowTicks) {
        Matched hit = defenderBlocks(ability, defender, nowTicks, ALWAYS_BLOCKS);
        return hit == null ? null : hit.feedback();
    }

    /** A draw that never fails, so {@link #defenderFeedback} sees exactly the windows that are live. */
    private static final java.util.function.DoubleSupplier ALWAYS_BLOCKS = () -> -1.0;

    /** A window at 100 blocks without drawing at all, so an absolute mask never touches the roll supplier. */
    private static boolean rolls(Defender w, java.util.function.DoubleSupplier roll) {
        return w.chance() >= 100 || roll.getAsDouble() < w.chance();
    }
}
