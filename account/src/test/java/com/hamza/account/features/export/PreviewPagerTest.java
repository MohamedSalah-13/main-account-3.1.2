package com.hamza.account.features.export;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreviewPagerTest {

    /** A4 in points. */
    private static final double A4_WIDTH = 595;
    private static final double A4_HEIGHT = 842;

    @Nested
    class Moving {

        @Test
        void aMovePastEitherEndStaysAtThatEnd() {
            PreviewPager pager = new PreviewPager(3);
            assertTrue(pager.isFirst());
            assertFalse(pager.previous(), "nothing before the first page");
            assertTrue(pager.next());
            assertTrue(pager.next());
            assertTrue(pager.isLast());
            assertFalse(pager.next(), "nothing after the last page");
            assertEquals(2, pager.page());
        }

        @Test
        void firstAndLastAndATypedPageAreHeldInsideTheDocument() {
            PreviewPager pager = new PreviewPager(12);
            assertTrue(pager.last());
            assertEquals(11, pager.page());
            assertTrue(pager.first());
            assertEquals(0, pager.page());
            pager.goTo(500);
            assertEquals(11, pager.page());
            pager.goTo(-4);
            assertEquals(0, pager.page());
        }

        @Test
        void aOnePageDocumentHasNowhereToGo() {
            PreviewPager pager = new PreviewPager(1);
            assertTrue(pager.isFirst() && pager.isLast());
            assertFalse(pager.next() || pager.previous() || pager.last());
        }

        @Test
        void aDocumentWithNoPageIsRefused() {
            assertThrows(IllegalArgumentException.class, () -> new PreviewPager(0));
        }
    }

    @Nested
    class Sizing {

        @Test
        void theWholePageFitsTheShorterSideOfTheWindow() {
            PreviewPager pager = new PreviewPager(1);
            // A tall, narrow window: the width decides.
            assertEquals(300 / A4_WIDTH, pager.scale(A4_WIDTH, A4_HEIGHT, 300, 900), 1e-9);
            // A wide, short window: the height decides.
            assertEquals(421 / A4_HEIGHT, pager.scale(A4_WIDTH, A4_HEIGHT, 1200, 421), 1e-9);
        }

        @Test
        void theWidthFitsTheWindowsWidthWhateverItsHeight() {
            PreviewPager pager = new PreviewPager(1);
            pager.fitWidth();
            assertEquals(1190 / A4_WIDTH, pager.scale(A4_WIDTH, A4_HEIGHT, 1190, 100), 1e-9);
        }

        /** The first press after a fit moves one step from what is on screen, not from a remembered figure. */
        @Test
        void aZoomStepsFromTheScaleOnScreen() {
            PreviewPager pager = new PreviewPager(1);
            double onScreen = pager.scale(A4_WIDTH, A4_HEIGHT, 800, 700); // ≈ 0.83
            pager.zoomIn(onScreen);
            assertEquals(PreviewPager.Fit.ZOOM, pager.fit());
            assertEquals(0.9, pager.scale(A4_WIDTH, A4_HEIGHT, 800, 700), 1e-9);
            pager.zoomOut(0.9);
            assertEquals(0.75, pager.scale(A4_WIDTH, A4_HEIGHT, 1, 1), 1e-9, "a zoom ignores the window");
        }

        @Test
        void aZoomStopsAtItsSmallestAndLargestSteps() {
            PreviewPager pager = new PreviewPager(1);
            pager.zoomIn(50);
            assertEquals(3.0, pager.scale(A4_WIDTH, A4_HEIGHT, 800, 700), 1e-9);
            assertFalse(pager.canZoomIn(3.0));
            pager.zoomOut(0.01);
            assertEquals(0.5, pager.scale(A4_WIDTH, A4_HEIGHT, 800, 700), 1e-9);
            assertFalse(pager.canZoomOut(0.5));
            assertTrue(pager.canZoomIn(0.5) && pager.canZoomOut(3.0));
        }

        @Test
        void aFitTakesOverFromAZoom() {
            PreviewPager pager = new PreviewPager(1);
            pager.zoomIn(1.0);
            pager.fitPage();
            assertEquals(PreviewPager.Fit.PAGE, pager.fit());
            assertEquals(400 / A4_HEIGHT, pager.scale(A4_WIDTH, A4_HEIGHT, 1000, 400), 1e-9);
        }
    }

    /** How a document opens: the whole page, unless that is too small to read. */
    @Nested
    class Opening {

        /** An 80mm roll in points, and a window of about 1366x768 less its bars. */
        private static final double ROLL_WIDTH = 226;
        private static final double VIEW_WIDTH = 1300;
        private static final double VIEW_HEIGHT = 600;

        @Test
        void aSheetOfPaperOpensWholeAsItAlwaysDid() {
            PreviewPager pager = new PreviewPager(1);
            pager.open(A4_WIDTH, A4_HEIGHT, VIEW_WIDTH, VIEW_HEIGHT);
            assertEquals(PreviewPager.Fit.PAGE, pager.fit());

            pager.open(1191, 842, VIEW_WIDTH, VIEW_HEIGHT);
            assertEquals(PreviewPager.Fit.PAGE, pager.fit(), "an A3 on its side too");
        }

        @Test
        void aShortReceiptOpensWhole() {
            PreviewPager pager = new PreviewPager(1);
            pager.open(ROLL_WIDTH, 520, VIEW_WIDTH, VIEW_HEIGHT);
            assertEquals(PreviewPager.Fit.PAGE, pager.fit());
        }

        @Test
        void aLongReceiptOpensAtASizeItCanBeReadAtAndScrolls() {
            PreviewPager pager = new PreviewPager(1);
            pager.open(ROLL_WIDTH, 920, VIEW_WIDTH, VIEW_HEIGHT);

            double scale = pager.scale(ROLL_WIDTH, 920, VIEW_WIDTH, VIEW_HEIGHT);
            assertEquals(PreviewPager.ROLL_SCALE, scale, 1e-9,
                    "not the 0.65 the whole of thirty lines would take, and not stretched across 1,300 pixels");
            assertEquals(PreviewPager.Fit.ZOOM, pager.fit());
        }

        @Test
        void inANarrowWindowItOpensAcrossTheWidthInstead() {
            PreviewPager pager = new PreviewPager(1);
            pager.open(ROLL_WIDTH, 3000, 300, VIEW_HEIGHT);

            assertEquals(PreviewPager.Fit.WIDTH, pager.fit());
            assertEquals(300 / ROLL_WIDTH, pager.scale(ROLL_WIDTH, 3000, 300, VIEW_HEIGHT), 1e-9);
        }

        @Test
        void aSheetOpensWholeHoweverSmallTheWindowMakesIt() {
            PreviewPager pager = new PreviewPager(1);
            pager.open(A4_WIDTH, A4_HEIGHT, VIEW_WIDTH, 300);
            assertEquals(PreviewPager.Fit.PAGE, pager.fit(), "a sheet's type is read smaller than a roll's");
        }

        @Test
        void aRollSmallOnlyBecauseTheWindowIsNarrowOpensWhole() {
            PreviewPager pager = new PreviewPager(1);
            pager.open(ROLL_WIDTH, 700, 120, VIEW_HEIGHT);
            assertEquals(PreviewPager.Fit.PAGE, pager.fit(), "fitting its width would not make it larger");
        }

        /** A 41x28mm label fitted to the window would be eight times its size, drawn at four and blurred. */
        @Test
        void aSmallPageIsNotFittedLargerThanItCanBeDrawn() {
            PreviewPager pager = new PreviewPager(1);
            pager.open(116, 79, VIEW_WIDTH, VIEW_HEIGHT);

            assertEquals(PreviewDocument.MAX_SCALE, pager.scale(116, 79, VIEW_WIDTH, VIEW_HEIGHT), 1e-9);
            pager.fitWidth();
            assertEquals(PreviewDocument.MAX_SCALE, pager.scale(116, 79, VIEW_WIDTH, VIEW_HEIGHT), 1e-9);
        }

        @Test
        void aWindowNotLaidOutYetLeavesTheWholePage() {
            PreviewPager pager = new PreviewPager(1);
            pager.open(ROLL_WIDTH, 3000, 0, 0);
            assertEquals(PreviewPager.Fit.PAGE, pager.fit());
        }
    }
}
