package com.hamza.account.controller.convert_treasury;

import com.hamza.account.config.AppIcon;
import com.hamza.account.features.treasury.CashCategory;
import com.hamza.account.features.treasury.CashMovement;
import com.hamza.account.features.treasury.TreasuryCashService;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.table.ListToolbar;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.table.Columns;
import javafx.fxml.FXML;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * What the owner has put into the business and taken out of it, over a period.
 * <p>
 * It is not a profit report and must never be read as one: none of these amounts is
 * income or expense, and none of them appears in the profit and loss. What they
 * change is the owner's equity - and the treasury, which is why they are entered on
 * the deposit screen rather than anywhere else.
 * <p>
 * The three totals are computed here rather than in SQL because the rows are already
 * loaded and there are at most a few hundred of them; the moment a period could hold
 * more than a screen's worth, this becomes a {@code SUM} in
 * {@code TreasuryStatements} and the table becomes a page.
 */
@FxmlPath(pathFile = "treasury/treasuryCapital.fxml")
public class TreasuryCapitalController {

    @FXML
    private BorderPane root;

    @FXML
    private DatePicker fromDate;

    @FXML
    private DatePicker toDate;

    @FXML
    private Label paidInLabel;

    @FXML
    private Label drawnLabel;

    @FXML
    private Label netLabel;

    @FXML
    private TableView<CashMovement> movementsTable;

    @FXML
    private HBox reportActions;

    private final TreasuryCashService cashService;
    private TreasuryHistoryTable<CashMovement> historyTable;
    private List<CashMovement> shown = List.of();

    public TreasuryCapitalController(DaoFactory daoFactory) {
        this.cashService = new TreasuryCashService(daoFactory);
    }

    @FXML
    private void initialize() {
        LocalDate today = LocalDate.now();
        fromDate.setValue(today.withDayOfYear(1));
        toDate.setValue(today);

        // No delete here: a capital movement is entered and undone on the deposits screen, and this
        // is the report of them. A null permission with no action would still draw a button.
        historyTable = new TreasuryHistoryTable<>(movementsTable, "treasuryCapitalTable", List.of(
                TreasuryHistoryTable.withId("capitalDate",
                        Columns.date("treasury.capital.column.date", CashMovement::date)),
                TreasuryHistoryTable.withId("capitalCategory", Columns.text("treasury.capital.column.category",
                        movement -> text(movement.category().labelKey()))),
                TreasuryHistoryTable.withId("capitalTreasury",
                        Columns.text("treasury.capital.column.treasury", CashMovement::treasuryName)),
                TreasuryHistoryTable.withId("capitalAmount",
                        Columns.money("treasury.capital.column.amount", CashMovement::amount)),
                TreasuryHistoryTable.withId("capitalStatement",
                        Columns.text("treasury.capital.column.statement", CashMovement::statement))));
        new ListToolbar()
                .print(ListToolbar.printButton(this::print))
                .export(ListToolbar.button("treasury.history.export.excel", AppIcon.SPREADSHEET, this::exportExcel))
                .installIn(reportActions);

        reload();
    }

    @FXML
    private void reload() {
        try {
            List<CashMovement> movements =
                    cashService.capitalMovements(fromDate.getValue(), toDate.getValue());
            shown = movements;
            historyTable.show(movements);

            BigDecimal paidIn = total(movements, CashCategory.CAPITAL_IN);
            BigDecimal drawn = total(movements, CashCategory.OWNER_DRAW);
            paidInLabel.setText(text("treasury.capital.total.in") + " " + Columns.money(paidIn));
            drawnLabel.setText(text("treasury.capital.total.out") + " " + Columns.money(drawn));
            netLabel.setText(text("treasury.capital.total.net") + " "
                    + Columns.money(paidIn.subtract(drawn)));
        } catch (DaoException e) {
            AllAlerts.handleError(text("treasury.capital.op.load"), e);
        }
    }

    /**
     * Capital paid in and drawings share one amount column, so the paper carries no totals line - a
     * sum of the two means nothing. The three figures the screen shows go in the subtitle.
     */
    private void print() {
        historyTable.print(text("treasury.capital.title"),
                text("from") + ": " + fromDate.getValue() + "  |  " + text("to") + ": " + toDate.getValue()
                        + "  |  " + paidInLabel.getText() + "  |  " + drawnLabel.getText()
                        + "  |  " + netLabel.getText(),
                shown, Set.of());
    }

    private void exportExcel() {
        try {
            historyTable.exportExcel(text("treasury.capital.title"), shown);
        } catch (Exception e) {
            AllAlerts.handleError(text("treasury.history.export.excel"), e);
        }
    }

    private BigDecimal total(List<CashMovement> movements, CashCategory category) {
        return movements.stream()
                .filter(movement -> movement.category() == category)
                .map(CashMovement::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }
}
