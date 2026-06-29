package feature.trak;

import compile.load.TraksConfig;
import feature.compat.Hands;
import item.codec.AppliedSlot;
import item.codec.TrakCodec;
import item.codec.TrakCodec.Kind;
import item.mint.ItemFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import platform.item.ItemGroups;
import platform.sched.Scheduling;

/**
 * The trak-gem economy (§I) — three gems (BlockTrak / MobTrak / SoulTrak) that, applied to gear, reveal a
 * per-item lifetime counter. The counters are tracked in the BACKGROUND on every eligible tool/weapon from
 * first use ({@link #trackBlockBreak} / {@link #trackKill}), so an applied gem shows a true lifetime total, not
 * a count that starts at zero on application. Applying a gem adds its own marker to the item's applied-utility
 * set ({@link AppliedSlot}); markers are independent, so several traks (and a scroll) coexist and each renders
 * its own count line, re-stamped on every tracked event.
 *
 * <p><strong>Threading.</strong> {@link #trackBlockBreak} and {@link #applyTo} run on the acting player's own
 * region thread (BlockBreakEvent / InventoryClickEvent fire there). {@link #trackKill} is the exception: a
 * death event fires on the VICTIM's region, but the inventory it mutates belongs to the KILLER (a different
 * entity, a different region for ranged/AoE kills), so it hops to the killer's own scheduler before touching
 * it — the same Folia-correct pattern as {@code SoulService.onKill}.
 */
public final class TrakService {

    private final TrakCodec codec;
    private final AppliedSlot slot;
    private final ItemGroups groups;
    private final Supplier<TraksConfig> config;
    private final item.lang.Messages messages;

    public TrakService(TrakCodec codec, AppliedSlot slot, ItemGroups groups, Supplier<TraksConfig> config) {
        this(codec, slot, groups, config, item.lang.Messages.defaults());
    }

    public TrakService(TrakCodec codec, AppliedSlot slot, ItemGroups groups, Supplier<TraksConfig> config,
                       item.lang.Messages messages) {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.slot = Objects.requireNonNull(slot, "slot");
        this.groups = Objects.requireNonNull(groups, "groups");
        this.config = Objects.requireNonNull(config, "config");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    public boolean isTrakGem(ItemStack stack) {
        return codec.gemKind(stack) != null;
    }

    /** Mint a trak gem of {@code kind} from its configured likeness (the gem's lore shows its applies-to kinds). */
    public ItemStack mint(Kind kind) {
        TraksConfig.Trak cfg = trakFor(kind);
        String kinds = ItemGroups.kindsLabel(cfg.appliesTo());
        List<String> lore = new ArrayList<>(cfg.lore().size());
        for (String line : cfg.lore()) {
            lore.add(line.replace("{KINDS}", kinds));
        }
        // The per-kind material comes from the config token (resolved cross-version by ItemFactory, with a
        // FIRE_CHARGE→FIREBALL legacy degradation); SLIME_BALL is just a floor-stable last resort.
        ItemStack gem = ItemFactory.buildItem(cfg.material(), Material.SLIME_BALL, cfg.name(), lore);
        codec.markGem(gem, kind);
        return gem;
    }

    /**
     * Apply the trak gem {@code gem} onto {@code gear}: refuse (nothing consumed) if it is the wrong kind of
     * item or already carries this trak; else add this trak's marker (it never clashes with another trak or a
     * scroll), stamp the live count line, and consume the gem.
     */
    public TrakResult applyTo(ItemStack gem, ItemStack gear) {
        Kind kind = codec.gemKind(gem);
        if (kind == null) {
            return TrakResult.noop(null); // not a trak gem (defensive)
        }
        if (gear == null || gear.getType() == Material.AIR) {
            return TrakResult.noop(messages.format("trak.apply-target"));
        }
        if (gear.getAmount() > 1) {
            return TrakResult.noop(messages.format("common.single-item"));
        }
        TraksConfig.Trak cfg = trakFor(kind);
        if (!groups.matches(gear.getType(), cfg.appliesTo())) {
            return TrakResult.noop(messages.format("trak.wrong-kind", "KINDS", ItemGroups.kindsLabel(cfg.appliesTo())));
        }
        String slotKind = slotKindOf(kind);
        if (slot.holds(gear, slotKind)) {
            return TrakResult.noop(messages.format("trak.already"));
        }
        slot.occupy(gear, slotKind);
        renderTraks(gear);
        gem.setAmount(gem.getAmount() - 1);
        return TrakResult.committed(messages.format("trak.applied"));
    }

    /** Background block-break tracking: bump the held tool's lifetime count; refresh its line if it shows it. */
    public void trackBlockBreak(Player player) {
        track(player, Kind.BLOCK);
    }

    /** Background fish-catch tracking: bump the held rod's lifetime count. The fish event fires on the
     * player's own region thread, so (unlike kills) this needs no scheduler hop. */
    public void trackFishCatch(Player player) {
        track(player, Kind.FISH);
    }

    /**
     * Background kill tracking: a player victim counts toward SoulTrak, any other toward MobTrak. The death
     * event fires on the victim's region, so this hops to the KILLER's own thread before touching their
     * inventory (Folia cross-region safety; cf. {@code SoulService.onKill}).
     */
    public void trackKill(Player killer, boolean victimIsPlayer) {
        Kind kind = victimIsPlayer ? Kind.SOUL : Kind.MOB;
        Scheduling.onEntity(killer, () -> track(killer, kind));
    }

    private void track(Player player, Kind kind) {
        ItemStack tool = Hands.mainHand(player);
        if (tool == null || tool.getType() == Material.AIR) {
            return;
        }
        if (!groups.matches(tool.getType(), trakFor(kind).appliesTo())) {
            return; // only eligible tools/weapons accumulate, bounding the background writes
        }
        codec.increment(tool, kind);
        if (slot.holds(tool, slotKindOf(kind))) {
            renderTraks(tool); // an applied gem shows the live count (re-stamps every applied trak)
        }
        Hands.setMainHand(player, tool);
    }

    /** Re-stamp every applied trak's count line on {@code item} from state, replacing any prior ones. */
    @SuppressWarnings("deprecation") // getLore/setLore(List): the floor-stable item-meta path
    private void renderTraks(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        TraksConfig cfg = config.get();
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        // Drop every kind's prior count line, then re-stamp the applied ones. Identified by the count-format's
        // VISIBLE prefix (colours stripped), NOT a §-code marker: Bukkit's ItemMeta normalisation drops leading
        // reset codes on the round-trip, so a §r§r-prefixed marker stops matching and the old line would never be
        // removed → it duplicated on every kill. Anything else on the item (enchant/economy lore, a PROTECTED
        // line) is left untouched, so trak lines coexist with the rest.
        lore.removeIf(line -> isCountLine(line, cfg));
        for (Kind kind : Kind.values()) { // BLOCK, MOB, SOUL, FISH — a stable, deterministic line order
            if (slot.holds(item, slotKindOf(kind))) {
                lore.add(ItemFactory.color(trakFor(kind).countFormat().replace("{COUNT}", formatCount(codec.count(item, kind)))));
            }
        }
        meta.setLore(lore.isEmpty() ? null : lore);
        item.setItemMeta(meta);
    }

    /**
     * Whether {@code line} is a trak count line of ANY kind — matched on the count-format's VISIBLE prefix (the
     * text before {@code {COUNT}}, colours stripped), so it survives Bukkit's lore colour-code normalisation and
     * also catches lines a prior (buggy) §-marker render left behind. Shared so the other lore writers (the
     * enchant/protection renderers) can PRESERVE trak lines rather than clobber them.
     */
    public static boolean isCountLine(String line, TraksConfig cfg) {
        if (line == null) {
            return false;
        }
        String visible = ChatColor.stripColor(line);
        return matches(visible, cfg.block()) || matches(visible, cfg.mob())
                || matches(visible, cfg.soul()) || matches(visible, cfg.fish());
    }

    private static boolean matches(String visibleLine, TraksConfig.Trak trak) {
        String fmt = trak.countFormat();
        int idx = fmt.indexOf("{COUNT}");
        if (idx <= 0) {
            return false; // need a non-empty prefix before {COUNT} to identify the line
        }
        String prefix = ChatColor.stripColor(ItemFactory.color(fmt.substring(0, idx)));
        return !prefix.isEmpty() && visibleLine != null && visibleLine.startsWith(prefix);
    }

    private static String formatCount(int count) {
        return String.format(Locale.US, "%,d", count); // comma-grouped for readability (e.g. 1,234)
    }

    private TraksConfig.Trak trakFor(Kind kind) {
        TraksConfig cfg = config.get();
        return switch (kind) {
            case BLOCK -> cfg.block();
            case MOB -> cfg.mob();
            case SOUL -> cfg.soul();
            case FISH -> cfg.fish();
        };
    }

    private static String slotKindOf(Kind kind) {
        return switch (kind) {
            case BLOCK -> AppliedSlot.BLOCKTRAK;
            case MOB -> AppliedSlot.MOBTRAK;
            case SOUL -> AppliedSlot.SOULTRAK;
            case FISH -> AppliedSlot.FISHTRAK;
        };
    }
}
