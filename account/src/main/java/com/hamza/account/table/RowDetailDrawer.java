package com.hamza.account.table;

import com.hamza.account.config.AppIcon;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.animation.Interpolator;
import javafx.animation.TranslateTransition;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * The panel a row opens over its own screen, in place of a second table underneath it.
 *
 * <h2>Why</h2>
 * A master table with its detail table stacked below shares one column of height between
 * two lists. On a 1366x768 screen that is about 300 points each once the toolbar, the
 * summary card and the tab strip have taken theirs - four rows in the list you are reading
 * and four in the list you are checking it against. The screens that do it were laid out on
 * a large monitor, where it looks reasonable; the till it ships to is not one.
 *
 * <p>The detail therefore moves <em>over</em> the master, along one edge, for as long as it
 * is being read. The table keeps its full height, and the panel gets a full column of its
 * own rather than a fifth of one.
 *
 * <h2>The three decisions in it</h2>
 * <ul>
 *   <li><b>No scrim.</b> A dimmed backdrop would make this a modal dialog with a different
 *       shape, and the gesture it exists for is running down a list - click a row, read it,
 *       click the next. The table stays live underneath, so the caller can keep an open
 *       panel pointed at whichever row is selected.</li>
 *   <li><b>It is laid out, not attached and detached.</b> The panel is a child of the host
 *       from the moment it is installed and is only made invisible, so the slide has
 *       something to measure, and an invisible node is not pickable - the table underneath
 *       goes on receiving the clicks it would otherwise have swallowed.</li>
 *   <li><b>The edge is the trailing one, and it does not depend on the language.</b> The
 *       panel covers the columns a table puts last, never the ones that say which row is
 *       being read. JavaFX renders an RTL node's children mirrored, so the logical right
 *       <em>is</em> the trailing edge in both directions - visually left in Arabic, visually
 *       right in English - and one anchor answers both. The first draft flipped it with the
 *       language, which is the same mistake in mirror image: running it in English put the
 *       panel over "View", "Role" and "Item", the three columns that say which row it is
 *       describing. Nothing but opening it in the other language could show that.</li>
 * </ul>
 *
 * <h2>Using it</h2>
 * The caller owns the content node and keeps it loaded; this class owns the header, the
 * close button, Escape, the width and the animation.
 */
public final class RowDetailDrawer {

    /** Narrow enough for a small window; below it the panel simply covers the screen. */
    private static final double MIN_WIDTH = 380;
    /** Of the host's width, when the caller has not said what its content needs. */
    private static final double SHARE = 0.45;
    /** Under this, splitting the width leaves two unusable halves, so the panel takes it all. */
    private static final double FULL_COVER_BELOW = 760;
    private static final Duration SLIDE = Duration.millis(180);
    /**
     * The logical right, which mirroring makes the trailing edge in both reading directions.
     * It is named because {@link #hiddenOffsetFor} has to agree with it.
     */
    private static final double TRAILING_ANCHOR = 0.0;

    private final AnchorPane host;
    private final VBox panel = new VBox();
    private final Label title = new Label();
    private final Label subtitle = new Label();
    private final StackPane body = new StackPane();
    /** What the content says it needs; zero means "use {@link #SHARE}". */
    private final SimpleDoubleProperty preferredWidth = new SimpleDoubleProperty(0);

    private boolean showing;
    private Runnable onHidden;

    private RowDetailDrawer(AnchorPane host) {
        this.host = host;
    }

    /**
     * @param host the screen's root. An {@link AnchorPane} because that is what an overlay
     *             needs - a pane that lays a child out against its own edges without
     *             disturbing the siblings already anchored across all four of them
     */
    public static RowDetailDrawer installIn(AnchorPane host) {
        RowDetailDrawer drawer = new RowDetailDrawer(host);
        drawer.build();
        return drawer;
    }

    private void build() {
        title.getStyleClass().add("detail-drawer-title");
        title.setWrapText(true);
        subtitle.getStyleClass().add("detail-drawer-subtitle");
        subtitle.setWrapText(true);

        VBox captions = new VBox(title, subtitle);
        HBox.setHgrow(captions, Priority.ALWAYS);

        HBox header = new HBox(8, captions, close());
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("detail-drawer-header");

        body.getStyleClass().add("detail-drawer-body");
        VBox.setVgrow(body, Priority.ALWAYS);

        panel.getStyleClass().add("detail-drawer");
        panel.getChildren().setAll(header, body);
        panel.setVisible(false);

        panel.prefWidthProperty().bind(Bindings.createDoubleBinding(
                () -> widthFor(host.getWidth()), host.widthProperty(), preferredWidth));
        panel.minWidthProperty().bind(panel.prefWidthProperty());
        panel.maxWidthProperty().bind(panel.prefWidthProperty());

        AnchorPane.setTopAnchor(panel, 0.0);
        AnchorPane.setBottomAnchor(panel, 0.0);
        AnchorPane.setRightAnchor(panel, TRAILING_ANCHOR);
        host.getChildren().add(panel);

        // On the host rather than on the panel: Escape has to close it whether the focus
        // is inside the detail or still in the table the user is clicking down.
        host.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (showing && event.getCode() == KeyCode.ESCAPE) {
                hide();
                event.consume();
            }
        });
    }

    private Button close() {
        Button button = new Button();
        button.setGraphic(AppIcon.CLOSE.graphic());
        button.getStyleClass().add("icon-button");
        String text = LanguageManager.getInstance().getString("common.close");
        button.setTooltip(new Tooltip(text));
        button.setAccessibleText(text);
        button.setOnAction(event -> hide());
        return button;
    }

    /** What the panel shows. Set once; the caller reloads the node's own contents. */
    public void setContent(Node content) {
        body.getChildren().setAll(content);
        if (content instanceof Region region) {
            region.setMaxWidth(Double.MAX_VALUE);
            region.setMaxHeight(Double.MAX_VALUE);
        }
    }

    /**
     * What the content needs, in points, when there is room for it.
     * <p>
     * Without this the panel takes a share of the window, and a share of a 1366-point screen
     * is not a number the content had any say in: the merge screen's operations are seven
     * columns needing some 650 points, and at 45% of that window they arrived two at a time
     * behind a horizontal scroll bar. It is still clamped - never wider than the host, never
     * narrower than {@link #MIN_WIDTH} - so a caller asking for more than the screen has
     * gets the screen.
     */
    public void setPreferredWidth(double width) {
        preferredWidth.set(width);
    }

    /** Called after the panel has closed, however it was closed. */
    public void setOnHidden(Runnable action) {
        this.onHidden = action;
    }

    public boolean isShowing() {
        return showing;
    }

    /**
     * Shows the panel, or - when it is already open - re-titles it for the row just
     * selected. That second case is the whole point of leaving the table live underneath.
     */
    public void show(String titleText, String subtitleText) {
        title.setText(titleText == null ? "" : titleText);
        subtitle.setText(subtitleText == null ? "" : subtitleText);
        boolean hasSubtitle = !subtitle.getText().isBlank();
        subtitle.setVisible(hasSubtitle);
        subtitle.setManaged(hasSubtitle);

        if (showing) {
            return;
        }
        showing = true;
        panel.setVisible(true);
        panel.setTranslateX(hiddenOffset());
        slideTo(0, null);
    }

    public void hide() {
        if (!showing) {
            return;
        }
        showing = false;
        slideTo(hiddenOffset(), () -> {
            panel.setVisible(false);
            if (onHidden != null) {
                onHidden.run();
            }
        });
    }

    private void slideTo(double target, Runnable finished) {
        TranslateTransition slide = new TranslateTransition(SLIDE, panel);
        slide.setToX(target);
        slide.setInterpolator(Interpolator.EASE_BOTH);
        if (finished != null) {
            slide.setOnFinished(event -> finished.run());
        }
        slide.play();
    }

    private double hiddenOffset() {
        double width = panel.getWidth() > 0 ? panel.getWidth() : widthFor(host.getWidth());
        return hiddenOffsetFor(width);
    }

    private double widthFor(double hostWidth) {
        return widthFor(hostWidth, preferredWidth.get());
    }

    // ------------------------------------------------------------------
    // The geometry, kept out of the control so it can be pinned without a toolkit. Every
    // rule below was a defect waiting to happen in four lines of JavaFX: see
    // RowDetailDrawerTest.
    // ------------------------------------------------------------------

    /**
     * Where the panel sits when it is closed, in its own coordinates.
     * <p>
     * It has to agree with {@link #TRAILING_ANCHOR}: a panel anchored to the logical right
     * leaves by moving logically right. One of the two written the other way and the panel
     * slides in from the wrong edge, or never arrives at all.
     */
    static double hiddenOffsetFor(double width) {
        return width;
    }

    /**
     * How wide the panel is: what the content asked for, or a share of the window when it
     * asked for nothing - never wider than the host, never below {@link #MIN_WIDTH}, and the
     * whole width once there is too little to divide.
     */
    static double widthFor(double hostWidth, double preferred) {
        if (hostWidth <= 0) {
            return MIN_WIDTH;
        }
        if (hostWidth <= FULL_COVER_BELOW) {
            return hostWidth;
        }
        double wanted = preferred > 0 ? preferred : hostWidth * SHARE;
        return Math.min(hostWidth, Math.max(MIN_WIDTH, wanted));
    }
}
