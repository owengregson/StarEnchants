package feature.bless;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import platform.lang.Messages;
import platform.sched.Scheduling;

/**
 * {@code /bless} — the player-facing cleanse (ADR-0072). ONE application of the very same
 * {@code CURE category: HARMFUL} sweep clarity's Bless fires on a timer: the command owns no cleanse logic of
 * its own, only the permission and the cost/cooldown policy around it, so the two can never diverge.
 *
 * <p>Gated on {@code starenchants.bless} (default true, the {@code starenchants.use} precedent) with the policy
 * in {@link BlessGate}; a player holding {@code starenchants.bless.bypass} skips both, which is also how
 * {@code /se bless} runs it for an admin. Registered dynamically on the command map like {@code /enchants}.
 *
 * <p>Folia-correct: a command runs on the command thread, not the player's region thread, so the cleanse itself
 * hops via {@link Scheduling#onEntity}. The gate is claimed BEFORE the hop — it is pure map/economy work with no
 * entity access, and claiming it inline keeps a double-submit from passing two cleanses through one window.
 */
public final class BlessCommand extends Command {

    public static final String PERMISSION = "starenchants.bless";
    public static final String BYPASS_PERMISSION = "starenchants.bless.bypass";

    private final Consumer<Player> cleanse; // the shared CURE HARMFUL sweep, run on the target's own thread
    private final BlessGate gate;
    private final Messages messages;
    private final LongSupplier nowMillis;

    public BlessCommand(String label, Consumer<Player> cleanse, BlessGate gate, Messages messages,
                        LongSupplier nowMillis) {
        super(label);
        this.cleanse = Objects.requireNonNull(cleanse, "cleanse");
        this.gate = Objects.requireNonNull(gate, "gate");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.nowMillis = Objects.requireNonNull(nowMillis, "nowMillis");
        setDescription("Cleanse every debuff an opponent has landed on you.");
        setUsage("/" + label);
        setPermission(PERMISSION);
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.format("command.not-a-player"));
            return true;
        }
        if (player.hasPermission(BYPASS_PERMISSION)) {
            run(player, player);
            return true;
        }
        BlessGate.Decision decision = gate.claim(player.getUniqueId(), nowMillis.getAsLong());
        if (!decision.allowed()) {
            player.sendMessage(refusal(decision));
            return true;
        }
        run(player, player);
        return true;
    }

    /** The refusal text for a non-ALLOWED decision. */
    private String refusal(BlessGate.Decision decision) {
        return switch (decision.verdict()) {
            case COOLING_DOWN -> messages.format("command.bless.cooldown", "SECONDS", decision.remainingSeconds());
            case NO_ECONOMY -> messages.format("command.bless.no-economy");
            case CANNOT_AFFORD -> messages.format("command.bless.cannot-afford", "COST", decision.cost());
            case ALLOWED -> ""; // unreachable — the caller only refuses on a non-ALLOWED verdict
        };
    }

    /**
     * Cleanse {@code target} on its own region thread and report to {@code notify}. Shared with {@code /se bless},
     * where the two differ (an admin blessing someone else).
     */
    public void run(Player notify, Player target) {
        Scheduling.onEntity(target, () -> {
            cleanse.accept(target);
            if (notify.getUniqueId().equals(target.getUniqueId())) {
                notify.sendMessage(messages.format("command.bless.cleansed"));
                return;
            }
            target.sendMessage(messages.format("command.bless.cleansed-by", "PLAYER", notify.getName()));
            notify.sendMessage(messages.format("command.bless.cleansed-other", "PLAYER", target.getName()));
        });
    }
}
