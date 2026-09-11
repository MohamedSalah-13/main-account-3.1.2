package com.hamza.controlsfx.table;

import com.hamza.controlsfx.language.LanguageManager;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ObservableValue;
import javafx.css.PseudoClass;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;

/**
 * Builds a table column from a method reference instead of a field name string.
 * <p>
 * {@link TableColumnAnnotation} resolves a field by name at run time through
 * {@code PropertyValueFactory}, so a renamed field yields a silently empty
 * column - no compile error, no exception. A method reference here is checked
 * by the compiler, so the same rename is a compile error instead. See rule
 * ق-ل1 in {@code docs/new-code-rules.md}.
 * <p>
 * The title is resolved the same way the annotation resolves it - through
 * {@link LanguageManager#getString(String)} - so a screen migrating away from
 * {@code @ColumnData} keeps the same bundle keys.
 * <p>
 * This does not set a column id. {@code TableColumnAnnotation} always set it
 * to the annotated field's Java name, and some callers depend on that specific
 * value to find a column again later (for example
 * {@code CardController} looks a column up by
 * {@code "balance".equals(column.getId())}). Call {@link TableColumn#setId}
 * on the result when a caller needs that.
 */
public final class Columns {

    private Columns() {
    }

    public static <S> TableColumn<S, String> text(String titleKey, Function<S, String> extractor) {
        return column(titleKey, extractor);
    }

    public static <S> TableColumn<S, Number> number(String titleKey, Function<S, ? extends Number> extractor) {
        return column(titleKey, extractor);
    }

    /**
     * A column whose cell follows the row's own property, for a table where editing one cell
     * changes another cell of the same row.
     * <p>
     * Every other builder here wraps what the extractor answered <b>when the cell was built</b>,
     * and the cell shows that value until the row is rebuilt. That is right for a list that is
     * read and reloaded, and wrong for an invoice line: editing the quantity recalculates the
     * total on the model while the total's cell goes on showing the old figure. Handing the cell
     * the property itself is what lets it see the change. It is still a method reference, so a
     * renamed property is still a compile error.
     */
    public static <S, T> TableColumn<S, T> observable(String titleKey,
                                                      Function<S, ? extends ObservableValue<T>> property) {
        TableColumn<S, T> column = new TableColumn<>(LanguageManager.getInstance().getString(titleKey));
        column.setCellValueFactory(features -> property.apply(features.getValue()));
        return column;
    }

    /** Formats with {@link DateTimeFormatter#ISO_LOCAL_DATE}; a null date renders as an empty cell. */
    public static <S> TableColumn<S, String> date(String titleKey, Function<S, LocalDate> extractor) {
        return text(titleKey, row -> {
            LocalDate value = extractor.apply(row);
            return value == null ? "" : value.format(DateTimeFormatter.ISO_LOCAL_DATE);
        });
    }

    /**
     * A money column: two decimals, thousands separated, right-aligned, negatives in red.
     * <p>
     * <b>One definition, because there were none and every screen improvised.</b>
     * {@link #number} hands the cell a {@code Number} and lets JavaFX print it, so a balance showed
     * as {@code 1234.5600000000002} wherever a {@code double} had been through arithmetic, and the
     * same figure appeared with and without a separator on two screens of one ledger. A reader
     * comparing two columns of money needs the decimal points under each other; a reader comparing
     * two screens needs the same number to look like the same number.
     * <p>
     * The grouping separator follows the bundle's locale, which is what a {@code String.format} with
     * {@code %,.2f} does - so Arabic and English both read naturally without this knowing which is
     * active. An empty cell stays empty rather than printing {@code 0.00}: a row with nothing in a
     * column is not a row with zero in it.
     */
    public static <S> TableColumn<S, BigDecimal> money(String titleKey, Function<S, BigDecimal> extractor) {
        return formatted(column(titleKey, extractor), value -> value, Columns::money);
    }

    /**
     * Writes an existing column's numbers the way {@link #money(String, Function)} writes its own -
     * for a column that holds a {@code Number} rather than a {@code BigDecimal}, such as one bound to
     * a {@code DoubleProperty} through {@link #observable}. An editable column keeps the alignment
     * but gets its cell from its editor; give that editor a converter that writes the same way.
     */
    public static <S, N extends Number> TableColumn<S, N> asMoney(TableColumn<S, N> column) {
        return formatted(column, Columns::decimalOf, Columns::money);
    }

    /** The same for a quantity - see {@link #quantity(BigDecimal)}. */
    public static <S, N extends Number> TableColumn<S, N> asQuantity(TableColumn<S, N> column) {
        return formatted(column, Columns::decimalOf, Columns::quantity);
    }

    /** The same for a screen that holds its amounts as {@code double}. */
    public static <S> TableColumn<S, BigDecimal> moneyOfDouble(String titleKey, Function<S, Double> extractor) {
        return money(titleKey, row -> {
            Double value = extractor.apply(row);
            return value == null ? null : BigDecimal.valueOf(value);
        });
    }

    /**
     * One amount as a person reads it. Also for labels and totals, so a figure in a footer matches
     * the column it sums.
     */
    public static String money(BigDecimal value) {
        return value == null ? "" : String.format("%,.2f", value.setScale(2, RoundingMode.HALF_UP));
    }

    /**
     * A quantity as a person reads it: thousands separated, and only the decimals it has, up to
     * three - {@code 3}, {@code 2.5}, {@code 0.625}. A quantity is not money: {@code 3.00} pieces
     * reads as a price, and {@code 3.0} is a {@code double} showing through. Three places, because
     * an item weighed by the kilogram is sold to the gram.
     */
    public static String quantity(BigDecimal value) {
        if (value == null) {
            return "";
        }
        DecimalFormat format = new DecimalFormat("#,##0.###", DecimalFormatSymbols.getInstance());
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(value);
    }

    /** How every figure column is aligned, so the decimal points of a column sit under each other. */
    public static final String AMOUNT_ALIGNMENT = "-fx-alignment: CENTER-RIGHT;";

    /**
     * Set on a money cell holding a negative. Styled in {@code app-theme.css} rather than with an
     * inline {@code setStyle}, so it answers the dark palette - and so a screen cannot colour the
     * same condition a different red.
     */
    public static final PseudoClass NEGATIVE = PseudoClass.getPseudoClass("negative-amount");

    /**
     * Escape hatch for anything the three builders above do not cover - a boolean, a button, a custom type.
     * The value is read once, when the cell is built; a row whose values change in place wants
     * {@link #observable}.
     */
    public static <S, T> TableColumn<S, T> column(String titleKey, Function<S, ? extends T> extractor) {
        TableColumn<S, T> column = new TableColumn<>(LanguageManager.getInstance().getString(titleKey));
        column.setCellValueFactory(features -> new ReadOnlyObjectWrapper<>(extractor.apply(features.getValue())));
        return column;
    }

    private static <S, T> TableColumn<S, T> formatted(TableColumn<S, T> column,
                                                      Function<T, BigDecimal> toDecimal,
                                                      Function<BigDecimal, String> format) {
        column.setStyle(AMOUNT_ALIGNMENT);
        column.setCellFactory(ignored -> new TableCell<>() {
            @Override
            protected void updateItem(T value, boolean empty) {
                super.updateItem(value, empty);
                BigDecimal amount = empty || value == null ? null : toDecimal.apply(value);
                setText(amount == null ? null : format.apply(amount));
                pseudoClassStateChanged(NEGATIVE, amount != null && amount.signum() < 0);
            }
        });
        return column;
    }

    /** A {@code double} that is not a number has nothing to show; it is not zero. */
    private static BigDecimal decimalOf(Number value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        double asDouble = value.doubleValue();
        return Double.isFinite(asDouble) ? BigDecimal.valueOf(asDouble) : null;
    }
}
