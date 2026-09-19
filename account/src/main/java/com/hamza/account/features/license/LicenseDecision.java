package com.hamza.account.features.license;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * The answer about one licence file on one day.
 *
 * @param terms what the licence says; present exactly when the signature was genuine, which
 *              includes {@link LicenseStatus#OTHER_MACHINE}
 * @param today the day it was judged on - {@link LicenseClock#today()}, not the computer's
 */
public record LicenseDecision(LicenseStatus status, Optional<LicenseTerms> terms, LocalDate today) {

    /** A subscription that has ended keeps working this long, saying so every day. */
    public static final int GRACE_DAYS = 14;

    static LicenseDecision without(LicenseStatus status, LocalDate today) {
        return new LicenseDecision(status, Optional.empty(), today);
    }

    public boolean skipsTrial() {
        return status.skipsTrial();
    }

    /**
     * Whether new business may be recorded. False only once a subscription and its grace
     * have both run out; an unlicensed machine is the trial's to limit, not this class's.
     */
    public boolean mayRecord() {
        return status != LicenseStatus.READ_ONLY;
    }

    /** The last day of the grace period; empty for a perpetual licence. */
    public Optional<LocalDate> graceEnds() {
        return terms.map(LicenseTerms::expires).map(expires -> expires.plusDays(GRACE_DAYS));
    }

    /** Days of grace left, today included; zero outside the grace period. */
    public long graceDaysLeft() {
        if (status != LicenseStatus.GRACE) {
            return 0;
        }
        return graceEnds().map(end -> ChronoUnit.DAYS.between(today, end) + 1).orElse(0L);
    }

    /**
     * Whether a release built on {@code buildDate} is covered by this licence's updates.
     *
     * <p>It is the <b>build</b> date that is compared, not the day the update is offered: a
     * customer whose renewal lapsed in June is still owed the release built in May, whenever
     * they get round to installing it.
     */
    public boolean updatesCover(LocalDate buildDate) {
        return skipsTrial() && terms
                .map(licence -> !buildDate.isAfter(licence.updatesUntil()))
                .orElse(false);
    }
}
