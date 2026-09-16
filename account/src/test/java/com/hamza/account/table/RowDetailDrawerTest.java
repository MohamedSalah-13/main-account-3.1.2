package com.hamza.account.table;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The drawer's geometry, which is the half of it that can be wrong without looking wrong.
 *
 * <p>Seeing the panel work in Arabic said nothing about English, and the first draft proved
 * it: the edge was flipped with the language, so in English the panel opened over the columns
 * naming the row instead of the ones after them. The edge is the trailing one in both
 * directions, and the offset has to say the same thing as the anchor.
 */
class RowDetailDrawerTest {

    @Nested
    class TheEdge {

        @Test
        void itLeavesTowardsTheEdgeItIsAnchoredTo() {
            // Anchored to the logical right, so it leaves by moving logically right - the
            // same number in both reading directions, because JavaFX mirrors an RTL node's
            // children and the logical right is the trailing edge either way.
            assertEquals(500, RowDetailDrawer.hiddenOffsetFor(500));
        }

        @Test
        void aPanelWithNoWidthYetDoesNotMove() {
            assertEquals(0, RowDetailDrawer.hiddenOffsetFor(0));
        }
    }

    @Nested
    class TheWidth {

        @Test
        void theContentIsAskedBeforeTheWindow() {
            // 45% of a 1080-point content area is 486, and the operations table is 655.
            assertEquals(690, RowDetailDrawer.widthFor(1080, 690));
        }

        @Test
        void contentAskingForNothingGetsAShareOfTheWindow() {
            assertEquals(486, RowDetailDrawer.widthFor(1080, 0));
        }

        @Test
        void itIsNeverWiderThanWhatItIsIn() {
            assertEquals(900, RowDetailDrawer.widthFor(900, 1400));
        }

        @Test
        void tooLittleToDivideMeansTheWholeWidth() {
            assertEquals(700, RowDetailDrawer.widthFor(700, 400));
        }

        @Test
        void aWindowThatHasNotBeenLaidOutYetGetsTheFloor() {
            assertEquals(380, RowDetailDrawer.widthFor(0, 690));
        }
    }
}
