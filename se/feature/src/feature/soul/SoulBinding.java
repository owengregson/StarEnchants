package feature.soul;

import engine.interact.SoulLedger;
import java.util.UUID;

/**
 * The soul context for one activation (docs/architecture.md §6.3): the active gem's id and a
 * {@link SoulLedger.Balance} that debits the gem on a successful spend. Handed to
 * {@code Activation.soulMode} so gate 10 can consume souls for a soul-cost ability.
 */
public record SoulBinding(UUID gemId, SoulLedger.Balance balance) {
}
