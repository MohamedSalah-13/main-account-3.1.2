package com.hamza.account.interfaces;

import java.util.function.Predicate;

public interface FilterDateInterface<T> {

    Predicate<T> predicateByYear(int year);

    Predicate<T> predicateByMonth(int month);

    Predicate<T> predicateByDay(int day);
}
