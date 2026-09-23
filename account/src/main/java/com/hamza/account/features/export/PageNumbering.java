package com.hamza.account.features.export;

import java.util.IllegalFormatException;

/**
 * Whether a printed page carries its number, and how it is written.
 * <p>
 * {@link #SLASH} is what an invoice has always printed at its foot ({@code 1 / 3}), and is the
 * default so that no invoice changes on upgrade; a report, which carried no number at all, now gets
 * the same one.
 */
public enum PageNumbering {

    /** No number on the page. */
    NONE,

    /** {@code 1 / 3}: digits only, the same in every language. */
    SLASH,

    /** {@code صفحة 1 من 3}: the words come from the language's own pattern. */
    PAGE_OF_TOTAL;

    /**
     * The text printed on one page.
     *
     * @param pageOfPattern a {@code String.format} pattern taking the page and then the count, such as
     *                      {@code "صفحة %d من %d"}; when it is blank or a translator broke it, the page
     *                      falls back to {@code 1 / 3} rather than printing nothing or failing the file
     * @return the text, or an empty string for {@link #NONE}
     */
    public String text(int page, int pages, String pageOfPattern) {
        return switch (this) {
            case NONE -> "";
            case SLASH -> slash(page, pages);
            case PAGE_OF_TOTAL -> {
                if (pageOfPattern == null || pageOfPattern.isBlank()) {
                    yield slash(page, pages);
                }
                try {
                    yield String.format(pageOfPattern, page, pages);
                } catch (IllegalFormatException e) {
                    yield slash(page, pages);
                }
            }
        };
    }

    private static String slash(int page, int pages) {
        return page + " / " + pages;
    }

    /** The stored name back, or {@link #SLASH} for anything this build does not know. */
    public static PageNumbering fromStoredValue(String value) {
        if (value != null) {
            for (PageNumbering numbering : values()) {
                if (numbering.name().equals(value.trim())) {
                    return numbering;
                }
            }
        }
        return SLASH;
    }
}
