package com.hamza.account.controller.invoice;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.config.AppIcon;
import com.hamza.account.config.NamesTables;
import com.hamza.account.config.PropertiesName;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.document.DocumentType;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.features.invoice.InvoiceDetailsLine;
import com.hamza.account.features.invoice.InvoiceDetailsPresentation;
import com.hamza.account.features.invoice.InvoiceDetailsSearch;
import com.hamza.account.features.invoice.InvoiceDetailsSummary;
import com.hamza.account.features.invoice.InvoicePrintRequest;
import com.hamza.account.features.invoice.InvoicePrintService;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.interfaces.api.InvoiceHeaderView;
import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.account.type.InvoiceType;
import com.hamza.account.view.barcode.PrintBarcodeApp;
import com.hamza.account.view.barcode.PrintBarcodeModel;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.table.Columns;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.hamza.controlsfx.dateTime.DateUtils.DATE_TIME_FORMATTER;

/** Read-only, responsive presentation of one saved invoice. */
@FxmlPath(pathFile = "invoice/showInv-view.fxml")
public final class ShowInvoiceController<T3 extends BaseNames, T4 extends BaseAccount>
        implements Initializable, AppSettingInterface {

    private static final ExecutorService INVOICE_READER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "saved-invoice-reader");
        thread.setDaemon(true);
        return thread;
    });

    private final DataInterface<?, ?, T3, T4> dataInterface;
    private final int invoiceNumber;
    private final String highlightedItemName;
    private final InvoicePrintService printService;
    private final DocumentType documentType;
    private final InvoiceDetailsPresentation presentation;

    @FXML
    private VBox root;
    @FXML
    private TableView<InvoiceDetailsLine> tableView;
    @FXML
    private Label documentTitle;
    @FXML
    private Label paymentBadge;
    @FXML
    private Label partyCaption;
    @FXML
    private Label valueInvoiceNumber;
    @FXML
    private Label valueParty;
    @FXML
    private Label valueDate;
    @FXML
    private Label valueStock;
    @FXML
    private Label valueDelegate;
    @FXML
    private Label valueSourceInvoice;
    @FXML
    private Label valueReturnReason;
    @FXML
    private Label valueEnteredAt;
    @FXML
    private Label valueNotes;
    @FXML
    private Label valueNet;
    @FXML
    private Label valuePaid;
    @FXML
    private Label valueRemaining;
    @FXML
    private Label valueLineCount;
    @FXML
    private Label valueItemCount;
    @FXML
    private Label valueQuantity;
    @FXML
    private Label valueLinesTotal;
    @FXML
    private Label valueLinesDiscount;
    @FXML
    private Label valueInvoiceDiscount;
    @FXML
    private Label valueTotalCost;
    @FXML
    private Label valueProfit;
    @FXML
    private Label stateMessage;
    @FXML
    private Label searchLabel;
    @FXML
    private Label searchResult;
    @FXML
    private VBox delegateField;
    @FXML
    private VBox sourceInvoiceField;
    @FXML
    private VBox returnReasonField;
    @FXML
    private VBox enteredAtField;
    @FXML
    private VBox notesField;
    @FXML
    private VBox remainingCard;
    @FXML
    private VBox statePane;
    @FXML
    private HBox profitSection;
    @FXML
    private ProgressIndicator stateProgress;
    @FXML
    private ProgressIndicator actionProgress;
    @FXML
    private Button btnPrint;
    @FXML
    private Button btnPrintBarcode;
    @FXML
    private Button btnRetry;
    @FXML
    private Button btnClose;
    @FXML
    private Button btnClearSearch;
    @FXML
    private TextField searchField;

    private InvoiceHeaderView header;
    private List<? extends BasePurchasesAndSales> sourceLines = List.of();
    private final ObservableList<InvoiceDetailsLine> invoiceLines = FXCollections.observableArrayList();
    private final FilteredList<InvoiceDetailsLine> filteredLines =
            new FilteredList<>(invoiceLines, ignored -> true);
    private final Label tablePlaceholder = new Label();
    private long loadGeneration;

    public ShowInvoiceController(DataInterface<?, ?, T3, T4> dataInterface,
                                 DaoFactory ignoredDaoFactory,
                                 DataPublisher ignoredPublisher,
                                 int invoiceNumber,
                                 String highlightedItemName) {
        this.dataInterface = dataInterface;
        this.invoiceNumber = invoiceNumber;
        this.highlightedItemName = highlightedItemName;
        this.printService = new InvoicePrintService();
        this.documentType = dataInterface.designInterface().documentType();
        this.presentation = InvoiceDetailsPresentation.of(documentType,
                AuthorizationGuard.isGranted(AppPermissions.INVOICE_PROFIT_SHOW));
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        configureVisibility();
        configureActions();
        configureTable();
        configureSearch();
        applyDocumentPresentation();
        loadInvoice();
    }

    private void configureVisibility() {
        List<Pane> optionalPanes = List.of(delegateField, sourceInvoiceField, returnReasonField,
                enteredAtField, notesField, remainingCard, statePane, profitSection);
        optionalPanes.forEach(pane -> pane.managedProperty().bind(pane.visibleProperty()));
        stateProgress.managedProperty().bind(stateProgress.visibleProperty());
        actionProgress.managedProperty().bind(actionProgress.visibleProperty());
        btnRetry.managedProperty().bind(btnRetry.visibleProperty());
    }

    private void configureActions() {
        btnPrint.setGraphic(AppIcon.PRINT.graphic(17));
        btnPrintBarcode.setGraphic(AppIcon.BARCODE.graphic(17));
        btnRetry.setGraphic(AppIcon.REFRESH.graphic(17));
        btnClose.setGraphic(AppIcon.CLOSE.graphic(17));
        btnClearSearch.setGraphic(AppIcon.CLEAR.graphic(16));
        searchLabel.setGraphic(AppIcon.SEARCH.graphic(16));
    }

    private void configureTable() {
        TableColumn<InvoiceDetailsLine, Number> code =
                Columns.number(NamesTables.CODE, InvoiceDetailsLine::itemId);
        TableColumn<InvoiceDetailsLine, String> item =
                Columns.text(NamesTables.NAME_ITEM, InvoiceDetailsLine::itemName);
        TableColumn<InvoiceDetailsLine, String> unit =
                Columns.text(NamesTables.TYPE, InvoiceDetailsLine::unitName);
        TableColumn<InvoiceDetailsLine, BigDecimal> quantity =
                Columns.asQuantity(Columns.column(NamesTables.QUANTITY, InvoiceDetailsLine::quantity));
        TableColumn<InvoiceDetailsLine, BigDecimal> price =
                Columns.money(NamesTables.PRICE, InvoiceDetailsLine::price);
        TableColumn<InvoiceDetailsLine, BigDecimal> total =
                Columns.money(NamesTables.TOTAL, InvoiceDetailsLine::total);
        TableColumn<InvoiceDetailsLine, BigDecimal> discount =
                Columns.money(NamesTables.DISCOUNT, InvoiceDetailsLine::discount);
        TableColumn<InvoiceDetailsLine, BigDecimal> net =
                Columns.money(NamesTables.TOTAL_AFTER, InvoiceDetailsLine::net);

        code.setPrefWidth(78);
        item.setPrefWidth(230);
        unit.setPrefWidth(100);
        quantity.setPrefWidth(105);
        price.setPrefWidth(115);
        total.setPrefWidth(125);
        discount.setPrefWidth(110);
        net.setPrefWidth(130);
        List<TableColumn<InvoiceDetailsLine, ?>> columns =
                List.of(code, item, unit, quantity, price, total, discount, net);
        tableView.getColumns().setAll(columns);
        tableView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        tableView.setItems(filteredLines);
        tableView.setPlaceholder(tablePlaceholder);
        updateSearch();
    }

    private void configureSearch() {
        searchField.textProperty().addListener((observable, oldValue, newValue) -> updateSearch());
        btnClearSearch.disableProperty().bind(searchField.disabledProperty()
                .or(searchField.textProperty().isEmpty()));
        searchField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE && !searchField.getText().isEmpty()) {
                clearSearch();
                event.consume();
            }
        });
        root.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.isShortcutDown() && event.getCode() == KeyCode.F) {
                searchField.requestFocus();
                searchField.selectAll();
                event.consume();
            }
        });
    }

    private void updateSearch() {
        String query = searchField == null ? "" : searchField.getText();
        filteredLines.setPredicate(line -> InvoiceDetailsSearch.matches(line, query));
        tablePlaceholder.setText(query == null || query.isBlank()
                ? text("invoice.details.empty")
                : text("invoice.details.search.no.results"));
        if (searchResult != null) {
            searchResult.setText(text("invoice.details.search.results",
                    filteredLines.size(), invoiceLines.size()));
        }
    }

    @FXML
    private void clearSearch() {
        searchField.clear();
        searchField.requestFocus();
    }

    private void applyDocumentPresentation() {
        root.getStyleClass().add(presentation.styleClass());
        partyCaption.setText(text(documentType.partyKind() == PartyKind.CUSTOMER
                ? "invoice.pdf.party.customer" : "invoice.pdf.party.supplier"));
        delegateField.setVisible(presentation.showDelegate());
        sourceInvoiceField.setVisible(presentation.showReturnDetails());
        returnReasonField.setVisible(presentation.showReturnDetails());
        profitSection.setVisible(presentation.showProfit());
    }

    @FXML
    private void retryLoad() {
        loadInvoice();
    }

    private void loadInvoice() {
        long generation = ++loadGeneration;
        showLoadingState();
        Task<LoadedInvoice> task = new Task<>() {
            @Override
            protected LoadedInvoice call() throws Exception {
                InvoiceHeaderView loadedHeader = dataInterface.loadInvoiceHeader(invoiceNumber);
                if (loadedHeader == null || loadedHeader.totals() == null) {
                    throw new IllegalStateException("Saved invoice header is missing");
                }
                List<? extends BasePurchasesAndSales> loadedLines =
                        dataInterface.listForAllPurchase(invoiceNumber);
                return new LoadedInvoice(loadedHeader,
                        loadedLines == null ? List.of() : List.copyOf(loadedLines));
            }
        };
        task.setOnSucceeded(event -> {
            if (generation == loadGeneration) {
                showInvoice(task.getValue());
            }
        });
        task.setOnFailed(event -> {
            if (generation == loadGeneration) {
                showLoadFailure(task.getException());
            }
        });
        INVOICE_READER.execute(task);
    }

    private void showLoadingState() {
        stateMessage.setText(text("invoice.details.loading"));
        stateProgress.setVisible(true);
        btnRetry.setVisible(false);
        statePane.setVisible(true);
        tableView.setDisable(true);
        searchField.setDisable(true);
        setActionsDisabled(true);
    }

    private void showLoadFailure(Throwable failure) {
        stateMessage.setText(text("invoice.details.load.failed"));
        stateProgress.setVisible(false);
        btnRetry.setVisible(true);
        statePane.setVisible(true);
        tableView.setDisable(true);
        searchField.setDisable(true);
        setActionsDisabled(true);
        AllAlerts.handleError(text("invoice.error.load.details.title"), failure);
    }

    private void showInvoice(LoadedInvoice loaded) {
        header = loaded.header();
        sourceLines = loaded.lines();
        InvoiceDetailsSummary summary = InvoiceDetailsSummary.from(header.totals(), sourceLines,
                presentation.showProfit());

        invoiceLines.setAll(sourceLines.stream().map(InvoiceDetailsLine::from).toList());
        updateSearch();
        documentTitle.setText(text("invoice.details.heading", documentType.label(), header.totals().getId()));
        valueInvoiceNumber.setText(String.valueOf(header.totals().getId()));
        valueParty.setText(orUnavailable(header.partyName()));
        valueDate.setText(orUnavailable(header.totals().getDate()));
        valueStock.setText(header.totals().getStockData() == null
                ? text("invoice.details.not.available")
                : orUnavailable(header.totals().getStockData().getName()));
        valueDelegate.setText(orUnavailable(header.delegateName()));
        valueSourceInvoice.setText(header.sourceInvoiceNumber() > 0
                ? String.valueOf(header.sourceInvoiceNumber())
                : text("invoice.details.not.available"));
        valueReturnReason.setText(orUnavailable(header.returnReason()));
        valueEnteredAt.setText(formatDateTime(header.dateInsert()));
        valueNotes.setText(orUnavailable(header.totals().getNotes()));

        InvoiceType invoiceType = header.totals().getInvoiceType() == null
                ? InvoiceType.CASH : header.totals().getInvoiceType();
        paymentBadge.setText(invoiceType.getType());
        paymentBadge.getStyleClass().removeAll("invoice-payment-cash", "invoice-payment-deferred");
        paymentBadge.getStyleClass().add(invoiceType == InvoiceType.CASH
                ? "invoice-payment-cash" : "invoice-payment-deferred");

        valueNet.setText(Columns.money(summary.invoiceNet()));
        valuePaid.setText(Columns.money(summary.paid()));
        valueRemaining.setText(Columns.money(summary.remaining()));
        valueLineCount.setText(String.valueOf(summary.lineCount()));
        valueItemCount.setText(String.valueOf(summary.distinctItemCount()));
        valueQuantity.setText(Columns.quantity(summary.quantity()));
        valueLinesTotal.setText(Columns.money(summary.linesTotal()));
        valueLinesDiscount.setText(Columns.money(summary.linesDiscount()));
        valueInvoiceDiscount.setText(Columns.money(summary.invoiceDiscount()));
        valueTotalCost.setText(summary.totalCost().map(Columns::money).orElse(""));
        valueProfit.setText(summary.profit().map(Columns::money).orElse(""));

        remainingCard.getStyleClass().removeAll("invoice-kpi-success", "invoice-kpi-danger");
        remainingCard.getStyleClass().add(summary.remaining().signum() <= 0
                ? "invoice-kpi-success" : "invoice-kpi-danger");
        enteredAtField.setVisible(header.dateInsert() != null);
        notesField.setVisible(header.totals().getNotes() != null
                && !header.totals().getNotes().isBlank());

        statePane.setVisible(false);
        tableView.setDisable(false);
        searchField.setDisable(false);
        setActionsDisabled(false);
        btnPrintBarcode.setDisable(sourceLines.isEmpty());
        selectHighlightedItem();
    }

    private void selectHighlightedItem() {
        if (highlightedItemName == null || highlightedItemName.isBlank()) {
            return;
        }
        for (int index = 0; index < tableView.getItems().size(); index++) {
            if (highlightedItemName.equals(tableView.getItems().get(index).itemName())) {
                tableView.getSelectionModel().select(index);
                tableView.scrollTo(index);
                return;
            }
        }
    }

    @FXML
    private void printInvoice() {
        if (header == null || actionProgress.isVisible()) {
            return;
        }
        setActionBusy(true);
        InvoiceHeaderView printedHeader = header;
        List<? extends BasePurchasesAndSales> printedLines = sourceLines;
        // Two different times, as on the invoice screen: the receipt carries when the invoice was
        // entered, and the A4 page's "printed at" line is now - a reprint a month later must not
        // claim it was printed on the day of the sale.
        String enteredAt = formatDateTime(printedHeader.dateInsert());
        String printedAt = LocalDateTime.now().format(DATE_TIME_FORMATTER);
        Task<InvoicePrintRequest> task = new Task<>() {
            @Override
            protected InvoicePrintRequest call() throws Exception {
                return printService.prepare(printedLines, enteredAt,
                        PropertiesName.getPrintPaperReceiptInvoice(),
                        lines -> ShowInvoiceDetails.printDocument(printedHeader, documentType, lines, printedAt));
            }
        };
        task.setOnSucceeded(event -> {
            setActionBusy(false);
            try {
                printService.print(task.getValue());
            } catch (RuntimeException exception) {
                AllAlerts.handleError(text("party.error.export.generic"), exception);
            }
        });
        task.setOnFailed(event -> {
            setActionBusy(false);
            AllAlerts.handleError(text("party.error.export.generic"), task.getException());
        });
        INVOICE_READER.execute(task);
    }

    @FXML
    private void printBarcode() {
        if (sourceLines.isEmpty()) {
            AllAlerts.alertError(text("invoice.details.empty"));
            return;
        }
        var rows = FXCollections.<PrintBarcodeModel>observableArrayList();
        sourceLines.stream()
                .map(BasePurchasesAndSales::getItems)
                .filter(item -> item != null)
                .map(item -> new PrintBarcodeModel(item.getBarcode(), item.getNameItem(), item.getSelPrice1()))
                .forEach(rows::add);
        if (rows.isEmpty()) {
            AllAlerts.alertError(text("invoice.details.empty"));
            return;
        }
        try {
            new PrintBarcodeApp(rows);
        } catch (Exception exception) {
            AllAlerts.handleError(text("item.dialog.barcode.title"), exception);
        }
    }

    @FXML
    private void close() {
        if (root.getScene() != null && root.getScene().getWindow() != null) {
            root.getScene().getWindow().hide();
        }
    }

    private void setActionBusy(boolean busy) {
        actionProgress.setVisible(busy);
        btnPrint.setDisable(busy);
        btnPrintBarcode.setDisable(busy || sourceLines.isEmpty());
        btnRetry.setDisable(busy);
    }

    private void setActionsDisabled(boolean disabled) {
        btnPrint.setDisable(disabled);
        btnPrintBarcode.setDisable(disabled);
    }

    private static String formatDateTime(LocalDateTime value) {
        return value == null ? "" : value.format(DATE_TIME_FORMATTER);
    }

    private static String orUnavailable(String value) {
        return value == null || value.isBlank()
                ? text("invoice.details.not.available") : value;
    }

    private static String text(String key, Object... arguments) {
        return LanguageManager.getInstance().getString(key, arguments);
    }

    @Override
    public @NotNull Pane pane() throws IOException {
        return new OpenFxmlApplication(this).getPane();
    }

    @Override
    public String title() {
        return documentType.label();
    }

    @Override
    public String dialogStyleClass() {
        return "invoice-details-dialog";
    }

    @Override
    public boolean resize() {
        return true;
    }

    @Override
    public double minWidth() {
        return 720;
    }

    @Override
    public double minHeight() {
        return 520;
    }

    private record LoadedInvoice(InvoiceHeaderView header,
                                 List<? extends BasePurchasesAndSales> lines) {
    }
}
