package com.hamza.controlsfx.dateTime;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class DateUtils {

    /**
     * A DateTimeFormatter instance for formatting date and time in the pattern 'yyyy-MM-dd HH:mm:ss'.
     * This formatter can be used to parse and format date-time strings according to the specified pattern.
     */
    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /**
     * A DateTimeFormatter instance with a custom pattern "yyyy-MM-dd".
     * This formatter is used for parsing and formatting dates in the format of "year-month-day".
     */
    public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Converts a given Date object to a LocalDate object.
     *
     * @param date the Date object to be converted
     * @return the corresponding LocalDate object
     */
    public static LocalDate dateToLocalDate(@NotNull Date date) {
        return stringToLocalDate(new SimpleDateFormat("yyyy-MM-dd").format(date));
    }

    /**
     * Converts a given date string to a LocalDate using a predefined date formatter.
     *
     * @param date the date string to be converted
     * @return the resulting LocalDate
     */
    public static LocalDate stringToLocalDate(@NotNull String date) {
        return LocalDate.parse(date, DATE_FORMATTER);
    }

    /**
     * Generates a random barcode by appending a random suffix to the current date formatted as "yyyyMMdd".
     *
     * @param randomSuffix the random integer suffix to be appended to the formatted date
     * @return a string representing the generated barcode, consisting of the current date and the provided random suffix
     */
    public static String generateRandomBarcode(int randomSuffix) {
        return new SimpleDateFormat("yyyyMMdd", Locale.ENGLISH).format(new Date()) + randomSuffix;
    }

    /**
     * Extracts the year from a given date string.
     *
     * @param date the date string in ISO-8601 format (yyyy-MM-dd)
     * @return the year extracted from the provided date string
     */
    public static int extractYear(@NotNull String date) {
        return LocalDate.parse(date).getYear();
    }

    /**
     * Extracts the month value from the given date string.
     *
     * @param date the date string in ISO-8601 format (yyyy-MM-dd)
     * @return the integer value of the month (1-12) from the date string
     */
    public static int extractMonth(@NotNull String date) {
        return LocalDate.parse(date).getMonthValue();
    }

    /**
     * Extracts the day of the month from a given date string.
     *
     * @param date the date string in ISO-8601 format (e.g., yyyy-MM-dd)
     * @return the day of the month as an integer
     */
    public static int extractDay(@NotNull String date) {
        return LocalDate.parse(date).getDayOfMonth();
    }

    /**
     * Retrieves a list of distinct years from the provided list of date strings.
     *
     * @param dates a list of date strings
     * @return an observable list of distinct years
     */
    public static ObservableList<Integer> getDistinctYears(@NotNull List<String> dates) {
        List<Integer> years = dates.isEmpty()
                ? List.of(LocalDate.now().getYear())
                : dates.stream().map(DateUtils::extractYear).distinct().collect(Collectors.toList());
        return FXCollections.observableArrayList(years);
    }

    /**
     * Adjusts the given LocalDate by a specified amount of time in days, months, or years according to the specified unit.
     *
     * @param amount the amount of time to adjust the LocalDate by, which can be positive or negative
     * @param unit the unit of time for the adjustment; valid values are "Day", "Month", or "Year"
     * @param date the initial LocalDate to be adjusted
     * @param dateInterfaceSetting an implementation of DateInterfaceSetting used for the adjustment operations
     * @return a new LocalDate adjusted by the specified unit and amount, or null if the unit is not "Day", "Month", or "Year"
     */
    public static LocalDate adjustLocalDate(long amount, @NotNull String unit, @NotNull LocalDate date, @NotNull DateInterfaceSetting dateInterfaceSetting) {
        return switch (unit) {
            case "Day" -> dateInterfaceSetting.localDate_day(date, amount);
            case "Month" -> dateInterfaceSetting.localDate_month(date, amount);
            case "Year" -> dateInterfaceSetting.localDate_year(date, amount);
            default -> null;
        };
    }

    /**
     * Extracts minimum date from a list of objects using a provided date string extractor function.
     *
     * @param <T> the type of objects in the list
     * @param list the list of objects
     * @param dateExtractor a function to extract date string from each object
     * @return the minimum LocalDate found in the list, or {@code null} if list is empty or contains non-parsable dates
     */
    public static <T> LocalDate getMinDate(@NotNull List<T> list, @NotNull Function<T, String> dateExtractor) {
        return list.stream()
                .map(dateExtractor)
                .map(LocalDate::parse)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    /**
     * Returns the minimum date from the provided list that satisfies the given filter.
     * Extracts the date string using the specified date extractor function
     * and converts it to a LocalDate.
     *
     * @param <T> the type of elements in the list
     * @param list the list of elements to process
     * @param filter the predicate to filter the elements
     * @param dateExtractor a function to extract the date string from each element
     * @return the minimum LocalDate that satisfies the filter condition,
     *         or null if no such date is found
     */
    public static <T> LocalDate getMinDateWithFilter(@NotNull List<T> list, @NotNull Predicate<T> filter, Function<T, String> dateExtractor) {
        return list.stream()
                .filter(filter)
                .map(dateExtractor)
                .map(LocalDate::parse)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    /**
     * Compares two dates based on the specified comparator.
     *
     * @param date1 the first date string in the format 'yyyy-MM-dd'
     * @param date2 the second date string in the format 'yyyy-MM-dd'
     * @param comparator the comparison type, either 'before' or 'after'
     * @return true if date1 is before or after date2 based on the comparator; false otherwise
     */
    private boolean isDateBeforeOrAfter(@NotNull String date1, @NotNull String date2, @NotNull String comparator) {
        LocalDate localDate1 = stringToLocalDate(date2);
        LocalDate parsedDate = LocalDate.parse(date1.substring(0, 10), DATE_FORMATTER);
        return comparator.equals("before") ? parsedDate.isBefore(localDate1) : parsedDate.isAfter(localDate1);
    }
}