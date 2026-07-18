package feature.reforge;

import compile.model.CompiledEffect;
import compile.resolve.PlatformResolvers;
import engine.stores.PlayerScoped;
import feature.trigger.TriggerDispatch;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import platform.caps.Regions;
import platform.lang.Messages;
import platform.sched.Scheduling;
import platform.sched.TaskHandle;
import platform.text.Colors;
import schema.spec.Args;

/**
 * The Castling channel machine (ADR-0071): per caster, at most ONE live channel — a repeating task on the
 * CASTER's entity scheduler that re-validates the crosshair lock every {@code check-period} ticks, plays the
 * audible rising countdown (cue layers + a message both sides), and at channel end swaps the two parties'
 * positions through fresh per-call {@link TriggerDispatch} sinks, velocities zeroed, each keeping their OWN
 * facing (you inherit the spot, not the view). Abort = LOS broken / range+slack exceeded / world change /
 * either party invalid or dead / caster quit (scope sweep) / disable (clearAll). The armed cooldown is NEVER
 * refunded (the countdown IS the enemy's counterplay; a refund would price it at zero) — the service holds no
 * {@code CooldownStore} reference, so a refund is structurally impossible.
 */
public final class CastlingService {

    private static final class Channel {
        final UUID casterId;
        final Player caster;
        final LivingEntity victim; // acquired at start; the handle only, re-read under a Regions guard
        final double rangeSq;      // (range + range-slack)^2
        final int channelTicks;
        final int checkPeriod;
        int elapsed;
        int lastCueSecond = Integer.MIN_VALUE;
        TaskHandle task;

        Channel(UUID casterId, Player caster, LivingEntity victim, double rangeSq, int channelTicks, int checkPeriod) {
            this.casterId = casterId;
            this.caster = caster;
            this.victim = victim;
            this.rangeSq = rangeSq;
            this.channelTicks = channelTicks;
            this.checkPeriod = checkPeriod;
        }
    }

    private static final Map<UUID, Channel> LIVE = new ConcurrentHashMap<>();

    private final TriggerDispatch dispatch;
    private final BiFunction<Player, Integer, Entity> targetEntity; // era raycast ref (§B2.5 composition-only seam)
    private final Messages messages;
    private final Cue[] tickCue;  // rising-pitch countdown plings
    private final Cue[] swapCue;  // the swap crack, played at BOTH endpoints
    private final Cue[] abortCue; // the fizzle

    public CastlingService(TriggerDispatch dispatch, BiFunction<Player, Integer, Entity> targetEntity,
                           Messages messages, PlatformResolvers resolvers) {
        this.dispatch = Objects.requireNonNull(dispatch, "dispatch");
        this.targetEntity = Objects.requireNonNull(targetEntity, "targetEntity");
        this.messages = Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(resolvers, "resolvers");
        this.tickCue = new Cue[]{
                cue(resolvers, "BLOCK_NOTE_BLOCK_PLING", 1.0f, 1.0f),
                cue(resolvers, "NOTE_PLING", 1.0f, 1.0f)};
        this.swapCue = new Cue[]{
                cue(resolvers, "ENTITY_ENDERMAN_TELEPORT", 1.0f, 0.9f),
                cue(resolvers, "BLOCK_ANVIL_LAND", 0.5f, 1.2f),
                cue(resolvers, "ANVIL_LAND", 0.5f, 1.2f)};
        this.abortCue = new Cue[]{
                cue(resolvers, "BLOCK_FIRE_EXTINGUISH", 1.0f, 0.8f),
                cue(resolvers, "FIZZ", 1.0f, 0.8f)};
    }

    /** Reforge runner hook: def activated with a {@code SWAP_POSITION} effect — acquire + start (or fail-feedback). */
    public void start(Player actor, CompiledEffect fx) {
        Args a = fx.args();
        double range = a.dbl("range");
        int channel = a.integer("channel");
        int checkPeriod = a.integer("check-period");
        double rangeSlack = a.dbl("range-slack");

        UUID casterId = actor.getUniqueId();
        Channel existing = LIVE.get(casterId);
        if (existing != null) {
            abort(existing, false); // re-activating mid-channel restarts (reachable only via a /reload edge)
        }

        Entity hit = targetEntity.apply(actor, (int) Math.ceil(range));
        LivingEntity victim = (hit instanceof LivingEntity le && !le.getUniqueId().equals(casterId)) ? le : null;
        if (victim == null) {
            messages.sendText(actor, Colors.translate(
                    messages.fragment("reforge.castling.no-target", "RANGE", (int) range)));
            play(actor.getLocation(), abortCue);
            return; // cooldown spent (the no-refund family economy)
        }

        double reach = range + rangeSlack;
        Channel ch = new Channel(casterId, actor, victim, reach * reach, channel, checkPeriod);
        LIVE.put(casterId, ch);
        int seconds = (int) Math.ceil(channel / 20.0);
        ch.lastCueSecond = seconds;
        announceSecond(ch, seconds);
        if (victim instanceof Player warned) {
            messages.sendText(warned, Colors.translate(messages.fragment("reforge.castling.warned")));
        }
        ch.task = Scheduling.repeatingEntity(actor, checkPeriod, checkPeriod, () -> tick(casterId));
    }

    /** Per {@code check-period} on the caster's scheduler: re-validate, cue the countdown, swap at the end. */
    private void tick(UUID casterId) {
        Channel ch = LIVE.get(casterId);
        if (ch == null) {
            return;
        }
        ch.elapsed += ch.checkPeriod;
        if (!ch.caster.isValid() || ch.caster.isDead()) {
            abort(ch, true);
            return;
        }
        boolean held = Regions.read("CastlingService.victim", () ->
                        ch.victim.isValid() && !ch.victim.isDead()
                                && ch.victim.getWorld() == ch.caster.getWorld()
                                && distanceSq(ch.caster.getLocation(), ch.victim.getLocation()) <= ch.rangeSq
                                && ch.caster.hasLineOfSight(ch.victim),
                false);
        if (!held) {
            abort(ch, true);
            return;
        }
        int secondsLeft = (int) Math.ceil((ch.channelTicks - ch.elapsed) / 20.0);
        if (secondsLeft != ch.lastCueSecond && secondsLeft > 0) {
            ch.lastCueSecond = secondsLeft;
            announceSecond(ch, secondsLeft); // once per second-change, both sides (never announces the 0 = swap moment)
        }
        if (ch.elapsed >= ch.channelTicks) {
            swap(ch);
            drop(casterId);
        }
    }

    /** The two-teleport exchange: victim hops to the caster's spot on its own thread, then the caster nests to the victim's. */
    private void swap(Channel ch) {
        Location casterLoc = ch.caster.getLocation().clone(); // caster thread — owned here
        Scheduling.onEntity(ch.victim, () -> {
            if (!ch.victim.isValid()) {
                return;
            }
            Location victimLoc = ch.victim.getLocation().clone(); // victim thread — owned here
            Location victimDest = keepFacing(casterLoc, victimLoc.getYaw(), victimLoc.getPitch());
            dispatch.teleportEntity(ch.victim, victimDest); // fresh sink: era teleport leaf, mob or player
            ch.victim.setVelocity(new Vector(0, 0, 0));
            play(victimDest, swapCue);
            if (ch.victim instanceof Player victimPlayer) {
                messages.sendText(victimPlayer, Colors.translate(
                        messages.fragment("reforge.castling.swapped-victim", "PLAYER", ch.caster.getName())));
            }
            Scheduling.onEntity(ch.caster, () -> {
                if (!ch.caster.isValid()) {
                    return;
                }
                Location casterDest = keepFacing(victimLoc, casterLoc.getYaw(), casterLoc.getPitch());
                dispatch.teleportEntity(ch.caster, casterDest);
                ch.caster.setVelocity(new Vector(0, 0, 0));
                play(casterDest, swapCue);
                messages.sendText(ch.caster, Colors.translate(
                        messages.fragment("reforge.castling.swapped", "TARGET", victimName(ch.victim))));
            });
        });
    }

    private void abort(Channel ch, boolean cue) {
        if (cue) {
            play(ch.caster.getLocation(), abortCue);
            messages.sendText(ch.caster, Colors.translate(messages.fragment("reforge.castling.aborted")));
        }
        drop(ch.casterId);
    }

    /** Cancel + evict, idempotent. */
    private static void drop(UUID casterId) {
        Channel ch = LIVE.remove(casterId);
        if (ch != null && ch.task != null) {
            ch.task.cancel();
        }
    }

    /** Caster quit → abort without a cue (the quitter is gone) + drop. */
    public static PlayerScoped scope() {
        return CastlingService::drop;
    }

    /** Disable stop ("castling channels"): cancel every channel task and clear the registry. */
    public static void clearAll() {
        for (UUID casterId : List.copyOf(LIVE.keySet())) {
            drop(casterId);
        }
    }

    /** Test seam: whether {@code casterId} holds a live channel. */
    static boolean channelActive(UUID casterId) {
        return LIVE.containsKey(casterId);
    }

    /** Test seam: the number of live channels. */
    static int liveCount() {
        return LIVE.size();
    }

    /** The audible + written second-tick, both parties (the owner-required "audible countdown"). */
    private void announceSecond(Channel ch, int secondsLeft) {
        float pitch = 1.0f + 0.3f * Math.max(0, 2 - secondsLeft); // audibly rising as the swap nears
        for (Cue base : tickCue) {
            play2(ch, base.sound(), base.volume(), pitch);
        }
        messages.sendText(ch.caster, Colors.translate(
                messages.fragment("reforge.castling.countdown", "SECONDS", secondsLeft)));
    }

    private void play2(Channel ch, int sound, float volume, float pitch) {
        dispatch.sound(ch.caster.getLocation(), sound, volume, pitch);
        Location victimLoc = Regions.read("CastlingService.cue", ch.victim::getLocation, null);
        if (victimLoc != null) {
            dispatch.sound(victimLoc, sound, volume, pitch);
        }
    }

    private void play(Location at, Cue[] cues) {
        for (Cue cue : cues) {
            dispatch.sound(at, cue.sound(), cue.volume(), cue.pitch());
        }
    }

    private static Cue cue(PlatformResolvers resolvers, String token, float volume, float pitch) {
        return new Cue(resolvers.sound(token).orElse(-1), volume, pitch);
    }

    /** {@code loc}'s position with the MOVER's own yaw/pitch — you inherit the spot, not the view. */
    private static Location keepFacing(Location loc, float yaw, float pitch) {
        return new Location(loc.getWorld(), loc.getX(), loc.getY(), loc.getZ(), yaw, pitch);
    }

    private static String victimName(LivingEntity victim) {
        return victim instanceof Player p ? p.getName() : victim.getName();
    }

    private static double distanceSq(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private record Cue(int sound, float volume, float pitch) {
    }
}
