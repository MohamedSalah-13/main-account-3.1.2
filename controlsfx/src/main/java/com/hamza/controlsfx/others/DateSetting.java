package com.hamza.controlsfx.others;

import javafx.scene.control.DatePicker;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;

public class DateSetting {

    public static LocalDate firstDateInMonth = LocalDate.now().with(TemporalAdjusters.firstDayOfMonth());

    public static LocalDate lastDateInMonth = LocalDate.now().with(TemporalAdjusters.lastDayOfMonth());
    public static LocalDate firstDayOfYear = LocalDate.now().with(TemporalAdjusters.firstDayOfYear());

    public static LocalDate today = LocalDate.now();

    public static LocalDate yesterday = LocalDate.now().minusDays(1);
    public static LocalDate tomorrow = LocalDate.now().plusDays(1);
    public static LocalDate nextMonth = LocalDate.now().plusMonths(1);
    public static LocalDate lastMonth = LocalDate.now().minusMonths(1);

    public static void dateAction(DatePicker datePicker) {
        datePicker.setValue(LocalDate.now());
        datePicker.setEditable(false);
        datePicker.setConverter(new StringConverter<>() {
            private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd");

            public String toString(LocalDate localDate) {
                return localDate == null ? "" : this.dateTimeFormatter.format(localDate);
            }

            public LocalDate fromString(String dateString) {
                return dateString != null && !dateString.trim().isEmpty() ? LocalDate.parse(dateString, this.dateTimeFormatter) : null;
            }
        });

    }
}
