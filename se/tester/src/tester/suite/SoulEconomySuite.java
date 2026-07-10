package tester.suite;

import compile.load.ScrollsConfig;
import compile.load.SoulGemConfig;
import engine.interact.SoulPool;
import engine.stores.SoulModeStore;
import feature.soul.SoulService;
import item.codec.AppliedSlot;
import item.codec.ItemKeys;
import item.codec.SoulCodec;
import item.codec.SoulData;
import item.render.ProtectionLore;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import platform.sched.Scheduling;
import tester.fake.FakePlayers;
import tester.harness.Harness;

/**
 * §D soul ECONOMY over a real {@link Player} inventory + gem PDC: deposit-on-any-kill (deferred to the
 * killer's thread), combine, {@code /se split}, and the §E cross-gem multi-gem drain. Also pins two
 * adversarial-review dupe/lose regressions: a spend persists to a gem wherever it sits (not only the main
 * hand), and toggling soul mode OFF flushes the pending spend to PDC before dropping the pool (else the spend
 * refunds). Fake player.
 */
public final class SoulEconomySuite implements Harness.Scenario {

    private static final String[] KEYS = {
        "soul.depositOnAnyKill", "soul.combineSumsAndRetires", "soul.splitCarvesAndKeeps",
        "soul.spendPersistsByIdentity", "soul.toggleOffFlushesSpend", "soul.multiGemDrainsLeastFirstAndCleansUp",
        "soul.toggleDebounceRejectsSpam", "soul.holyScrollSurvivesMergeAndRerender",
    };

    private final Plugin plugin;

    public SoulEconomySuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        for (String key : KEYS) {
            h.expect(key);
        }

        ItemKeys keys = ItemKeys.of();
        SoulCodec codec = new SoulCodec(keys.soul(), Stores.state());
        SoulService souls = new SoulService(new SoulPool(), new SoulModeStore(), codec, SoulGemConfig::defaults,
                () -> true, platform.lang.Messages.defaults(), feature.fx.ParticleFx.NONE, Stores.hands(), Stores.sounds());

        World world = plugin.getServer().getWorlds().get(0);
        Location at = world.getSpawnLocation();
        int cx = at.getBlockX() >> 4;
        int cz = at.getBlockZ() >> 4;

        Scheduling.onGlobal(() -> {
            world.setChunkForceLoaded(cx, cz, true);
            Scheduling.onRegion(at, () -> {
                Player player;
                try {
                    player = FakePlayers.spawn(world, "se_soul_eco");
                } catch (Throwable t) {
                    failAll(h, "fake player spawn: " + t);
                    return;
                }
                Scheduling.onEntity(player, () -> {
                    h.guard("soul.combineSumsAndRetires", () -> {
                        player.getInventory().clear();
                        ItemStack a = gem(souls, codec, 5);
                        ItemStack b = gem(souls, codec, 3);
                        UUID idA = codec.read(a).gemId();
                        UUID idB = codec.read(b).gemId();
                        ItemStack merged = souls.combine(player, a, b);
                        SoulData md = merged == null ? null : codec.read(merged);
                        if (md == null || md.souls() != 8) {
                            throw new IllegalStateException("combine did not sum to 8: "
                                    + (md == null ? "null" : md.souls()));
                        }
                        if (md.gemId().equals(idA) || md.gemId().equals(idB)) {
                            throw new IllegalStateException("combine reused a source identity");
                        }
                    });

                    h.guard("soul.splitCarvesAndKeeps", () -> {
                        player.getInventory().clear();
                        player.getInventory().setItemInMainHand(gem(souls, codec, 10));
                        SoulService.SplitResult r = souls.split(player, 4);
                        if (!r.ok() || r.moved() != 4 || r.remaining() != 6) {
                            throw new IllegalStateException("split outcome wrong: " + r);
                        }
                        SoulData held = codec.read(player.getInventory().getItemInMainHand());
                        if (held == null || held.souls() != 6) {
                            throw new IllegalStateException("held gem did not keep 6: "
                                    + (held == null ? "null" : held.souls()));
                        }
                        if (countGemsWithSouls(codec, player, 4) != 1) {
                            throw new IllegalStateException("the carved-off 4-soul gem was not produced");
                        }
                        SoulService.SplitResult tooMany = souls.split(player, 100);
                        if (tooMany.status() != SoulService.SplitResult.Status.TOO_MANY) {
                            throw new IllegalStateException("splitting everything away should be refused: " + tooMany);
                        }
                    });

                    // Finding #3: a spend must persist to the gem wherever it sits, not only the main hand.
                    // Seed via toggle-on, move the gem to the bag, do a gate-10 spend, then clear() (the quit
                    // flush settles the pending spend to the gem by IDENTITY).
                    h.guard("soul.spendPersistsByIdentity", () -> {
                        player.getInventory().clear();
                        ItemStack g = gem(souls, codec, 10);
                        player.getInventory().setItemInMainHand(g);
                        souls.forgetToggleDebounce(player.getUniqueId()); // guards toggle far faster than a player can
                        if (souls.toggle(player) != SoulService.Toggle.ENABLED) {
                            throw new IllegalStateException("toggle-on did not enable soul mode");
                        }
                        player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));
                        player.getInventory().setItem(18, g); // gem now in the bag, weapon in hand
                        souls.trySpend(player.getUniqueId(), 3); // a gate-10 spend (10 -> 7); drain may defer
                        souls.clear(player); // settles the pending spend to the bag gem by IDENTITY, drops the pool
                        SoulData after = codec.read(player.getInventory().getItem(18));
                        if (after == null || after.souls() != 7) {
                            throw new IllegalStateException("spend did not persist to the off-hand gem: "
                                    + (after == null ? "no gem" : after.souls()));
                        }
                    });

                    // Finding #1: toggling soul mode OFF must flush the pending spend to PDC before dropping the
                    // pool, else the spend refunds (a dupe).
                    h.guard("soul.toggleOffFlushesSpend", () -> {
                        player.getInventory().clear();
                        ItemStack g = gem(souls, codec, 10);
                        player.getInventory().setItemInMainHand(g);
                        souls.forgetToggleDebounce(player.getUniqueId());
                        souls.toggle(player); // ENABLED, seeds the pool from 10
                        souls.trySpend(player.getUniqueId(), 3); // spend 10 -> 7 (drain may be deferred)
                        souls.forgetToggleDebounce(player.getUniqueId()); // bypass the 0.5s window between the two toggles
                        souls.toggle(player); // DISABLED — must flush the pending 3 to PDC before dropping the pool
                        SoulData after = codec.read(player.getInventory().getItemInMainHand());
                        if (after == null || after.souls() != 7) {
                            throw new IllegalStateException("toggle-off did not flush the spent balance: "
                                    + (after == null ? "no gem" : after.souls()));
                        }
                    });

                    // §E multi-gem draining: drain the LEAST-souls gem first; an emptied gem is consumed only
                    // while another gem remains; the LAST gem is never destroyed; soul mode turns off only at
                    // zero total. The 250/500 example from the spec (no ticks — direct, deterministic).
                    h.guard("soul.multiGemDrainsLeastFirstAndCleansUp", () -> {
                        player.getInventory().clear();
                        ItemStack low = gem(souls, codec, 250);
                        ItemStack high = gem(souls, codec, 500);
                        player.getInventory().setItemInMainHand(low); // a gem with souls in hand → toggle enables
                        player.getInventory().setItem(18, high);
                        souls.forgetToggleDebounce(player.getUniqueId());
                        if (souls.toggle(player) != SoulService.Toggle.ENABLED) {
                            throw new IllegalStateException("toggle did not enable soul mode");
                        }
                        // drain 250: the LEAST gem empties first and (another gem present) is consumed.
                        souls.debit(player, player.getUniqueId(), 250);
                        if (countGems(codec, player) != 1 || countGemsWithSouls(codec, player, 500) != 1) {
                            throw new IllegalStateException("the emptied 250 gem was not removed while the 500 remained: "
                                    + countGems(codec, player) + " gems");
                        }
                        if (souls.bindingFor(player).isEmpty()) {
                            throw new IllegalStateException("soul mode must stay on while souls remain");
                        }
                        // drain 500: now the LAST gem hits zero — it is kept as a lone zero gem.
                        souls.debit(player, player.getUniqueId(), 500);
                        if (countGems(codec, player) != 1) {
                            throw new IllegalStateException("the last soul gem must never be destroyed");
                        }
                        SoulData last = firstGem(codec, player);
                        if (last == null || last.souls() != 0) {
                            throw new IllegalStateException("the surviving lone gem should be a zero gem: "
                                    + (last == null ? "none" : last.souls()));
                        }
                        souls.maintain(player); // the tick sees total == 0 and auto-disables soul mode
                        if (souls.bindingFor(player).isPresent()) {
                            throw new IllegalStateException("soul mode did not turn off when total souls hit zero");
                        }
                    });

                    // The manual-toggle debounce itself: a second toggle inside the 0.5s window is refused and
                    // changes nothing (the seam above bypasses it; this pins that gameplay spam cannot strobe).
                    h.guard("soul.toggleDebounceRejectsSpam", () -> {
                        player.getInventory().clear();
                        player.getInventory().setItemInMainHand(gem(souls, codec, 5));
                        souls.forgetToggleDebounce(player.getUniqueId());
                        if (souls.toggle(player) != SoulService.Toggle.ENABLED) {
                            throw new IllegalStateException("first toggle did not enable soul mode");
                        }
                        if (souls.toggle(player) != SoulService.Toggle.TOO_FAST) {
                            throw new IllegalStateException("an immediate second toggle was not debounced");
                        }
                        if (souls.bindingFor(player).isEmpty()) {
                            throw new IllegalStateException("the debounced toggle must leave soul mode enabled");
                        }
                        souls.clear(player); // reset for the guards below (also drops the debounce entry)
                    });

                    // §4 a holy white scroll dragged onto a soul gem must SURVIVE a merge and a soul-count re-render:
                    // the merged gem carries the HOLY marker, its name keeps its count bracket (the gear composer's
                    // transmog strip never runs on a gem), and its lore shows the holy protected line. A dedicated
                    // service wired with the AppliedSlot + protection-line seam (like the composition root) and a
                    // bracketed name config so the name-strip regression is observable.
                    h.guard("soul.holyScrollSurvivesMergeAndRerender", () -> {
                        player.getInventory().clear();
                        AppliedSlot slot = new AppliedSlot(keys.appliedSlot(), Stores.state());
                        SoulGemConfig base = SoulGemConfig.defaults();
                        SoulGemConfig bracketed = new SoulGemConfig(base.material(), "&aSoul Gem &7[{AMOUNT}]",
                                base.lore(), base.soulsPerKill(), base.soulsPerMob(), base.colorTiers(),
                                base.emptyColor(), base.sounds(), base.particles());
                        String holyTemplate = ScrollsConfig.defaults().holy().protectedLine();
                        Function<org.bukkit.inventory.ItemStack, List<String>> protectionLines = stack ->
                                ProtectionLore.lines(false, slot.holds(stack, AppliedSlot.HOLY), "", holyTemplate);
                        List<String> expectedHoly = ProtectionLore.lines(false, true, "", holyTemplate);
                        SoulService holy = new SoulService(new SoulPool(), new SoulModeStore(), codec,
                                () -> bracketed, () -> true, platform.lang.Messages.defaults(),
                                feature.fx.ParticleFx.NONE, Stores.hands(), Stores.sounds(), slot, protectionLines);

                        ItemStack a = gem(holy, codec, 5);
                        ItemStack b = gem(holy, codec, 3);
                        slot.occupy(a, AppliedSlot.HOLY); // holy-protect one of the inputs
                        ItemStack merged = holy.combine(player, a, b);
                        assertHolyGem(slot, merged, 8, expectedHoly, "after combine");

                        // A soul-count re-render (split carves 1 off the merged gem, re-rendering the source in place)
                        // must keep the marker, the bracket, and the holy line — the reRenderGem the deposit path uses.
                        player.getInventory().setItemInMainHand(merged);
                        SoulService.SplitResult r = holy.split(player, 1);
                        if (!r.ok()) {
                            throw new IllegalStateException("split to force a re-render failed: " + r);
                        }
                        assertHolyGem(slot, player.getInventory().getItemInMainHand(), 7, expectedHoly, "after re-render");
                    });

                    // Deposit-on-any-kill: onKill DEFERS to the killer's thread, so assert after a few ticks.
                    player.getInventory().clear();
                    player.getInventory().setItem(20, souls.mintGem()); // a gem far from the main hand, 0 souls
                    souls.onKill(player, EntityType.ZOMBIE); // soul mode OFF — must still deposit
                    Scheduling.onEntityLater(player, 5L, () -> {
                        try {
                            h.guard("soul.depositOnAnyKill", () -> {
                                SoulData after = codec.read(player.getInventory().getItem(20));
                                if (after == null || after.souls() != 1) {
                                    throw new IllegalStateException("a kill did not deposit into the carried gem: "
                                            + (after == null ? "no gem" : after.souls()));
                                }
                            });
                        } finally {
                            FakePlayers.despawn(player);
                        }
                    });
                });
            });
        });
    }

    /**
     * Assert a merged/re-rendered soul gem kept its holy protection: the {@code HOLY} marker, a display name still
     * ending with the {@code [souls]} count bracket (never eaten by the gear composer's transmog count-suffix strip),
     * and the holy protected line in its lore. {@code where} labels the stage for the failure message.
     */
    @SuppressWarnings("deprecation") // getDisplayName/getLore: the floor-stable item-meta read (matches SoulService)
    private static void assertHolyGem(AppliedSlot slot, ItemStack gem, int souls, List<String> expectedHoly,
                                      String where) {
        if (gem == null) {
            throw new IllegalStateException("no gem " + where);
        }
        if (!slot.holds(gem, AppliedSlot.HOLY)) {
            throw new IllegalStateException("the holy marker was dropped " + where);
        }
        String name = gem.hasItemMeta() ? gem.getItemMeta().getDisplayName() : null;
        if (name == null || !name.endsWith("[" + souls + "]")) {
            throw new IllegalStateException("the count bracket [" + souls + "] was stripped from the name " + where
                    + ": " + name);
        }
        List<String> lore = gem.hasItemMeta() ? gem.getItemMeta().getLore() : null;
        if (lore == null || !lore.containsAll(expectedHoly)) {
            throw new IllegalStateException("the holy protected line is missing from the lore " + where + ": " + lore);
        }
    }

    /** A gem ITEM carrying exactly {@code souls} (mint, then stamp the count onto its identity). */
    private static ItemStack gem(SoulService service, SoulCodec codec, int souls) {
        ItemStack stack = service.mintGem();
        codec.write(stack, new SoulData(codec.read(stack).gemId(), souls));
        return stack;
    }

    private static int countGemsWithSouls(SoulCodec codec, Player player, int souls) {
        int n = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            SoulData data = codec.read(stack);
            if (data != null && data.souls() == souls) {
                n++;
            }
        }
        return n;
    }

    /** Total soul gems carried, regardless of count. */
    private static int countGems(SoulCodec codec, Player player) {
        int n = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (codec.read(stack) != null) {
                n++;
            }
        }
        return n;
    }

    /** The first carried soul gem's data, or {@code null} if none. */
    private static SoulData firstGem(SoulCodec codec, Player player) {
        for (ItemStack stack : player.getInventory().getContents()) {
            SoulData data = codec.read(stack);
            if (data != null) {
                return data;
            }
        }
        return null;
    }

    private static void failAll(Harness h, String message) {
        for (String key : KEYS) {
            h.fail(key, message);
        }
    }
}
