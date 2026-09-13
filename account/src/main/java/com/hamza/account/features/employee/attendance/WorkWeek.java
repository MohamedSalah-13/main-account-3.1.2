package com.hamza.account.features.employee.attendance;

import com.hamza.account.config.SharedSettings;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * The shop's working week: which days are rest, and how long a day is.
 *
 * <h2>Why it is the shop's and not the computer's</h2>
 * Two tills disagreeing about which day is Friday is not a difference of preference - it is a
 * <b>defect</b>, and one that reaches the payroll as a deduction on one machine and none on
 * the other. That is the membership test {@code docs/multi-device-plan.md} sets for
 * {@code SharedSettingKeys}: not "would sharing be convenient" but "would disagreeing be a
 * bug". So it lives in {@code app_setting} (V60), read here.
 *
 * <h2>Where the week starts is not decided here</h2>
 * {@code StatementPeriod.FIRST_DAY_OF_WEEK} answers that, once, for the whole system. This
 * class says which days are <i>rest</i>; it does not have an opinion about which day a week
 * begins on, and writing a second answer in MySQL's {@code WEEK()} modes is exactly what
 * {@code CLAUDE.md} warns against.
 */
public final class WorkWeek {

    /** V60 seeds Friday - {@link DayOfWeek#FRIDAY} is 5 - and eight hours. */
    static final String WEEKEND_KEY = "attendance.weekend.days";
    static final String HOURS_KEY = "attendance.hours.per.day";

    private static final Set<DayOfWeek> DEFAULT_WEEKEND = EnumSet.of(DayOfWeek.FRIDAY);
    private static final BigDecimal DEFAULT_HOURS = new BigDecimal("8");

    private final Set<DayOfWeek> restDays;
    private final BigDecimal hoursPerDay;

    public WorkWeek(Set<DayOfWeek> restDays, BigDecimal hoursPerDay) {
        this.restDays = restDays == null || restDays.isEmpty()
                ? DEFAULT_WEEKEND : Collections.unmodifiableSet(EnumSet.copyOf(restDays));
        this.hoursPerDay = hoursPerDay == null || hoursPerDay.signum() <= 0
                ? DEFAULT_HOURS : hoursPerDay;
    }

    /**
     * What the shop has recorded, falling back to the seeded values.
     * <p>
     * A malformed setting falls back rather than throwing: a screen that refuses to open
     * because somebody typed a letter into a settings box is worse than one that opens with
     * Friday as the rest day and can be corrected.
     */
    public static WorkWeek current() {
        return new WorkWeek(parseDays(SharedSettings.read(WEEKEND_KEY).orElse(null)),
                parseHours(SharedSettings.read(HOURS_KEY).orElse(null)));
    }

    public Set<DayOfWeek> restDays() {
        return restDays;
    }

    public BigDecimal hoursPerDay() {
        return hoursPerDay;
    }

    public boolean isRestDay(DayOfWeek day) {
        return restDays.contains(day);
    }

    /** The value as it is stored: the {@link DayOfWeek} numbers, comma separated. */
    public String storedDays() {
        StringBuilder text = new StringBuilder();
        for (DayOfWeek day : DayOfWeek.values()) {
            if (restDays.contains(day)) {
                if (text.length() > 0) {
                    text.append(',');
                }
                text.append(day.getValue());
            }
        }
        return text.toString();
    }

    static Set<DayOfWeek> parseDays(String stored) {
        if (stored == null || stored.isBlank()) {
            return DEFAULT_WEEKEND;
        }
        EnumSet<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (String part : stored.split(",")) {
            try {
                int value = Integer.parseInt(part.trim());
                if (value >= 1 && value <= 7) {
                    days.add(DayOfWeek.of(value));
                }
            } catch (NumberFormatException ignored) {
                // A malformed entry is skipped rather than taking the whole setting down.
            }
        }
        return days.isEmpty() ? DEFAULT_WEEKEND : days;
    }

    static BigDecimal parseHours(String stored) {
        if (stored == null || stored.isBlank()) {
            return DEFAULT_HOURS;
        }
        try {
            BigDecimal hours = new BigDecimal(stored.trim());
            return hours.signum() > 0 && hours.compareTo(new BigDecimal("24")) <= 0
                    ? hours : DEFAULT_HOURS;
        } catch (NumberFormatException malformed) {
            return DEFAULT_HOURS;
        }
    }
}
