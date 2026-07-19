package feature.combat;

import java.util.UUID;

/**
 * The Berry Overdrive timing seam (StrikeSync {@code project/docs/api-timing-overrides.md}): a combat
 * plugin that OWNS hit timing may accept a temporary per-(victim, attacker) window re-pricing inside its
 * own admission math. True = a live provider accepted the override — the caller then performs NO vanilla
 * i-frame write, NO spawn-invulnerability disarm and NO third-party fairness stamp (nothing global was
 * erased). False = no provider — the shipped vanilla-write fallback runs unchanged.
 */
public interface TimingOverrideBridge {

    boolean overrideWindow(UUID victim, UUID attacker, double factor, int durationTicks);

    /** The no-provider bridge — vanilla servers and tests take the fallback path unconditionally. */
    TimingOverrideBridge NONE = (victim, attacker, factor, durationTicks) -> false;
}
