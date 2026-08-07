package com.hamza.controlsfx.others;

import javafx.scene.control.DateCell;
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

    /**
     * Stops the user picking a day after today.
     * <p>
     * Greys the future out in the calendar rather than only complaining on save, so
     * the restriction is visible at the point of choosing. It does not clear a value
     * already set - a screen that loads an existing record with a future date should
     * still show what is stored - so anything that saves must check the value as
     * well; see {@link #isInTheFuture}.
     * <p>
     * "Today" is read when each cell is drawn, not once at setup, so a till left
     * open overnight starts allowing the new day on its own.
     */
    public static void noFutureDates(DatePicker datePicker) {
        datePicker.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                boolean future = date != null && date.isAfter(LocalDate.now());
                setDisable(empty || future);
                if (future) {
                    getStyleClass().add("date-cell-disabled");
                }
            }
        });
    }

    /** Whether {@code date} is after today. Null is not in the future. */
    public static boolean isInTheFuture(LocalDate date) {
        return date != null && date.isAfter(LocalDate.now());
    }

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
