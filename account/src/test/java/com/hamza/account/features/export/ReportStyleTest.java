package com.hamza.account.features.export;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportStyleTest {

    @Nested
    class TheDefault {

        /** The sizes the renderer wrote as constants before there was a style - so no page moves on upgrade. */
        @Test
        void printsTheSizesEveryReportAlreadyHad() {
            ReportStyle style = ReportStyle.DEFAULT;
            assertEquals(20, style.titleSize());
            assertEquals(12, style.subtitleSize());
            assertEquals(11, style.headerSize());
            assertEquals(11, style.bodySize());
            assertEquals(10, style.branchRowSize());
            assertEquals(10, style.totalsSize());
            assertEquals(10, style.smallSize());
            assertEquals(8, style.pageNumberSize(), "an invoice's page number has always been 8 points");
        }

        @Test
        void keepsTheHeadAndFootAsTheyWere() {
            ReportStyle style = ReportStyle.DEFAULT;
            assertFalse(style.showLetterhead(), "no report carried the company before");
            assertTrue(style.showTitle() && style.showSubtitle() && style.showPrintedAt() && style.showFooter());
            assertFalse(style.showPrintedBy(), "a statement is handed to a customer");
            assertTrue(style.showDocumentLetterhead(), "every invoice carries its letterhead");
            assertEquals(ReportPalette.BLUE, style.palette());
            assertFalse(style.inkSaver());
            assertFalse(style.compactRows());
        }

        /** {@code 1 / 3} is what every invoice already printed at its foot. */
        @Test
        void numbersPagesTheWayAnInvoiceAlwaysDid() {
            assertEquals(PageNumbering.SLASH, ReportStyle.DEFAULT.pageNumbering());
        }

        /** The colours the renderer held as constants: the blue heading, the totals band, the light band. */
        @Test
        void isTheBlueEveryPagePrintedIn() {
            assertEquals(0x2980B9, ReportPalette.BLUE.heading());
            assertEquals(0x3498DB, ReportPalette.BLUE.totals());
            assertEquals(0xD6EAF8, ReportPalette.BLUE.band());
            assertEquals(0x154360, ReportPalette.BLUE.bandText());
        }
    }

    @Nested
    class OutOfRangeValues {

        @Test
        void aFontSizeIsHeldInsideItsRange() {
            ReportStyle style = ReportStyle.DEFAULT.toBuilder().titleSize(400).bodySize(1).build();
            assertEquals(ReportStyle.MAX_FONT_SIZE, style.titleSize());
            assertEquals(ReportStyle.MIN_FONT_SIZE, style.bodySize());
            assertEquals(ReportStyle.MIN_FONT_SIZE, style.branchRowSize(), "never below the smallest size");
        }

        @Test
        void theTopSpaceIsHeldInsideItsRange() {
            assertEquals(0, ReportStyle.DEFAULT.toBuilder().documentTopSpaceMm(-5).build().documentTopSpaceMm());
            assertEquals(ReportStyle.MAX_TOP_SPACE_MM,
                    ReportStyle.DEFAULT.toBuilder().documentTopSpaceMm(999).build().documentTopSpaceMm());
        }

        @Test
        void aMissingChoiceFallsBackToTheDefault() {
            ReportStyle style = ReportStyle.DEFAULT.toBuilder().pageNumbering(null).palette(null).footerText(null).build();
            assertEquals(PageNumbering.SLASH, style.pageNumbering());
            assertEquals(ReportPalette.BLUE, style.palette());
            assertEquals("", style.footerText());
        }

        /** A wrapped Arabic paragraph prints its end first, so the footer is kept to one line. */
        @Test
        void theFooterIsOneTrimmedLineOfBoundedLength() {
            assertEquals("شكرا لتعاملكم معنا",
                    ReportStyle.DEFAULT.toBuilder().footerText("  شكرا\nلتعاملكم\r\nمعنا  ").build().footerText());
            String longText = "ا".repeat(ReportStyle.MAX_FOOTER_LENGTH + 40);
            assertEquals(ReportStyle.MAX_FOOTER_LENGTH,
                    ReportStyle.DEFAULT.toBuilder().footerText(longText).build().footerText().length());
        }
    }

    @Test
    void theBuilderChangesOnlyWhatItIsTold() {
        ReportStyle changed = ReportStyle.DEFAULT.toBuilder().headerSize(14).inkSaver(true).build();
        assertEquals(14, changed.headerSize());
        assertTrue(changed.inkSaver());
        assertEquals(ReportStyle.DEFAULT.toBuilder().headerSize(14).inkSaver(true).build(), changed);
        assertEquals(ReportStyle.DEFAULT, ReportStyle.DEFAULT.toBuilder().build());
    }
}
