package feature.carrier;

import feature.apply.ApplyGestureListener;
import feature.apply.GestureOutcome;
import feature.fx.ParticleFx;
import item.codec.CarrierCodec;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import platform.lang.Messages;

/**
 * The carrier application UX (ADR-0016): a carrier on the CURSOR clicked onto a target item applies it. A thin
 * leaf of the shared {@link ApplyGestureListener} (ADR-0041) — logic is in {@link CarrierService}, sound and
 * particle feedback flow through the {@link GestureOutcome.Cue}.
 */
public final class CarrierListener extends ApplyGestureListener {

    private final CarrierService service;
    private final CarrierCodec codec;

    public CarrierListener(CarrierService service, CarrierCodec codec, Messages messages) {
        this(service, codec, ParticleFx.NONE, messages);
    }

    public CarrierListener(CarrierService service, CarrierCodec codec, ParticleFx particles, Messages messages) {
        super(messages, particles);
        this.service = Objects.requireNonNull(service, "service");
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    @Override
    protected boolean claimsCursor(ItemStack cursor) {
        return codec.read(cursor) != null;
    }

    @Override
    protected boolean claimsTarget(ItemStack cursor, ItemStack target) {
        // Carrier-onto-carrier is only meaningful for dust onto a content book (ADR-0019); any other such
        // pairing falls through to the vanilla click rather than being swallowed as a cancelled no-op.
        return codec.read(target) == null || service.canCombineDust(cursor, target);
    }

    @Override
    protected GestureOutcome apply(Player player, ItemStack cursor, ItemStack target, int slot) {
        return service.applyTo(cursor, target);
    }
}
