package tester;

import java.nio.file.Path;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import platform.caps.Capabilities;
import platform.sched.Scheduling;
import tester.harness.Harness;
import tester.suite.ApplySuite;
import tester.suite.CapabilitiesSuite;
import tester.suite.CarrierSuite;
import tester.suite.CatalogSuite;
import tester.suite.CombatFlagsSuite;
import tester.suite.CombatSuite;
import tester.suite.ConditionSuite;
import tester.suite.ContentFormatSuite;
import tester.suite.ContentLoaderSuite;
import tester.suite.CrystalSuite;
import tester.suite.EconomyItemsSuite;
import tester.suite.EconomySuite;
import tester.suite.FakePlayerSuite;
import tester.suite.HeroicSuite;
import tester.suite.ItemCodecSuite;
import tester.suite.ItemViewSuite;
import tester.suite.MenuSuite;
import tester.suite.ProtectionSuite;
import tester.suite.RenderSuite;
import tester.suite.ResolverSuite;
import tester.suite.SetSuite;
import tester.suite.SoulEconomySuite;
import tester.suite.SoulSuite;
import tester.suite.TeleportSuite;
import tester.suite.TriggerSuite;
import tester.suite.RuntimeHandlesSuite;
import tester.suite.SchedulingSuite;
import tester.suite.ScrollPlayerSuite;
import tester.suite.SinkSuite;
import tester.suite.WornResolverSuite;

/**
 * The live matrix harness plugin (docs/architecture.md §11; live-server-testing, matrix-gate
 * skills). Booted inside a real Paper/Folia server by {@code scripts/run-matrix.sh}, it probes the
 * platform, installs the scheduling backend, runs the in-server suites across game ticks, writes a
 * fresh {@code test-results.txt} (PASS/FAIL), and shuts the server down. The runner then reads that
 * file and fails the gate on anything but a fresh PASS.
 *
 * <p>Suites start on {@link ServerLoadEvent}, not in {@code onEnable}: a fully-initialised server
 * is the only stable footing for scenarios that spawn entities or expect a delayed task to fire
 * (mid-startup the world is still loading — a freshly-spawned entity may not survive a few ticks,
 * which is exactly the flake the matrix surfaced on the slow-booting ceiling build).
 */
public final class SeTesterPlugin extends JavaPlugin implements Listener {

    /** Game-tick budget before unresolved checks are failed (20 s at 20 tps). */
    private static final long DEADLINE_TICKS = 400L;

    private Harness harness;
    private boolean started;

    @Override
    public void onEnable() {
        Capabilities caps = Capabilities.probe(getServer());
        Scheduling.init(this, caps);
        getLogger().info("[tester] " + caps + " — scheduling backend "
                + Scheduling.backend().getClass().getSimpleName());

        // The server's working directory is the matrix run dir; results land there.
        Path serverRoot = Path.of(System.getProperty("user.dir", "."));

        harness = new Harness(this, serverRoot, DEADLINE_TICKS)
                .add(new CapabilitiesSuite(this, caps))
                .add(new SchedulingSuite(this))
                .add(new ItemCodecSuite(this))
                .add(new ItemViewSuite(this))
                .add(new RenderSuite())
                .add(new ApplySuite(this))
                .add(new CatalogSuite(this))
                .add(new ResolverSuite())
                .add(new RuntimeHandlesSuite())
                .add(new SinkSuite(this))
                .add(new ContentLoaderSuite(this))
                .add(new ContentFormatSuite(this))
                .add(new CarrierSuite(this))
                .add(new EconomyItemsSuite(this)) // §I slot/black/randomizer/unopened/transmog over real ItemStacks
                .add(new WornResolverSuite(this));

        // The fake-player harness now spans the whole range — mojang-mapped (1.20.5+) and the spigot-mapped
        // floor (1.17.1–1.19.4) via FakePlayers' two paths (ADR 0018) — so the combat-path suites run
        // floor-wide; they no longer self-defer behind Capabilities.mojangMapped().
        harness.add(new FakePlayerSuite(this));
        harness.add(new CombatSuite(this)); // end-to-end combat needs the fake-player attacker
        harness.add(new CombatFlagsSuite(this)); // §C combat-flags: KNOCKBACK_CONTROL version-split, GUARD spawn+target, KEEP_ON_DEATH
        harness.add(new ConditionSuite(this)); // %victim.health% condition gate fires/blocks (populated FactBuffer)
        harness.add(new ProtectionSuite(this)); // gate-2 protection blocks/allows a hit by location
        harness.add(new EconomySuite(this)); // MODIFY_MONEY deposits via a discovered economy provider
        harness.add(new MenuSuite(this)); // the enchant-apply GUI applies on click, end-to-end
        harness.add(new CrystalSuite(this)); // crystal source fires end-to-end (also needs the attacker)
        harness.add(new SetSuite(this)); // armour-set resolution on a real equipped fake player
        harness.add(new HeroicSuite(this)); // heroic flat stats fold into combat damage
        harness.add(new SoulSuite(this)); // soul-cost enchant spends from the gem in soul mode
        harness.add(new SoulEconomySuite(this)); // §D deposit-on-any-kill + combine + split on a real inventory
        harness.add(new ScrollPlayerSuite(this)); // §I holy death-save + nametag rename on a real player inventory
        harness.add(new TriggerSuite(this)); // non-combat triggers (MINE) fire end-to-end
        harness.add(new TeleportSuite(this)); // TELEPORT effect → teleportAsync, first user of that intent

        getServer().getPluginManager().registerEvents(this, this);
    }

    /** Begin the suites once the server is fully loaded (fires once per startup). */
    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        if (started) {
            return;
        }
        started = true;
        getLogger().info("[tester] server loaded (" + event.getType() + ") — starting suites");
        harness.start();
    }
}
