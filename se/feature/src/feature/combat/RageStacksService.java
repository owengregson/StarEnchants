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
 * On every qualifying melee hit the attacker's stacks become {@code min(combo streak, rage level)} — so a level-N
 * rage tops out at N — and drive a rising {@link Titles#sendActionBar action bar} + a {@code BLAZE_HURT} cue whose
 * pitch climbs with the stack. A combo that breaks (a victim switch, or the window elapsing) flashes a
 * {@code BROKEN} action bar + a {@code BLAZE_DEATH} cue. The stacks live in the shared {@link RageStackStore}, which also
 * sources the {@code %ragestacks%} fact the rage DAMAGE_MOD reads (so the audio ladder and the damage scale share
 * one number).
 *
 * <p>Threading: the listener calls {@link #onHit} on the firing region thread (the attacker's own region for a
 * melee hit), so the attacker-directed cue/title/action-bar are in-region — no hop. Only the delayed expiry probe
 * hops, via {@link Scheduling#onEntityLater} on the attacker.
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
     * Register one qualifying melee hit by {@code attacker}. No-op unless the attacker carries rage. The combo
     * streak (already advanced by the combat dispatch at HIGH priority) is clamped to the rage level; a streak
     * that has reset to 1 while stacks were built (a victim switch) breaks the run first, then the fresh stack fx
     * plays. Called on the attacker's region thread.
     */
    public void onHit(Player attacker) {
        int level = rageLevelOf.apply(attacker);
        if (level <= 0) {
            return;
        }
        UUID id = attacker.getUniqueId();
        long now = nowTicks.getAsLong();
        int streak = combo.current(id, now); // the combat dispatch ran combo.hit() at HIGH, so this is this hit's streak
        int stacks = clamp(streak, level);
        int prev = store.current(id);
        if (streak <= 1 && prev > 1) {
            breakFx(attacker); // the combo reset (a new victim) while a stack was live — flash BROKEN before the new run
        }
        store.set(id, stacks, now);
        stackFx(attacker, stacks);
        armExpiryProbe(attacker, now);
    }

    /** The rising stack cue + action bar (played every qualifying hit). Attacker's region thread. */
    private void stackFx(Player player, int stacks) {
        sounds.play(player, player.getLocation(), STACK_SOUND, STACK_VOLUME, pitch(stacks));
        Titles.sendActionBar(player, Colors.translate(messages.fragment("rage.stacks-actionbar", "STACKS", stacks)));
    }

    /** The combo-broken cue + action bar, then zero the stored stacks. Attacker's region thread. */
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
     * The stack cue pitch: an absolute descending ladder {@code 1.45 - 0.10 * stacks}, floored at {@code 0.85}
     * (so stack 6 = 0.85 and a level-2 rage tops out at pitch(2) = 1.25). Pure.
     */
    static float pitch(int stacks) {
        return Math.max(0.85f, 1.45f - 0.10f * stacks);
    }
}
