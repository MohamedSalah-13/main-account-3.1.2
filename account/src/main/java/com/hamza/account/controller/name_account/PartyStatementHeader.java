package com.hamza.account.controller.name_account;

import com.hamza.account.features.party.statement.PartyStatementSummary;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.table.Columns;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The three figures at the top of a party statement, and the credit limit beside them.
 * <p>
 * <b>Two of the three were wrong on the screen this replaces, and the way they were wrong is the
 * argument for building a header rather than filling one in.</b> The field labelled "opening
 * balance" held the <em>credit limit</em>; the field labelled "final balance" was declared in the
 * FXML and written by nothing at all, so it read {@code 0} for every party, always. Neither is
 * possible here: each figure is a method that takes the number it displays.
 * <p>
 * The credit limit is shown as a bar as well as a number. A limit is a number a person has to
 * compare with another number, and 8,400 against 10,000 is a comparison somebody has to make; a bar
 * that turns red at the line makes it without them.
 */
final class PartyStatementHeader extends FlowPane {

    /** Set on the limit bar once the balance has passed it. Styled in {@code app-theme.css}. */
    private static final PseudoClass OVER_LIMIT = PseudoClass.getPseudoClass("over-limit");

    private final Label opening = figureValue("statement-opening");
    private final Label closing = figureValue("statement-closing");
    private final Label debit = figureValue("statement-total-debit");
    private final Label credit = figureValue("statement-total-credit");
    private final Label net = figureValue("statement-net");
    private final Label limitValue = figureValue("statement-limit");
    private final ProgressBar limitBar = new ProgressBar(0);
    private final VBox limitBox;

    PartyStatementHeader() {
        super(18, 10);
        setAlignment(Pos.CENTER_LEFT);
        getStyleClass().add("app-card");
        setId("party-statement-header");

        limitBar.setPrefWidth(160);
        limitBox = figure("party.statement.limit.used", limitValue);
        limitBox.getChildren().add(limitBar);

        getChildren().addAll(
                figure("party.statement.opening", opening),
                figure("party.statement.total.debit", debit),
                figure("party.statement.total.credit", credit),
                figure("party.statement.net", net),
                figure("party.statement.closing", closing),
                limitBox);
    }

    /** Every figure on the header comes from one summary, so no two of them can disagree. */
    void show(PartyStatementSummary summary) {
        opening.setText(Columns.money(summary.openingBalance()));
        debit.setText(Columns.money(summary.totalDebit()));
        credit.setText(Columns.money(summary.totalCredit()));
        net.setText(Columns.money(summary.netMovement()));
        closing.setText(Columns.money(summary.closingBalance()));
    }

    /**
     * The credit limit, or nothing.
     * <p>
     * A limit of zero means "none set" - the reading {@code CreditLimitSource} already takes - and a
     * supplier has none at all, so the whole box leaves the layout rather than showing a permanent
     * zero. A field that is always zero is one every user learns to ignore, including on the day it
     * is not.
     */
    void showCreditLimit(BigDecimal limit, BigDecimal balance) {
        boolean shown = limit != null && limit.signum() > 0;
        limitBox.setVisible(shown);
        limitBox.setManaged(shown);
        if (!shown) {
            return;
        }
        BigDecimal used = balance == null ? BigDecimal.ZERO : balance;
        limitValue.setText(Columns.money(used) + " / " + Columns.money(limit));
        double fraction = used.signum() <= 0 ? 0
                : used.divide(limit, 4, RoundingMode.HALF_UP).doubleValue();
        limitBar.setProgress(Math.min(fraction, 1));
        limitBar.pseudoClassStateChanged(OVER_LIMIT, fraction > 1);
        limitValue.pseudoClassStateChanged(OVER_LIMIT, fraction > 1);
    }

    private static VBox figure(String titleKey, Label value) {
        Label caption = new Label(LanguageManager.getInstance().getString(titleKey));
        caption.getStyleClass().add("form-label");
        VBox box = new VBox(2, caption, value);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private static Label figureValue(String id) {
        Label label = new Label("0.00");
        label.getStyleClass().add("stat-value");
        label.setId(id);
        return label;
    }
}
