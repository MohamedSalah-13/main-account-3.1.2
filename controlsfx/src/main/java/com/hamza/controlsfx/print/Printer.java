package com.hamza.controlsfx.print;

import javafx.collections.ObservableSet;
import javafx.scene.control.RadioButton;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

@Log4j2
public class Printer {

    /**
     * Populates a list of radio buttons with available printers and sets up an
     * event listener to make the selected printer the default printer.
     *
     * This method fetches all available printers using {@code javafx.print.Printer.getAllPrinters()},
     * iterates through each printer, creates a radio button for it, and sets its selection state. The
     * radio button corresponding to the default printer is automatically selected. An event listener
     * is attached to each radio button to set the clicked printer as the default printer using {@link #setDefaultPrinter(String)}.
     */
    private void allPrinter() {
        ObservableSet<javafx.print.Printer> printers = javafx.print.Printer.getAllPrinters();
        for (javafx.print.Printer printer : printers) {
            RadioButton box = new RadioButton(printer.getName());
//            group.getToggles().add(box);
            box.setSelected(printer.getName().equals(javafx.print.Printer.getDefaultPrinter().getName()));
            box.selectedProperty().addListener((observable, oldValue, newValue) -> {
                setDefaultPrinter(box.getText());
            });

        }

    }

    /**
     * Sets the specified printer as the default printer on the system using WMIC command.
     *
     * @param printerName the name of the printer to be set as default
     */
    private void setDefaultPrinter(@NotNull String printerName) {
        String defaultPrinterSetter = "wmic printer where name='" + printerName + "' call setdefaultprinter";
        try {
            Runtime.getRuntime().exec("cmd /c start cmd.exe /C " + defaultPrinterSetter);
        } catch (Exception e) {
            log.error(e.getMessage(), e.getCause());
        }
    }

}
