package com.hamza.account.table;

import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.table.Columns;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

/**
 * A caption and its figures on one line under a report's card, each figure a label of its own read left
 * to right. Written into one Arabic sentence, a loss drew its minus on the far side of the number and a
 * percentage its sign after it.
 *
 * <p>It was a private class in the profit and loss, the yearly report and the returns reasons, three
 * copies of one decision; a fourth screen wanting it takes this one.</p>
 */
public final class FigureLine {

    private final Label caption = subtitleLabel();
    private final Label value = subtitleLabel();
    private final Label extra = subtitleLabel();
    private final HBox box = new HBox(6, caption, value, extra);

    public FigureLine() {
        value.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
        extra.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
        box.setAlignment(Pos.CENTER_LEFT);
        hide();
    }

    /** What to place in the card. It takes no room while hidden. */
    public Node node() {
        return box;
    }

    /**
     * @param captionKey the caption's message key
     * @param negative   whether the value is drawn as a negative amount is
     * @param extraText  a second figure after the value - a change, a share - or null for none
     */
    public void show(String captionKey, String valueText, boolean negative, String extraText) {
        caption.setText(LanguageManager.getInstance().getString(captionKey));
        value.setText(valueText);
        value.pseudoClassStateChanged(Columns.NEGATIVE, negative);
        extra.setText(extraText == null ? "" : extraText);
        extra.setVisible(extraText != null);
        extra.setManaged(extraText != null);
        box.setVisible(true);
        box.setManaged(true);
    }

    /** A value that is never negative - a count, a percentage of a whole. */
    public void show(String captionKey, String valueText, String extraText) {
        show(captionKey, valueText, false, extraText);
    }

    public void hide() {
        box.setVisible(false);
        box.setManaged(false);
    }

    private static Label subtitleLabel() {
        Label label = new Label();
        label.getStyleClass().add("stat-subtitle");
        return label;
    }
}
