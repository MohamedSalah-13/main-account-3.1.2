package com.hamza.account.features.about;

import com.hamza.account.trial.TrialManager;

/**
 * What the About screen says about the licence, worked out from what the trial check reports.
 * <p>
 * The screen used to write each of the three lines as one sentence - "الحالة: مفعلة" - in a colour
 * named in the code ({@code green}, {@code orange}, {@code red}), which the dark theme could not
 * answer for. A line is now a caption and a value, and the value's tone is a word the theme colours.
 *
 * @param tone          how the value reads: all well, a trial running, or a trial over or unreadable
 * @param statusKey     the status in a word
 * @param remainingKey  what is left of the trial - {@code about.remaining.days} takes {@code remainingDays}
 * @param remainingDays the days left, never below zero; {@code null} unless {@code remainingKey} counts them
 * @param fileKey       what the licence file amounts to
 */
public record AboutLicense(Tone tone, String statusKey, String remainingKey, Long remainingDays, String fileKey) {

    public enum Tone { GOOD, WARNING, BAD }

    /** Nothing could be read: no connection, or the check itself failed. */
    public static final AboutLicense UNAVAILABLE = new AboutLicense(Tone.BAD, "about.status.unavailable",
            "about.remaining.unavailable", null, "about.license.unavailable");

    public static AboutLicense of(TrialManager.TrialDisplayInfo info) {
        if (info == null || info.error != null) {
            return UNAVAILABLE;
        }
        if (info.licenseValid) {
            return new AboutLicense(Tone.GOOD, "about.status.activated", "about.remaining.unlimited", null,
                    "about.license.valid");
        }
        String file = info.licensePresent ? "about.license.invalid" : "about.license.missing";
        if (info.daysRemaining == null) {
            return new AboutLicense(Tone.BAD, "about.status.trial", "about.remaining.unavailable", null, file);
        }
        if (info.trialExpired) {
            return new AboutLicense(Tone.BAD, "about.status.expired", "about.remaining.days", 0L, file);
        }
        return new AboutLicense(Tone.WARNING, "about.status.trial", "about.remaining.days",
                Math.max(0, info.daysRemaining), file);
    }
}
