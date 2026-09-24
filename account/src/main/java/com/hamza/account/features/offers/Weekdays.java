package com.hamza.account.features.offers;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

/**
 * The days of the week an offer runs, as {@code offer.weekdays} stores them: null for every day, else a bit
 * a day in {@link DayOfWeek}'s order - Monday 1, Tuesday 2 ... Sunday 64. The shop's week starts on
 * Saturday, which is how the screen lists them; the mask does not care.
 */
public final class Weekdays {

    public static final int EVERY_DAY = 127;

    private Weekdays() {
    }

    public static boolean includes(Integer mask, LocalDate day) {
        return mask == null || (mask & bit(day.getDayOfWeek())) != 0;
    }

    public static int bit(DayOfWeek day) {
        return 1 << (day.getValue() - 1);
    }

    /** The mask for a set of days: null for none or all of them, which is the same offer. */
    public static Integer of(Set<DayOfWeek> days) {
        if (days == null || days.isEmpty() || days.size() == 7) {
            return null;
        }
        int mask = 0;
        for (DayOfWeek day : days) {
            mask |= bit(day);
        }
        return mask;
    }

    public static Set<DayOfWeek> days(Integer mask) {
        EnumSet<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (DayOfWeek day : DayOfWeek.values()) {
            if (mask == null || (mask & bit(day)) != 0) {
                days.add(day);
            }
        }
        return days;
    }
}
