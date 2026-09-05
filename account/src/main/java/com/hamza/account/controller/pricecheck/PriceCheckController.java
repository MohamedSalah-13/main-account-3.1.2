package com.hamza.account.controller.pricecheck;

import com.hamza.account.features.pricecheck.PriceCheckResult;
import com.hamza.account.features.pricecheck.PriceCheckService;
import com.hamza.account.features.pricecheck.PriceCheckSession;
import com.hamza.account.features.pricecheck.PriceCheckSettings;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.otherSetting.Currency_Setting;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import lombok.extern.log4j.Log4j2;

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The customer-facing price screen: a barcode goes in, a price comes out, and nothing
 * else is possible from it.
 *
 * <p>Everything that decides an answer is in {@link PriceCheckService}; what is here is
 * the wiring - reading the scan, showing the result, and going back to waiting. Three
 * things about that wiring are not decoration:
 *
 * <ul>
 *   <li><b>The lookup never runs on the JavaFX thread.</b> One query per scan is enough
 *       to freeze the screen mid-scan on a slow database, and this screen is watched by
 *       the person waiting for it.</li>
 *   <li><b>A late answer to a superseded scan is dropped</b> - {@link PriceCheckSession}
 *       carries the rule and the reason.</li>
 *   <li><b>The caret goes back to the scan field after everything.</b> A barcode scanner
 *       is a keyboard: a field that has lost focus makes the whole device look broken,
 *       with nothing on screen to say why.</li>
 * </ul>
 */
@Log4j2
@FxmlPath(pathFile = "pricecheck/price-check-view.fxml")
public class PriceCheckController implements Initializable {

    /** One shared daemon thread: the scans are answered in the order they were made. */
    private static final ExecutorService LOOKUP_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "price-check-lookup");
        thread.setDaemon(true);
        return thread;
    });

    private static final DateTimeFormatter EXPIRY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final PriceCheckService service;
    private final PriceCheckSettings settings;
    private final PriceCheckSession session = new PriceCheckSession();
    private final PauseTransition idle;

    @FXML
    private StackPane root;
    @FXML
    private TextField scanField;
    @FXML
    private VBox waitingBox, notFoundBox;
    @FXML
    private HBox resultBox;
    @FXML
    private ImageView imgItem;
    @FXML
    private Label lblItemName, lblPrice, lblUnit, lblWeight, lblBalance, lblExpiry, lblNotFoundCode;

    public PriceCheckController(PriceCheckService service, PriceCheckSettings settings, int resetSeconds) {
        this.service = Objects.requireNonNull(service, "service");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.idle = new PauseTransition(Duration.seconds(Math.max(3, resetSeconds)));
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        idle.setOnFinished(event -> showWaiting());
        keepTheCaretInTheScanField();
        showWaiting();
    }

    /**
     * Nothing on this screen is clickable, so any focus the field loses - to a click on
     * the background, to the window being shown - is focus nothing else has a use for.
     * <p>
     * <b>Only while this window is the focused one.</b> Taking the caret back
     * unconditionally would fight the exit dialog for it: that dialog is how the screen is
     * unlocked, and a password field losing the caret on every keystroke cannot be typed
     * into. The window regaining focus is what puts the caret back afterwards.
     */
    private void keepTheCaretInTheScanField() {
        root.setOnMouseClicked(event -> scanField.requestFocus());
        scanField.focusedProperty().addListener((observable, was, focused) -> {
            if (!focused && isOnTop()) {
                Platform.runLater(scanField::requestFocus);
            }
        });
        root.sceneProperty().addListener((observable, oldScene, scene) -> {
            if (scene != null) {
                scene.windowProperty().addListener((observed, oldWindow, window) -> {
                    if (window != null) {
                        window.focusedProperty().addListener((watched, lost, gained) -> {
                            if (gained) {
                                Platform.runLater(scanField::requestFocus);
                            }
                        });
                    }
                });
            }
        });
        Platform.runLater(scanField::requestFocus);
    }

    /** Whether this screen's own window is the one the operating system is giving keys to. */
    private boolean isOnTop() {
        return root.getScene() != null
                && root.getScene().getWindow() != null
                && root.getScene().getWindow().isFocused();
    }

    @FXML
    private void onScan() {
        String code = scanField.getText();
        scanField.clear();
        if (code == null || code.isBlank()) {
            return;
        }
        long token = session.begin();
        CompletableFuture
                .supplyAsync(() -> {
                    try {
                        return service.lookup(code, settings);
                    } catch (Exception failed) {
                        // The customer gets the same answer either way: this screen cannot
                        // price that packet. The reason belongs in the log, not on a wall.
                        log.error("price check failed for code {}", code, failed);
                        return (PriceCheckResult) new PriceCheckResult.NotFound(code.trim());
                    }
                }, LOOKUP_EXECUTOR)
                .thenAccept(result -> Platform.runLater(() -> publish(token, result)));
    }

    private void publish(long token, PriceCheckResult result) {
        if (!session.isCurrent(token)) {
            return;
        }
        if (result instanceof PriceCheckResult.Found found) {
            showFound(found);
        } else if (result instanceof PriceCheckResult.NotFound notFound) {
            showNotFound(notFound);
        }
        idle.playFromStart();
        scanField.requestFocus();
    }

    private void showFound(PriceCheckResult.Found found) {
        var lm = LanguageManager.getInstance();
        lblItemName.setText(found.itemName());

        // A weighed packet is not priced per kilo: what the customer pays is the total the
        // scale's own barcode works out to, and the unit price is the supporting line.
        double headline = found.scaleBarcode() ? found.total() : found.price();
        lblPrice.setText(money(headline));
        lblUnit.setText(lm.getString("pricecheck.per.unit", found.unitName()));

        show(lblWeight, found.scaleBarcode());
        if (found.scaleBarcode()) {
            lblWeight.setText(lm.getString("pricecheck.weight",
                    String.format("%,.3f", found.quantity()), found.unitName(), money(found.price())));
        }

        show(lblBalance, settings.showBalance());
        if (settings.showBalance()) {
            lblBalance.setText(lm.getString("pricecheck.balance",
                    String.format("%,.2f", found.balance()), found.unitName()));
        }

        boolean hasExpiry = found.nearestExpiry() != null;
        show(lblExpiry, hasExpiry);
        if (hasExpiry) {
            lblExpiry.setText(lm.getString("pricecheck.expiry", EXPIRY_FORMAT.format(found.nearestExpiry())));
        }

        showImage(found.image());
        showOnly(resultBox);
    }

    private void showImage(byte[] image) {
        boolean hasImage = image != null && image.length > 0;
        if (hasImage) {
            imgItem.setImage(new Image(new ByteArrayInputStream(image)));
        } else {
            imgItem.setImage(null);
        }
        show(imgItem, hasImage);
    }

    private void showNotFound(PriceCheckResult.NotFound notFound) {
        lblNotFoundCode.setText(notFound.code());
        showOnly(notFoundBox);
    }

    private void showWaiting() {
        session.cancel();
        idle.stop();
        showOnly(waitingBox);
        scanField.requestFocus();
    }

    private void showOnly(Region panel) {
        show(waitingBox, panel == waitingBox);
        show(notFoundBox, panel == notFoundBox);
        show(resultBox, panel == resultBox);
    }

    /** Hidden means gone: a hidden node that still takes its space leaves the panel off-centre. */
    private static void show(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    private static String money(double amount) {
        String symbol = Currency_Setting.getCurrency()
                .map(entry -> entry.getValue().getSymbol(entry.getKey()))
                .orElse("");
        String formatted = String.format("%,.2f", amount);
        return symbol.isBlank() ? formatted : formatted + " " + symbol;
    }
}
