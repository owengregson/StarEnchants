package tester.suite;

import compile.Compiler;
import compile.load.ContentHolder;
import compile.load.Library;
import compile.load.LibraryLoader;
import compile.model.Affinity;
import compile.model.Snapshot;
import engine.boot.ContentCompiler;
import engine.effect.EffectRegistry;
import engine.effect.kind.BuiltinEffects;
import engine.interact.SoulSpender;
import engine.pipeline.ActivationPipeline;
import engine.run.AbilityExecutor;
import engine.run.AbilityQuarantine;
import engine.run.AreaScan;
import engine.selector.kind.BuiltinSelectors;
import engine.spec.EffectSpec;
import engine.stores.CooldownStore;
import engine.trigger.BuiltinTriggers;
import engine.trigger.TriggerRegistry;
import feature.combat.CombatDispatch;
import feature.combat.CombatListener;
import item.codec.CombatCodec;
import item.codec.CombatState;
import item.codec.ItemKeys;
import item.view.ItemViewCache;
import item.worn.WornResolver;
import item.worn.WornStateStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import platform.resolve.RegistryResolvers;
import platform.resolve.RuntimeHandles;
import platform.sched.Scheduling;
import schema.spec.Param;
import schema.spec.ParamType;
import tester.fake.FakePlayers;
import tester.harness.CombatRig;
import tester.harness.Harness;

/**
 * Auto-generated cross-region activation coverage for every non-local effect kind (docs/architecture.md §7,
 * §3.6): the promise that "extensibility and coverage grow together" made real. At run time it enumerates the
 * live {@link EffectRegistry} and, for every kind whose declared {@link Affinity} is
 * {@code TARGET_ENTITY}/{@code REGION}/{@code AOE}, synthesises a one-effect ATTACK enchant (args derived from
 * the kind's own {@code ParamSpec}), then fires it once with the attacker and victim staged in DISTINCT Folia
 * regions. A kind passes when its ability activates and its effects emit + flush with no run-time fault — the
 * shape only a wrong-thread bug (an effect that touches an entity off its owning region) breaks.
 *
 * <p><strong>Why this staging.</strong> Mirrors {@link CrossRegionTeleportSuite}: the attacker sits at world
 * spawn (region A), each victim is a cow {@value #REGION_GAP} blocks away (region B), and the hit is fired on
 * the victim's region thread — where Folia delivers a real combat event. So a {@code @Self}-targeted intent
 * must hop B&rarr;A through the Sink; an effect that instead reads the (remote) actor inline throws on Folia,
 * is caught by the executor, and lands in the {@link AbilityQuarantine} — the fault channel this suite reads.
 * On Paper the two chunks share the one thread, so this is still a smoke of the whole dispatch path.
 *
 * <p><strong>Skip list.</strong> {@link #SKIPS} names the non-local kinds that genuinely cannot run in this
 * staging (each reads a remote actor's live location in {@code run()}, so a cross-region firing thread would
 * fault). Every skip is PRINTED, and the suite asserts {@code non-local kinds == checks + skips} so a future
 * kind can never be silently dropped from Folia coverage.
 */
public final class AffinityAutogenSuite implements Harness.Scenario {

    /** Kinds that cannot be synthesised as a cross-region check, mapped to the reason (all PRINTED at run). */
    private static final Map<String, String> SKIPS = new LinkedHashMap<>();

    static {
        String actorLoc = "reads the @Self target's live location (who.getLocation()) in run(); requires the "
                + "actor local to the firing thread, so it cannot be exercised with a cross-region actor";
        SKIPS.put("PARTICLE_RING", actorLoc);
        SKIPS.put("WALKER", actorLoc);
        SKIPS.put("SPAWN_ENTITY", actorLoc);
        SKIPS.put("PARTICLE_LINE", "reads the actor's live location in run() to anchor the tether; requires the "
                + "actor local to the firing thread");
        SKIPS.put("TELEPORT_BEHIND", "reads the actor's eye-location in run() to compute the blink vector; "
                + "cannot run when actor and victim occupy different regions");
    }

    private static final Set<Affinity> NON_LOCAL = EnumSet.of(Affinity.TARGET_ENTITY, Affinity.REGION, Affinity.AOE);

    /** {@value} blocks (32 chunks): comfortably a distinct Folia region from world spawn (as in the teleport suite). */
    private static final int REGION_GAP = 512;

    /** Ticks between successive kinds — lets a kind's deferred (hopped) intents settle before the next hit. */
    private static final long STEP_TICKS = 2L;

    private final Plugin plugin;

    public AffinityAutogenSuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        String totality = "affinity.autogen.totality";
        h.expect(totality);

        EffectRegistry registry = BuiltinEffects.registry();
        List<String> nonLocal = new ArrayList<>();
        for (EffectSpec spec : specs(registry)) {
            if (NON_LOCAL.contains(spec.affinity())) {
                nonLocal.add(spec.head());
            }
        }

        // Partition every non-local kind into a fire check or a printed skip; a kind whose args cannot be
        // derived becomes a dynamic skip so the totality identity still holds.
        Map<String, String> skips = new LinkedHashMap<>();
        List<Firing> firings = new ArrayList<>();
        Path root;
        try {
            root = Files.createTempDirectory("se-affinity-autogen-suite");
        } catch (IOException e) {
            h.fail(totality, "temp content dir: " + e);
            return;
        }
        try {
            for (EffectSpec spec : specs(registry)) {
                String head = spec.head();
                if (!NON_LOCAL.contains(spec.affinity())) {
                    continue;
                }
                if (SKIPS.containsKey(head)) {
                    skips.put(head, SKIPS.get(head));
                    continue;
                }
                String block = effectBlock(spec);
                if (block == null) {
                    skips.put(head, "no argument could be derived from the kind's ParamSpec or example");
                    continue;
                }
                String key = "enchants/autogen_" + head.toLowerCase(Locale.ROOT);
                write(root, key + ".yml", enchantYaml(head, block));
                firings.add(new Firing(head, key));
            }
        } catch (IOException e) {
            h.fail(totality, "writing synthesised content: " + e);
            return;
        }

        // Print every skip (coverage gaps are never silent) and declare each as a resolved check.
        for (Map.Entry<String, String> skip : skips.entrySet()) {
            String check = "affinity.autogen.SKIP." + skip.getKey();
            h.expect(check);
            plugin.getLogger().info("[affinity-autogen] SKIP " + skip.getKey() + " — " + skip.getValue());
            h.pass(check);
        }
        for (Firing firing : firings) {
            h.expect("affinity.autogen." + firing.head());
        }

        plugin.getLogger().info("[affinity-autogen] non-local kinds=" + nonLocal.size()
                + " checked=" + firings.size() + " skipped=" + skips.size());

        // The invariant the ratified promise rests on: every non-local kind is a check OR a printed skip.
        if (nonLocal.size() != firings.size() + skips.size()) {
            h.fail(totality, "non-local kinds (" + nonLocal.size() + ") != checks (" + firings.size()
                    + ") + skips (" + skips.size() + ")");
            failAll(h, firings, "totality mismatch — see " + totality);
            return;
        }

        // Retained resolver so the compile→runtime handle round-trip pairs (as in CombatSuite §9).
        RegistryResolvers resolvers = new RegistryResolvers();
        Compiler compiler = ContentCompiler.production(resolvers);
        RuntimeHandles handles = new RuntimeHandles(resolvers);

        Library library = LibraryLoader.load(root, compiler, 0);
        if (library.hasErrors()) {
            h.fail(totality, "synthesised content failed to compile: " + library.diagnostics());
            failAll(h, firings, "library did not compile — see " + totality);
            return;
        }
        Snapshot snapshot = library.snapshot();
        for (Firing firing : firings) {
            // An enchant's ability is keyed baseKey/level; the level-1 ability is the one we arm and fire.
            if (snapshot.byStableKey(firing.key() + "/1") == null) {
                h.fail("affinity.autogen." + firing.head(), "no ability compiled for " + firing.key());
            }
        }
        h.pass(totality);

        Driver driver = new Driver(h, library, snapshot, handles, firings);
        driver.start();
    }

    /** Every registered kind's spec, sorted by head so the run order (and any failure) is stable across servers. */
    private static List<EffectSpec> specs(EffectRegistry registry) {
        List<EffectSpec> specs = new ArrayList<>();
        registry.kinds().forEach(k -> specs.add(k.spec()));
        specs.sort((a, b) -> a.head().compareTo(b.head()));
        return specs;
    }

    /** The one-effect ATTACK enchant carrying {@code block} at 100% chance / no cooldown / no condition. */
    private static String enchantYaml(String head, String block) {
        return "display: Autogen " + head + "\n"
                + "trigger: ATTACK\n"
                + "levels:\n"
                + "  1: { chance: 100, effects: [ " + block + " ] }\n";
    }

    /**
     * The block form {@code { HEAD: { name: value, ... } }} with each arg derived from the kind's ParamSpec:
     * declared default, else the numeric minimum, else a sane per-type literal, else the value the kind's own
     * example pins for that param. {@code null} when a param cannot be filled (the kind then becomes a skip).
     */
    private static String effectBlock(EffectSpec spec) {
        List<Param> params = spec.paramSpec().params();
        StringBuilder sb = new StringBuilder("{ ").append(spec.head()).append(": {");
        boolean first = true;
        for (Param p : params) {
            String value = deriveArg(p.type(), p.name(), spec.example());
            if (value == null) {
                return null;
            }
            sb.append(first ? " " : ", ").append(p.name()).append(": ")
                    .append(p.type().kind() == ParamType.Kind.STRING ? quote(value) : value);
            first = false;
        }
        return sb.append(params.isEmpty() ? "} }" : " } }").toString();
    }

    private static String deriveArg(ParamType type, String name, String example) {
        if (type.defaultRaw().isPresent()) {
            return type.defaultRaw().get();
        }
        return switch (type.kind()) {
            case DOUBLE, INT, TICKS -> type.min().isPresent() ? trim(type.min().getAsDouble()) : "1";
            case BOOL -> "true";
            case ENUM -> type.allowed().isEmpty() ? null : type.allowed().get(0);
            // A version-volatile handle (or free-form string) has no safe generic literal; the kind's own
            // example carries a real, author-vetted token, so single-source it from there.
            case HANDLE, STRING -> exampleToken(example, name);
        };
    }

    /** Whole numbers render without a decimal point so the INT/TICKS parsers accept them (no getInt trap). */
    private static String trim(double d) {
        return d == Math.rint(d) ? Long.toString((long) d) : Double.toString(d);
    }

    private static String quote(String value) {
        return '"' + value.replace("\"", "\\\"") + '"';
    }

    /** Extract {@code name}'s value from a {@code { HEAD: { name: value, ... } }} example, or {@code null}. */
    private static String exampleToken(String example, String name) {
        if (example == null || example.isEmpty()) {
            return null;
        }
        Matcher m = Pattern.compile("[,{]\\s*" + Pattern.quote(name) + "\\s*:\\s*([^,}]+)").matcher(example);
        if (!m.find()) {
            return null;
        }
        String raw = m.group(1).trim();
        if (raw.length() >= 2 && raw.charAt(0) == '"' && raw.charAt(raw.length() - 1) == '"') {
            raw = raw.substring(1, raw.length() - 1);
        }
        return raw.isEmpty() ? null : raw;
    }

    private static void failAll(Harness h, List<Firing> firings, String reason) {
        for (Firing firing : firings) {
            h.fail("affinity.autogen." + firing.head(), reason);
        }
    }

    private static void write(Path root, String relative, String yaml) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, yaml, StandardCharsets.UTF_8);
    }

    /** One synthesised kind to fire: its canonical head and its compiled stable content key. */
    private record Firing(String head, String key) {
    }

    /**
     * Fires the synthesised enchants one at a time. A persistent attacker (region A) is re-armed with the
     * current kind's single enchant on its own thread; the hit is delivered on a fresh cow's region (B). Because
     * dispatch runs synchronously inside {@code cow.damage(...)}, the activation count, a run-time fault (via a
     * threshold-1 quarantine), and any synchronous throw are all known the instant the call returns.
     */
    private final class Driver {

        private final Harness h;
        private final Library library;
        private final Snapshot snapshot;
        private final List<Firing> firings;
        private final CombatCodec codec;
        private final WornStateStore worn;
        private final AbilityQuarantine quarantine;
        private final AtomicInteger activations = new AtomicInteger();
        private final CombatRig rig;
        private final World world;
        private final Location attackerAt;
        private final Location victimAt;

        private Player attacker;

        private Driver(Harness h, Library library, Snapshot snapshot, RuntimeHandles handles, List<Firing> firings) {
            this.h = h;
            this.library = library;
            this.snapshot = snapshot;
            this.firings = firings;
            this.codec = new CombatCodec(ItemKeys.of().combat());
            ItemViewCache itemViews = new ItemViewCache(codec, snapshot.generation());
            TriggerRegistry triggers = BuiltinTriggers.registry();
            this.worn = new WornStateStore(new WornResolver(itemViews, triggers.count(),
                    triggers.attackTriggers(), triggers.defenseTriggers())::resolve);
            AbilityExecutor executor = new AbilityExecutor(BuiltinEffects.registry(), BuiltinSelectors.registry(),
                    new ActivationPipeline(new CooldownStore(), SoulSpender.NONE), AreaScan.NONE,
                    (key, ability, ctx) -> activations.incrementAndGet());
            // Threshold 1: a single run-time fault disables the ability AND surfaces it in quarantinedKeys(),
            // which this driver reads as the per-kind fault signal.
            this.quarantine = new AbilityQuarantine(snapshot.sourceMap(), snapshot.stableKeys(), 1);
            executor.bindQuarantine(quarantine);
            this.rig = new CombatRig(plugin);
            CombatDispatch dispatch = new CombatDispatch(executor, new engine.sink.DispatchSinkFactory(handles),
                    new ContentHolder(library), worn,
                    triggers.idOf("ATTACK").orElseThrow(), triggers.idOf("DEFENSE").orElseThrow(),
                    new AtomicLong()::incrementAndGet);
            rig.listen(new CombatListener(dispatch));
            this.world = plugin.getServer().getWorlds().get(0);
            this.attackerAt = world.getSpawnLocation();
            this.victimAt = attackerAt.clone().add(REGION_GAP, 0, REGION_GAP);
        }

        void start() {
            // Force-load BOTH regions, then stage the attacker on its (world-spawn) region thread.
            rig.onArena(attackerAt, victimAt, () -> {
                try {
                    attacker = rig.track(FakePlayers.spawn(world, "se_affinity_atk"));
                } catch (Throwable t) {
                    failAll(h, firings, "attacker spawn: " + t);
                    rig.teardown();
                    return;
                }
                step(0);
            });
        }

        private void step(int index) {
            if (index >= firings.size()) {
                rig.teardown();
                return;
            }
            Firing firing = firings.get(index);
            String check = "affinity.autogen." + firing.head();

            // Re-arm the attacker on ITS thread (region A) so only this kind is a candidate on the next hit.
            Scheduling.onEntity(attacker, () -> {
                try {
                    ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
                    codec.write(sword, new CombatState(Map.of(firing.key(), 1), List.of()));
                    attacker.getInventory().setItemInMainHand(sword);
                    worn.refresh(attacker, library.snapshot());
                } catch (Throwable t) {
                    h.fail(check, "arm attacker: " + t);
                    Scheduling.onGlobalLater(STEP_TICKS, () -> step(index + 1));
                    return;
                }
                // Deliver the hit on the victim's region (B) — where Folia owns a real combat event.
                Scheduling.onRegion(victimAt, () -> fireOnVictimRegion(index, firing, check));
            });
        }

        private void fireOnVictimRegion(int index, Firing firing, String check) {
            LivingEntity cow;
            try {
                cow = rig.spawn(world, victimAt, EntityType.COW, LivingEntity.class);
                cow.setAI(false); // pin it in region B so it can't wander across the boundary mid-hit
            } catch (Throwable t) {
                h.fail(check, "victim spawn: " + t);
                Scheduling.onGlobalLater(STEP_TICKS, () -> step(index + 1));
                return;
            }
            int faultsBefore = quarantine.quarantinedKeys().size();
            activations.set(0);
            String threw = null;
            try {
                cow.damage(1.0, attacker); // synchronous: fires EDBE → dispatch → executor.run → sink.flush
            } catch (Throwable t) {
                threw = t.toString();
            }
            boolean faulted = quarantine.quarantinedKeys().size() > faultsBefore;
            if (threw != null) {
                h.fail(check, "the hit threw synchronously: " + threw);
            } else if (activations.get() != 1) {
                h.fail(check, "expected exactly 1 activation, got " + activations.get()
                        + " (worn resolve / candidate issue)");
            } else if (faulted) {
                h.fail(check, "effect kind faulted during run — quarantined (wrong-thread or bad intent)");
            } else {
                h.pass(check);
            }
            try {
                cow.remove();
            } catch (Throwable ignored) {
                // best-effort: a dead/removed cow (KILL, EXPLODE) must never mask the result
            }
            Scheduling.onGlobalLater(STEP_TICKS, () -> step(index + 1));
        }
    }
}
