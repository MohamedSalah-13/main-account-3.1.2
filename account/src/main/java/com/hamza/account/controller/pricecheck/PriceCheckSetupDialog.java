package com.hamza.account.controller.pricecheck;

import com.hamza.account.config.PropertiesName;
import com.hamza.account.config.ThemeManager;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.events.StocksChanged;
import com.hamza.account.features.invoice.InvoiceItemSelectionService.ScaleBarcodeSettings;
import com.hamza.account.features.pricecheck.PriceCheckSettings;
import com.hamza.account.features.scalebarcode.ScaleBarcodeValueType;
import com.hamza.account.model.domain.Stock;
import com.hamza.account.service.SelPriceItemService;
import com.hamza.account.service.StockService;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.controlsfx.observer.Subscriptions;
import com.hamza.controlsfx.others.ChangeOrientation;
import javafx.collections.FXCollections;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import lombok.extern.log4j.Log4j2;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Asks how this device is to be set up, once, before the wall screen opens.
 *
 * <p>It is a dialog rather than a row in the settings screen because the questions belong
 * to the person hanging the device on the wall, and are answered while standing in front
 * of it. Two of them cannot be defaulted safely:
 *
 * <ul>
 *   <li><b>the warehouse</b> - a balance is per warehouse, so a wrong one reports another
 *       branch's stock to a customer;</li>
 *   <li><b>the price tier</b> - the customer standing there does not know which of the
 *       three they are on, so the screen commits to one.</li>
 * </ul>
 *
 * <p>The answers are remembered, so the next opening is one Enter.
 */
@Log4j2
public class PriceCheckSetupDialog {

    /** Empty when the setup was cancelled - nothing opens, and nothing is remembered. */
    public Optional<PriceCheckSettings> ask() throws Exception {
        var lm = LanguageManager.getInstance();
        var stockService = ServiceRegistry.get(StockService.class);
        var priceNames = ServiceRegistry.get(SelPriceItemService.class);

        List<Stock> stocks = stockService.getStocks();
        if (stocks.isEmpty()) {
            return Optional.empty();
        }
        Map<Integer, String> tierNames = priceNames.getIntegerStringHashMap();

        ComboBox<Stock> comboStock = new ComboBox<>(FXCollections.observableArrayList(stocks));
        comboStock.setButtonCell(stockCell());
        comboStock.setCellFactory(list -> stockCell());
        comboStock.getSelectionModel().select(selectedStock(stocks, stockService));

        ComboBox<Integer> comboTier = new ComboBox<>(FXCollections.observableArrayList(1, 2, 3));
        comboTier.setButtonCell(tierCell(tierNames));
        comboTier.setCellFactory(list -> tierCell(tierNames));
        comboTier.getSelectionModel().select(Integer.valueOf(PropertiesName.getPriceCheckPriceTier()));

        CheckBox showBalance = new CheckBox(lm.getString("pricecheck.setup.show.balance"));
        showBalance.setSelected(PropertiesName.getPriceCheckShowBalance());
        CheckBox showImage = new CheckBox(lm.getString("pricecheck.setup.show.image"));
        showImage.setSelected(PropertiesName.getPriceCheckShowImage());
        CheckBox showExpiry = new CheckBox(lm.getString("pricecheck.setup.show.expiry"));
        showExpiry.setSelected(PropertiesName.getPriceCheckShowExpiry());

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(12);
        form.addRow(0, new Label(lm.getString("pricecheck.setup.stock")), comboStock);
        form.addRow(1, new Label(lm.getString("pricecheck.setup.tier")), comboTier);

        VBox content = new VBox(14, form, showBalance, showImage, showExpiry,
                new Label(lm.getString("pricecheck.setup.exit.hint")));

        // A warehouse added while this dialog is open would otherwise not be offered - the
        // rule every warehouse combo follows, see StocksChanged. The subscription dies with
        // the dialog: the bus behind it lives for the whole process.
        Subscriptions subscriptions = new Subscriptions();
        EventBus eventBus = ServiceRegistry.get(EventBus.class);
        if (eventBus != null) {
            subscriptions.add(eventBus.subscribe(StocksChanged.class,
                    event -> reloadStocks(comboStock, stockService)));
        }
        subscriptions.disposeWith(content);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(lm.getString("pricecheck.title"));
        dialog.getDialogPane().setHeaderText(lm.getString("pricecheck.setup.header"));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        // The dialog builds its scene lazily, so this is a "if it is there yet" rather than
        // a guarantee - the dialog is readable either way, it just misses the theme.
        var dialogScene = dialog.getDialogPane().getScene();
        if (dialogScene != null) {
            ThemeManager.apply(dialogScene);
            ChangeOrientation.sceneOrientation(dialogScene);
        }

        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return Optional.empty();
        }

        Stock stock = comboStock.getSelectionModel().getSelectedItem();
        if (stock == null) {
            return Optional.empty();
        }
        int tier = comboTier.getSelectionModel().getSelectedItem() == null
                ? 1 : comboTier.getSelectionModel().getSelectedItem();

        PropertiesName.setPriceCheckStock(stock.getId());
        PropertiesName.setPriceCheckPriceTier(tier);
        PropertiesName.setPriceCheckShowBalance(showBalance.isSelected());
        PropertiesName.setPriceCheckShowImage(showImage.isSelected());
        PropertiesName.setPriceCheckShowExpiry(showExpiry.isSelected());

        return Optional.of(new PriceCheckSettings(stock.getId(), tier, showBalance.isSelected(),
                showImage.isSelected(), showExpiry.isSelected(), scaleBarcodeSettings()));
    }

    /**
     * The setup this device already carries, or empty the first time.
     * <p>
     * It exists for the device that signs in with a kiosk account: after a power cut nobody
     * should have to answer a dialog on a screen hanging on a wall, so a device that has
     * been set up once comes straight back. The dialog is still shown when nothing is
     * remembered - a warehouse cannot be guessed.
     */
    public static Optional<PriceCheckSettings> remembered() {
        int stockId = PropertiesName.getPriceCheckStock();
        if (stockId <= 0) {
            return Optional.empty();
        }
        return Optional.of(new PriceCheckSettings(stockId,
                PropertiesName.getPriceCheckPriceTier(),
                PropertiesName.getPriceCheckShowBalance(),
                PropertiesName.getPriceCheckShowImage(),
                PropertiesName.getPriceCheckShowExpiry(),
                scaleBarcodeSettings()));
    }

    /** Rereads the warehouses, keeping whatever was already picked if it still exists. */
    private void reloadStocks(ComboBox<Stock> comboStock, StockService stockService) {
        try {
            Stock picked = comboStock.getSelectionModel().getSelectedItem();
            List<Stock> stocks = stockService.getStocks();
            comboStock.setItems(FXCollections.observableArrayList(stocks));
            stocks.stream()
                    .filter(stock -> picked != null && stock.getId() == picked.getId())
                    .findFirst()
                    .ifPresent(stock -> comboStock.getSelectionModel().select(stock));
        } catch (Exception unavailable) {
            // The list on screen is the one that was readable; a failed reread leaves it.
            log.warn("could not reload the warehouses for the price check setup", unavailable);
        }
    }

    /**
     * The remembered warehouse, or the default one the first time - read through
     * {@code StockService} rather than from a constant, so this file needs no reference to
     * {@code DefaultStock} at all.
     */
    private Stock selectedStock(List<Stock> stocks, StockService stockService) throws Exception {
        int remembered = PropertiesName.getPriceCheckStock();
        return stocks.stream()
                .filter(stock -> stock.getId() == remembered)
                .findFirst()
                .orElseGet(() -> {
                    try {
                        return stockService.getDefaultStock();
                    } catch (Exception unavailable) {
                        return stocks.getFirst();
                    }
                });
    }

    /** The scale layout exactly as the barcode settings tab has it - one source, not two. */
    private static ScaleBarcodeSettings scaleBarcodeSettings() {
        return new ScaleBarcodeSettings(
                PropertiesName.getSettingBarcodeScaleActive(),
                PropertiesName.getSettingBarcodeStart(),
                PropertiesName.getSettingBarcodeScaleCodeDigits(),
                ScaleBarcodeValueType.valueOf(PropertiesName.getSettingBarcodeValueType()));
    }

    private static ListCell<Stock> stockCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(Stock stock, boolean empty) {
                super.updateItem(stock, empty);
                setText(empty || stock == null ? null : stock.getName());
            }
        };
    }

    private static ListCell<Integer> tierCell(Map<Integer, String> names) {
        return new ListCell<>() {
            @Override
            protected void updateItem(Integer tier, boolean empty) {
                super.updateItem(tier, empty);
                setText(empty || tier == null ? null : names.getOrDefault(tier, String.valueOf(tier)));
            }
        };
    }
}
