package item.head;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.lang.reflect.Field;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * The 1.8.9 {@link TexturedHeads}: a {@code SKULL_ITEM} with data value 3 (the player-skull variant — 1.8
 * has no {@code PLAYER_HEAD} material) whose {@code CraftMetaSkull.profile} field is reflectively set to an
 * authlib {@link GameProfile} carrying the base64 {@code textures} property. authlib is on the 1.8.8 server
 * classpath (the FakePlayers precedent); the private-field write is the only reflection — cached after the
 * first head. Degrades to {@code null} (plain-material fallback) if the field ever moves.
 */
public final class LegacyTexturedHeads implements TexturedHeads {

    private static final Logger LOG = System.getLogger("StarEnchants.TexturedHeads");
    private static final short PLAYER_SKULL_DATA = 3;

    private static volatile Field profileField;

    @Override
    @SuppressWarnings("deprecation") // (Material, int, short): the 1.8 data-value item ctor.
    public ItemStack head(String base64) {
        if (base64 == null || base64.isBlank()) {
            return null;
        }
        ItemStack stack = new ItemStack(Material.SKULL_ITEM, 1, PLAYER_SKULL_DATA);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        // A stable, texture-derived UUID (1.8 requires one; the null name marks it synthetic).
        GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(base64.getBytes()), null);
        profile.getProperties().put("textures", new Property("textures", base64));
        try {
            profileField(meta).set(meta, profile);
        } catch (ReflectiveOperationException moved) {
            LOG.log(Level.WARNING, "could not set the 1.8 CraftMetaSkull profile — pet heads mint untextured",
                    moved);
            return null;
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private static Field profileField(ItemMeta meta) throws NoSuchFieldException {
        Field cached = profileField;
        if (cached != null) {
            return cached;
        }
        Field found = meta.getClass().getDeclaredField("profile"); // CraftMetaSkull.profile (v1_8_R3)
        found.setAccessible(true);
        profileField = found;
        return found;
    }
}
