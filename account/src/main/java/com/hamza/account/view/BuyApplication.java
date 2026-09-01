package com.hamza.account.view;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.invoice.BuyController2;
import com.hamza.account.controller.invoice.InvoiceScreenMode;
import com.hamza.account.document.DocumentType;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter
public class BuyApplication extends Application {

    /**
     * The invoice windows currently open, so an invoice already on screen is brought
     * forward instead of opened a second time.
     *
     * <p>Keyed by <b>document type and number</b>, not by number alone. The four
     * families number their documents independently, so sales invoice 5 and purchase
     * invoice 5 both exist - and under the old key, opening the purchase while the sale
     * was open simply brought the sale forward and the purchase never appeared.
     */
    private static final Map<OpenInvoice, Stage> openInvoices = new HashMap<>();

    private final Pane pane;
    private final BuyController2<?, ?> controller;
    private final int numInvoiceUpdate;
    private final InvoiceScreenMode screenMode;
    private final DocumentType documentType;
    private String title = "Buy";

    /** Which document, of which family - see {@link #openInvoices}. */
    private record OpenInvoice(DocumentType documentType, int number) {
    }

    public BuyApplication(DataInterface<?, ?, ?, ?> dataInterface, int numInvoiceUpdate) throws Exception {
        this(dataInterface, numInvoiceUpdate, InvoiceScreenMode.STANDARD);
    }

    public BuyApplication(DataInterface<?, ?, ?, ?> dataInterface, int numInvoiceUpdate,
                          InvoiceScreenMode screenMode) throws Exception {
        controller = new BuyController2<>(dataInterface, numInvoiceUpdate, screenMode);
        pane = controller.pane();
        this.numInvoiceUpdate = numInvoiceUpdate;
        this.screenMode = screenMode;
        this.documentType = dataInterface.designInterface().documentType();
        title = dataInterface.designInterface().nameTextOfInvoice();
        if (screenMode == InvoiceScreenMode.QUICK) {
            title += " - " + LanguageManager.getInstance().getString("invoice.quick.title");
        }
    }

    @Override
    public void start(Stage stage) throws Exception {

        // 0 means a new invoice rather than an edit, and any number of those may be open.
        OpenInvoice key = numInvoiceUpdate == 0
                ? null
                : new OpenInvoice(documentType, numInvoiceUpdate);
        if (key != null) {
            Stage existingStage = openInvoices.get(key);
            // isShowing, not merely present: a stage left behind by an entry that was
            // never cleared would otherwise refuse to open this invoice for ever.
            if (existingStage != null && existingStage.isShowing()) {
                existingStage.toFront();
                existingStage.requestFocus();
                return;
            }
            openInvoices.put(key, stage);
            // setOnHidden, not setOnCloseRequest: a close request is only raised when
            // the user closes the window. Saving an edit closes it with Stage.hide(),
            // which fires no close request at all - so the entry stayed behind and that
            // invoice could not be reopened until the application was restarted.
            stage.setOnHidden(event -> openInvoices.remove(key, stage));
        }

        Scene scene = new SceneAll(pane);
        stage.setScene(scene);
        stage.setTitle(title);
        stage.setResizable(true);
        stage.getIcons().add(new javafx.scene.image.Image(new Image_Setting().tools));

        stage.show();
//        StageDimensions.stageDimensions(getClass(), stage);

        var btnSave = getController().getBtnSave();

        btnSave.setText(btnSave.getText() + " F10");
        btnSave.getScene().getAccelerators().put(
                new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.F10),
                btnSave::fire
        );

        var btnPrintSave = getController().getBtnPrintSave();
        btnPrintSave.setText(LanguageManager.getInstance().getString("invoice.btn.save.print") + " F12");
        btnPrintSave.getScene().getAccelerators().put(
                new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.F12),
                btnPrintSave::fire
        );

        var btnAddItem = getController().getBtnUpdateItem();
        btnAddItem.getScene().getAccelerators().put(
                new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.F4),
                btnAddItem::fire
        );

        var btnSwitchMode = getController().getBtnQuickMode();
        btnSwitchMode.getScene().getAccelerators().put(
                new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.F6),
                btnSwitchMode::fire
        );

    }
}
