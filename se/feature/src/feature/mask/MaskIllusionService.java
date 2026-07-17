package feature.mask;

import compile.load.Library;
import compile.load.MaskDef;
import item.head.EquipmentRepaint;
import item.head.HeadAttributes;
import item.head.IllusionMark;
import item.head.TexturedHeads;
import item.view.ItemViewCache;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import platform.caps.Regions;
import platform.sched.Scheduling;

/**
 * The worn-mask illusion (ADR-0053 §5): snapshot-out, recipient-thread-in. {@link #refresh} — the ONLY
 * re-derive, on the wearer's own thread — reads the worn helmet's mask key, builds/caches the shown head via
 * {@link TexturedHeads}, stores it and broadcasts; a mask gone from the helmet broadcasts the TRUE helmet once
 * (the restore). {@link #sweep} and {@link #pushTo} are dumb pushes of stored state (vanilla re-broadcasts true
 * equipment whenever an observer starts tracking a wearer; the 40t sweep bounds that flash). Every actual send
 * hops to the recipient's region thread — the {@link EquipmentRepaint} contract.
 */
public final class MaskIllusionService {

    private final EquipmentRepaint repaint;
    private final TexturedHeads heads;
    private final HeadAttributes headAttributes;
    private final IllusionMark mark;
    private final Supplier<Library> library;
    private final ItemViewCache itemViews;
    private final BooleanSupplier enabled;
    private final MaskIllusionStore store;
    // Built heads keyed by base64 (texture-derived, so reload-stable); cloned per wearer refresh.
    private final ConcurrentHashMap<String, ItemStack> templates = new ConcurrentHashMap<>();

    public MaskIllusionService(EquipmentRepaint repaint, TexturedHeads heads, HeadAttributes headAttributes,
                               IllusionMark mark, Supplier<Library> library, ItemViewCache itemViews,
                               BooleanSupplier enabled, MaskIllusionStore store) {
        this.repaint = Objects.requireNonNull(repaint, "repaint");
        this.heads = Objects.requireNonNull(heads, "heads");
        this.headAttributes = Objects.requireNonNull(headAttributes, "headAttributes");
        this.mark = Objects.requireNonNull(mark, "mark");
        this.library = Objects.requireNonNull(library, "library");
        this.itemViews = Objects.requireNonNull(itemViews, "itemViews");
        this.enabled = Objects.requireNonNull(enabled, "enabled");
        this.store = Objects.requireNonNull(store, "store");
    }

    /**
     * Re-derive {@code wearer}'s illusion from their worn helmet and broadcast it — or, when the mask is gone
     * but an illusion was stored, broadcast the TRUE helmet once (the restore). MUST run on the wearer's own
     * region thread: it reads their live inventory and world.
     */
    public void refresh(Player wearer) {
        repairWorn(wearer); // ADR-0064: a leaked illusion head in the REAL slot is undressed before anything derives
        // Disabled resolves as unmasked, so a live toggle-off restores reality through this same spine.
        ItemStack shown = enabled.getAsBoolean() ? shownHeadFor(wearer) : null;
        UUID id = wearer.getUniqueId();
        if (shown != null) {
            store.put(id, shown);
            broadcast(wearer, shown);
        } else if (store.remove(id)) {
            broadcast(wearer, realHelmet(wearer));
        }
    }

    /**
     * ADR-0064: if the REAL helmet slot holds a marked illusion head (a client write-back that slipped a
     * gate), restore the helmet it was dressed from. A payload-less mark clears the slot — the visual was
     * never a real item. MUST run on the wearer's own region thread (inventory write).
     */
    public void repairWorn(Player wearer) {
        ItemStack helmet = wearer.getInventory().getHelmet();
        if (helmet == null || !mark.isMarked(helmet)) {
            return;
        }
        wearer.getInventory().setHelmet(mark.undress(helmet));
    }

    /** Re-assert every stored illusion to one just-joined {@code recipient}; each send hops to its thread. */
    public void pushTo(Player recipient) {
        if (!enabled.getAsBoolean()) {
            return;
        }
        for (UUID id : store.wearers()) {
            if (id.equals(recipient.getUniqueId())) {
                continue; // the joiner's own illusion is refresh()'s job
            }
            Player wearer = Bukkit.getPlayer(id);
            ItemStack shown = wearer == null ? null : store.get(id);
            if (shown == null) {
                continue;
            }
            // No world filter: the wearer's world is not readable from here, and a packet about an entity the
            // recipient's client doesn't track is client-ignored anyway.
            Scheduling.onEntity(recipient, () -> repaint.helmet(recipient, wearer, shown));
        }
    }

    /**
     * Re-assert every stored illusion to the wearer's world (and the wearer itself, the F5 self-view). Dumb by
     * design: pushes stored state only, never re-derives — that is {@link #refresh}'s job on the wearer's
     * thread. MUST run on the global thread, where enumerating online players is safe.
     */
    public void sweep() {
        if (!enabled.getAsBoolean()) {
            return;
        }
        for (UUID id : store.wearers()) {
            Player wearer = Bukkit.getPlayer(id);
            ItemStack shown = wearer == null ? null : store.get(id);
            if (shown == null) {
                continue;
            }
            // The wearer's world is a cross-region read from the global thread; unreadable → null → send to
            // everyone (an equipment packet about an untracked entity id is client-ignored — over-sending is
            // harmless). Never wearer.getLocation() here.
            World wearerWorld = Regions.read("mask.sweep wearer world", wearer::getWorld, null);
            sendToWorld(wearer, wearerWorld, shown);
        }
    }

    private void broadcast(Player wearer, ItemStack shown) {
        sendToWorld(wearer, wearer.getWorld(), shown); // own-thread read (the refresh contract)
    }

    /** Repaint {@code wearer} to every online player in {@code world} ({@code null} = all), wearer included. */
    private void sendToWorld(Player wearer, World world, ItemStack shown) {
        for (Player recipient : Bukkit.getOnlinePlayers()) {
            // The recipient's world/gamemode are only readable on its own thread — filter inside the hop.
            Scheduling.onEntity(recipient, () -> {
                if (world != null && !world.equals(recipient.getWorld())) {
                    return;
                }
                // ADR-0064: a CREATIVE client is inventory-authoritative — a self-view illusion is adopted into
                // its local inventory and echoed back over the REAL helmet (the bake). The creative wearer is
                // always shown reality; observers still see the mask.
                if (recipient.getUniqueId().equals(wearer.getUniqueId())
                        && recipient.getGameMode() == GameMode.CREATIVE) {
                    repaint.helmet(recipient, wearer, realHelmet(wearer));
                    return;
                }
                repaint.helmet(recipient, wearer, shown);
            });
        }
    }

    /** The pre-built mask head for the worn helmet, or {@code null} when this wearer shows reality. */
    private ItemStack shownHeadFor(Player wearer) {
        ItemStack helmet = wearer.getInventory().getHelmet();
        if (helmet == null) {
            return null;
        }
        String maskKey = itemViews.of(helmet).combat().maskKey();
        if (maskKey == null) {
            return null;
        }
        MaskDef def = library.get().maskDefOf(maskKey);
        if (def == null || def.head() == null || def.head().isBlank()) {
            return null; // key stale after a reload, or an untextured mask — no illusion (ADR-0053 §4)
        }
        ItemStack template = template(def.head());
        return template == null ? null : dress(template.clone(), helmet);
    }

    /**
     * Dress the shown head as the helmet it visually replaces (1.8.1): the client renders the ARMOR SLOT from
     * the repaint packet, so a bare head there hovers as a nameless, lore-less skull. Copy the helmet's
     * display name (or its friendly material name — an unnamed helmet must not hover as "Player Head"), lore,
     * enchants (tooltip lines + glint) and flags — all floor+1.8-safe meta — then the worn-attribute block
     * ("When on Head: …", heroic included) and the durability mirror through the {@link HeadAttributes} era
     * seam. Durability stays live: Paper's armour-change event fires on any component diff (damage included),
     * so every durability tick re-derives this dress through the equip-refresh seam.
     */
    @SuppressWarnings("deprecation") // get/set DisplayName/Lore: the floor-stable item-meta path (the LoreRenderer rule)
    private ItemStack dress(ItemStack shown, ItemStack helmet) {
        ItemMeta shownMeta = shown.getItemMeta();
        ItemMeta helmetMeta = helmet.getItemMeta();
        if (shownMeta != null) {
            if (helmetMeta != null && helmetMeta.hasDisplayName()) {
                shownMeta.setDisplayName(helmetMeta.getDisplayName());
            } else {
                shownMeta.setDisplayName(ChatColor.WHITE + friendlyName(helmet.getType()));
            }
            if (helmetMeta != null) {
                if (helmetMeta.hasLore()) {
                    shownMeta.setLore(helmetMeta.getLore());
                }
                for (Map.Entry<Enchantment, Integer> enchant : helmetMeta.getEnchants().entrySet()) {
                    shownMeta.addEnchant(enchant.getKey(), enchant.getValue(), true);
                }
                shownMeta.addItemFlags(helmetMeta.getItemFlags().toArray(new ItemFlag[0]));
            }
            shown.setItemMeta(shownMeta);
        }
        headAttributes.copyWorn(shown, helmet);
        mark.stamp(shown, helmet); // ADR-0064: detectable + losslessly undressable if a client ever writes it back
        return shown;
    }

    /** "DIAMOND_HELMET" → "Diamond Helmet" — the unnamed-helmet hover fallback (no server-side localization). */
    static String friendlyName(Material material) {
        String[] words = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    /** The TRUE helmet for the restore broadcast — AIR when bare ({@code sendEquipmentChange} rejects null;
     *  AIR crosses both eras as the empty slot). */
    private static ItemStack realHelmet(Player wearer) {
        ItemStack helmet = wearer.getInventory().getHelmet();
        return helmet == null ? new ItemStack(Material.AIR) : helmet.clone();
    }

    /** The built head template for {@code base64}, cached (the ItemViewCache putIfAbsent shape). */
    ItemStack template(String base64) {
        ItemStack cached = templates.get(base64);
        if (cached != null) {
            return cached;
        }
        ItemStack built = heads.head(base64);
        if (built == null) {
            return null; // untexturable server — no repaint; a miss is never cached (ADR-0053 §4)
        }
        ItemStack raced = templates.putIfAbsent(base64, built);
        return raced != null ? raced : built;
    }
}
