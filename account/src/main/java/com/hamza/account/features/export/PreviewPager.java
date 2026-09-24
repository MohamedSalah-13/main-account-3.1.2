package com.hamza.account.features.export;

/**
 * Which page of a preview is showing and at what size - the decisions of the print preview window,
 * over plain numbers, so they are tested without a toolkit.
 * <p>
 * Pages are counted from zero here and shown from one. Every move is clamped rather than refused: a
 * "next" on the last page stays there, as a typed page number past the end goes to the last page
 * ({@code PageJump}'s rule, which the window uses for the typed number).
 * <p>
 * <b>The size is a fit until somebody zooms.</b> "The whole page" and "the page's width" are answered
 * afresh from the window every time it is resized; a zoom is a scale of its own and stays put. Zooming
 * steps from the scale the page is shown at now, so the first press after a fit moves one step from
 * what is on screen rather than jumping to some remembered figure.
 */
public final class PreviewPager {

    public enum Fit {
        /** The whole page inside the window. */
        PAGE,
        /** The page's width across the window; its height scrolls. */
        WIDTH,
        /** A scale somebody chose with the zoom buttons. */
        ZOOM
    }

    /** The zoom steps, as scales of the page's own size in points. */
    static final double[] ZOOM_STEPS = {0.5, 0.67, 0.75, 0.9, 1.0, 1.25, 1.5, 1.75, 2.0, 2.5, 3.0};

    private final int pageCount;
    private int page;
    private Fit fit = Fit.PAGE;
    private double zoom = 1.0;

    public PreviewPager(int pageCount) {
        if (pageCount < 1) {
            throw new IllegalArgumentException("A preview needs at least one page, got " + pageCount);
        }
        this.pageCount = pageCount;
    }

    public int pageCount() {
        return pageCount;
    }

    /** The page showing, counted from zero. */
    public int page() {
        return page;
    }

    public Fit fit() {
        return fit;
    }

    public boolean isFirst() {
        return page == 0;
    }

    public boolean isLast() {
        return page == pageCount - 1;
    }

    /** @return whether the page changed */
    public boolean next() {
        return goTo(page + 1);
    }

    /** @return whether the page changed */
    public boolean previous() {
        return goTo(page - 1);
    }

    /** @return whether the page changed */
    public boolean first() {
        return goTo(0);
    }

    /** @return whether the page changed */
    public boolean last() {
        return goTo(pageCount - 1);
    }

    /**
     * @param index counted from zero; held inside the document
     * @return whether the page changed
     */
    public boolean goTo(int index) {
        int target = Math.clamp(index, 0, pageCount - 1);
        boolean changed = target != page;
        page = target;
        return changed;
    }

    public void fitPage() {
        fit = Fit.PAGE;
    }

    public void fitWidth() {
        fit = Fit.WIDTH;
    }

    /**
     * One step larger than the page is shown at now.
     *
     * @param current the scale on screen - {@link #scale} for the window as it is
     */
    public void zoomIn(double current) {
        zoom = ZOOM_STEPS[ZOOM_STEPS.length - 1];
        for (double step : ZOOM_STEPS) {
            if (step > current + 0.001) {
                zoom = step;
                break;
            }
        }
        fit = Fit.ZOOM;
    }

    /** One step smaller than the page is shown at now. */
    public void zoomOut(double current) {
        zoom = ZOOM_STEPS[0];
        for (int i = ZOOM_STEPS.length - 1; i >= 0; i--) {
            if (ZOOM_STEPS[i] < current - 0.001) {
                zoom = ZOOM_STEPS[i];
                break;
            }
        }
        fit = Fit.ZOOM;
    }

    /**
     * The scale a page is shown at: pixels per point of the page.
     *
     * @param pageWidth      the page's width in points, as it is drawn (a sideways page is wide)
     * @param pageHeight     its height in points
     * @param viewportWidth  the room the window has for it, in pixels
     * @param viewportHeight the room's height
     */
    public double scale(double pageWidth, double pageHeight, double viewportWidth, double viewportHeight) {
        if (pageWidth <= 0 || pageHeight <= 0) {
            return 1.0;
        }
        double width = Math.max(1, viewportWidth);
        double height = Math.max(1, viewportHeight);
        return switch (fit) {
            case PAGE -> Math.min(width / pageWidth, height / pageHeight);
            case WIDTH -> width / pageWidth;
            case ZOOM -> zoom;
        };
    }

    public boolean canZoomIn(double current) {
        return current < ZOOM_STEPS[ZOOM_STEPS.length - 1] - 0.001;
    }

    public boolean canZoomOut(double current) {
        return current > ZOOM_STEPS[0] + 0.001;
    }
}
