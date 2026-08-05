package feature.pet;

import compile.load.ContentHolder;
import compile.load.MasterConfig;
import compile.load.PetBracket;
import compile.load.PetCurve;
import compile.load.PetDef;
import compile.load.PetFoodConfig;
import compile.load.PetItemConfig;
import compile.model.Ability;
import compile.model.CompiledEffect;
import compile.model.Snapshot;
import engine.effect.kind.CageEffect;
import engine.effect.kind.DigHomeEffect;
import engine.run.UseAttempt;
import engine.selector.kind.Allies;
import engine.sink.CageGeometry;
import engine.stores.TeleblockStore;
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
 * pet's own cap under the universal max, level-up cue on every gain), and runs an ACTIVE pet's right-click
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

    /** ADR-0070 rider: the shared any-pet gate a successful activation arms (2s). */
    private static final long SHARED_USE_GATE_TICKS = 40L;

    private final ContentHolder content;
    private final PetCodec codec;
    private final TriggerDispatch dispatch;
    private final TexturedHeads heads;
    private final HeadEquip headEquip; // strips client-side helmet wearability at mint (ADR-0052 pets, 1.8.4)
    private final VanillaEnchants vanilla; // glint lives at the feature layer (the use-item precedent)
    private final PetMessenger messenger;
    private final PetArmedStore armed;
    private final PetSharedUseStore sharedGate; // ADR-0070: the shared any-pet 2s gate
    private final Supplier<MasterConfig.PetsSection> pets;
    private final Supplier<PetItemConfig> likeness;
    private final Supplier<PetFoodConfig> food;
    private final Consumer<Player> refresh; // the EquipListener.refresh seam — worn state + drivers re-derive
    private final LongSupplier nowTicks;
    private final Predicate<Material> isAir; // era-correct block-air test (ActorProbe seam) for the cage pre-check
    private final PetLevelCue cue;
    private final Random rolls; // injected (never ThreadLocalRandom) so suites can stub the use-XP roll
    private final PetHomeStore homes;       // ADR-0061: the Mole dig-home windows (same-package store)
    private final TeleblockStore teleblock; // ADR-0061: the pack-wide teleport counter, read at recall
    private final PetHomeVisuals visuals;   // ADR-0061 amendment: the window-tied pulse + recall cues

    public PetService(ContentHolder content, PetCodec codec, TriggerDispatch dispatch, TexturedHeads heads,
                      HeadEquip headEquip, VanillaEnchants vanilla, PetMessenger messenger, PetArmedStore armed,
                      PetSharedUseStore sharedGate, Supplier<MasterConfig.PetsSection> pets,
                      Supplier<PetItemConfig> likeness, Supplier<PetFoodConfig> food, Consumer<Player> refresh,
                      LongSupplier nowTicks, Predicate<Material> isAir, PetLevelCue cue, Random rolls,
                      PetHomeStore homes, TeleblockStore teleblock, PetHomeVisuals visuals) {
        this.content = Objects.requireNonNull(content, "content");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.dispatch = Objects.requireNonNull(dispatch, "dispatch");
        this.heads = Objects.requireNonNull(heads, "heads");
        this.headEquip = Objects.requireNonNull(headEquip, "headEquip");
        this.vanilla = Objects.requireNonNull(vanilla, "vanilla");
        this.messenger = Objects.requireNonNull(messenger, "messenger");
        this.armed = Objects.requireNonNull(armed, "armed");
        this.sharedGate = Objects.requireNonNull(sharedGate, "sharedGate");
        this.pets = Objects.requireNonNull(pets, "pets");
        this.likeness = Objects.requireNonNull(likeness, "likeness");
        this.food = Objects.requireNonNull(food, "food");
        this.refresh = Objects.requireNonNull(refresh, "refresh");
        this.nowTicks = Objects.requireNonNull(nowTicks, "nowTicks");
        this.isAir = Objects.requireNonNull(isAir, "isAir");
        this.cue = Objects.requireNonNull(cue, "cue");
        this.rolls = Objects.requireNonNull(rolls, "rolls");
        this.homes = Objects.requireNonNull(homes, "homes");
        this.teleblock = Objects.requireNonNull(teleblock, "teleblock");
        this.visuals = Objects.requireNonNull(visuals, "visuals");
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
            // which is absent on 1.8. A blank blob never reaches the era seam, so the default PLAYER_HEAD
            // token is what has to land there: ItemFactory degrades it to SKULL_ITEM (data 0, so a skeleton
            // skull rather than the seam's SKULL_ITEM:3) and PAPER stays the last resort.
            stack = ItemFactory.buildItem(def.material(), Material.PAPER, null, null);
        }
        headEquip.unwearable(stack); // a pet activates from the HOTBAR, never the helmet slot — deny client-side (1.8.4)
        int clamped = Math.min(Math.max(1, level), def.cappedAt(pets.get().maxLevel()));
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
        int needed = def.expNeededFrom(level, section.expPerLevel());
        int max = def.cappedAt(section.maxLevel());
        Object[] tokens = {
                "COLOR", def.color(),
                "NAME", def.display(),
                "TIME_FORMATTED", time,
                "LEVEL", Integer.toString(level),
                "MAX_LEVEL", Integer.toString(max),
                "EXP", Integer.toString(exp),
                "EXP_NEXT", Integer.toString(needed),
                "EXP_BAR", expBar(level, exp, needed, max),
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
        return expBar(level, exp, cfg.expPerLevel(), cfg.maxLevel());
    }

    /** As above over an explicit per-level threshold + cap, so a per-pet curve renders its OWN bar. */
    static String expBar(int level, int exp, int needed, int maxLevel) {
        int filled = level >= maxLevel ? 10
                : (int) Math.min(10, Math.max(0, (10L * exp) / Math.max(1, needed)));
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

    /**
     * {@link #rollExp}'s per-pet-curve twin: the same multi-level, park-at-the-cap semantics, but each level
     * costs what the pet's own ladder says rather than one flat rate. Separate from {@link #rollExp} so the
     * universal path — every signature-pack pet, which declares no curve — is byte-identical to what shipped.
     */
    static LevelRoll rollCurve(int level, int exp, int amount, int maxLevel, PetCurve curve) {
        int newLevel = level;
        int newExp = exp + amount;
        while (newLevel < maxLevel && newExp >= curve.neededFrom(newLevel)) {
            newExp -= curve.neededFrom(newLevel);
            newLevel++;
        }
        if (newLevel >= maxLevel) {
            newExp = 0; // the cap is a clean landmark, not a part-filled bar
        }
        return new LevelRoll(newLevel, newExp);
    }

    /**
     * {@code ITEM_XP_TRACK}'s roll — the COSMIC semantics, owner ruling 2026-08-01: <em>at most one level per
     * grant, remainder banked; bank unbounded at the cap</em>. Deliberately NOT {@link #rollExp}, which rolls
     * as many levels as the grant pays for and zeroes exp at the cap. The two coexist, keyed by WHICH PATH
     * GRANTS: a kill / vanilla-XP / food / passive credit keeps the shipped roll, an authored
     * {@code ITEM_XP_TRACK} takes this one.
     */
    static LevelRoll bankExp(int level, int exp, int amount, int maxLevel, int needed) {
        int total = exp + amount;
        if (level >= maxLevel) {
            return new LevelRoll(level, total); // at the cap the bank just keeps growing — no ceiling at all
        }
        if (total >= Math.max(1, needed)) {
            return new LevelRoll(level + 1, total - Math.max(1, needed)); // ONE level, remainder banked
        }
        return new LevelRoll(level, total);
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
        int max = def.cappedAt(cfg.maxLevel());
        int level = codec.level(stack);
        int exp = codec.exp(stack);
        if (level >= max) {
            return Progress.NONE; // capped: exp no longer accrues
        }
        LevelRoll roll = def.expCurve() == null
                ? rollExp(level, exp, amount, max, cfg.expPerLevel())
                : rollCurve(level, exp, amount, max, def.expCurve());
        return commitProgress(owner, stack, def, level, exp, roll.level(), roll.exp());
    }

    /**
     * {@code ITEM_XP_TRACK}: credit {@code amount} to the pet {@code holder} is HOLDING, under the COSMIC
     * semantics ({@link #bankExp}) rather than {@link #gainExp}'s shipped roll. {@code windowMinutes > 0}
     * gates the grant to once per window using a stamp that rides the ITEM, so the gate travels with a traded
     * pet and a freshly minted one earns immediately (it carries no stamp at all).
     *
     * <p>Runs on the holder's own region thread — the caller is the sink's entity hop — and writes the held
     * slot back itself, since a level write that never reaches the inventory is a level nobody keeps.
     * A STACKED pet is skipped for {@link #creditUseExp}'s reason: crediting would level every copy.
     */
    public void grantTrackedExp(Player holder, int amount, int windowMinutes, String gainMessage,
                                String levelUpMessage) {
        if (holder == null || amount <= 0) {
            return;
        }
        PlayerInventory inventory = holder.getInventory();
        ItemStack stack = inventory.getItem(inventory.getHeldItemSlot());
        if (stack == null || stack.getAmount() > 1) {
            return;
        }
        PetDef def = defOf(codec.keyOf(stack));
        if (def == null) {
            return; // not a pet in hand: nothing carries item progression
        }
        int nowMinutes = (int) (System.currentTimeMillis() / 60_000L);
        if (windowMinutes > 0) {
            int last = codec.xpGateMinutes(stack);
            if (last > 0 && nowMinutes - last < windowMinutes) {
                return; // inside the window — the grant is skipped whole, not scaled
            }
        }
        MasterConfig.PetsSection cfg = pets.get();
        int level = codec.level(stack);
        int exp = codec.exp(stack);
        LevelRoll roll = bankExp(level, exp, amount, def.cappedAt(cfg.maxLevel()),
                def.expNeededFrom(level, cfg.expPerLevel()));
        String oldName = displayNameOf(stack, def);
        Progress progress = commitProgress(holder, stack, def, level, exp, roll.level(), roll.exp());
        if (windowMinutes > 0) {
            codec.writeXpGateMinutes(stack, nowMinutes); // stamped on the GRANT, so a skipped one does not slide it
        }
        emitXpLines(holder, def, gainMessage, levelUpMessage, amount, roll, oldName,
                level, cfg.expPerLevel());
        if (progress.changed() || windowMinutes > 0) {
            inventory.setItem(inventory.getHeldItemSlot(), stack);
            if (progress.bracketCrossed()) {
                refresh.accept(holder);
            }
        }
    }

    /** The stack's rendered name — read BEFORE the level write, so the level-up line names the old level. */
    @SuppressWarnings("deprecation") // getDisplayName(): the floor-stable item-meta path
    private static String displayNameOf(ItemStack stack, PetDef def) {
        return stack.getItemMeta() == null ? def.display() : stack.getItemMeta().getDisplayName();
    }

    /** The two authored lines of an {@code ITEM_XP_TRACK} grant; an empty template is silent. */
    private void emitXpLines(Player holder, PetDef def, String gainMessage, String levelUpMessage,
                             int amount, LevelRoll roll, String oldName, int oldLevel, int universalFlat) {
        if (gainMessage != null && !gainMessage.isEmpty()) {
            // {needed} is the requirement AFTER the level-up — the recorded quirk, kept: on the activation
            // that levels you the bar you are told about is the new one.
            messenger.line(holder, Tokens.sub(gainMessage,
                    "xp", amount, "exp", roll.exp(),
                    "needed", def.expNeededFrom(roll.level(), universalFlat)));
        }
        if (roll.level() > oldLevel && levelUpMessage != null && !levelUpMessage.isEmpty()) {
            // {item} is the PRE-rebuild display name, so the line names the pet at the level it just left.
            messenger.line(holder, Tokens.sub(levelUpMessage, "item", oldName, "level", roll.level()));
        }
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
        int max = def.cappedAt(cfg.maxLevel());
        int level = codec.level(stack);
        int exp = codec.exp(stack);
        int newLevel = Math.min(max, level + levels);
        int newExp = newLevel >= max ? 0 : exp;
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
        if (codec.level(stack) >= def.cappedAt(cfg.maxLevel())) {
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
        if (displayedChanged(oldLevel, newLevel, oldExp, newExp,
                def.expNeededFrom(oldLevel, pets.get().expPerLevel()))) {
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
        PetDef def = defOf(codec.keyOf(stack));
        int max = pets.get().maxLevel();
        return codec.level(stack) >= (def == null ? max : def.cappedAt(max));
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
        // ADR-0061: a digger pet's click during a LIVE home window is a RECALL — resolved BEFORE the gate
        // walk, so the cooldown armed at dig never blocks the return trip. Sneaking or not: a re-dig is
        // impossible while the window is open (the cooldown armed at dig), so every click is the recall.
        CompiledEffect dig = digHomeEffect(bracket);
        if (dig != null && recallAttempt(player, def, stack)) {
            return;
        }
        // ADR-0070 rider: the shared any-pet gate — a successful activation of ANY pet arms a 2s window so a
        // hotbar of DIFFERENT pet actives cannot fire as one burst. Only a NON-digger active consults the gate
        // (owner ruling 2026-07-18): a digger's whole cycle is EXEMPT from the CHECK — the recall resolved
        // above, and a fresh dig is that same cycle's outbound leg, never delayed by a gate its own prior
        // dig/recall armed (the pre-gate mole flow). Every successful use still ARMS the gate below.
        if (dig == null) {
            long sharedLeft = sharedGate.remaining(player.getUniqueId(), nowTicks.getAsLong());
            if (sharedLeft > 0) {
                messenger.sharedCooldown(player, sharedLeft);
                return;
            }
        }
        if (cageWouldFail(player, bracket)) {
            messenger.failed(player, def); // provably-unsafe cage volume — fail BEFORE the cooldown arms
            return;
        }
        UseAttempt attempt = dispatch.fireUse(player, bracket.useStableKeys());
        if (attempt.activated()) {
            sharedGate.arm(player.getUniqueId(), nowTicks.getAsLong() + SHARED_USE_GATE_TICKS); // ADR-0070 rider
            messenger.activated(player, def);
            if (dig != null) {
                armHome(player, def, dig); // ADR-0061: the dig is non-XP — use-XP lands on the RECALL
            } else {
                creditUseExp(player, stack);
            }
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
        if (dig != null) {
            messenger.noHome(player, def); // a digger's condition-fail = the plain click with no home dug (ADR-0067)
            return;
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

    /** The bracket's {@code DIG_HOME} effect, or {@code null} when this pet is not a digger (ADR-0061). */
    private CompiledEffect digHomeEffect(PetBracket bracket) {
        Snapshot snapshot = content.snapshot();
        for (String key : bracket.useStableKeys()) {
            Ability ability = snapshot.byStableKey(key);
            if (ability == null) {
                continue;
            }
            for (CompiledEffect effect : ability.effects()) {
                if (DigHomeEffect.HEAD.equals(effect.head())) {
                    return effect;
                }
            }
        }
        return null;
    }

    /**
     * Try the RECALL (ADR-0061). {@code false} = no live window — the click falls through to a fresh dig
     * attempt (the normal gate walk). {@code true} = the click was claimed: either the teleport home landed
     * (window consumed FIRST so a re-click mid-hop cannot double-fire; universal ENDED sent; use-XP granted —
     * the recall, never the dig, is the XP moment) or it was refused — an active teleblock (the pack-wide
     * teleport counter; the universal pet fail line) or out of range / another world (the universal
     * out-of-range line) — with the window KEPT alive for a retry until expiry.
     */
    private boolean recallAttempt(Player player, PetDef def, ItemStack stack) {
        long now = nowTicks.getAsLong();
        PetHomeStore.Home home = homes.get(player.getUniqueId(), now);
        if (home == null) {
            return false;
        }
        if (teleblock.isBlocked(player.getUniqueId(), now)) {
            messenger.failed(player, def);
            return true;
        }
        World world = player.getWorld();
        Location at = player.getLocation();
        if (!home.inRange(world.getUID(), at.getX(), at.getY(), at.getZ())) {
            messenger.outOfRange(player);
            return true;
        }
        homes.clear(player.getUniqueId());
        sharedGate.arm(player.getUniqueId(), now + SHARED_USE_GATE_TICKS); // ADR-0070: a successful recall arms the shared gate
        visuals.clear(player.getUniqueId());
        Location to = new Location(world, home.x(), home.y(), home.z(), home.yaw(), home.pitch());
        visuals.recallCues(player, def, player.getLocation(), to); // cues mark both ends before the async hop
        dispatch.teleport(player, to);
        messenger.ended(player, def);
        creditUseExp(player, stack);
        return true;
    }

    /**
     * Arm the dig-home window (ADR-0061) from the bracket's {@code DIG_HOME} args: capture the digger's spot
     * (primitives + world UID — never a retained {@code Location}), open the window and schedule the
     * generation-guarded expiry on the player's entity scheduler — expired unused, the universal ENDED is sent
     * and the cooldown stays spent (it armed at dig, inside the gate walk).
     */
    private void armHome(Player player, PetDef def, CompiledEffect dig) {
        int window = dig.args().integer("window");
        double range = dig.args().dbl("range");
        Location at = player.getLocation();
        UUID id = player.getUniqueId();
        long generation = homes.arm(id, player.getWorld().getUID(), at.getX(), at.getY(), at.getZ(),
                at.getYaw(), at.getPitch(), range, nowTicks.getAsLong() + window);
        visuals.begin(player, def, generation);
        visuals.digCues(player); // the layered dig cue (ADR-0067) — was the authored SOUND op
        Scheduling.onEntityLater(player, window, () -> {
            if (homes.clearIfGeneration(id, generation)) {
                visuals.endIfGeneration(id, generation);
                visuals.expiredCues(player); // the home collapsed unused (ADR-0067)
                messenger.ended(player, def);
            }
        });
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

    /**
     * The nearest other UNALLIED player within a cube of half-extent {@code radius} of {@code player} — the
     * {@code @NearestPlayer} scan, and it must agree with it: this is the pre-check that decides whether a cage
     * use is refused BEFORE the cooldown arms, so a party-mate the selector would skip must not keep the
     * gesture alive here (R-QC17).
     */
    static Player nearestOtherPlayer(Player player, double radius) { // package-private: unit-tested directly
        Location center = player.getLocation();
        Player nearest = null;
        double best = Double.MAX_VALUE;
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof Player other) || other.equals(player) || Allies.allied(player, other)) {
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

    /** Death/quit teardown for one player's windows (buffs and dig-homes never survive either, ADR-0061). */
    public void dropWindows(UUID player) {
        armed.clear(player);
        homes.clear(player); // the pending expiry task then no-ops via the generation guard — no post-death ENDED
        visuals.clear(player);
    }
}
