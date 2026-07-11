package feature.pet;

import compile.load.PetFoodConfig;
import feature.apply.ApplyGestureListener;
import feature.apply.GestureOutcome;
import feature.compat.Sounds;
import feature.fx.ParticleFx;
import item.codec.PetCodec;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import platform.lang.Messages;

/**
 * Drag Pet Food onto a pet head to raise its level (ADR-0052) — an ADR-0041 gesture leaf. The +levels come
 * from the FOOD's baked PDC amount (its mint-time config, the dust rule), clamped to the universal max by
 * {@link PetService#addLevels}; a maxed pet rejects the drop BEFORE the food is consumed (the check/apply
 * split). One food is spent per drop; a bracket-crossing level-up refreshes the holder's worn state in the
 * follow-up (the gesture already runs on their region thread).
 */
public final class PetFoodListener extends ApplyGestureListener {

    private final PetService service;
    private final PetCodec codec;
    private final Supplier<PetFoodConfig> config;
    private final Messages messages;
    private final Consumer<Player> refresh;
    private final BooleanSupplier enabled;

    public PetFoodListener(PetService service, PetCodec codec, Supplier<PetFoodConfig> config, Messages messages,
                           ParticleFx particles, Sounds sounds, Consumer<Player> refresh, BooleanSupplier enabled) {
        super(messages, particles, sounds);
        this.service = Objects.requireNonNull(service, "service");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.config = Objects.requireNonNull(config, "config");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.refresh = Objects.requireNonNull(refresh, "refresh");
        this.enabled = Objects.requireNonNull(enabled, "enabled");
    }

    @Override
    protected boolean claimsCursor(ItemStack cursor) {
        return enabled.getAsBoolean() && codec.isPetFood(cursor);
    }

    @Override
    protected boolean claimsTarget(ItemStack cursor, ItemStack target) {
        return codec.isPet(target); // food onto anything else stays a vanilla click (never swallowed)
    }

    @Override
    protected GestureOutcome apply(Player player, ItemStack cursor, ItemStack target, int slot) {
        if (target.getAmount() > 1) {
            // Fresh identical heads stack; the codec would level EVERY head in the stack for one food (a
            // pet-value dupe). Feeding is single-head only — reject before anything is consumed.
            return GestureOutcome.noop(messages.format("pet.food-stacked"));
        }
        if (service.atMaxLevel(target)) {
            return GestureOutcome.noop(messages.format("pet.food-max")); // reject BEFORE consuming
        }
        int levels = codec.foodLevels(cursor);
        PetService.Progress progress = service.addLevels(target, levels);
        if (!progress.changed()) {
            return GestureOutcome.noop(messages.format("pet.food-max")); // stale key / already clamped
        }
        cursor.setAmount(cursor.getAmount() - 1); // the service-side decrement the base writes back
        PetFoodConfig cfg = config.get();
        Runnable followUp = progress.bracketCrossed() ? () -> refresh.accept(player) : null;
        return new GestureOutcome(true, true, target, null,
                GestureOutcome.Cue.of(cfg.sound(), cfg.particles()),
                messages.format("pet.food-applied", "AMOUNT", Integer.toString(levels)),
                followUp);
    }
}
