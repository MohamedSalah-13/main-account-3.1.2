package com.hamza.controlsfx.controller;

import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.interfaceData.Disable;
import com.hamza.controlsfx.interfaceData.ToolbarAccountInt;
import com.hamza.controlsfx.language.Setting_Language;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import lombok.extern.log4j.Log4j2;

import java.lang.reflect.Method;

@Log4j2
public class ToolbarAccountController<T> {

    private final ObservableList<T> observableList;
    private final ObjectProperty<T> selectedItem = new SimpleObjectProperty<>();
    private final ToolbarAccountInt<T> toolbarAccountInt;
    @FXML
    private Button btnFirstPage, btnPreviousRecord, btnNextRecord, btnLastPage, btnAddNew, btnSave, btnPrint, btnDelete;

    public ToolbarAccountController(ToolbarAccountInt<T> toolbarAccountInt) {
        this.toolbarAccountInt = toolbarAccountInt;
        this.observableList = toolbarAccountInt.observableList();
    }

    @FXML
    public void initialize() {
        otherSetting();
        actionButton(toolbarAccountInt);
        checkDisableAnnotation("addNewAccount", btnAddNew);
        checkDisableAnnotation("saveAccount", btnSave);
        checkDisableAnnotation("deleteAccount", btnDelete);
        checkDisableAnnotation("printAccount", btnPrint);
    }

    /**
     * Checks if the deleteAccount method in the implementation class has the @Disable annotation.
     * If the annotation is present, disables the print button.
     */
    private void checkDisableAnnotation(String nameMethod, Button button) {
        try {
            // Get the actual implementation class of toolbarAccountInt
            Class<?> implementationClass = toolbarAccountInt.getClass();

            // Try to find the deleteAccount method
            Method deleteAccountMethod = null;
            for (Method method : implementationClass.getMethods()) {
                if (method.getName().equals(nameMethod)) {
                    deleteAccountMethod = method;
                    break;
                }
            }

            // If method is found and has @Disable annotation, disable the print button
            if (deleteAccountMethod != null && deleteAccountMethod.isAnnotationPresent(Disable.class)) {
                button.setDisable(true);
            }
        } catch (Exception e) {
            logError(e);
        }
    }

    private void otherSetting() {
        btnPreviousRecord.setTooltip(new Tooltip("السابق"));
        btnFirstPage.setTooltip(new Tooltip("السجل الاولى"));
        btnNextRecord.setTooltip(new Tooltip("التالى"));
        btnLastPage.setTooltip(new Tooltip("السجل الاخير"));
        btnAddNew.setTooltip(new Tooltip(Setting_Language.WORD_NEW));
        btnSave.setTooltip(new Tooltip(Setting_Language.WORD_SAVE));
        btnPrint.setTooltip(new Tooltip(Setting_Language.WORD_PRINT));
        btnDelete.setTooltip(new Tooltip(Setting_Language.WORD_UPDATE));

    }

    private void actionButton(ToolbarAccountInt<T> toolbarAccountInt) {
        btnAddNew.setOnAction(event -> toolbarAccountInt.addNewAccount());
        btnSave.setOnAction(event -> save(toolbarAccountInt));
        btnPrint.setOnAction(event -> toolbarAccountInt.printAccount());
        btnDelete.setOnAction(event -> delete(toolbarAccountInt));
        btnFirstPage.setOnAction(event -> {
            setFirstIndex();
            toolbarAccountInt.firstPage(selectedItem.get());
        });

        btnPreviousRecord.setOnAction(event -> {
            setPreviousIndex();
            toolbarAccountInt.previousPage(selectedItem.get());
        });

        btnNextRecord.setOnAction(event -> {
            setNextIndex();
            toolbarAccountInt.nextPage(selectedItem.get());
        });
        btnLastPage.setOnAction(event -> {
            setLastIndex();
            toolbarAccountInt.lastPage(selectedItem.get());
        });
    }

    private void delete(ToolbarAccountInt<T> toolbarAccountInt) {
        // check confirm delete
        if (AllAlerts.confirmDelete()) {
            int i = 0;
            try {
                i = toolbarAccountInt.deleteAccount();
            } catch (Exception e) {
                logError(e);
            }
            if (i >= 1) {
                AllAlerts.alertDelete();
                toolbarAccountInt.afterSaveOrDelete();
                toolbarAccountInt.publisherTable().notifyObservers();
            } else AllAlerts.alertError(Setting_Language.PLEASE_INSERT_ALL_DATA);
        }
    }

    private void save(ToolbarAccountInt<T> toolbarAccountInt) {
        // check confirm save
        if (AllAlerts.confirmSave()) {
            T i = null;
            try {
                i = toolbarAccountInt.saveAccount();
            } catch (Exception e) {
                logError(e);
            }
            if (i != null) {
                AllAlerts.alertSave();
                toolbarAccountInt.afterSaveOrDelete();
                toolbarAccountInt.publisherTable().notifyObservers();
            }
        }
    }

    /**
     * Sets the first index from the observable list to the selected item property if the list is not empty.
     * <p>
     * This method checks if the observable list is empty using {@code checkIfListIsEmpty()}.
     * If the list is not empty, it retrieves the first element of the observable list
     * and sets it as the value of the {@code selectedItem} property.
     * <p>
     * The method performs the following actions:
     * - Checks if the list is empty and immediately returns if true.
     * - Retrieves the first element from the observable list.
     * - Updates the {@code selectedItem} property with the first element.
     */
    private void setFirstIndex() {
        if (checkIfListIsEmpty()) return;
        selectedItem.set(observableList.getFirst());
    }

    /**
     * Updates the selected item to the last element in the observable list.
     * If the list is empty, the method will exit without making any changes.
     * The method retrieves the last element from the observable list and sets
     * it as the selected item.
     */
    private void setLastIndex() {
        if (checkIfListIsEmpty()) return;
        selectedItem.set(observableList.getLast());
    }

    /**
     * Updates the `selectedItem` property to the previous item in the `observableList`.
     * If the current item is the first item in the list, it wraps around to the last item.
     * If the list is empty, no action is performed.
     * <p>
     * This method calculates the previous index of the `selectedItem` in the `observableList`
     * and sets the `selectedItem` to the item at the calculated index. If the `selectedItem`
     * is at the beginning of the list, the index wraps around to the last item in the list.
     * <p>
     * Preconditions:
     * - `observableList` is a valid list containing items.
     * - `selectedItem` is not null and points to an item in the list.
     * <p>
     * Postconditions:
     * - `selectedItem` is updated to the previous item in the list.
     * - If the list is empty, `selectedItem` remains unchanged.
     */
    private void setPreviousIndex() {
        if (checkIfListIsEmpty()) return;
        int index = observableList.indexOf(selectedItem.get()) - 1;
        if (index < 0) {
            index = observableList.size() - 1;
        }
        selectedItem.set(observableList.get(index));
    }

    /**
     * Updates the index of the selected item in the observable list to the next item.
     * If the currently selected item's index is the last in the list, it wraps around
     * and selects the first item in the list. If the list is empty, the method does nothing.
     * <p>
     * This method modifies the `selectedItem` property by updating it to the next
     * element in the `observableList`. The index computation accounts for wrapping
     * when reaching the end of the list.
     */
    private void setNextIndex() {
        if (checkIfListIsEmpty()) return;
        int index = observableList.indexOf(selectedItem.get()) + 1;
        if (index >= observableList.size()) {
            index = 0;
        }
        selectedItem.set(observableList.get(index));
    }

    /**
     * Checks whether the observable list is empty.
     * Logs an informational message if the list is empty.
     *
     * @return true if the observable list is empty, false otherwise
     */
    private boolean checkIfListIsEmpty() {
        if (observableList.isEmpty()) {
            log.info("Observable list is empty.");
            return true;
        }
        return false;
    }

    private void logError(Exception e) {
        AllAlerts.alertError(e.getMessage());
        log.error(e.getMessage(), e.getCause());
    }
}
