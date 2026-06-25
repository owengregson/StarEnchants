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
import tester.suite.GuiSuite;
import tester.suite.HeroicApplySuite;
import tester.suite.HeroicSuite;
import tester.suite.ItemCodecSuite;
import tester.suite.ItemViewSuite;
import tester.suite.LifecycleSuite;
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
 * The live matrix harness plugin (§11), booted by {@code scripts/run-matrix.sh}: probe, install the
 * scheduling backend, run the in-server suites, write a fresh {@code test-results.txt}, shut down.
 *
 * <p>Suites start on {@link ServerLoadEvent}, not {@code onEnable}: mid-startup the world is still loading
 * and a freshly-spawned entity may not survive a few ticks (a flake seen on the slow ceiling build).
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

        // The fake-player harness spans the whole range via FakePlayers' two paths (ADR 0018), so the
        // combat-path suites run floor-wide.
        harness.add(new FakePlayerSuite(this));
        harness.add(new CombatSuite(this));
        harness.add(new CombatFlagsSuite(this)); // §C: KNOCKBACK_CONTROL version-split, GUARD spawn+target, KEEP_ON_DEATH
        harness.add(new ConditionSuite(this)); // %victim.health% condition gate fires/blocks (populated FactBuffer)
        harness.add(new ProtectionSuite(this));
        harness.add(new EconomySuite(this)); // MODIFY_MONEY via a discovered economy provider
        harness.add(new MenuSuite(this));
        harness.add(new GuiSuite(this)); // §K benches: input-slot lock, combine, salvage, close-return
        harness.add(new CrystalSuite(this));
        harness.add(new SetSuite(this));
        harness.add(new HeroicSuite(this));
        harness.add(new HeroicApplySuite(this)); // §F: success/fail/consume + armour-weapon guard
        harness.add(new SoulSuite(this));
        harness.add(new SoulEconomySuite(this)); // §D: deposit-on-any-kill + combine + split
        harness.add(new ScrollPlayerSuite(this)); // §I: holy death-save + nametag rename
        harness.add(new TriggerSuite(this));
        harness.add(new LifecycleSuite(this)); // §B: HELD/PASSIVE start+stop + COMMAND trigger fire
        harness.add(new TeleportSuite(this));

        getServer().getPluginManager().registerEvents(this, this);
    }

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
