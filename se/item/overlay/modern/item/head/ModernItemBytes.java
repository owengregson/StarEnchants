package item.head;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import org.bukkit.inventory.ItemStack;

/**
 * The modern {@link ItemBytes}: Paper's {@code ItemStack#serializeAsBytes()} /
 * {@code ItemStack.deserializeBytes(byte[])} resolved ONCE by name (Bukkit API surface — mapping-flip immune;
 * probed rather than compiled against so a fork without the pair degrades to {@code null} instead of a
 * boot-time linkage error — the ModernEquipmentRepaint pattern).
 */
public final class ModernItemBytes implements ItemBytes {

    private static final Logger LOG = System.getLogger("StarEnchants.ItemBytes");

    private static final MethodHandle SERIALIZE = probeSerialize();
    private static final MethodHandle DESERIALIZE = probeDeserialize();

    /** Log-once latch; later failures keep degrading quietly (a corrupt payload is a per-item event). */
    private static volatile boolean failureLogged;

    @Override
    public byte[] serialize(ItemStack stack) {
        if (SERIALIZE == null || stack == null) {
            return null;
        }
        try {
            return (byte[]) SERIALIZE.invokeExact(stack);
        } catch (Throwable failure) {
            logOnce(failure);
            return null;
        }
    }

    @Override
    public ItemStack deserialize(byte[] bytes) {
        if (DESERIALIZE == null || bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            return (ItemStack) DESERIALIZE.invokeExact(bytes);
        } catch (Throwable corruptOrFailed) {
            logOnce(corruptOrFailed);
            return null;
        }
    }

    private static void logOnce(Throwable failure) {
        if (!failureLogged) {
            failureLogged = true;
            LOG.log(Level.WARNING, "ItemStack byte round-trip failed — illusion payloads degrade to detection-only",
                    failure);
        }
    }

    private static MethodHandle probeSerialize() {
        try {
            return MethodHandles.publicLookup().findVirtual(ItemStack.class, "serializeAsBytes",
                    MethodType.methodType(byte[].class));
        } catch (NoSuchMethodException | IllegalAccessException absent) {
            LOG.log(Level.DEBUG, "ItemStack.serializeAsBytes absent — illusion payloads degrade to detection-only");
            return null;
        }
    }

    private static MethodHandle probeDeserialize() {
        try {
            return MethodHandles.publicLookup().findStatic(ItemStack.class, "deserializeBytes",
                    MethodType.methodType(ItemStack.class, byte[].class));
        } catch (NoSuchMethodException | IllegalAccessException absent) {
            return null;
        }
    }
}
