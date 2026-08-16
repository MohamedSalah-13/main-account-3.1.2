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

}