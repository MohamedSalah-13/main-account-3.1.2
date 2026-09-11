package com.hamza.controlsfx.table.columnEdit;

import com.hamza.controlsfx.table.Columns;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.text.DecimalFormatSymbols;
import java.util.function.Function;

/**
 * The converter behind an editable number cell: it writes the value the way the column around it
 * does, and reads back whatever that writing looks like.
 * <p>
 * {@code DoubleStringConverter} writes {@code Double.toString}, so an editable price showed
 * {@code 7.5} beside a total showing {@code 7.50}, and a thousand as {@code 1000.0}. Its reading
 * half was the harder half to replace: the text a cell's editor opens with is this converter's own
 * {@link #toString}, so it has to read back {@code 1,050.00} - and, on a machine whose number
 * format is Arabic, {@code ١٬٠٥٠٫٠٠}, which is what {@link Columns#money} writes there.
 * <p>
 * Blank reads as {@code null}, which each edit already answers for itself (a blank quantity is
 * one). Text that is not a number reads as {@code NaN} rather than throwing: an exception out of a
 * cell's commit reaches nobody, while {@code NaN} reaches the edit's own check for a finite value
 * and is refused there, with that check's message.
 */
public final class NumberTextConverter extends StringConverter<Double> {

    private final Function<BigDecimal, String> format;

    private NumberTextConverter(Function<BigDecimal, String> format) {
        this.format = format;
    }

    /** Writes as {@link Columns#money}. */
    public static NumberTextConverter money() {
        return new NumberTextConverter(Columns::money);
    }

    /** Writes as {@link Columns#quantity}. */
    public static NumberTextConverter quantity() {
        return new NumberTextConverter(Columns::quantity);
    }

    @Override
    public String toString(Double value) {
        return value == null || !Double.isFinite(value) ? "" : format.apply(BigDecimal.valueOf(value));
    }

    @Override
    public Double fromString(String text) {
        try {
            BigDecimal value = parse(text);
            return value == null ? null : value.doubleValue();
        } catch (NumberFormatException notANumber) {
            return Double.NaN;
        }
    }

    /**
     * Reads a number as a person may have typed or seen it written: grouping separators dropped,
     * the machine's decimal separator taken as a point, Arabic-Indic and Persian digits read as
     * digits. {@code null} for blank.
     *
     * @throws NumberFormatException when what is left is not a number
     */
    public static BigDecimal parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance();
        char decimal = symbols.getDecimalSeparator();
        char grouping = symbols.getGroupingSeparator();
        StringBuilder plain = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            if (c >= '٠' && c <= '٩') {
                plain.append((char) ('0' + (c - '٠')));
            } else if (c >= '۰' && c <= '۹') {
                plain.append((char) ('0' + (c - '۰')));
            } else if (c == decimal || c == '٫') {
                plain.append('.');
            } else if (c == grouping || c == '٬' || (c == ',' && decimal != ',')
                    || Character.isSpaceChar(c) || Character.getType(c) == Character.FORMAT) {
                // a separator, a space, or a bidirectional mark an Arabic format puts round a sign
                continue;
            } else {
                plain.append(c);
            }
        }
        return new BigDecimal(plain.toString());
    }
}
