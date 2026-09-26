package com.hamza.account.features.about;

import com.hamza.account.features.license.LicenseDecision;
import com.hamza.account.features.license.LicenseTerms;
import com.hamza.account.trial.TrialManager;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * What the About screen says about the licence, worked out from what the trial check reports.
 * <p>
 * The screen used to write each of the three lines as one sentence - "الحالة: مفعلة" - in a colour
 * named in the code ({@code green}, {@code orange}, {@code red}), which the dark theme could not
 * answer for. A line is now a caption and a value, and the value's tone is a word the theme colours.
 * <p>
 * <b>A licence from the licence server has dates, and the screen says them</b>: whether it is perpetual
 * or a subscription until a day, and the last day its updates are covered. It used to say "unlimited"
 * for every licence - true of the older {@code HAMZA_ACCOUNT} file, which carries no date, and wrong for
 * a server licence whose updates end on a day the customer should know before it passes.
 *
 * @param tone          how the value reads: all well, a trial running or a subscription ending, or a trial over or unreadable
 * @param statusKey     the status in a word
 * @param remainingKey  what is left - it takes {@code remainingDate} (a {@code %s}) and then {@code remainingDays}
 *                      (a {@code %d}), each only when it is not null
 * @param remainingDays the days left, never below zero; null unless {@code remainingKey} counts them
 * @param remainingDate the day it runs to; null unless {@code remainingKey} names one
 * @param fileKey       what the licence file amounts to
 * @param updatesKey    the updates line, which takes {@code updatesUntil} (a {@code %s}); null when there is no
 *                      such line - a trial, or the older licence
 * @param updatesUntil  the last day of updates; null with {@code updatesKey}
 */
public record AboutLicense(Tone tone, String statusKey, String remainingKey, Long remainingDays,
                           LocalDate remainingDate, String fileKey, String updatesKey, LocalDate updatesUntil) {

    public enum Tone { GOOD, WARNING, BAD }

    /** Nothing could be read: no connection, or the check itself failed. */
    public static final AboutLicense UNAVAILABLE = new AboutLicense(Tone.BAD, "about.status.unavailable",
            "about.remaining.unavailable", null, null, "about.license.unavailable", null, null);

    public static AboutLicense of(TrialManager.TrialDisplayInfo info) {
        return of(info, null);
    }

    /**
     * @param server what the server-issued licence came to, if one was read - {@code LicenseService.check}; it is
     *               asked only when the trial check says licensed, and only a decision that skips the trial and
     *               has its terms is read for dates
     */
    public static AboutLicense of(TrialManager.TrialDisplayInfo info, LicenseDecision server) {
        if (info == null || info.error != null) {
            return UNAVAILABLE;
        }
        if (info.licenseValid) {
            if (server != null && server.skipsTrial() && server.terms().isPresent()) {
                return serverLicence(server, server.terms().get());
            }
            return new AboutLicense(Tone.GOOD, "about.status.activated", "about.remaining.unlimited", null, null,
                    "about.license.valid", null, null);
        }
        String file = info.licensePresent ? "about.license.invalid" : "about.license.missing";
        if (info.daysRemaining == null) {
            return new AboutLicense(Tone.BAD, "about.status.trial", "about.remaining.unavailable", null, null, file,
                    null, null);
        }
        if (info.trialExpired) {
            return new AboutLicense(Tone.BAD, "about.status.expired", "about.remaining.days", 0L, null, file, null, null);
        }
        return new AboutLicense(Tone.WARNING, "about.status.trial", "about.remaining.days",
                Math.max(0, info.daysRemaining), null, file, null, null);
    }

    private static AboutLicense serverLicence(LicenseDecision decision, LicenseTerms terms) {
        LocalDate today = decision.today();
        String updatesKey = terms.updatesUntil().isBefore(today) ? "about.updates.ended" : "about.updates.until";
        return switch (decision.status()) {
            case GRACE -> new AboutLicense(Tone.WARNING, "about.status.grace", "about.remaining.grace",
                    decision.graceDaysLeft(), null, "about.license.valid", updatesKey, terms.updatesUntil());
            case READ_ONLY -> new AboutLicense(Tone.BAD, "about.status.read.only", "about.remaining.read.only", null,
                    terms.expires(), "about.license.valid", updatesKey, terms.updatesUntil());
            default -> terms.perpetual()
                    ? new AboutLicense(Tone.GOOD, "about.status.activated", "about.remaining.perpetual", null, null,
                    "about.license.valid", updatesKey, terms.updatesUntil())
                    : new AboutLicense(Tone.GOOD, "about.status.activated", "about.remaining.subscription",
                    Math.max(0, ChronoUnit.DAYS.between(today, terms.expires()) + 1), terms.expires(),
                    "about.license.valid", updatesKey, terms.updatesUntil());
        };
    }
}
