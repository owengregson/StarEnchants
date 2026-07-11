package feature.pet;

import compile.load.ContentHolder;
import compile.load.MasterConfig;
import compile.load.PetBracket;
import compile.load.PetDef;
import compile.load.PetFoodConfig;
import compile.load.PetItemConfig;
import engine.run.UseAttempt;
import feature.menu.MenuIcons;
import feature.trigger.TriggerDispatch;
import item.codec.PetCodec;
import item.head.TexturedHeads;
import item.mint.ItemFactory;
import item.mint.VanillaEnchants;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import platform.sched.Scheduling;
import platform.text.TimeFormat;
import platform.text.Tokens;

/**
 * The pets cold path (ADR-0052): mints pet heads and Pet Food from the universal likeness, renders a pet's
 * name/lore from its stored state (never parsed back), owns the level economy (exp from kills / vanilla XP /
 * held time, +levels from food, both clamped to the universal max), and runs an ACTIVE pet's right-click
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
    private final VanillaEnchants vanilla; // glint lives at the feature layer (the use-item precedent)
    private final PetMessenger messenger;
    private final PetArmedStore armed;
    private final Supplier<MasterConfig.PetsSection> pets;
    private final Supplier<PetItemConfig> likeness;
    private final Supplier<PetFoodConfig> food;
    private final Consumer<Player> refresh; // the EquipListener.refresh seam — worn state + drivers re-derive
    private final LongSupplier nowTicks;

    public PetService(ContentHolder content, PetCodec codec, TriggerDispatch dispatch, TexturedHeads heads,
                      VanillaEnchants vanilla, PetMessenger messenger, PetArmedStore armed,
                      Supplier<MasterConfig.PetsSection> pets, Supplier<PetItemConfig> likeness,
                      Supplier<PetFoodConfig> food, Consumer<Player> refresh, LongSupplier nowTicks) {
        this.content = Objects.requireNonNull(content, "content");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.dispatch = Objects.requireNonNull(dispatch, "dispatch");
        this.heads = Objects.requireNonNull(heads, "heads");
        this.vanilla = Objects.requireNonNull(vanilla, "vanilla");
        this.messenger = Objects.requireNonNull(messenger, "messenger");
        this.armed = Objects.requireNonNull(armed, "armed");
        this.pets = Objects.requireNonNull(pets, "pets");
        this.likeness = Objects.requireNonNull(likeness, "likeness");
        this.food = Objects.requireNonNull(food, "food");
        this.refresh = Objects.requireNonNull(refresh, "refresh");
        this.nowTicks = Objects.requireNonNull(nowTicks, "nowTicks");
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
        };
        List<String> template = def.active() ? cfg.loreActive() : cfg.lorePassive();
        List<String> lore = new ArrayList<>(
                Tokens.expandLines(template, "DESCRIPTION", def.description(), tokens));
        if (!cfg.levelLine().isBlank()) {
            lore.add(Tokens.sub(cfg.levelLine(), tokens));
        }
        ItemFactory.decorated(stack, Tokens.sub(cfg.name(), tokens), ItemFactory.wrapLore(lore));
    }

    /** What one exp credit did to a pet (the caller re-renders slots / refreshes on a bracket change). */
    public record Progress(boolean changed, boolean bracketCrossed) {
        static final Progress NONE = new Progress(false, false);
    }

    /**
     * Credit {@code amount} pet exp to {@code stack}, rolling levels at the universal exp-per-level up to the
     * max (exp parks at the cap). Mutates PDC in place and re-renders on any change — the caller writes the
     * stack back to its slot and requests a worn refresh when the bracket crossed.
     */
    public Progress gainExp(ItemStack stack, int amount) {
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
        int newLevel = level;
        int newExp = exp + amount;
        while (newExp >= cfg.expPerLevel() && newLevel < cfg.maxLevel()) {
            newExp -= cfg.expPerLevel();
            newLevel++;
        }
        if (newLevel >= cfg.maxLevel()) {
            newExp = 0; // the cap is a clean landmark, not a part-filled bar
        }
        return commitProgress(stack, def, level, newLevel, newExp);
    }

    /** Add {@code levels} whole levels (Pet Food), clamped to the max; exp is preserved below the cap. */
    public Progress addLevels(ItemStack stack, int levels) {
        if (levels <= 0) {
            return Progress.NONE;
        }
        PetDef def = defOf(codec.keyOf(stack));
        if (def == null) {
            return Progress.NONE;
        }
        MasterConfig.PetsSection cfg = pets.get();
        int level = codec.level(stack);
        int newLevel = Math.min(cfg.maxLevel(), level + levels);
        int newExp = newLevel >= cfg.maxLevel() ? 0 : codec.exp(stack);
        return commitProgress(stack, def, level, newLevel, newExp);
    }

    private Progress commitProgress(ItemStack stack, PetDef def, int oldLevel, int newLevel, int newExp) {
        boolean changed = newLevel != oldLevel || newExp != codec.exp(stack);
        if (!changed) {
            return Progress.NONE;
        }
        codec.writeProgress(stack, newLevel, newExp);
        render(stack, def);
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
        UseAttempt attempt = dispatch.fireUse(player, bracket.useStableKeys());
        if (attempt.activated()) {
            messenger.activated(player, def);
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

    private void openWindow(Player player, PetDef def, PetBracket bracket) {
        int duration = bracket.durationTicks();
        if (duration <= 0) {
            return; // instant effects only — no window, no ENDED message
        }
        java.util.UUID id = player.getUniqueId();
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
    public void dropWindows(java.util.UUID player) {
        armed.clear(player);
    }
}
