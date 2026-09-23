package com.hamza.account.features.export;

/**
 * The colours a printed page wears: the heading row, the totals band, the light band a branch or a
 * summary sits on, and the dark text written on that band.
 * <p>
 * A choice of a few finished sets rather than a colour picker. Four colours have to agree with one
 * another - white text has to read on the heading and on the totals, dark text on the band - and a
 * picker would let one of them be chosen without the other three. {@link #BLUE} is the set every page
 * printed in before there was a choice, value for value.
 * <p>
 * Each colour is {@code 0xRRGGBB}, so the settings screen and the PDF read the same numbers.
 */
public enum ReportPalette {

    BLUE(0x2980B9, 0x3498DB, 0xD6EAF8, 0x154360),
    GREEN(0x1E8449, 0x229954, 0xD4EFDF, 0x145A32),
    CHARCOAL(0x34495E, 0x5D6D7E, 0xD5D8DC, 0x1B2631),
    MAROON(0x922B21, 0xC0392B, 0xF2D7D5, 0x641E16),
    PURPLE(0x6C3483, 0x8E44AD, 0xE8DAEF, 0x4A235A);

    private final int heading;
    private final int totals;
    private final int band;
    private final int bandText;

    ReportPalette(int heading, int totals, int band, int bandText) {
        this.heading = heading;
        this.totals = totals;
        this.band = band;
        this.bandText = bandText;
    }

    /** The table's heading row, written in white; also the document's title and its rule. */
    public int heading() {
        return heading;
    }

    /** The closing totals line, written in white. */
    public int totals() {
        return totals;
    }

    /** The light band under a branch heading, a subtotal or the figure a document's reader looks for. */
    public int band() {
        return band;
    }

    /** The text written on {@link #band()}. */
    public int bandText() {
        return bandText;
    }

    /** The stored name back, or {@link #BLUE} for anything this build does not know. */
    public static ReportPalette fromStoredValue(String value) {
        if (value != null) {
            for (ReportPalette palette : values()) {
                if (palette.name().equals(value.trim())) {
                    return palette;
                }
            }
        }
        return BLUE;
    }
}
