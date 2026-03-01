package com.hamza.controlsfx.dateTime;

import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;

public interface DateInterfaceSetting {

    /**
     * Adjusts the given LocalDate by adding or subtracting the specified number of days.
     *
     * @param localDate the initial LocalDate to be adjusted
     * @param time the number of days to add (if positive) or subtract (if negative)
     * @return a new LocalDate after adding or subtracting the specified number of days
     */
    LocalDate localDate_day(@NotNull LocalDate localDate, long time);

    /**
     * Adjusts the given LocalDate by a specified number of months.
     *
     * @param localDate the starting LocalDate to be adjusted
     * @param time the number of months to add (positive) or subtract (negative)
     * @return a new LocalDate that is adjusted by the specified number of months
     */
    LocalDate localDate_month(@NotNull LocalDate localDate, long time);

    /**
     * Adjusts the year component of the provided LocalDate by the specified amount of years.
     *
     * @param localDate the initial LocalDate to adjust
     * @param time the number of years to add or subtract; negative to subtract, positive to add
     * @return a new LocalDate with the adjusted year component
     */
    LocalDate localDate_year(@NotNull LocalDate localDate, long time);
}
