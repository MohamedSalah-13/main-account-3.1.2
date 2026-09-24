package com.hamza.account.features.export;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportSetupTest {

    private static final ReportLabels ARABIC =
            new ReportLabels("تاريخ التقرير", "طبع بواسطة", "صفحة %d من %d", "تم إنشاء هذا التقرير بواسطة نظام الحسابات");
    private static final ReportLetterhead COMPANY =
            new ReportLetterhead("شركة الأمل", List.of("القاهرة", "هاتف: 0100"), null);

    private static ReportSetup setup(ReportStyle style) {
        return new ReportSetup(style, COMPANY, ARABIC, "admin");
    }

    @Nested
    class ThePrintedLine {

        @Test
        void saysWhenByDefaultAndNotWho() {
            assertEquals("تاريخ التقرير: 2026-09-23 10:30", setup(ReportStyle.DEFAULT).printedLine("2026-09-23 10:30"));
        }

        @Test
        void saysWhoWhenAsked() {
            ReportStyle style = ReportStyle.DEFAULT.toBuilder().showPrintedBy(true).build();
            assertEquals("تاريخ التقرير: 2026-09-23 10:30" + ReportSetup.SEPARATOR + "طبع بواسطة: admin",
                    setup(style).printedLine("2026-09-23 10:30"));
        }

        @Test
        void isEmptyWhenNeitherIsWanted() {
            ReportStyle style = ReportStyle.DEFAULT.toBuilder().showPrintedAt(false).build();
            assertEquals("", setup(style).printedLine("2026-09-23 10:30"));
        }

        @Test
        void leavesOutANameThatIsNotThere() {
            ReportStyle style = ReportStyle.DEFAULT.toBuilder().showPrintedAt(false).showPrintedBy(true).build();
            assertEquals("", new ReportSetup(style, COMPANY, ARABIC, "  ").printedLine("2026-09-23"));
        }

        @Test
        void withoutWordsIsTheBareValue() {
            assertEquals("2026-09-23", ReportSetup.plain().printedLine("2026-09-23"));
        }
    }

    @Nested
    class TheFooter {

        @Test
        void isTheApplicationsSentenceUntilTheShopWritesItsOwn() {
            assertEquals(ARABIC.defaultFooter(), setup(ReportStyle.DEFAULT).footer());
            assertEquals("شكرا لتعاملكم",
                    setup(ReportStyle.DEFAULT.toBuilder().footerText("شكرا لتعاملكم").build()).footer());
        }

        @Test
        void isNothingWhenTurnedOff() {
            assertEquals("", setup(ReportStyle.DEFAULT.toBuilder().showFooter(false).footerText("x").build()).footer());
        }
    }

    @Nested
    class ThePageNumber {

        @Test
        void followsTheStyle() {
            assertEquals("2 / 5", setup(ReportStyle.DEFAULT).pageText(2, 5));
            assertEquals("صفحة 2 من 5",
                    setup(ReportStyle.DEFAULT.toBuilder().pageNumbering(PageNumbering.PAGE_OF_TOTAL).build()).pageText(2, 5));
            assertEquals("", setup(ReportStyle.DEFAULT.toBuilder().pageNumbering(PageNumbering.NONE).build()).pageText(2, 5));
        }

        /** A broken translation must not fail the file or print a half-formatted sentence. */
        @Test
        void fallsBackToDigitsWhenThePatternCannotBeUsed() {
            assertEquals("2 / 5", PageNumbering.PAGE_OF_TOTAL.text(2, 5, ""));
            assertEquals("2 / 5", PageNumbering.PAGE_OF_TOTAL.text(2, 5, "صفحة %s من %d %d"));
        }
    }

    @Nested
    class TheLetterhead {

        @Test
        void isPrintedOnlyWhenAskedAndThereIsSomethingToPrint() {
            ReportStyle asked = ReportStyle.DEFAULT.toBuilder().showLetterhead(true).build();
            assertFalse(setup(ReportStyle.DEFAULT).printsLetterhead());
            assertTrue(setup(asked).printsLetterhead());
            assertFalse(new ReportSetup(asked, ReportLetterhead.EMPTY, ARABIC, "").printsLetterhead());
        }

        @Test
        void dropsBlankLinesAndAnEmptyPicture() {
            ReportLetterhead letterhead = new ReportLetterhead(" شركة ", Arrays.asList("", null, " القاهرة "), new byte[0]);
            assertEquals("شركة", letterhead.name());
            assertEquals(List.of("القاهرة"), letterhead.lines());
            assertNull(letterhead.logo());
            assertTrue(new ReportLetterhead(null, null, null).isEmpty());
        }
    }

    @Nested
    class TheInstalledSource {

        @AfterEach
        void uninstall() {
            ReportSetups.uninstall();
        }

        @Test
        void isThePlainSetupUntilOneIsInstalled() {
            assertEquals(ReportSetup.plain(), ReportSetups.current());
        }

        @Test
        void answersWhatWasInstalled() {
            ReportSetup mine = setup(ReportStyle.DEFAULT);
            ReportSetups.install(() -> mine);
            assertSame(mine, ReportSetups.current());
        }

        /** A company read that fails costs the page its letterhead, never the page. */
        @Test
        void aSourceThatFailsPrintsThePlainSetup() {
            ReportSetups.install(() -> {
                throw new IllegalStateException("the database went away");
            });
            assertEquals(ReportSetup.plain(), ReportSetups.current());
        }
    }
}
