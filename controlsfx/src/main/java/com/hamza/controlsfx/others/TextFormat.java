package com.hamza.controlsfx.others;

import javafx.scene.control.TextFormatter;
import javafx.util.StringConverter;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

public class TextFormat {

    /**
     * A StringConverter instance for converting between Double values and their String representations.
     * Uses DefaultStringConverter with a default type of Double.
     * This can be used for text formatting or parsing where Double values are involved.
     */
    public static final StringConverter<Double> doubleStringConverter = new DefaultStringConverter<>();
    /**
     * A static final instance of the {@link StringConverter} class for converting
     * strings to and from {@link Integer} objects. The converter uses a default
     * value of 0 for invalid or empty string inputs.
     */
    public static final StringConverter<Integer> integerStringConverter = new DefaultStringConverter<>(0);
    /**
     * Regular expression pattern to validate the current editing state of a text input field.
     * This pattern allows for optional leading negative sign, integral and fractional parts of a number,
     * including handling cases where parts of the number might be empty during input.
     */
    public static final Pattern VALID_EDITING_STATE_PATTERN = Pattern.compile("-?(([1-9][0-9]*)|0)?(\\.[0-9]*)?");
    /**
     * A {@code UnaryOperator<TextFormatter.Change>} that filters text input based on a predefined pattern.
     * This filter allows changes only if the resulting text matches the {@code VALID_EDITING_STATE_PATTERN}.
     * If the new text matches the pattern, the change is accepted; otherwise, it is rejected by returning {@code null}.
     */
    public static final UnaryOperator<TextFormatter.Change> TEXT_FORMATTER_FILTER = change -> {
        String newText = change.getControlNewText();
        return VALID_EDITING_STATE_PATTERN.matcher(newText).matches() ? change : null;
    };

    @NotNull
    public static TextFormatter<Object> createNumericTextFormatter() {
        return new TextFormatter<>(change -> {
            String newText = change.getControlNewText();
            if (newText.matches("^[+]?[0-9]{0,15}$")) {
                return change;
            }
            return null;
        });
    }

    /**
     * DefaultStringConverter is a generic converter class that extends StringConverter to handle
     * common conversions between strings and numeric types (Double, Integer).
     * It provides methods to convert a number to its string representation and parse
     * a string into a number, utilizing a default value when the input is invalid or empty.
     *
     * @param <T> the type of number that the converter will handle, which must extend Number
     */
    @SuppressWarnings("unchecked")
    public static class DefaultStringConverter<T extends Number> extends StringConverter<T> {
        private final T zero;

        public DefaultStringConverter() {
            this.zero = (T) (Number) 0.0; // Default type as Double
        }

        public DefaultStringConverter(T zero) {
            this.zero = zero;
        }

        @Override
        public String toString(T number) {
            return number != null ? number.toString() : "";
        }

        @Override
        public T fromString(String string) {
            if (string.isEmpty() || "-".equals(string) || ".".equals(string) || "-.".equals(string)) {
                return zero;
            } else {
                if (zero instanceof Double) {
                    return (T) Double.valueOf(string);
                } else if (zero instanceof Integer) {
                    return (T) Integer.valueOf(string);
                }
                throw new IllegalArgumentException("Unsupported type");
            }
        }
    }
}
