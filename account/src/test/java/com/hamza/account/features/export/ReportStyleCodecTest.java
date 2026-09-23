package com.hamza.account.features.export;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportStyleCodecTest {

    @Test
    void everyValueSurvivesTheRoundTrip() {
        ReportStyle style = new ReportStyle(24, 13, 14, 9, 12, 8, true, PageNumbering.PAGE_OF_TOTAL,
                true, false, false, false, true, false, "شكرا لتعاملكم = مرحبا \\ بكم",
                ReportPalette.MAROON, true, false, 35);

        assertEquals(style, ReportStyleCodec.decode(ReportStyleCodec.encode(style)));
    }

    @Test
    void theDefaultSurvivesTheRoundTrip() {
        assertEquals(ReportStyle.DEFAULT, ReportStyleCodec.decode(ReportStyleCodec.encode(ReportStyle.DEFAULT)));
    }

    @Test
    void nothingStoredIsTheDefault() {
        assertEquals(ReportStyle.DEFAULT, ReportStyleCodec.decode(null));
        assertEquals(ReportStyle.DEFAULT, ReportStyleCodec.decode("   "));
        assertEquals(ReportStyle.DEFAULT, ReportStyleCodec.decode("not a style at all"));
    }

    /** A style stored before a setting existed reads as that setting's default. */
    @Test
    void aMissingLineKeepsItsDefault() {
        ReportStyle style = ReportStyleCodec.decode("v=1\nheader=15");
        assertEquals(ReportStyle.DEFAULT.toBuilder().headerSize(15).build(), style);
    }

    /** A value another build wrote, or somebody typed, must never stop a report from printing. */
    @Test
    void anUnreadableValueKeepsItsDefaultAndAnUnknownNameIsPassedOver() {
        ReportStyle style = ReportStyleCodec.decode(
                "header=big\ncompact=yes\nnumbering=ROMAN\npalette=GOLD\nsomethingNew=3\nbody=13");
        assertEquals(ReportStyle.DEFAULT.toBuilder().bodySize(13).build(), style);
    }

    @Test
    void anOutOfRangeValueIsHeldInsideItsRange() {
        assertEquals(ReportStyle.MAX_FONT_SIZE, ReportStyleCodec.decode("title=900").titleSize());
    }

    /** One {@code app_setting} row holds it, and that column is {@code VARCHAR(1000)}. */
    @Test
    void theLongestStyleFitsTheSharedSettingColumn() {
        String footer = "\\".repeat(ReportStyle.MAX_FOOTER_LENGTH);
        String encoded = ReportStyleCodec.encode(ReportStyle.DEFAULT.toBuilder().footerText(footer).build());
        assertTrue(encoded.length() <= 1000, "encoded length " + encoded.length());
    }
}
