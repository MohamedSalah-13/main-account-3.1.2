package com.hamza.account.config;

import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * A semantic name for an icon, each mapped to one Feather glyph through
 * {@link IconFactory}. See rule ق-ل4 in {@code docs/new-code-rules.md}.
 * <p>
 * {@code Image_Setting} opens roughly forty {@code InputStream} fields in its
 * own initialisers on every single construction - one gets used, the rest
 * leak unclosed - and an {@code InputStream} is single-use, so reading the
 * same field twice returns an empty image the second time. A PNG also cannot
 * be recolored, so it cannot answer a dark-mode or high-contrast palette the
 * way {@code -fx-icon-color} on a {@link FontIcon} can.
 * <p>
 * This enum is a starting catalogue, not a completed migration - the eleven
 * names below are exactly the Feather glyphs three screens
 * ({@code UnitsController}, {@code UpdateSomeItems}, {@code SearchItemsController})
 * already ship today, given a name instead of being written inline at each
 * call site. Nothing in {@code Image_Setting} has been touched: rule ق-ل5's
 * "one touch" approach means a screen adopts {@code AppIcon} in place of
 * {@code Image_Setting} when that screen is next opened for its own reasons,
 * not as a single sweep across all ~69 call sites with no way here to check
 * each one still renders correctly.
 */
public enum AppIcon {

    SAVE(Feather.SAVE),
    CONFIRM(Feather.CHECK),
    CLOSE(Feather.X),
    CLEAR(Feather.ROTATE_CCW),
    REFRESH(Feather.REFRESH_CW),
    SEARCH(Feather.SEARCH),
    ADD(Feather.PLUS),
    INFO(Feather.INFO),
    PERCENT(Feather.PERCENT),
    DELETE(Feather.TRASH_2),
    EDIT(Feather.EDIT_2),
    PRINT(Feather.PRINTER),
    EXPORT(Feather.DOWNLOAD),
    SETTINGS(Feather.SETTINGS),
    EXIT(Feather.LOG_OUT),
    DUPLICATE(Feather.COPY),
    SHOW(Feather.EYE),
    MAIN_GROUP(Feather.FOLDER),
    SUB_GROUP(Feather.FOLDER_PLUS),
    ITEM(Feather.PACKAGE),
    TREASURY_CASH(Feather.DOLLAR_SIGN),
    TREASURY_WALLET(Feather.SMARTPHONE),
    TREASURY_BANK(Feather.CREDIT_CARD);

    private final Ikon glyph;

    AppIcon(Ikon glyph) {
        this.glyph = glyph;
    }

    /** A fresh {@link FontIcon} at {@link IconFactory#baseSize()}. Build a new one per use; a Node has one parent. */
    public FontIcon graphic() {
        return IconFactory.of(glyph);
    }

    public FontIcon graphic(int size) {
        return IconFactory.of(glyph, size);
    }
}
