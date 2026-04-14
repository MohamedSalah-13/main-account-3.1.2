package com.hamza.controlsfx.util;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.ToIntFunction;

/**
 * MaxNumberList is a class that maintains an atomic integer code
 * initialized based on the maximum integer value found in a provided list.
 *
 * @param <T> the type of elements in the input list
 */
public class MaxNumberList<T> {

    /**
     * The initial value used to set the starting point for the code.
     * This value is used to initialize the AtomicInteger field 'code'
     * to ensure it starts from a defined point before any operations.
     */
    private static final int INITIAL_CODE_VALUE = 1;
    /**
     * An AtomicInteger that holds a code value used in the MaxNumberList class.
     * This value is initialized to INITIAL_CODE_VALUE and may be updated
     * based on the maximum value within a provided list.
     */
    private final AtomicInteger code;

    /**
     * Creates a new instance of MaxNumberList and initializes the code with a value based on
     * the maximum number in the provided list.
     *
     * @param intExtractor a function that extracts an integer from an object of type T
     * @param list a list of objects from which the maximum number is to be extracted and used for initialization
     */
    public MaxNumberList(@NotNull ToIntFunction<T> intExtractor, @NotNull List<T> list) {
        code = new AtomicInteger(INITIAL_CODE_VALUE);
        initializeCode(intExtractor, list);
    }

    /**
     * Initializes the code value based on the maximum integer value extracted from the provided list.
     *
     * @param intExtractor a function that extracts an integer value from an element of type T
     * @param list the list of elements of type T from which the integer values are extracted
     */
    private void initializeCode(ToIntFunction<T> intExtractor, List<T> list) {
        OptionalInt max = list.stream().mapToInt(intExtractor).max();
        max.ifPresent(maxValue -> code.set(maxValue + 1));
    }

    /**
     * Returns the current code value.
     *
     * @return the current value of the atomic integer 'code'
     */
    public int getCode() {
        return code.get();
    }
}
