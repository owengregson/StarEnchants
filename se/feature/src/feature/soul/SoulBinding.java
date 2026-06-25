package feature.soul;

import engine.interact.SoulLedger;
import java.util.UUID;

/**
 * Soul context for one activation (§6.3): the active gem and a {@link SoulLedger.Balance} that debits it on
 * a successful spend. Handed to {@code Activation.soulMode} so gate 10 can consume souls.
 */
public record SoulBinding(UUID gemId, SoulLedger.Balance balance) {
}
