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
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import platform.item.ItemGroups;

/**
 * The trak-gem economy (§I) — three gems (BlockTrak / MobTrak / SoulTrak) that, applied to gear, reveal a
 * per-item lifetime counter. The counters are tracked in the BACKGROUND on every eligible tool/weapon from
 * first use ({@link #trackBlockBreak} / {@link #trackKill}), so an applied gem shows a true lifetime total, not
 * a count that starts at zero on application. Applying a gem takes the item's exclusive applied-utility slot
 * ({@link AppliedSlot}); the count line then re-renders on every tracked event.
 *
 * <p>All methods that read/write a held item MUST run on the holder's own region thread — the callers
 * ({@link TrakListener}) are inventory/block/death events, which already fire there (Folia-correct).
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
     * item, already carries this trak, or its applied-slot is taken; else occupy the slot, stamp the live count
     * line, and consume the gem.
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
        if (!slot.canApply(gear, slotKind)) {
            return TrakResult.noop(messages.format("trak.occupied"));
        }
        slot.occupy(gear, slotKind);
        renderCount(gear, kind);
        gem.setAmount(gem.getAmount() - 1);
        return TrakResult.committed(messages.format("trak.applied"));
    }

    /** Background block-break tracking: bump the held tool's lifetime count; refresh its line if it shows it. */
    public void trackBlockBreak(Player player) {
        track(player, Kind.BLOCK);
    }

    /** Background kill tracking: a player victim counts toward SoulTrak, any other toward MobTrak. */
    public void trackKill(Player killer, boolean victimIsPlayer) {
        track(killer, victimIsPlayer ? Kind.SOUL : Kind.MOB);
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
            renderCount(tool, kind); // an applied gem shows the live count
        }
        Hands.setMainHand(player, tool);
    }

    /** Stamp/refresh the count line on {@code item} for {@code kind}, replacing any prior one (render from state). */
    @SuppressWarnings("deprecation") // getLore/setLore(List): the floor-stable item-meta path
    private void renderCount(ItemStack item, Kind kind) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        TraksConfig.Trak cfg = trakFor(kind);
        String prefix = ItemFactory.color(prefixOf(cfg.countFormat()));
        String line = ItemFactory.color(cfg.countFormat().replace("{COUNT}", formatCount(codec.count(item, kind))));
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        if (!prefix.isEmpty()) {
            lore.removeIf(existing -> existing.startsWith(prefix)); // drop the stale count line
        }
        lore.add(line);
        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    /** The literal text of {@code format} before {@code {COUNT}} (used to locate a prior count line). */
    private static String prefixOf(String format) {
        int idx = format.indexOf("{COUNT}");
        return idx < 0 ? format : format.substring(0, idx);
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
        };
    }

    private static String slotKindOf(Kind kind) {
        return switch (kind) {
            case BLOCK -> AppliedSlot.BLOCKTRAK;
            case MOB -> AppliedSlot.MOBTRAK;
            case SOUL -> AppliedSlot.SOULTRAK;
        };
    }
}
