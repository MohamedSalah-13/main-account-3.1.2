package com.hamza.account.controller.pos;

import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.controlsfx.others.TextFormat;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.TablePosition;
import javafx.scene.control.TableView;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.KeyEvent;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

import java.util.Optional;

import static com.hamza.controlsfx.util.NumberUtils.roundToTwoDecimalPlaces;

@Log4j2
public class ButtonSetting {

    private static final String PRESSED_STYLE = "-fx-background-color: #0f981a;";
    private static final long FEEDBACK_DURATION_MS = 100;
    private final BooleanProperty isChangeData = new SimpleBooleanProperty(false);
    private final int COL_QUANTITY = 1;
    private final int COL_PRICE = 2;
    private final java.util.Map<BasePurchasesAndSales, String> quantityBuffer = new java.util.WeakHashMap<>();
    private final java.util.Map<BasePurchasesAndSales, String> priceBuffer = new java.util.WeakHashMap<>();
    private final BooleanProperty printCustomer = new SimpleBooleanProperty(false);
    private final BooleanProperty printInvoice = new SimpleBooleanProperty(false);
    private final BooleanProperty printToKitchen = new SimpleBooleanProperty(false);
    @Setter
    private TableView<BasePurchasesAndSales> tableView;
    private EventHandler<KeyEvent> keyPressedHandler;


    // مساعد لاختيار الـ buffer حسب العمود
    private java.util.Map<BasePurchasesAndSales, String> bufferForCol(int col) {
        return (col == COL_QUANTITY) ? quantityBuffer : priceBuffer;
    }

    public BooleanProperty isChangeDataProperty() {
        return isChangeData;
    }

    // احصل على النص الحالي من الـ buffer إن وجد، وإلا من القيمة الرقمية بشكل منسق
    private String currentTextForEditing(BasePurchasesAndSales item, int col, double numericValue) {
        String buf = bufferForCol(col).get(item);
        return (buf != null) ? buf : stripTrailingZerosDouble(numericValue);
    }

    public boolean isChangeData() {
        return isChangeData.get();
    }

    public void setChangeData(boolean changeData) {
        isChangeData.set(changeData);
    }


    protected void setupKeyboardHandlers() {
        if (keyPressedHandler == null) {
            keyPressedHandler = event -> {
                switch (event.getCode()) {
                    case DIGIT0, NUMPAD0 -> handleNumberButton("0");
                    case DIGIT1, NUMPAD1 -> handleNumberButton("1");
                    case DIGIT2, NUMPAD2 -> handleNumberButton("2");
                    case DIGIT3, NUMPAD3 -> handleNumberButton("3");
                    case DIGIT4, NUMPAD4 -> handleNumberButton("4");
                    case DIGIT5, NUMPAD5 -> handleNumberButton("5");
                    case DIGIT6, NUMPAD6 -> handleNumberButton("6");
                    case DIGIT7, NUMPAD7 -> handleNumberButton("7");
                    case DIGIT8, NUMPAD8 -> handleNumberButton("8");
                    case DIGIT9, NUMPAD9 -> handleNumberButton("9");
                    case PERIOD, DECIMAL -> handleNumberButton(".");
                    case BACK_SPACE -> handleBackspace();
                    default -> { /* ignore other keys */ }
                }
            };
        }

        tableView.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (oldScene != null) {
                oldScene.removeEventHandler(KeyEvent.KEY_PRESSED, keyPressedHandler);
            }
            if (newScene != null) {
                newScene.addEventHandler(KeyEvent.KEY_PRESSED, keyPressedHandler);
            }
        });

        // If a scene is already present (e.g., opened via prebuilt stage), install immediately
        Scene scene = tableView.getScene();
        if (scene != null) {
            scene.addEventHandler(KeyEvent.KEY_PRESSED, keyPressedHandler);
        }
    }


    protected void handleNumberButton(String number) {
        if (tableView == null || tableView.getSelectionModel().getSelectedCells().isEmpty()) {
            log.warn("No selected cell for number {}", number);
            return;
        }
        showButtonFeedback(number);

        TablePosition<?, ?> selectedCell = tableView.getSelectionModel().getSelectedCells().getFirst();
        if (selectedCell != null) {
            int row = selectedCell.getRow();
            int col = selectedCell.getColumn();
            BasePurchasesAndSales item = tableView.getItems().get(row);

            if (col == COL_QUANTITY || col == COL_PRICE) {
                double numeric = (col == COL_QUANTITY) ? item.getQuantity() : item.getPrice();

                // 2) استخدم النص من الـ buffer إن وجد، وإلا من القيمة الرقمية
                String current = currentTextForEditing(item, col, numeric);

                // 3) أضف الرقم/الفاصلة إلى النص
                String newText = appendNumber(current, number);

                // 4) خزّن النص في الـ buffer للمحافظة على النقطة العشرية بين الضغطات
                bufferForCol(col).put(item, newText);

                // 5) حدث القيمة الرقمية من النص (حتى "12." تُفسَّر كـ 12.0 بشكل طبيعي)
                double parsed = parseSafeDouble(newText);
                if (col == COL_QUANTITY) {
                    log.info("Quantity: current : {} -> next: {}", current, newText);
                    item.setQuantity(parsed);
                } else {
                    item.setPrice(parsed);
                }
            }

            updateTotalPrice(item);
        }
    }

    private String appendNumber(String current, String input) {
        if (current == null || current.equals("0")) current = "";
        if (".".equals(input)) {
            if (current.isEmpty()) return "0.";
            if (current.contains(".")) return current; // ignore extra decimals
            return current + ".";
        }
        return current + input;
    }


    protected void handleBackspace() {
        showButtonFeedback("←");
        var selectionModel = tableView.getSelectionModel();
        var selectedCells = selectionModel.getSelectedCells();

        if (selectedCells == null || selectedCells.isEmpty()) {
            return;
        }

        TablePosition<?, ?> cell = selectedCells.getFirst();
        if (cell == null) {
            return;
        }

        int rowIndex = cell.getRow();
        int columnIndex = cell.getColumn();

        if (rowIndex < 0 || rowIndex >= tableView.getItems().size()) {
            return;
        }

        BasePurchasesAndSales item = tableView.getItems().get(rowIndex);
        if (item == null) {
            return;
        }

        switch (columnIndex) {
            case COL_QUANTITY -> backspaceField(item, columnIndex, item::getQuantity, item::setQuantity);
            case COL_PRICE -> backspaceField(item, columnIndex, item::getPrice, item::setPrice);
            default -> { /* ignore */ }
        }
        updateTotalPrice(item);
    }

    private double parseSafeDouble(String s) {
        try {
            return Double.parseDouble(s);
        } catch (Exception e) {
            return 0d;
        }
    }

    // نسخة جديدة تعمل بالـ buffer
    private void backspaceField(BasePurchasesAndSales item,
                                int columnIndex,
                                java.util.function.DoubleSupplier getter,
                                java.util.function.DoubleConsumer setter) {
        var map = bufferForCol(columnIndex);
        String s = map.getOrDefault(item, stripTrailingZerosDouble(getter.getAsDouble()));

        // لو نص قصير جداً، صفّر القيمة واحذف الـ buffer
        if (s.length() <= 1) {
            map.remove(item);
            setter.accept(0d);
            return;
        }

        String newValue = s.substring(0, s.length() - 1);

        // حالـة فراغ/سالب فقط => صفّر واحذف الـ buffer
        if (newValue.isBlank() || "-".equals(newValue)) {
            map.remove(item);
            setter.accept(0d);
            return;
        }

        // حدّث الـ buffer أولاً ثم القيمة الرقمية
        map.put(item, newValue);
        setter.accept(parseSafeDouble(newValue));
    }

    protected Optional<Double> promptAndSetNumber(String title, int columnIndex) {
        if (tableView == null || tableView.getSelectionModel().getSelectedCells().isEmpty()) {
            return Optional.empty();
        }

        var selectionModel = tableView.getSelectionModel();
        TablePosition<?, ?> selectedCell = selectionModel.getSelectedCells().getFirst();
        int row = selectedCell.getRow();
        int col = selectedCell.getColumn();

        if (row < 0 || row >= tableView.getItems().size()) {
            return Optional.empty();
        }

        BasePurchasesAndSales item = tableView.getItems().get(row);

        double initialValue = 0;
        if (columnIndex == COL_PRICE)
            initialValue = item.getPrice();
        else if (columnIndex == COL_QUANTITY)
            initialValue = item.getQuantity();

        Optional<Double> valueOpt = askForNumber(title, initialValue);
        valueOpt.ifPresent(value -> applyValueAndRecalculate(item, columnIndex, value));
        return valueOpt;
    }

    // 7) عند إدخال رقم عبر نافذة (prompt) اعتبره إدخالاً منتهياً، فامسح الـ buffer
    private void applyValueAndRecalculate(BasePurchasesAndSales item, int columnIndex, double value) {
        if (columnIndex == COL_PRICE) {
            item.setPrice(value);
            priceBuffer.remove(item);
        } else if (columnIndex == COL_QUANTITY) {
            item.setQuantity(value);
            quantityBuffer.remove(item);
        }
        updateTotalPrice(item);
    }

    protected Optional<Double> promptAndSetNumber(String title, double initial) {
        return askForNumber(title, initial);
    }

    private String stripTrailingZerosDouble(double val) {
        String s = stripTrailingZeros(val);
        return s.equals("0E-") ? "0" : s;
    }

    private String stripTrailingZeros(double val) {
        return new java.math.BigDecimal(Double.toString(val)).stripTrailingZeros().toPlainString();
    }


    /* -------- Extracted helpers -------- */
    private Optional<Double> askForNumber(String title, double initial) {
        TextInputDialog dialog = buildNumberDialog(title, initial);
        return dialog.showAndWait()
                .filter(s -> !s.isBlank())
                .map(this::parseSafeDouble);
    }

    private TextInputDialog buildNumberDialog(String title, double initial) {
        TextInputDialog dialog = new TextInputDialog(stripTrailingZerosDouble(initial));

        dialog.setTitle(title == null ? "أدخل الرقم" : title);
        dialog.setHeaderText(null);
        dialog.setContentText("القيمة:");

        DialogButtons.changeNameAndGraphic(dialog.getDialogPane());

        dialog.getEditor().setTextFormatter(new TextFormatter<>(TextFormat.doubleStringConverter, 0.0,
                change -> {
                    String newText = change.getControlNewText();
                    if (newText.isEmpty() || newText.equals(".")) {
                        return change;
                    }
                    try {
                        double value = Double.parseDouble(newText);
                        return value >= 0 ? change : null;
                    } catch (NumberFormatException e) {
                        return null;
                    }
                }));
        return dialog;
    }

    private void showButtonFeedback(String buttonText) {
        Scene scene = tableView.getScene();
        scene.lookup(".button-calc").lookupAll(".button-calc").forEach(node -> {
            if (node instanceof javafx.scene.control.Button button
                    && button.getText().equals(buttonText)) {
                String oldStyle = button.getStyle();
                button.setStyle(PRESSED_STYLE);
                new Thread(() -> {
                    try {
                        Thread.sleep(FEEDBACK_DURATION_MS);
                        javafx.application.Platform.runLater(() ->
                                button.setStyle(oldStyle));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }).start();
            }
        });
    }

    private void updateTotalPrice(BasePurchasesAndSales item) {
        double price = item.getPrice();
        double quantity = item.getQuantity();
        double round = roundToTwoDecimalPlaces((quantity * price));
        item.setTotal(round);
        isChangeData.set(true);
        tableView.refresh();
    }

    public boolean isPrintCustomer() {
        return printCustomer.get();
    }

    public void setPrintCustomer(boolean printCustomer) {
        this.printCustomer.set(printCustomer);
    }

    public BooleanProperty printCustomerProperty() {
        return printCustomer;
    }

    public boolean isPrintInvoice() {
        return printInvoice.get();
    }

    public void setPrintInvoice(boolean printInvoice) {
        this.printInvoice.set(printInvoice);
    }

    public BooleanProperty printInvoiceProperty() {
        return printInvoice;
    }

    public boolean isPrintToKitchen() {
        return printToKitchen.get();
    }

    public void setPrintToKitchen(boolean printToKitchen) {
        this.printToKitchen.set(printToKitchen);
    }

    public BooleanProperty printToKitchenProperty() {
        return printToKitchen;
    }
}