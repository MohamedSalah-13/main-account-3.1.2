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
     *
     * @param <T> the type of number that the converter will handle, which must extend Number
     */
    @SuppressWarnings("unchecked")
    public static class DefaultStringConverter<T extends Number> extends StringConverter<T> {
        /** Only tells {@link #fromString} which numeric type to parse into - its value is never returned. */
        private final T typeWitness;

        public DefaultStringConverter() {
            this.typeWitness = (T) (Number) 0.0; // Double by default
        }

        public DefaultStringConverter(T typeWitness) {
            this.typeWitness = typeWitness;
        }

        @Override
        public String toString(T number) {
            return number != null ? number.toString() : "";
        }

        /**
         * An empty field, or a bare "-" or "." typed on the way to a real number,
         * is null - not zero.
         * <p>
         * This used to answer zero, and {@code TextFormatter} resyncs its text
         * from the parsed value on every accepted change - so clearing a field
         * that held anything other than zero produced a value change, which
         * triggered that resync, which wrote "0.0" straight back over the empty
         * text the user had just typed. The field read as refusing to erase.
         * Null keeps the resync a no-op: {@link #toString} already turns it back
         * into "", matching what is already there.
         */
        @Override
        public T fromString(String string) {
            if (string == null || string.isEmpty() || "-".equals(string) || ".".equals(string) || "-.".equals(string)) {
                return null;
            }
            if (typeWitness instanceof Double) {
                return (T) Double.valueOf(string);
            } else if (typeWitness instanceof Integer) {
                return (T) Integer.valueOf(string);
            }
            throw new IllegalArgumentException("Unsupported type");
        }
    }
}
