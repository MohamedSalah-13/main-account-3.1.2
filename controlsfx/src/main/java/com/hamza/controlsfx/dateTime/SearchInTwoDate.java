package com.hamza.controlsfx.dateTime;

import com.hamza.controlsfx.language.Error_Text_Show;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public class SearchInTwoDate {

    /**
     * Searches through a list of objects within a specified date range.
     *
     * @param searchByDate An instance of SearchByDate providing the data and date range predicates.
     * @param <T>          The type of objects being searched.
     * @return A list of objects that fall within the specified date range, sorted by date.
     * @throws Exception If the date range provided is invalid.
     */
    public static <T> List<T> searchInDate(SearchByDate<T> searchByDate) throws Exception {
        List<T> items = searchByDate.list();
        Predicate<T> filterBeforeDate = searchByDate.getDateBefore();
        Predicate<T> filterAfterDate = searchByDate.getDateAfter();
        Function<T, String> extractDate = searchByDate.getDate();

        validateDateRange(searchByDate);

        if (items.isEmpty()) {
            return Collections.emptyList();
        }

        return items.stream()
                .filter(filterBeforeDate.and(filterAfterDate))
                .sorted(Comparator.comparing(extractDate))
                .toList();
    }

    /**
     * Validates the date range specified in the SearchByDate instance.
     *
     * @param <T>         The type of objects being handled by SearchByDate.
     * @param searchByDate An instance of SearchByDate providing the data and date range information.
     * @throws Exception If the date range specified is invalid.
     */
    private static <T> void validateDateRange(SearchByDate<T> searchByDate) throws Exception {
        if (searchByDate.checkDate()) {
            throw new Exception(Error_Text_Show.NOT_POSSIBLE);
        }
    }
}
