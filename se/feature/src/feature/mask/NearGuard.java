package feature.mask;

import compile.load.MasterConfig;
import engine.stores.WardStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import platform.caps.Regions;
import platform.lang.Messages;
import platform.text.Colors;

/**
 * The owned {@code /near} interception (ADR-0053 §8): no plugin can filter another plugin's proximity scan, so
 * for each configured command name ({@code masks.near-commands}, default {@code [near]}) this cancels the
 * command and answers with its OWN listing (within {@code masks.near-radius}) that omits NEAR-warded players.
 * An empty command list disables the interception entirely — the documented opt-out divergence knob. Runs on
 * the sender's own region thread (its command event); OTHER players' locations are cross-region reads on Folia,
 * each {@link Regions}-guarded (unreadable → omitted).
 */
public final class NearGuard implements Listener {

    private final WardStore wards;
    private final LongSupplier nowTicks;
    private final Messages messages;
    private final Supplier<MasterConfig.MasksSection> masks;
    private final BooleanSupplier enabled; // features.masks — LIVE; see the toggle note on onCommand

    public NearGuard(WardStore wards, LongSupplier nowTicks, Messages messages,
                     Supplier<MasterConfig.MasksSection> masks, BooleanSupplier enabled) {
        this.wards = Objects.requireNonNull(wards, "wards");
        this.nowTicks = Objects.requireNonNull(nowTicks, "nowTicks");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.masks = Objects.requireNonNull(masks, "masks");
        this.enabled = Objects.requireNonNull(enabled, "enabled");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        // Unlike the other guards (ward-driven, so naturally inert once arming stops), the interception itself
        // is config-driven — without this short-circuit a live features.masks=false would keep replacing the
        // external /near's output. Toggle off = the external command runs untouched.
        if (!enabled.getAsBoolean()) {
            return;
        }
        List<String> commands = masks.get().nearCommands();
        if (commands.isEmpty()) {
            return; // near-commands: [] turns the interception off entirely (the opt-out knob)
        }
        if (!commands.contains(commandWord(event.getMessage()))) {
            return; // cheapest reject: not one of the intercepted commands, leave it to whoever owns it
        }
        event.setCancelled(true); // OWN the answer — the external /near never runs while we intercept its name

        Player sender = event.getPlayer();
        Location origin = sender.getLocation(); // the sender's own location on the sender's own thread — no guard
        int radius = masks.get().nearRadius();
        long now = nowTicks.getAsLong();

        List<String> entries = new ArrayList<>();
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.getUniqueId().equals(sender.getUniqueId())) {
                continue; // never list the sender themselves
            }
            // No SE-specific vanish convention exists in the repo — defer to Bukkit's native visibility, which
            // every vanish plugin drives via hidePlayer/showPlayer. canSee reads the SENDER's own hidden set,
            // so it is safe to call on the sender's thread.
            if (!sender.canSee(other)) {
                continue;
            }
            if (wards.isWarded(other.getUniqueId(), WardStore.Type.NEAR, now)) {
                continue; // near-warded players are shielded from the listing
            }
            // Another player's location is a cross-region read on Folia (we are on the sender's region thread);
            // guard it — unreadable → omit that player (a documented degrade, never a crash).
            Location there = Regions.read("mask.near player location", other::getLocation, null);
            if (there == null || !Objects.equals(origin.getWorld(), there.getWorld())) {
                continue; // different world (or unreadable) — not "near" in any meaningful sense
            }
            int distance = (int) Math.round(origin.distance(there));
            if (distance > radius) {
                continue;
            }
            entries.add(Colors.translate(messages.fragment("mask.near-entry",
                    "NAME", other.getName(), "DISTANCE", distance)));
        }

        // Prefix-free listing block (the use-item feedback idiom): fragment → translate → sendText.
        messages.sendText(sender, Colors.translate(messages.fragment("mask.near-header", "RADIUS", radius)));
        if (entries.isEmpty()) {
            messages.sendText(sender, Colors.translate(messages.fragment("mask.near-none")));
            return;
        }
        for (String entry : entries) {
            messages.sendText(sender, entry);
        }
    }

    /**
     * The command word from a raw chat message: strip a leading {@code '/'}, take the token before the first
     * space, lowercased ({@code Locale.ROOT}). Pure so the match rule is unit-tested without an event.
     */
    static String commandWord(String message) {
        if (message == null) {
            return "";
        }
        String body = message.startsWith("/") ? message.substring(1) : message;
        int space = body.indexOf(' ');
        String word = space < 0 ? body : body.substring(0, space);
        return word.toLowerCase(Locale.ROOT);
    }
}
