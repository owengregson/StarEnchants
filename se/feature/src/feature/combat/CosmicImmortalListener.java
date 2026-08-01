package feature.combat;

import engine.sink.SinkEnv;
import engine.sink.SinkFactory;
import engine.sink.SinkReadback;
import feature.soul.SoulService;
import item.view.ItemViewCache;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import platform.resolve.RegistryResolvers;

/** Exact intended implementation of Cosmic Immortal's all-damage durability battery. */
public final class CosmicImmortalListener implements Listener {

    private static final String ENCHANT = "enchants/immortal";
    private static final int OUT_OF_SOULS_THROTTLE_TICKS = 300;

    private record Worn(int slot, int level) {
    }

    private final ItemViewCache views;
    private final SinkFactory sinks;
    private final SinkEnv env;
    private final SoulService souls;
    private final Map<UUID, Long> outOfSoulsUntil = new ConcurrentHashMap<>();
    private final int lava;
    private final int itemBreak;
    private final int levelUp;

    public CosmicImmortalListener(ItemViewCache views, SinkFactory sinks, SinkEnv env,
                                  SoulService souls, RegistryResolvers resolvers) {
        this.views = Objects.requireNonNull(views, "views");
        this.sinks = Objects.requireNonNull(sinks, "sinks");
        this.env = Objects.requireNonNull(env, "env");
        this.souls = Objects.requireNonNull(souls, "souls");
        Objects.requireNonNull(resolvers, "resolvers");
        lava = resolvers.particle("LAVA").orElse(-1);
        itemBreak = resolvers.sound("ITEM_BREAK").orElse(-1);
        levelUp = resolvers.sound("LEVEL_UP").orElse(-1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player wearer) || !CosmicTierGate.tierSixPlusEnabled(wearer)) {
            return;
        }
        long now = env.nowTicks().getAsLong();
        UUID wearerId = wearer.getUniqueId();
        if (env.stores().suppression().defenseSuppressed(wearerId, now)
                || env.stores().vars().get(wearerId, "soul-trapped", now) != null) {
            return;
        }
        Worn worn = firstWorn(wearer);
        if (worn == null) {
            return;
        }

        int cost = soulCost(worn.level());
        if (!souls.trySpendCarried(wearer, cost)) {
            outOfSouls(wearer, now);
            return;
        }

        SinkReadback sink = sinks.create(env);
        sink.repairArmor(wearer, 2);
        if (event instanceof EntityDamageByEntityEvent byEntity
                && byEntity.getDamager() instanceof Player attacker) {
            sink.damageArmorSlot(attacker, worn.slot(), 1);
        }
        int remaining = souls.currentTotal(wearer);
        if (remaining % 20 == 0) {
            sink.message(wearer, "");
            sink.message(wearer, "&6&l** IMMORTAL **");
            sink.message(wearer, "&7You have &n" + remaining + "&7 souls left.");
            sink.message(wearer, "");
            if (levelUp >= 0) {
                sink.privateSound(wearer, levelUp, 1.0f, 0.65f);
            }
        }
        sink.flush();
    }

    private Worn firstWorn(Player wearer) {
        ItemStack[] armor = wearer.getInventory().getArmorContents();
        for (int slot = 0; slot < armor.length; slot++) {
            ItemStack piece = armor[slot];
            if (piece == null || piece.getType() == Material.AIR) {
                continue;
            }
            int level = views.of(piece).combat().enchants().getOrDefault(ENCHANT, 0);
            if (level > 0) {
                return new Worn(slot, level);
            }
        }
        return null;
    }

    private void outOfSouls(Player wearer, long now) {
        UUID id = wearer.getUniqueId();
        Long until = outOfSoulsUntil.get(id);
        if (until != null && until > now) {
            return;
        }
        outOfSoulsUntil.put(id, now + OUT_OF_SOULS_THROTTLE_TICKS);
        SinkReadback sink = sinks.create(env);
        sink.message(wearer, "&c&l** OUT OF SOULS **");
        if (lava >= 0) {
            sink.particle(wearer.getEyeLocation(), lava, 20, -1, 0.0, 0.0, 0.0, 0.4);
        }
        if (itemBreak >= 0) {
            sink.privateSound(wearer, itemBreak, 0.7f, 0.4f);
        }
        sink.flush();
    }

    static int soulCost(int level) {
        return Math.max(1, 5 - level);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        outOfSoulsUntil.remove(event.getPlayer().getUniqueId());
    }

    public void stop() {
        outOfSoulsUntil.clear();
    }
}
