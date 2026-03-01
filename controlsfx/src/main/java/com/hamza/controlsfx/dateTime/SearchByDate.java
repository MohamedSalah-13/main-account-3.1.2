package com.hamza.controlsfx.dateTime;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.hamza.controlsfx.dateTime.DateUtils.DATE_FORMATTER;

public interface SearchByDate<T> {

    /**
     * Retrieves the date associated with the given object.
     *
     * @param t the object from which to extract the date
     * @return the date as a String
     */
    String getDate(T t);

    /**
     * Retrieves the first date in the dataset.
     *
     * @return A string representing the first date in the dataset.
     */
    String firstDate();

    /**
     * Retrieves the last date in a series or range.
     *
     * @return A string representing the last date.
     */
    String lastDate();

    /**
     * Provides a list of objects that this instance manages.
     *
     * @return A list of objects of type T.
     */
    List<T> list();

    /**
     * Provides a predicate to check if an object's date is on or after the specified first date.
     *
     * @return A predicate that evaluates to true if the given object's date is on or after the first date.
     */
    default Predicate<T> getDateBefore() {
        return t -> !localDateParse(getDate(t)).isBefore(localDateParse(firstDate()));
    }

    /**
     * Returns a predicate that evaluates to true if the date of the given object
     * is not after the specified last date.
     *
     * @return a predicate that evaluates to true if the date is not after the last date.
     */
    default Predicate<T> getDateAfter() {
        return t -> !localDateParse(getDate(t)).isAfter(localDateParse(lastDate()));
    }

    /**
     * Provides a default implementation to retrieve a date string from a generic object.
     *
     * @return A function that takes an object of type T and returns its associated date as a string.
     */
    default Function<T, String> getDate() {
        return this::getDate;
    }

    /**
     * Parses a given string into a LocalDate using a predefined date formatter.
     *
     * @param date the date string to be parsed
     * @return the parsed LocalDate object
     */
    default LocalDate localDateParse(String date) {
        return LocalDate.parse(date, DATE_FORMATTER);
    }

    /**
     * Checks if the last date is before the first date.
     *
     * @return true if the last date is before the first date, false otherwise
     */
    default boolean checkDate() {
        return localDateParse(lastDate()).isBefore(localDateParse(firstDate()));
    }
}
