package com.hamza.controlsfx.table;

import com.hamza.controlsfx.language.LanguageManager;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.control.TableColumn;

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

    /** Formats with {@link DateTimeFormatter#ISO_LOCAL_DATE}; a null date renders as an empty cell. */
    public static <S> TableColumn<S, String> date(String titleKey, Function<S, LocalDate> extractor) {
        return text(titleKey, row -> {
            LocalDate value = extractor.apply(row);
            return value == null ? "" : value.format(DateTimeFormatter.ISO_LOCAL_DATE);
        });
    }

    /** Escape hatch for anything the three builders above do not cover - a boolean, a button, a custom type. */
    public static <S, T> TableColumn<S, T> column(String titleKey, Function<S, ? extends T> extractor) {
        TableColumn<S, T> column = new TableColumn<>(LanguageManager.getInstance().getString(titleKey));
        column.setCellValueFactory(features -> new ReadOnlyObjectWrapper<>(extractor.apply(features.getValue())));
        return column;
    }
}
