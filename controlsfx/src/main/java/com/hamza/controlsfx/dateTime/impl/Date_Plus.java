package com.hamza.controlsfx.dateTime.impl;

import com.hamza.controlsfx.dateTime.DateInterfaceSetting;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;

public class Date_Plus implements DateInterfaceSetting {
    @Override
    public LocalDate localDate_day(@NotNull LocalDate localDate, long time) {
        return localDate.plusDays(time);
    }

    @Override
    public LocalDate localDate_month(@NotNull LocalDate localDate, long time) {
        return localDate.plusMonths(time);
    }

    @Override
    public LocalDate localDate_year(@NotNull LocalDate localDate, long time) {
        return localDate.plusMonths(time);
    }
}
