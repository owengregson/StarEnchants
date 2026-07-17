package feature.pet;

import compile.load.ContentHolder;
import compile.load.MasterConfig;
import compile.load.PetBracket;
import compile.load.PetDef;
import compile.load.PetFoodConfig;
import compile.load.PetItemConfig;
import compile.model.Ability;
import compile.model.CompiledEffect;
import compile.model.Snapshot;
import engine.effect.kind.CageEffect;
import engine.run.UseAttempt;
import engine.sink.CageGeometry;
import feature.apply.Rolls;
import feature.menu.MenuIcons;
import feature.trigger.TriggerDispatch;
import item.codec.PetCodec;
import item.head.HeadEquip;
import item.head.TexturedHeads;
import item.mint.ItemFactory;
import item.mint.VanillaEnchants;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import platform.caps.Regions;
import platform.sched.Scheduling;
import platform.text.TimeFormat;
import platform.text.Tokens;

/**
 * The pets cold path (ADR-0052, leveling per ADR-0059): mints pet heads and Pet Food from the universal
 * likeness, renders a pet's name/lore from its stored state (never parsed back), owns the level economy (exp
 * from kills / vanilla XP / successful use / passive inventory time, +levels from food, all clamped to the
 * universal max, level-up cue on every gain), and runs an ACTIVE pet's right-click
 * through the SAME pipeline every source uses ({@link TriggerDispatch#fireUse} over the live bracket's USE
 * keys — full gate sequence, gate-6 cooldown on the pet-wide scope). Activation may open an ARMED window
 * ({@link PetArmedStore}): the bracket's non-USE abilities join {@code WornState} until the scheduled expiry
 * clears it, sends the universal ENDED message and refreshes.
 *
 * <p>Bukkit-thin and Folia-correct: every caller is already on the pet holder's own region thread; the only
 * scheduling is the expiry on the player's entity scheduler. Level writes mutate the stack's PDC in place —
 * the CALLER owns the slot write-back (the trak rule).
 */
public final class PetService {

    private final ContentHolder content;
    private final PetCodec codec;
    private final TriggerDispatch dispatch;
    private final TexturedHeads heads;
    private final HeadEquip headEquip; // strips client-side helmet wearability at mint (ADR-0052 pets, 1.8.4)
    private final VanillaEnchants vanilla; // glint lives at the feature layer (the use-item precedent)
    private final PetMessenger messenger;
    private final PetArmedStore armed;
    private final Supplier<MasterConfig.PetsSection> pets;
    private final Supplier<PetItemConfig> likeness;
    private final Supplier<PetFoodConfig> food;
    private final Consumer<Player> refresh; // the EquipListener.refresh seam — worn state + drivers re-derive
    private final LongSupplier nowTicks;
    private final Predicate<Material> isAir; // era-correct block-air test (ActorProbe seam) for the cage pre-check
    private final PetLevelCue cue;
    private final Random rolls; // injected (never ThreadLocalRandom) so suites can stub the use-XP roll

    public PetService(ContentHolder content, PetCodec codec, TriggerDispatch dispatch, TexturedHeads heads,
                      HeadEquip headEquip, VanillaEnchants vanilla, PetMessenger messenger, PetArmedStore armed,
                      Supplier<MasterConfig.PetsSection> pets, Supplier<PetItemConfig> likeness,
                      Supplier<PetFoodConfig> food, Consumer<Player> refresh, LongSupplier nowTicks,
                      Predicate<Material> isAir, PetLevelCue cue, Random rolls) {
        this.content = Objects.requireNonNull(content, "content");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.dispatch = Objects.requireNonNull(dispatch, "dispatch");
        this.heads = Objects.requireNonNull(heads, "heads");
        this.headEquip = Objects.requireNonNull(headEquip, "headEquip");
        this.vanilla = Objects.requireNonNull(vanilla, "vanilla");
        this.messenger = Objects.requireNonNull(messenger, "messenger");
        this.armed = Objects.requireNonNull(armed, "armed");
        this.pets = Objects.requireNonNull(pets, "pets");
        this.likeness = Objects.requireNonNull(likeness, "likeness");
        this.food = Objects.requireNonNull(food, "food");
        this.refresh = Objects.requireNonNull(refresh, "refresh");
        this.nowTicks = Objects.requireNonNull(nowTicks, "nowTicks");
        this.isAir = Objects.requireNonNull(isAir, "isAir");
        this.cue = Objects.requireNonNull(cue, "cue");
        this.rolls = Objects.requireNonNull(rolls, "rolls");
    }

    public boolean isPet(ItemStack stack) {
        return codec.isPet(stack);
    }

    /** The def for {@code key} in the live library, or {@code null} (a stale key an old head still carries). */
    public PetDef defOf(String key) {
        return content.library().petDefOf(key);
    }

    /**
     * Mint the pet {@code key} at {@code level}: the textured head when this server supports it (the era
     * seam), else the def's fallback material; identity + level stamped, likeness rendered. {@code null} for
     * an unknown key.
     */
    public ItemStack mint(String key, int level) {
        PetDef def = defOf(key);
        if (def == null) {
            return null;
        }
        ItemStack stack = heads.head(def.head());
        if (stack == null) {
            // No texture (blank head / unsupported server): resolve the def's material token by NAME. The
            // fallback constant must exist on BOTH eras (the Material.CLOCK trap) — PAPER, not PLAYER_HEAD,
            // which is absent on 1.8 (there the era seam above already built a SKULL_ITEM head).
            stack = ItemFactory.buildItem(def.material(), Material.PAPER, null, null);
        }
        headEquip.unwearable(stack); // a pet activates from the HOTBAR, never the helmet slot — deny client-side (1.8.4)
        int clamped = Math.min(Math.max(1, level), pets.get().maxLevel());
        codec.stamp(stack, key, clamped);
        render(stack, def);
        return stack;
    }

    /** Mint one Pet Food with the CURRENT config's +levels baked on (the dust pattern); {@code {AMOUNT}} filled. */
    public ItemStack mintFood() {
        PetFoodConfig cfg = food.get();
        String amount = Integer.toString(cfg.levels());
        ItemStack stack = ItemFactory.buildItem(cfg.material(), Material.GOLDEN_CARROT,
                Tokens.sub(cfg.name(), "AMOUNT", amount),
                Tokens.subLines(cfg.lore(), "AMOUNT", amount));
        codec.stampFood(stack, cfg.levels());
        if (cfg.shiny()) {
            MenuIcons.glow(vanilla, stack); // cosmetic-only; a refusing server just leaves it un-glinted
        }
        return stack;
    }

    /**
     * Re-render {@code stack}'s name/lore from its stored state + the universal likeness — the ONLY pet lore
     * writer (render-from-state, §4.2). Safe on a stale-key head (renders nothing).
     */
    public void render(ItemStack stack) {
        PetDef def = defOf(codec.keyOf(stack));
        if (def != null) {
            render(stack, def);
        }
    }

    private void render(ItemStack stack, PetDef def) {
        PetItemConfig cfg = likeness.get();
        MasterConfig.PetsSection section = pets.get();
        int level = codec.level(stack);
        int exp = codec.exp(stack);
        PetBracket bracket = def.bracketFor(level);
        String time = TimeFormat.hmsFromTicks(bracket == null ? 0 : bracket.cooldownTicks());
        Object[] tokens = {
                "COLOR", def.color(),
                "NAME", def.display(),
                "TIME_FORMATTED", time,
                "LEVEL", Integer.toString(level),
                "MAX_LEVEL", Integer.toString(section.maxLevel()),
                "EXP", Integer.toString(exp),
                "EXP_NEXT", Integer.toString(section.expPerLevel()),
                "EXP_BAR", expBar(level, exp, section),
        };
        List<String> template = PetTokens.colorTolerant(def.active() ? cfg.loreActive() : cfg.lorePassive());
        // Two line-expanding tokens, nested: {DESCRIPTOR} (the flavour header) then {DESCRIPTION} (the
        // ability lines). An un-authored descriptor drops its line; strip what's left of its slot so the
        // lore never opens on a stray blank.
        List<String> lore = new ArrayList<>(Tokens.expandLines(
                Tokens.expandLines(template, "DESCRIPTOR", def.descriptor(), tokens),
                "DESCRIPTION", def.description(), tokens));
        while (!lore.isEmpty() && lore.get(0).isBlank()) {
            lore.remove(0);
        }
        ItemFactory.decorated(stack, Tokens.sub(PetTokens.colorTolerant(cfg.name()), tokens),
                ItemFactory.wrapLore(lore));
    }

    /**
     * The ten-slot exp meter toward the next level (ADR-0052): {@code &a■} per filled tenth, {@code &7_} per
     * empty one, space-separated — the pack's exact styling; the template wraps it in {@code &f[ ... &f]}.
     * Every filled square carries its own trailing space, so a level-capped FULL bar ends
     * {@code …■ &7} — the same right-hand pad every partial/empty bar shows before the closing {@code ]}
     * (max-level formatting matches the padded shape of the other levels). The all-empty bar additionally
     * pads a leading space so its first {@code _} does not hug the {@code [}.
     */
    static String expBar(int level, int exp, MasterConfig.PetsSection cfg) {
        int filled = level >= cfg.maxLevel() ? 10
                : (int) Math.min(10, Math.max(0, (10L * exp) / cfg.expPerLevel()));
        StringBuilder bar = new StringBuilder("&a");
        bar.append("■ ".repeat(filled)); // each square keeps its trailing space — the last one is the right-hand pad
        bar.append("&7");
        if (filled == 0) {
            bar.append(' ');
        }
        bar.append("_ ".repeat(10 - filled));
        return bar.toString();
    }

    /** What one exp credit did to a pet (the caller re-renders slots / refreshes on a bracket change). */
    public record Progress(boolean changed, boolean bracketCrossed) {
        static final Progress NONE = new Progress(false, false);
    }

    /** One exp credit's level roll — pure, so the carry/cap math is unit-tested by hand (ADR-0059). */
    record LevelRoll(int level, int exp) {
    }

    /** Fixed-point units per whole exp for the passive carry: 1000 milli-levels/hour × 60 sweeps/hour. */
    static final long FRAC_UNITS_PER_EXP = 60_000L;

    static LevelRoll rollExp(int level, int exp, int amount, int maxLevel, int expPerLevel) {
        int newLevel = level;
        int newExp = exp + amount;
        while (newExp >= expPerLevel && newLevel < maxLevel) {
            newExp -= expPerLevel;
            newLevel++;
        }
        if (newLevel >= maxLevel) {
            newExp = 0; // the cap is a clean landmark, not a part-filled bar
        }
        return new LevelRoll(newLevel, newExp);
    }

    /** The ACTIVE use-XP roll: uniform in {@code [expPerLevel/8, expPerLevel/5]}, floor division, min 1. */
    static int useExpRoll(Random random, int expPerLevel) {
        int lo = Math.max(1, expPerLevel / 8);
        int hi = Math.max(lo, expPerLevel / 5);
        return Rolls.between(random, lo, hi);
    }

    /**
     * One ONLINE minute of passive accrual in {@link #FRAC_UNITS_PER_EXP} units — exact integer math; the only
     * quantization is the rate rounding once to milli-levels/hour. MUST stay paired with the module's
     * one-minute sweep cadence.
     */
    static long accrueUnitsPerMinute(double levelsPerHour, int expPerLevel) {
        return Math.round(levelsPerHour * 1000.0) * (long) expPerLevel;
    }

    /** Whether a progress write moved a DISPLAYED number — the level or a bar tenth — else render is skipped. */
    static boolean displayedChanged(int oldLevel, int newLevel, int oldExp, int newExp, int expPerLevel) {
        return newLevel != oldLevel || (10L * oldExp) / expPerLevel != (10L * newExp) / expPerLevel;
    }

    /**
     * Credit {@code amount} pet exp to {@code stack}, rolling levels at the universal exp-per-level up to the
     * max (exp parks at the cap). Mutates PDC in place; re-renders name+lore only when a displayed number
     * changed; plays the level-up cue at {@code owner} on a level gain. The caller writes the stack back to
     * its slot and requests a worn refresh when the bracket crossed.
     */
    public Progress gainExp(Player owner, ItemStack stack, int amount) {
        if (amount <= 0) {
            return Progress.NONE;
        }
        PetDef def = defOf(codec.keyOf(stack));
        if (def == null) {
            return Progress.NONE;
        }
        MasterConfig.PetsSection cfg = pets.get();
        int level = codec.level(stack);
        int exp = codec.exp(stack);
        if (level >= cfg.maxLevel()) {
            return Progress.NONE; // capped: exp no longer accrues
        }
        LevelRoll roll = rollExp(level, exp, amount, cfg.maxLevel(), cfg.expPerLevel());
        return commitProgress(owner, stack, def, level, exp, roll.level(), roll.exp());
    }

    /** Add {@code levels} whole levels (Pet Food), clamped to the max; exp is preserved below the cap. */
    public Progress addLevels(Player owner, ItemStack stack, int levels) {
        if (levels <= 0) {
            return Progress.NONE;
        }
        PetDef def = defOf(codec.keyOf(stack));
        if (def == null) {
            return Progress.NONE;
        }
        MasterConfig.PetsSection cfg = pets.get();
        int level = codec.level(stack);
        int exp = codec.exp(stack);
        int newLevel = Math.min(cfg.maxLevel(), level + levels);
        int newExp = newLevel >= cfg.maxLevel() ? 0 : exp;
        return commitProgress(owner, stack, def, level, exp, newLevel, newExp);
    }

    /**
     * One ONLINE minute of passive inventory accrual (ADR-0059) — called by the sweep for every pet in the
     * main inventory: the base rate anywhere, the hotbar rate for a PASSIVE-type pet when {@code hotbar}. The
     * fractional carry rides the item ({@code petexpfrac}) so accrual survives moves; a capped pet is parked
     * (no accrual, no PDC churn). {@code Progress.changed} also covers a carry-only write — the caller still
     * owns the slot write-back.
     */
    public Progress accruePassive(Player owner, ItemStack stack, boolean hotbar) {
        PetDef def = defOf(codec.keyOf(stack));
        if (def == null) {
            return Progress.NONE;
        }
        MasterConfig.PetsSection cfg = pets.get();
        if (codec.level(stack) >= cfg.maxLevel()) {
            return Progress.NONE; // parked at the cap
        }
        double rate = !def.active() && hotbar ? cfg.passiveHotbarLevelsPerHour() : cfg.passiveLevelsPerHour();
        int before = codec.expFrac(stack);
        long units = before + accrueUnitsPerMinute(rate, cfg.expPerLevel());
        int whole = (int) Math.min(Integer.MAX_VALUE, units / FRAC_UNITS_PER_EXP);
        int frac = (int) (units % FRAC_UNITS_PER_EXP);
        if (frac != before) {
            codec.writeExpFrac(stack, frac);
        }
        Progress leveled = whole > 0 ? gainExp(owner, stack, whole) : Progress.NONE;
        if (leveled.changed() && atMaxLevel(stack)) {
            codec.writeExpFrac(stack, 0); // park clean — matches the exp zeroing at the cap
        }
        if (leveled.changed()) {
            return leveled;
        }
        return frac != before ? new Progress(true, false) : Progress.NONE;
    }

    private Progress commitProgress(Player owner, ItemStack stack, PetDef def, int oldLevel, int oldExp,
                                    int newLevel, int newExp) {
        if (newLevel == oldLevel && newExp == oldExp) {
            return Progress.NONE;
        }
        codec.writeProgress(stack, newLevel, newExp);
        if (displayedChanged(oldLevel, newLevel, oldExp, newExp, pets.get().expPerLevel())) {
            render(stack, def); // the name carries {LEVEL}, the lore the bar — silent tenths skip the recompose
        }
        if (newLevel > oldLevel) {
            cue.play(owner); // once per gain event, however many levels it rolled (ADR-0059)
        }
        boolean crossed = def.bracketFor(oldLevel) != def.bracketFor(newLevel);
        return new Progress(true, crossed);
    }

    /** Whether the pet on {@code stack} is already at the universal max level (the food check-before-consume). */
    public boolean atMaxLevel(ItemStack stack) {
        return codec.level(stack) >= pets.get().maxLevel();
    }

    /**
     * Run an ACTIVE pet's right-click: gate on permission, fire the live bracket's USE abilities through the
     * shared pipeline, render the universal outcome, and on activation open the armed window (worn refresh
     * now; scheduled expiry → clear + ENDED + refresh). A PASSIVE pet right-click is a quiet no-op (its
     * abilities are already live from the hotbar).
     */
    public void use(Player player, ItemStack stack) {
        PetDef def = defOf(codec.keyOf(stack));
        if (def == null || !def.active()) {
            return;
        }
        if (!def.permission().isEmpty() && !player.hasPermission(def.permission())) {
            messenger.failed(player, def);
            return;
        }
        PetBracket bracket = def.bracketFor(codec.level(stack));
        if (bracket == null || bracket.useStableKeys().isEmpty()) {
            messenger.failed(player, def);
            return;
        }
        if (cageWouldFail(player, bracket)) {
            messenger.failed(player, def); // provably-unsafe cage volume — fail BEFORE the cooldown arms
            return;
        }
        UseAttempt attempt = dispatch.fireUse(player, bracket.useStableKeys());
        if (attempt.activated()) {
            messenger.activated(player, def);
            creditUseExp(player, stack);
            openWindow(player, def, bracket);
            return;
        }
        if (attempt.onCooldown()) {
            messenger.onCooldown(player, def, attempt.cooldownRemainingTicks());
            return;
        }
        if (attempt.chanceFailed()) {
            return; // the roll just did not land — silent (the use-item convention)
        }
        messenger.failed(player, def); // condition failed / blocked
    }

    /**
     * Success-side use-XP (ADR-0059): fires only after {@code fireUse} activated — the full gate sequence
     * (incl. the gate-6 cooldown) passed and effects ran. A stacked head is skipped (crediting would level
     * every copy); the held slot is written back here because the listener handed us a copy (A20).
     */
    private void creditUseExp(Player player, ItemStack stack) {
        if (stack.getAmount() > 1) {
            return;
        }
        Progress progress = gainExp(player, stack, useExpRoll(rolls, pets.get().expPerLevel()));
        if (progress.changed()) {
            PlayerInventory inventory = player.getInventory();
            inventory.setItem(inventory.getHeldItemSlot(), stack);
            if (progress.bracketCrossed()) {
                refresh.accept(player);
            }
        }
    }

    /**
     * True when this bracket's right-click would build a {@code CAGE} into a volume that is provably NOT clear
     * — the cooldown-saving pre-check (ADR-0052): resolve the CAGE selector's would-be victim on the actor's
     * own region thread and evaluate the SHARED {@link CageGeometry} verdict before {@link TriggerDispatch#fireUse}
     * arms the cooldown. No victim in range is NOT a pre-block (the authored {@code %nearbyenemies%} condition
     * rejects that pre-cooldown at gate 7); an unreadable cross-region volume falls through to the normal fire
     * (a documented Folia degrade — the sink's own safety check still aborts the build, only then the cooldown
     * is spent, the pre-refactor behavior).
     */
    private boolean cageWouldFail(Player player, PetBracket bracket) {
        Snapshot snapshot = content.snapshot();
        for (String key : bracket.useStableKeys()) {
            Ability ability = snapshot.byStableKey(key);
            if (ability == null) {
                continue;
            }
            for (CompiledEffect effect : ability.effects()) {
                if (CageEffect.HEAD.equals(effect.head()) && cageVolumeBlocked(player, effect)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Whether {@code effect}'s would-be cage volume is provably obstructed; an absent radius / unreadable region → not blocked. */
    private boolean cageVolumeBlocked(Player player, CompiledEffect effect) {
        if (!effect.target().args().has("r")) {
            return false; // a non-radius selector: cannot resolve the victim here — let the normal gate walk run
        }
        return Regions.read("PetService.cagePreCheck", () -> {
            Player victim = nearestOtherPlayer(player, effect.target().args().dbl("r"));
            if (victim == null) {
                return false; // no target → the %nearbyenemies% condition rejects it pre-cooldown
            }
            Location actorLoc = player.getLocation();
            Location victimLoc = victim.getLocation();
            World world = victimLoc.getWorld();
            if (world == null || world != actorLoc.getWorld()) {
                return false;
            }
            Location origin = CageGeometry.origin(actorLoc, victimLoc, effect.args().integer("rise"));
            return !CageGeometry.volumeClear(world, origin, effect.args().integer("width"),
                    effect.args().integer("height"), effect.args().integer("depth"), b -> isAir.test(b.getType()));
        }, false); // unreadable region → fall through (fire normally)
    }

    /** The nearest OTHER player within a cube of half-extent {@code radius} of {@code player} — the {@code @NearestPlayer} scan. */
    private static Player nearestOtherPlayer(Player player, double radius) {
        Location center = player.getLocation();
        Player nearest = null;
        double best = Double.MAX_VALUE;
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof Player other) || other.equals(player)) {
                continue;
            }
            double d = other.getLocation().distanceSquared(center);
            if (d < best) {
                best = d;
                nearest = other;
            }
        }
        return nearest;
    }

    private void openWindow(Player player, PetDef def, PetBracket bracket) {
        int duration = bracket.durationTicks();
        if (duration <= 0) {
            return; // instant effects only — no window, no ENDED message
        }
        UUID id = player.getUniqueId();
        long generation = armed.arm(id, def.key(), nowTicks.getAsLong() + duration);
        refresh.accept(player); // the armed abilities join WornState now
        Scheduling.onEntityLater(player, duration, () -> {
            if (armed.clearIfGeneration(id, def.key(), generation)) {
                messenger.ended(player, def);
                refresh.accept(player);
            }
        });
    }

    /** Death/quit teardown for one player's windows (buffs never survive either). */
    public void dropWindows(UUID player) {
        armed.clear(player);
    }
}
