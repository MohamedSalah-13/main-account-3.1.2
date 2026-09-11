package com.hamza.account.table;

import com.hamza.account.features.totals.PageJump;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;

import java.util.function.IntConsumer;

/**
 * Type a page number, press Enter, land on it.
 *
 * <p>The invoice totals screen has had this for a while and it is the reason the pager there
 * is usable: twenty-four pages is twenty-three clicks to the last one and twenty-three back.
 * The arithmetic is {@link PageJump}'s and is not repeated here - including the part that is
 * easy to miss, that <b>an Arabic keyboard produces ٠-٩</b> and typing ٥ has to mean page
 * five.
 *
 * <p>Two behaviours are deliberate and were decided on that screen:
 *
 * <ul>
 *   <li><b>Out of range lands on the nearest real page rather than being refused.</b> Someone
 *       who types 500 into a list of 24 pages means the last one. Refusing an obvious
 *       intention is worse than answering it, and loading a page that does not exist would
 *       show an empty table - which reads as a search that found nothing.</li>
 *   <li><b>Leaving the field puts back the page actually shown.</b> Otherwise a number typed
 *       and abandoned sits there claiming to be where the table is.</li>
 * </ul>
 */
public final class PageJumpBox extends HBox {

    private final TextField field = new TextField();
    private final Label ofTotal = new Label();
    private final IntConsumer onJump;

    private int pageCount = 1;
    private int currentPage;

    /**
     * @param onJump given the zero-based page to load. It is called only when the target
     *               differs from the page already shown, so pressing Enter on the current
     *               page costs no query
     */
    public PageJumpBox(IntConsumer onJump) {
        super(6);
        this.onJump = onJump;
        setAlignment(Pos.CENTER_LEFT);

        field.setPrefColumnCount(4);
        field.setId("page-jump");
        field.getStyleClass().add("modern-input");
        field.setTooltip(new Tooltip(text("pager.jump.tip")));
        // The same alphabet the parser understands, so a field you can type into cannot
        // quietly do nothing - PageJump owns both halves of that.
        field.setTextFormatter(new TextFormatter<>(change ->
                PageJump.isTypablePageText(change.getControlNewText()) ? change : null));
        field.setOnAction(event -> jump());
        field.focusedProperty().addListener((observable, was, focused) -> {
            if (!focused) {
                showCurrent();
            }
        });

        // The same class the search caption beside it wears. A bare Label inherits whatever the
        // container happens to set, and in the table toolbar that came out invisible - the box
        // showed its number with no "page" and no "of 3" around it. A caption should be styled
        // like the captions it sits among rather than left to inherit.
        Label caption = new Label(text("pager.jump.label"));
        caption.getStyleClass().add("form-label");
        ofTotal.getStyleClass().add("form-label");
        getChildren().addAll(caption, field, ofTotal);
    }

    /**
     * Tells the box where the table now is.
     *
     * @param page      zero-based page being shown
     * @param pageCount how many there are in total, at least one
     */
    public void showing(int page, int pageCount) {
        this.currentPage = Math.max(0, page);
        this.pageCount = Math.max(1, pageCount);
        showCurrent();
        ofTotal.setText(text("pager.jump.of", this.pageCount));
    }

    // It does not hide itself on a one-page list. It did, and that made it a control that
    // appears and disappears as the filter changes - which is harder to find than one that is
    // simply there, and on a list whose page count is read from a count query it also made the
    // box's presence depend on a number arriving in time. "Page 1 of 1" is accurate and dull,
    // which is what a pager should be.


    private void jump() {
        PageJump.targetPage(field.getText(), pageCount).ifPresentOrElse(page -> {
            if (page == currentPage) {
                showCurrent();
            } else {
                onJump.accept(page);
            }
        }, this::showCurrent);
    }

    private void showCurrent() {
        field.setText(String.valueOf(currentPage + 1));
    }

    private static String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }

    private static String text(String key, Object argument) {
        return LanguageManager.getInstance().getString(key, argument);
    }
}
