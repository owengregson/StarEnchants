package item.head;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * The modern (1.17.1 → 26.1.x) {@link TexturedHeads}: Paper's API-level profile surface —
 * {@code Bukkit.createProfile} + a {@code textures} {@link ProfileProperty} +
 * {@link SkullMeta#setPlayerProfile} — deliberately NOT the NMS/CraftMetaSkull field route, so the 1.20.5
 * mapping flip never touches it. The profile is never network-completed (the base64 property is the skin);
 * each head gets a texture-derived stable UUID so clients cache one skin per texture, never a wrong reuse.
 */
public final class ModernTexturedHeads implements TexturedHeads {

    @Override
    public ItemStack head(String base64) {
        if (base64 == null || base64.isBlank()) {
            return null;
        }
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        if (!(stack.getItemMeta() instanceof SkullMeta meta)) {
            return null;
        }
        // A stable, texture-derived UUID (name null): two mints of one pet share a profile identity, and no
        // real player's cached skin can collide with it.
        PlayerProfile profile = Bukkit.createProfile(UUID.nameUUIDFromBytes(base64.getBytes()), null);
        profile.setProperty(new ProfileProperty("textures", base64));
        meta.setPlayerProfile(profile);
        stack.setItemMeta(meta);
        return stack;
    }
}
