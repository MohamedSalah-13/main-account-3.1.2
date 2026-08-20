package com.hamza.account.config;

import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Builds a {@link FontIcon} at the app's standard size and style class. This
 * is the sizing/styling logic that used to be copy-pasted, identically, as a
 * private {@code icon(Ikon)} method in three controllers
 * ({@code UnitsController}, {@code UpdateSomeItems}, {@code SearchItemsController}) -
 * this class is that method, written once.
 * <p>
 * Size is 16px scaled by {@link UiScale#factor()} so an icon grows the same
 * way the rest of the UI does when a user picks a larger font size. A header
 * icon typically wants a multiple of this - see the {@code * 2} calls at the
 * three call sites above.
 */
public final class IconFactory {

    private static final int BASE_SIZE = 16;

    private IconFactory() {
    }

    public static FontIcon of(Ikon glyph) {
        return of(glyph, baseSize());
    }

    public static FontIcon of(Ikon glyph, int size) {
        FontIcon fontIcon = new FontIcon(glyph);
        fontIcon.setIconSize(size);
        fontIcon.getStyleClass().add("icon-graphic");
        return fontIcon;
    }

    public static int baseSize() {
        return (int) Math.round(BASE_SIZE * UiScale.factor());
    }
}
