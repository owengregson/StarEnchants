package feature.combat;

import engine.stores.HeadTrophyStore;
import feature.compat.Hands;
import item.head.TexturedHeads;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import platform.text.Colors;
import platform.text.Tokens;

/**
 * Spends a {@code HEAD_TROPHY} arm: the marked player's next death adds a skull of themselves to the drops.
 * MONITOR, so the keep-inventory decision (KEEP_ON_DEATH at NORMAL, the scroll saves at HIGH) is already
 * final — a head added earlier would be swept away by {@code getDrops().clear()} or duplicated by a late
 * keep flip.
 */
public final class HeadTrophyListener implements Listener {

    /** Every lore token that has no answer without a killer — which is why a killer-less head ships bare. */
    private static final String NO_ITEM = "Fists";

    private final HeadTrophyStore trophies;
    private final TexturedHeads heads;
    private final Hands hands;

    public HeadTrophyListener(HeadTrophyStore trophies, TexturedHeads heads, Hands hands) {
        this.trophies = trophies;
        this.heads = heads;
        this.hands = hands;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    @SuppressWarnings("deprecation") // set/getDisplayName + setLore(List): the floor-stable item-meta path
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        if (event.getKeepInventory()) {
            // Nothing drops at all here, so spending the arm would burn the trophy on a death nobody collects.
            return;
        }
        HeadTrophyStore.Trophy trophy = trophies.consume(victim.getUniqueId());
        if (trophy == null) {
            return;
        }
        ItemStack head = heads.playerHead(victim.getUniqueId(), victim.getName());
        if (head == null) {
            return; // era cannot mint an owned skull — drop nothing rather than a wrong-skinned head
        }
        Player killer = victim.getKiller();
        ItemMeta meta = head.getItemMeta();
        if (meta != null) {
            String name = fill(trophy.name(), victim.getName(), killer, hands);
            if (!name.isEmpty()) {
                meta.setDisplayName(Colors.translate(name));
            }
            // A killer-less death (fall, mob, void) leaves every lore token empty, so the bare head ships.
            if (killer != null && !trophy.lore().isEmpty()) {
                List<String> lore = new ArrayList<>();
                for (String line : trophy.lore().split("\\|", -1)) {
                    lore.add(Colors.translate(fill(line, victim.getName(), killer, hands)));
                }
                meta.setLore(lore);
            }
            head.setItemMeta(meta);
        }
        event.getDrops().add(head);
    }

    /**
     * Resolve the brace tokens against THIS death. The coordinates and the weapon are the KILLER's, which is
     * the measured contract: the head records where the kill was landed from, not where the body fell.
     */
    static String fill(String template, String victim, Player killer, Hands hands) {
        if (template == null || template.indexOf('{') < 0) {
            return template == null ? "" : template;
        }
        LocalDate today = LocalDate.now();
        Location at = killer == null ? null : killer.getLocation();
        return Tokens.sub(template,
                "VICTIM", victim,
                "KILLER", killer == null ? "" : killer.getName(),
                "MONTH", today.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                "DAY", String.valueOf(today.getDayOfMonth()),
                "YEAR", String.valueOf(today.getYear()),
                "X", at == null ? "" : String.valueOf(at.getBlockX()),
                "Y", at == null ? "" : String.valueOf(at.getBlockY()),
                "Z", at == null ? "" : String.valueOf(at.getBlockZ()),
                "ITEM", weaponLabel(killer, hands));
    }

    /** The killer's held item's display name, else its prettified material name, else {@value #NO_ITEM}. */
    @SuppressWarnings("deprecation") // getDisplayName: the floor-stable item-meta path
    private static String weaponLabel(Player killer, Hands hands) {
        if (killer == null) {
            return NO_ITEM;
        }
        ItemStack held = hands.mainHand(killer);
        if (held == null || held.getType() == org.bukkit.Material.AIR) {
            return NO_ITEM;
        }
        ItemMeta meta = held.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return meta.getDisplayName();
        }
        return prettify(held.getType().name());
    }

    /** {@code DIAMOND_SWORD} → {@code Diamond Sword}. */
    static String prettify(String materialName) {
        StringBuilder out = new StringBuilder(materialName.length());
        for (String word : materialName.toLowerCase(Locale.ROOT).split("_")) {
            if (word.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word, 1, word.length());
        }
        return out.toString();
    }
}
