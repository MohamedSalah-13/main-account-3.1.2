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
}
