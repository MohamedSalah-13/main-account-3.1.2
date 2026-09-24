package com.hamza.account.features.barcodeprint;

/**
 * How large the label screen shows the selected label: inside its card, and never larger than it is.
 * <p>
 * The label arrives drawn at the printer's own density, a pixel for every dot the head burns, so shown
 * pixel for pixel it is every dot and nothing else - enlarged, its bars are smeared between pixels by
 * the smoothing that keeps a shrunk one legible. It is shrunk only when its card is smaller than it.
 * <p>
 * The image used to have a fixed box of 300 by 210 whatever its card was, and the card a fixed minimum
 * height of 210: at the screen's own opening size, 1040 by 620, a double label covered the card's
 * title and spilled past its edge, and at its minimum size the whole column rose over the printer bar.
 */
public final class LabelPreviewFit {

    private LabelPreviewFit() {
    }

    /**
     * One side of the image as shown.
     *
     * @param available the room the card has on that side, inside its padding and border
     * @param natural   the label's own pixels on that side; zero or less while there is no label
     * @return at least one pixel - an {@code ImageView} reads a fit of zero as "the image's own size",
     *         which is the overflow this class exists to prevent, on the layout pass before the card
     *         has a size
     */
    public static double side(double available, double natural) {
        double room = Math.max(1, available);
        return natural > 0 ? Math.min(room, natural) : room;
    }
}
