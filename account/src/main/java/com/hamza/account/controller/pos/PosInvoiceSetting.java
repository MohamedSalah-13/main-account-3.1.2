package com.hamza.account.controller.pos;

import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.Sales;
import com.hamza.controlsfx.alert.AllAlerts;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@Log4j2
@Setter
public class PosInvoiceSetting extends PosInvoiceSettingData {

    // Constants extracted to avoid magic strings
    public static final String SUSPENDED_DIR_NAME = "suspended_invoices";
    private static final String SUSPENDED_FILE_EXT = ".inv";

    public void suspendInvoice() {
        try {
            if (invoiceItems == null || invoiceItems.isEmpty()) {
                AllAlerts.alertError("لا يوجد فاتورة للتعليق");
                return;
            }

            if (textCode == null || textCode.get().isBlank()) {
                AllAlerts.alertError("من فضلك أدخل رقم الفاتورة");
                return;
            }

            final int invoiceCode;
            try {
                invoiceCode = Integer.parseInt(textCode.get().trim());
            } catch (NumberFormatException nfe) {
                AllAlerts.alertError("رقم الفاتورة غير صحيح");
                return;
            }

            final Path suspendDir = Path.of(SUSPENDED_DIR_NAME);
            Files.createDirectories(suspendDir);

            final List<LineItemDTO> itemDTOs = invoiceItems
                    .stream()
                    .map(this::mapToLineItemDTO)
                    .toList();

            final SuspendedInvoiceDTO dto = new SuspendedInvoiceDTO(
                    invoiceCode,
                    customerId.get(), // auto-boxed; null not expected here
                    textCustomName.get(),
                    textTel.get(),
                    textAddress.get(),
                    comboArea.get() == null ? "" : comboArea.get(),
                    itemDTOs,
                    parseDouble(textTotal.get()),
                    parseDouble(textDiscount.get()),
                    parseDouble(textAddons.get())
            );

            final Path file = suspendDir.resolve(invoiceCode + SUSPENDED_FILE_EXT);
            try (ObjectOutputStream oos = new ObjectOutputStream(Files.newOutputStream(file))) {
                oos.writeObject(dto);
            }

            AllAlerts.alertSaveWithMessage("تم تعليق الفاتورة برقم " + invoiceCode);
//            setIsChangeData(true);
            clearFields();
        } catch (Exception e) {
            logError(e);
        }
    }

    public void showSuspendedInvoices() {
        try {
            Path suspendedDir = Path.of(SUSPENDED_DIR_NAME);
            String NO_SUSPENDED_MSG = "لا توجد فواتير معلقة";
            if (!Files.exists(suspendedDir)) {
                AllAlerts.alertError(NO_SUSPENDED_MSG);
                return;
            }

            List<SuspendedInvoice> invoices = loadSuspendedInvoices(suspendedDir);
            if (invoices.isEmpty()) {
                AllAlerts.alertError(NO_SUSPENDED_MSG);
                return;
            }

            ListView<SuspendedInvoice> listView = new ListView<>();
            listView.getItems().setAll(invoices);
            listView.setCellFactory(param -> new ListCell<>() {
                @Override
                protected void updateItem(SuspendedInvoice item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null
                            ? null
                            : "فاتورة رقم: " + item.invoiceCode() + " - " + item.customerName());
                }
            });

            Dialog<SuspendedInvoice> dialog = new Dialog<>();
            String DIALOG_TITLE = "الفواتير المعلقة";
            dialog.setTitle(DIALOG_TITLE);
            dialog.getDialogPane().setContent(listView);
            dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
            dialog.setResultConverter(btn ->
                    btn == ButtonType.OK ? listView.getSelectionModel().getSelectedItem() : null);

            DialogButtons.changeNameAndGraphic(dialog.getDialogPane());
            dialog.showAndWait().ifPresent(this::restoreSuspendedInvoice);
        } catch (Exception e) {
            logError(e);
        }
    }

    private List<SuspendedInvoice> loadSuspendedInvoices(Path suspendedDir) {
        try (var paths = Files.list(suspendedDir)) {
            return paths
                    .filter(Files::isRegularFile)
                    .map(this::readSuspendedInvoice)
                    .flatMap(Optional::stream)
                    .toList();
        } catch (IOException e) {
            logError(e);
            return List.of();
        }
    }

    private Optional<SuspendedInvoice> readSuspendedInvoice(Path path) {
        try (InputStream in = Files.newInputStream(path);
             ObjectInputStream ois = new ObjectInputStream(in)) {
            Object obj = ois.readObject();
            log.debug("Read suspended invoice {}: {}", path, obj);

            switch (obj) {
                case null -> {
                    log.error("Unexpected null object when reading suspended invoice: {}", path);
                    return Optional.empty();
                }


                // Preferred persisted format
                case SuspendedInvoiceDTO dto -> {
                    SuspendedInvoice suspendedInvoice = new SuspendedInvoice(
                            dto.invoiceCode(),
                            dto.customerId() == null ? 0 : dto.customerId(),
                            dto.customerName(),
                            dto.tel(),
                            dto.address(),
                            dto.area(),
                            dto.items(),
                            dto.total(),
                            dto.discount(),
                            dto.addons()
                    );
                    return Optional.of(suspendedInvoice);
                }


                // Backward compatibility: if previously a SuspendedInvoice was written directly
                case SuspendedInvoice suspendedInvoice -> {
                    return Optional.of(suspendedInvoice);
                }
                default -> {
                }
            }

            log.error("Unexpected object type when reading suspended invoice {}: {}", path, obj.getClass().getName());
            return Optional.empty();
        } catch (Exception e) {
            logError(e);
            return Optional.empty();
        }
    }

    private void restoreSuspendedInvoice(SuspendedInvoice invoice) {
        try {
//            setIsChangeData(true);
            clearFields();
            applyInvoiceToUi(invoice);
            Path file = Path.of(SUSPENDED_DIR_NAME).resolve(invoice.invoiceCode() + SUSPENDED_FILE_EXT);
            Files.deleteIfExists(file);
        } catch (Exception e) {
            logError(e);
        }
    }

    private void applyInvoiceToUi(SuspendedInvoice invoice) {
        customerId.set(invoice.customerId());
        textCustomName.set(invoice.customerName());
        textTel.set(invoice.customerPhone());
        textAddress.set(invoice.customerAddress());
        comboArea.set(invoice.customerArea());
        log.info(invoice.items().toString());

        if (invoiceItems != null) {
            invoiceItems.clear();
//            if (lineItemMapper != null) {
//                invoiceItems.addAll(invoice.items().stream().map(lineItemMapper).toList());
//            }

            List<BasePurchasesAndSales> convertedItems = invoice.items().stream()
                    .map(this::convertToBasePurchasesAndSales)
                    .toList();
            invoiceItems.addAll(convertedItems);

        }

        textCode.set(String.valueOf(invoice.invoiceCode()));
        textTotal.set(String.valueOf(invoice.total()));
        textDiscount.set(String.valueOf(invoice.discount()));
        textAddons.set(String.valueOf(invoice.addons()));
    }

    private BasePurchasesAndSales convertToBasePurchasesAndSales(LineItemDTO dto) {
        Sales item = new Sales();
        // Assuming BasePurchasesAndSales has appropriate setters
        item.setId(Integer.parseInt(dto.code()));
        item.setItems(new ItemsModel(dto.code, dto.name));
        item.setQuantity(dto.quantity());
        item.setPrice(dto.unitPrice());
        item.setTotal(dto.lineTotal());
        return item;
    }

    private void clearFields() {
        customerId.set(0);
        textCustomName.set(null);
        textTel.set(null);
        textAddress.set(null);
        comboArea.set(null);
        textTotal.set(null);
        textDiscount.set(null);
        textAddons.set(null);
        if (invoiceItems != null) {
            invoiceItems.clear();
        }
    }

    private void logError(Exception e) {
        log.error(e.getMessage(), e);
        AllAlerts.alertError(e.getMessage());
    }

    private LineItemDTO mapToLineItemDTO(BasePurchasesAndSales r) {
        // Adapt to your model as needed
        return new PosInvoiceSetting.LineItemDTO(
                String.valueOf(r.getId()),
                r.getItems().getNameItem(),
                r.getQuantity(),
                r.getPrice(),
                r.getTotal()
        );
    }

    private record SuspendedInvoice(int invoiceCode, int customerId, String customerName, String customerPhone,
                                    String customerAddress, String customerArea, List<LineItemDTO> items,
                                    double total, double discount, double addons) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
    }

    private record LineItemDTO(
            String code,        // adapt names/types to your model
            String name,
            double quantity,
            double unitPrice,
            double lineTotal
    ) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
    }

    private record SuspendedInvoiceDTO(
            int invoiceCode,
            Integer customerId,
            String customerName,
            String tel,
            String address,
            String area,
            List<LineItemDTO> items,
            double total,
            double discount,
            double addons
    ) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
    }
}