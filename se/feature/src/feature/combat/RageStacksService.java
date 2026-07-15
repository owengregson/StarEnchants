package feature.combat;

import engine.stores.ComboStore;
import engine.stores.RageStackStore;
import feature.compat.Sounds;
import feature.compat.Titles;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.LongSupplier;
import org.bukkit.entity.Player;
import platform.lang.Messages;
import platform.sched.Scheduling;
import platform.text.Colors;

/**
 * Rage stacks (§3): the combat feedback the Rage enchant owns now that its content no longer plays its own sound.
 * A stack counts ONE hit dealt WITH a rage weapon in the current combo: only a rage-carrying hit advances the
 * count (capped at the rage level, so a level-N rage tops out at N), a hit with a non-rage weapon leaves it
 * untouched, and a new combo reseeds it. Stacks drive a rising {@link Titles#sendActionBar action bar} + a
 * {@code BLAZE_HURT} cue whose pitch climbs with the stack. Rage is a combo you keep by NOT being hit: the run
 * breaks — flashing a {@code BROKEN} action bar + a {@code BLAZE_DEATH} cue, stacks to zero — on a victim switch,
 * the window elapsing, OR the holder TAKING a hit from anyone ({@link #onHitTaken}, ADR-0058). The stacks live in
 * the shared {@link RageStackStore}, which also sources the {@code %ragestacks%} fact the rage DAMAGE_MOD reads
 * (so the audio ladder and the damage scale share one number).
 *
 * <p>The count is rage-specific by design — it does NOT reuse the shared {@code %combo%} streak, which counts
 * every melee swing regardless of the weapon. Reusing it let stacks jump to the full combo length the instant a
 * rage weapon was drawn mid-combo (hits landed with a plain weapon counted). The shared streak is still read, but
 * only as the combo-lifecycle signal (a value of {@code 1} means this hit opened a new combo → reseed).
 *
 * <p>Threading: the listener calls {@link #onHit}/{@link #onHitTaken} on the firing region thread (the parties are
 * co-located for a melee/projectile hit), so the holder-directed cue/title/action-bar are in-region — no hop. Only
 * the delayed expiry probe hops, via {@link Scheduling#onEntityLater} on the holder.
 */
public final class RageStacksService {

    /** The combo window (ticks) a rage stack survives without a follow-up hit — the same window {@link ComboStore} uses. */
    static final long WINDOW_TICKS = ComboStore.DEFAULT_WINDOW_TICKS;

    private static final String STACK_SOUND = "ENTITY_BLAZE_HURT";
    private static final String BREAK_SOUND = "ENTITY_BLAZE_DEATH";
    private static final float STACK_VOLUME = 1.0f;
    private static final float BREAK_VOLUME = 0.5f;
    private static final float BREAK_PITCH = 2.0f;

    private final Function<Player, Integer> rageLevelOf; // the attacker's active rage level from the worn/held resolution
    private final ComboStore combo;
    private final RageStackStore store;
    private final Messages messages;
    private final Sounds sounds;
    private final LongSupplier nowTicks;

    public RageStacksService(Function<Player, Integer> rageLevelOf, ComboStore combo, RageStackStore store,
                             Messages messages, Sounds sounds, LongSupplier nowTicks) {
        this.rageLevelOf = Objects.requireNonNull(rageLevelOf, "rageLevelOf");
        this.combo = Objects.requireNonNull(combo, "combo");
        this.store = Objects.requireNonNull(store, "store");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sounds = Objects.requireNonNull(sounds, "sounds");
        this.nowTicks = Objects.requireNonNull(nowTicks, "nowTicks");
    }

    /**
     * Register one direct melee hit by {@code attacker} — called for EVERY such hit, rage weapon or not, so a
     * non-rage hit that opens a new combo can clear a stale count before it leaks into the new run. A rage-carrying
     * hit advances the count ({@code +1}, capped at the level); a non-rage hit leaves it (mid-combo) or clears it
     * (a new combo). The shared combo streak (advanced by the combat dispatch at HIGH priority) is read only to
     * tell whether this hit opened a new combo. Called on the attacker's region thread.
     */
    public void onHit(Player attacker) {
        int level = rageLevelOf.apply(attacker);
        UUID id = attacker.getUniqueId();
        int prev = store.current(id);
        if (level <= 0 && prev <= 0) {
            return; // no rage weapon and no live stacks — the common melee hit has nothing to track
        }
        long now = nowTicks.getAsLong();
        boolean comboStart = combo.current(id, now) <= 1; // dispatch ran combo.hit() at HIGH; 1 (or 0) = a fresh combo
        boolean hitWithRage = level > 0;
        int next = nextStacks(comboStart, hitWithRage, prev, level);

        if (!hitWithRage) {
            // A non-rage hit plays no cue. It only ever clears stale stacks as it opens a new combo (so the
            // previous run's count cannot carry into this one); mid-combo it changes nothing (and must NOT
            // re-stamp the store, or it would keep an idle stack alive past its window).
            if (next != prev) {
                store.set(id, next, now);
            }
            return;
        }
        if (comboStart && prev > 1) {
            breakFx(attacker); // the previous run ends as this rage hit opens a new combo — flash BROKEN first
        }
        store.set(id, next, now);
        stackFx(attacker, next);
        armExpiryProbe(attacker, now);
    }

    /**
     * Register that {@code holder} TOOK a hit (from anyone — melee or projectile, player or mob). Rage is a combo
     * kept by NOT being hit, so any hit taken BREAKS the run: the stacks reset to zero (ADR-0058). A meaningful run
     * (more than one stack) flashes the BROKEN cue; a lone stack drops silently. A no-op unless the holder has a
     * live run. Called on the holder's region thread (they are the damaged entity for this event).
     */
    public void onHitTaken(Player holder) {
        int prev = store.current(holder.getUniqueId());
        if (prev <= 0) {
            return; // no live run — the common "took a hit" case has nothing to break
        }
        if (prev > 1) {
            breakFx(holder); // a real combo dropped → BROKEN cue + zero
        } else {
            store.set(holder.getUniqueId(), 0, nowTicks.getAsLong()); // a lone stack drops without the cue
        }
    }

    /** The rising stack cue + action bar (played every qualifying hit). Attacker's region thread. */
    private void stackFx(Player player, int stacks) {
        sounds.play(player, player.getLocation(), STACK_SOUND, STACK_VOLUME, pitch(stacks));
        Titles.sendActionBar(player, Colors.translate(messages.fragment("rage.stacks-actionbar", "STACKS", stacks)));
    }

    /** The combo-broken cue + action bar, then zero the stored stacks. The holder's region thread. */
    private void breakFx(Player player) {
        sounds.play(player, player.getLocation(), BREAK_SOUND, BREAK_VOLUME, BREAK_PITCH);
        Titles.sendActionBar(player, Colors.translate(messages.fragment("rage.stacks-broken-actionbar")));
        store.set(player.getUniqueId(), 0, nowTicks.getAsLong());
    }

    /**
     * Arm a one-shot probe {@code WINDOW+1} ticks out: if no later hit has re-stamped the store (its {@link
     * RageStackStore#lastTick} still equals {@code armedTick}) and a stack is still live, the combo window elapsed
     * silently, so break it. A later hit stamps a fresh tick, so its own probe supersedes this one (no dedupe needed).
     */
    private void armExpiryProbe(Player player, long armedTick) {
        UUID id = player.getUniqueId();
        Scheduling.onEntityLater(player, WINDOW_TICKS + 1, () -> {
            if (store.lastTick(id) == armedTick && store.current(id) > 0) {
                breakFx(player);
            }
        });
    }

    /** Stacks = {@code min(streak, level)} — this is what caps the ladder at the rage level. Pure. */
    static int clamp(int streak, int level) {
        return Math.min(streak, level);
    }

    /**
     * The rage stacks after one hit — the fix for the shared-combo-reuse bug. Only a rage-carrying hit advances
     * the count; a non-rage hit mid-combo leaves it as-is; either weapon opening a NEW combo reseeds it (to
     * {@code 1} if this opening hit carried rage, else {@code 0}). Rage-carrying advance is capped at the level.
     * Pure — the single source of the stack-count contract, unit-tested in isolation from the fx/threading.
     */
    static int nextStacks(boolean comboStart, boolean hitWithRage, int prevStacks, int level) {
        if (comboStart) {
            return hitWithRage ? 1 : 0;
        }
        if (!hitWithRage) {
            return prevStacks;
        }
        return clamp(prevStacks + 1, level);
    }

    /**
     * The stack cue pitch: an absolute descending ladder {@code 1.45 - 0.10 * stacks}, floored at {@code 0.85}
     * (so stack 6 = 0.85 and a level-2 rage tops out at pitch(2) = 1.25). Pure.
     */
    static float pitch(int stacks) {
        return Math.max(0.85f, 1.45f - 0.10f * stacks);
    }
}
