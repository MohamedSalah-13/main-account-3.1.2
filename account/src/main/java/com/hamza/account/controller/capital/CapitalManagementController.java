package com.hamza.account.controller.capital;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Capital;
import com.hamza.account.model.domain.Partner;
import com.hamza.account.model.domain.PartnerShare;
import com.hamza.account.view.LogApplication;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.account.database.DaoException;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

import java.net.URL;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

@Log4j2
public class CapitalManagementController implements Initializable {

    // Data
    private final ObservableList<Capital> capitalList = FXCollections.observableArrayList();
    private final ObservableList<Partner> partnerList = FXCollections.observableArrayList();
    private final ObservableList<PartnerShare> sharesList = FXCollections.observableArrayList();
    private final int userId = LogApplication.usersVo.getId();
    // ===== Capital Section =====
    @FXML
    private TableView<Capital> capitalTable;
    @FXML
    private TableColumn<Capital, Integer> capitalIdColumn;
    @FXML
    private TableColumn<Capital, String> capitalNameColumn;
    @FXML
    private TableColumn<Capital, Double> totalCapitalColumn;
    @FXML
    private TableColumn<Capital, LocalDate> startDateColumn;
    @FXML
    private TableColumn<Capital, String> capitalStatusColumn;
    @FXML
    private TextField capitalNameField;
    @FXML
    private TextField totalCapitalField;
    @FXML
    private DatePicker startDatePicker;
    @FXML
    private DatePicker endDatePicker;
    @FXML
    private CheckBox activeCheckBox;
    @FXML
    private TextArea capitalNotesArea;
    @FXML
    private Button saveCapitalBtn;
    @FXML
    private Button updateCapitalBtn;
    @FXML
    private Button deleteCapitalBtn;
    @FXML
    private Button newCapitalBtn;
    // ===== Partner Section =====
    @FXML
    private TableView<Partner> partnerTable;
    @FXML
    private TableColumn<Partner, Integer> partnerIdColumn;
    @FXML
    private TableColumn<Partner, String> partnerNameColumn;
    @FXML
    private TableColumn<Partner, String> partnerCodeColumn;
    @FXML
    private TableColumn<Partner, String> partnerPhoneColumn;
    @FXML
    private TableColumn<Partner, String> partnerStatusColumn;
    @FXML
    private TextField partnerNameField;
    @FXML
    private TextField partnerCodeField;
    @FXML
    private TextField nationalIdField;
    @FXML
    private TextField phoneField;
    @FXML
    private TextField emailField;
    @FXML
    private TextField addressField;
    @FXML
    private DatePicker joinDatePicker;
    @FXML
    private CheckBox partnerActiveCheckBox;
    @FXML
    private TextArea partnerNotesArea;
    @FXML
    private Button savePartnerBtn;
    @FXML
    private Button updatePartnerBtn;
    @FXML
    private Button deletePartnerBtn;
    @FXML
    private Button newPartnerBtn;
    // ===== Partner Shares Section =====
    @FXML
    private TableView<PartnerShare> sharesTable;
    @FXML
    private TableColumn<PartnerShare, Integer> shareIdColumn;
    @FXML
    private TableColumn<PartnerShare, String> shareCapitalColumn;
    @FXML
    private TableColumn<PartnerShare, String> sharePartnerColumn;
    @FXML
    private TableColumn<PartnerShare, Double> shareAmountColumn;
    @FXML
    private TableColumn<PartnerShare, Double> sharePercentColumn;
    @FXML
    private TableColumn<PartnerShare, Double> profitPercentColumn;
    @FXML
    private ComboBox<Capital> capitalComboBox;
    @FXML
    private ComboBox<Partner> partnerComboBox;
    @FXML
    private TextField shareAmountField;
    @FXML
    private TextField sharePercentField;
    @FXML
    private TextField profitPercentField;
    @FXML
    private TextField lossPercentField;
    @FXML
    private DatePicker contributionDatePicker;
    @FXML
    private CheckBox managingPartnerCheckBox;
    @FXML
    private TextArea shareNotesArea;
    @FXML
    private Button saveShareBtn;
    @FXML
    private Button updateShareBtn;
    @FXML
    private Button deleteShareBtn;
    @FXML
    private Button newShareBtn;
    @FXML
    private Label totalSharesLabel;
    @FXML
    private Label remainingCapitalLabel;
    private Capital selectedCapital;
    private Partner selectedPartner;
    private PartnerShare selectedShare;
    @Setter
    private DaoFactory daoFactory;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        try {
            setupTables();
            setupComboBoxes();
            loadAllData();
            setupEventHandlers();
            setFormState(FormState.NEW_CAPITAL);
        } catch (Exception e) {
            log.error("خطأ في تهيئة الشاشة", e);
            AllAlerts.alertError("خطأ في تهيئة الشاشة: " + e.getMessage());
        }
    }

    private void setupTables() {
        // Capital Table
        capitalIdColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        capitalNameColumn.setCellValueFactory(new PropertyValueFactory<>("capitalName"));
        totalCapitalColumn.setCellValueFactory(new PropertyValueFactory<>("totalCapital"));
        startDateColumn.setCellValueFactory(new PropertyValueFactory<>("startDate"));
        capitalStatusColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().isActive() ? "نشط" : "غير نشط")
        );

        capitalTable.setItems(capitalList);
        capitalTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldSelection, newSelection) -> {
                    if (newSelection != null) {
                        selectCapital(newSelection);
                    }
                }
        );

        // Partner Table
        partnerIdColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        partnerNameColumn.setCellValueFactory(new PropertyValueFactory<>("partnerName"));
        partnerCodeColumn.setCellValueFactory(new PropertyValueFactory<>("partnerCode"));
        partnerPhoneColumn.setCellValueFactory(new PropertyValueFactory<>("phone"));
        partnerStatusColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().isActive() ? "نشط" : "غير نشط")
        );

        partnerTable.setItems(partnerList);
        partnerTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldSelection, newSelection) -> {
                    if (newSelection != null) {
                        selectPartner(newSelection);
                    }
                }
        );

        // Shares Table
        shareIdColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        shareCapitalColumn.setCellValueFactory(new PropertyValueFactory<>("capitalName"));
        sharePartnerColumn.setCellValueFactory(new PropertyValueFactory<>("partnerName"));
        shareAmountColumn.setCellValueFactory(new PropertyValueFactory<>("shareAmount"));
        sharePercentColumn.setCellValueFactory(new PropertyValueFactory<>("sharePercentage"));
        profitPercentColumn.setCellValueFactory(new PropertyValueFactory<>("profitPercentage"));

        sharesTable.setItems(sharesList);
        sharesTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldSelection, newSelection) -> {
                    if (newSelection != null) {
                        selectShare(newSelection);
                    }
                }
        );
    }

    private void setupComboBoxes() {
        // Capital ComboBox
        capitalComboBox.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(Capital item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getCapitalName());
                }
            }
        });
        capitalComboBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Capital item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getCapitalName());
                }
            }
        });

        // Partner ComboBox
        partnerComboBox.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(Partner item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getPartnerName());
                }
            }
        });
        partnerComboBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Partner item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getPartnerName());
                }
            }
        });

        // Auto calculate share percentage
        shareAmountField.textProperty().addListener((obs, oldVal, newVal) -> autoCalculatePercentage());
        capitalComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            autoCalculatePercentage();
            updateSharesSummary();
        });
    }

    private void setupEventHandlers() {
        // Capital buttons
        saveCapitalBtn.setOnAction(e -> saveCapital());
        updateCapitalBtn.setOnAction(e -> updateCapital());
        deleteCapitalBtn.setOnAction(e -> deleteCapital());
        newCapitalBtn.setOnAction(e -> newCapital());

        // Partner buttons
        savePartnerBtn.setOnAction(e -> savePartner());
        updatePartnerBtn.setOnAction(e -> updatePartner());
        deletePartnerBtn.setOnAction(e -> deletePartner());
        newPartnerBtn.setOnAction(e -> newPartner());

        // Share buttons
        saveShareBtn.setOnAction(e -> saveShare());
        updateShareBtn.setOnAction(e -> updateShare());
        deleteShareBtn.setOnAction(e -> deleteShare());
        newShareBtn.setOnAction(e -> newShare());
    }

    private void loadAllData() {
        loadCapitals();
        loadPartners();
        loadShares();
    }

    private void loadCapitals() {
        try {
            capitalList.clear();
            List<Capital> capitals = daoFactory.capitalDao().loadAll();
            capitalList.addAll(capitals);
            capitalComboBox.setItems(capitalList);
        } catch (DaoException e) {
            log.error("خطأ في تحميل رأس المال", e);
            AllAlerts.alertError("خطأ في تحميل رأس المال: " + e.getMessage());
        }
    }

    private void loadPartners() {
        try {
            partnerList.clear();
            List<Partner> partners = daoFactory.partnerDao().loadAll();
            partnerList.addAll(partners);
            partnerComboBox.setItems(partnerList);
        } catch (DaoException e) {
            log.error("خطأ في تحميل الشركاء", e);
            AllAlerts.alertError("خطأ في تحميل الشركاء: " + e.getMessage());
        }
    }

    private void loadShares() {
        try {
            sharesList.clear();
            List<PartnerShare> shares = daoFactory.partnerShareDao().loadAll();
            sharesList.addAll(shares);
            updateSharesSummary();
        } catch (DaoException e) {
            log.error("خطأ في تحميل الحصص", e);
            AllAlerts.alertError("خطأ في تحميل الحصص: " + e.getMessage());
        }
    }

    // ===== Capital Methods =====
    private void saveCapital() {
        try {
            if (!validateCapitalForm()) return;

            Capital capital = new Capital();
            capital.setCapitalName(capitalNameField.getText().trim());
            capital.setTotalCapital(Double.parseDouble(totalCapitalField.getText()));
            capital.setStartDate(startDatePicker.getValue());
            capital.setEndDate(endDatePicker.getValue());
            capital.setActive(activeCheckBox.isSelected());
            capital.setNotes(capitalNotesArea.getText());
            capital.setUserId(userId);

            int result = daoFactory.capitalDao().insert(capital);
            if (result > 0) {
                AllAlerts.showSuccessAlert("تم إضافة رأس المال بنجاح");
                loadCapitals();
                clearCapitalForm();
            }
        } catch (Exception e) {
            log.error("خطأ في حفظ رأس المال", e);
            AllAlerts.alertError("خطأ في حفظ رأس المال: " + e.getMessage());
        }
    }

    private void updateCapital() {
        try {
            if (selectedCapital == null) {
                AllAlerts.alertWarning("الرجاء اختيار رأس مال للتعديل");
                return;
            }

            if (!validateCapitalForm()) return;

            selectedCapital.setCapitalName(capitalNameField.getText().trim());
            selectedCapital.setTotalCapital(Double.parseDouble(totalCapitalField.getText()));
            selectedCapital.setStartDate(startDatePicker.getValue());
            selectedCapital.setEndDate(endDatePicker.getValue());
            selectedCapital.setActive(activeCheckBox.isSelected());
            selectedCapital.setNotes(capitalNotesArea.getText());

            int result = daoFactory.capitalDao().update(selectedCapital);
            if (result > 0) {
                AllAlerts.showSuccessAlert("تم تعديل رأس المال بنجاح");
                loadCapitals();
                clearCapitalForm();
            }
        } catch (Exception e) {
            log.error("خطأ في تعديل رأس المال", e);
            AllAlerts.alertError("خطأ في تعديل رأس المال: " + e.getMessage());
        }
    }

    private void deleteCapital() {
        try {
            if (selectedCapital == null) {
                AllAlerts.alertWarning("الرجاء اختيار رأس مال للحذف");
                return;
            }

            Optional<ButtonType> result = AllAlerts.showConfirmation(
                    "تأكيد الحذف",
                    "هل أنت متأكد من حذف رأس المال: " + selectedCapital.getCapitalName() + "؟"
            );

            if (result.isPresent() && result.get() == ButtonType.OK) {
                int deleteResult = daoFactory.capitalDao().deleteById(selectedCapital.getId());
                if (deleteResult > 0) {
                    AllAlerts.showSuccessAlert("تم حذف رأس المال بنجاح");
                    loadCapitals();
                    clearCapitalForm();
                }
            }
        } catch (Exception e) {
            log.error("خطأ في حذف رأس المال", e);
            AllAlerts.alertError("خطأ في حذف رأس المال: " + e.getMessage());
        }
    }

    private void selectCapital(Capital capital) {
        this.selectedCapital = capital;
        capitalNameField.setText(capital.getCapitalName());
        totalCapitalField.setText(String.valueOf(capital.getTotalCapital()));
        startDatePicker.setValue(capital.getStartDate());
        endDatePicker.setValue(capital.getEndDate());
        activeCheckBox.setSelected(capital.isActive());
        capitalNotesArea.setText(capital.getNotes());
        setFormState(FormState.EDIT_CAPITAL);
    }

    private void newCapital() {
        clearCapitalForm();
        setFormState(FormState.NEW_CAPITAL);
    }

    private void clearCapitalForm() {
        selectedCapital = null;
        capitalNameField.clear();
        totalCapitalField.clear();
        startDatePicker.setValue(LocalDate.now());
        endDatePicker.setValue(null);
        activeCheckBox.setSelected(true);
        capitalNotesArea.clear();
        capitalTable.getSelectionModel().clearSelection();
    }

    private boolean validateCapitalForm() {
        if (capitalNameField.getText().trim().isEmpty()) {
            AllAlerts.alertWarning("الرجاء إدخال اسم رأس المال");
            return false;
        }
        if (totalCapitalField.getText().trim().isEmpty()) {
            AllAlerts.alertWarning("الرجاء إدخال قيمة رأس المال");
            return false;
        }
        try {
            double amount = Double.parseDouble(totalCapitalField.getText());
            if (amount <= 0) {
                AllAlerts.alertWarning("قيمة رأس المال يجب أن تكون أكبر من صفر");
                return false;
            }
        } catch (NumberFormatException e) {
            AllAlerts.alertWarning("قيمة رأس المال غير صحيحة");
            return false;
        }
        if (startDatePicker.getValue() == null) {
            AllAlerts.alertWarning("الرجاء اختيار تاريخ البداية");
            return false;
        }
        return true;
    }

    // ===== Partner Methods =====
    private void savePartner() {
        try {
            if (!validatePartnerForm()) return;

            Partner partner = new Partner();
            partner.setPartnerName(partnerNameField.getText().trim());
            partner.setPartnerCode(partnerCodeField.getText().trim());
            partner.setNationalId(nationalIdField.getText().trim());
            partner.setPhone(phoneField.getText().trim());
            partner.setEmail(emailField.getText().trim());
            partner.setAddress(addressField.getText().trim());
            partner.setJoinDate(joinDatePicker.getValue());
            partner.setActive(partnerActiveCheckBox.isSelected());
            partner.setNotes(partnerNotesArea.getText());
            partner.setUserId(userId);

            int result = daoFactory.partnerDao().insert(partner);
            if (result > 0) {
                AllAlerts.showSuccessAlert("تم إضافة الشريك بنجاح");
                loadPartners();
                clearPartnerForm();
            }
        } catch (Exception e) {
            log.error("خطأ في حفظ الشريك", e);
            AllAlerts.alertError("خطأ في حفظ الشريك: " + e.getMessage());
        }
    }

    private void updatePartner() {
        try {
            if (selectedPartner == null) {
                AllAlerts.alertWarning("الرجاء اختيار شريك للتعديل");
                return;
            }

            if (!validatePartnerForm()) return;

            selectedPartner.setPartnerName(partnerNameField.getText().trim());
            selectedPartner.setPartnerCode(partnerCodeField.getText().trim());
            selectedPartner.setNationalId(nationalIdField.getText().trim());
            selectedPartner.setPhone(phoneField.getText().trim());
            selectedPartner.setEmail(emailField.getText().trim());
            selectedPartner.setAddress(addressField.getText().trim());
            selectedPartner.setJoinDate(joinDatePicker.getValue());
            selectedPartner.setActive(partnerActiveCheckBox.isSelected());
            selectedPartner.setNotes(partnerNotesArea.getText());

            int result = daoFactory.partnerDao().update(selectedPartner);
            if (result > 0) {
                AllAlerts.showSuccessAlert("تم تعديل الشريك بنجاح");
                loadPartners();
                clearPartnerForm();
            }
        } catch (Exception e) {
            log.error("خطأ في تعديل الشريك", e);
            AllAlerts.alertError("خطأ في تعديل الشريك: " + e.getMessage());
        }
    }

    private void deletePartner() {
        try {
            if (selectedPartner == null) {
                AllAlerts.alertWarning("الرجاء اختيار شريك للحذف");
                return;
            }

            Optional<ButtonType> result = AllAlerts.showConfirmation(
                    "تأكيد الحذف",
                    "هل أنت متأكد من حذف الشريك: " + selectedPartner.getPartnerName() + "؟"
            );

            if (result.isPresent() && result.get() == ButtonType.OK) {
                int deleteResult = daoFactory.partnerDao().deleteById(selectedPartner.getId());
                if (deleteResult > 0) {
                    AllAlerts.showSuccessAlert("تم حذف الشريك بنجاح");
                    loadPartners();
                    clearPartnerForm();
                }
            }
        } catch (Exception e) {
            log.error("خطأ في حذف الشريك", e);
            AllAlerts.alertError("خطأ في حذف الشريك: " + e.getMessage());
        }
    }

    private void selectPartner(Partner partner) {
        this.selectedPartner = partner;
        partnerNameField.setText(partner.getPartnerName());
        partnerCodeField.setText(partner.getPartnerCode());
        nationalIdField.setText(partner.getNationalId());
        phoneField.setText(partner.getPhone());
        emailField.setText(partner.getEmail());
        addressField.setText(partner.getAddress());
        joinDatePicker.setValue(partner.getJoinDate());
        partnerActiveCheckBox.setSelected(partner.isActive());
        partnerNotesArea.setText(partner.getNotes());
        setFormState(FormState.EDIT_PARTNER);
    }

    private void newPartner() {
        clearPartnerForm();
        setFormState(FormState.NEW_PARTNER);
    }

    private void clearPartnerForm() {
        selectedPartner = null;
        partnerNameField.clear();
        partnerCodeField.clear();
        nationalIdField.clear();
        phoneField.clear();
        emailField.clear();
        addressField.clear();
        joinDatePicker.setValue(LocalDate.now());
        partnerActiveCheckBox.setSelected(true);
        partnerNotesArea.clear();
        partnerTable.getSelectionModel().clearSelection();
    }

    private boolean validatePartnerForm() {
        if (partnerNameField.getText().trim().isEmpty()) {
            AllAlerts.alertWarning("الرجاء إدخال اسم الشريك");
            return false;
        }
        if (joinDatePicker.getValue() == null) {
            AllAlerts.alertWarning("الرجاء اختيار تاريخ الانضمام");
            return false;
        }
        return true;
    }

    // ===== Share Methods =====
    private void saveShare() {
        try {
            if (!validateShareForm()) return;

            PartnerShare share = new PartnerShare();
            share.setCapitalId(capitalComboBox.getValue().getId());
            share.setPartnerId(partnerComboBox.getValue().getId());
            share.setShareAmount(Double.parseDouble(shareAmountField.getText()));
            share.setSharePercentage(Double.parseDouble(sharePercentField.getText()));
            share.setProfitPercentage(Double.parseDouble(profitPercentField.getText()));
            share.setLossPercentage(Double.parseDouble(lossPercentField.getText()));
            share.setContributionDate(contributionDatePicker.getValue());
            share.setManagingPartner(managingPartnerCheckBox.isSelected());
            share.setNotes(shareNotesArea.getText());
            share.setUserId(userId);

            int result = daoFactory.partnerShareDao().insert(share);
            if (result > 0) {
                AllAlerts.showSuccessAlert("تم إضافة الحصة بنجاح");
                loadShares();
                clearShareForm();
            }
        } catch (Exception e) {
            log.error("خطأ في حفظ الحصة", e);
            AllAlerts.alertError("خطأ في حفظ الحصة: " + e.getMessage());
        }
    }

    private void updateShare() {
        try {
            if (selectedShare == null) {
                AllAlerts.alertWarning("الرجاء اختيار حصة للتعديل");
                return;
            }

            if (!validateShareForm()) return;

            selectedShare.setShareAmount(Double.parseDouble(shareAmountField.getText()));
            selectedShare.setSharePercentage(Double.parseDouble(sharePercentField.getText()));
            selectedShare.setProfitPercentage(Double.parseDouble(profitPercentField.getText()));
            selectedShare.setLossPercentage(Double.parseDouble(lossPercentField.getText()));
            selectedShare.setContributionDate(contributionDatePicker.getValue());
            selectedShare.setManagingPartner(managingPartnerCheckBox.isSelected());
            selectedShare.setNotes(shareNotesArea.getText());

            int result = daoFactory.partnerShareDao().update(selectedShare);
            if (result > 0) {
                AllAlerts.showSuccessAlert("تم تعديل الحصة بنجاح");
                loadShares();
                clearShareForm();
            }
        } catch (Exception e) {
            log.error("خطأ في تعديل الحصة", e);
            AllAlerts.alertError("خطأ في تعديل الحصة: " + e.getMessage());
        }
    }

    private void deleteShare() {
        try {
            if (selectedShare == null) {
                AllAlerts.alertWarning("الرجاء اختيار حصة للحذف");
                return;
            }

            Optional<ButtonType> result = AllAlerts.showConfirmation(
                    "تأكيد الحذف",
                    "هل أنت متأكد من حذف حصة الشريك: " + selectedShare.getPartnerName() + "؟"
            );

            if (result.isPresent() && result.get() == ButtonType.OK) {
                int deleteResult = daoFactory.partnerShareDao().deleteById(selectedShare.getId());
                if (deleteResult > 0) {
                    AllAlerts.showSuccessAlert("تم حذف الحصة بنجاح");
                    loadShares();
                    clearShareForm();
                }
            }
        } catch (Exception e) {
            log.error("خطأ في حذف الحصة", e);
            AllAlerts.alertError("خطأ في حذف الحصة: " + e.getMessage());
        }
    }

    private void selectShare(PartnerShare share) {
        this.selectedShare = share;

        // Find and select capital
        capitalComboBox.getItems().stream()
                .filter(c -> c.getId() == share.getCapitalId())
                .findFirst()
                .ifPresent(capitalComboBox::setValue);

        // Find and select partner
        partnerComboBox.getItems().stream()
                .filter(p -> p.getId() == share.getPartnerId())
                .findFirst()
                .ifPresent(partnerComboBox::setValue);

        shareAmountField.setText(String.valueOf(share.getShareAmount()));
        sharePercentField.setText(String.valueOf(share.getSharePercentage()));
        profitPercentField.setText(String.valueOf(share.getProfitPercentage()));
        lossPercentField.setText(String.valueOf(share.getLossPercentage()));
        contributionDatePicker.setValue(share.getContributionDate());
        managingPartnerCheckBox.setSelected(share.isManagingPartner());
        shareNotesArea.setText(share.getNotes());

        setFormState(FormState.EDIT_SHARE);
    }

    private void newShare() {
        clearShareForm();
        setFormState(FormState.NEW_SHARE);
    }

    private void clearShareForm() {
        selectedShare = null;
        capitalComboBox.setValue(null);
        partnerComboBox.setValue(null);
        shareAmountField.clear();
        sharePercentField.clear();
        profitPercentField.clear();
        lossPercentField.clear();
        contributionDatePicker.setValue(LocalDate.now());
        managingPartnerCheckBox.setSelected(false);
        shareNotesArea.clear();
        sharesTable.getSelectionModel().clearSelection();
    }

    private boolean validateShareForm() {
        if (capitalComboBox.getValue() == null) {
            AllAlerts.alertWarning("الرجاء اختيار رأس المال");
            return false;
        }
        if (partnerComboBox.getValue() == null) {
            AllAlerts.alertWarning("الرجاء اختيار الشريك");
            return false;
        }
        if (shareAmountField.getText().trim().isEmpty()) {
            AllAlerts.alertWarning("الرجاء إدخال مبلغ الحصة");
            return false;
        }
        if (sharePercentField.getText().trim().isEmpty()) {
            AllAlerts.alertWarning("الرجاء إدخال نسبة الحصة");
            return false;
        }
        if (contributionDatePicker.getValue() == null) {
            AllAlerts.alertWarning("الرجاء اختيار تاريخ المساهمة");
            return false;
        }

        try {
            double percentage = Double.parseDouble(sharePercentField.getText());
            if (percentage <= 0 || percentage > 100) {
                AllAlerts.alertWarning("نسبة الحصة يجب أن تكون بين 0 و 100");
                return false;
            }
        } catch (NumberFormatException e) {
            AllAlerts.alertWarning("نسبة الحصة غير صحيحة");
            return false;
        }

        return true;
    }

    private void autoCalculatePercentage() {
        if (capitalComboBox.getValue() != null && !shareAmountField.getText().trim().isEmpty()) {
            try {
                double shareAmount = Double.parseDouble(shareAmountField.getText());
                double totalCapital = capitalComboBox.getValue().getTotalCapital();
                double percentage = (shareAmount / totalCapital) * 100;
                sharePercentField.setText(String.format("%.2f", percentage));

                // Set profit and loss percentage same as share percentage by default
                if (profitPercentField.getText().trim().isEmpty()) {
                    profitPercentField.setText(String.format("%.2f", percentage));
                }
                if (lossPercentField.getText().trim().isEmpty()) {
                    lossPercentField.setText(String.format("%.2f", percentage));
                }
            } catch (NumberFormatException e) {
                // Ignore
            }
        }
    }

    private void updateSharesSummary() {
        if (capitalComboBox.getValue() == null) {
            totalSharesLabel.setText("0.00%");
            remainingCapitalLabel.setText("0.00");
            return;
        }

        try {
            Capital selectedCap = capitalComboBox.getValue();
            List<PartnerShare> capitalShares = daoFactory.partnerShareDao()
                    .getSharesByCapitalId(selectedCap.getId());

            double totalPercentage = capitalShares.stream()
                    .mapToDouble(PartnerShare::getSharePercentage)
                    .sum();

            double totalAmount = capitalShares.stream()
                    .mapToDouble(PartnerShare::getShareAmount)
                    .sum();

            double remaining = selectedCap.getTotalCapital() - totalAmount;

            totalSharesLabel.setText(String.format("%.2f%%", totalPercentage));
            remainingCapitalLabel.setText(String.format("%.2f", remaining));

            // Color coding
            if (totalPercentage > 100) {
                totalSharesLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
            } else if (totalPercentage == 100) {
                totalSharesLabel.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
            } else {
                totalSharesLabel.setStyle("-fx-text-fill: orange;");
            }
        } catch (DaoException e) {
            log.error("خطأ في حساب ملخص الحصص", e);
        }
    }

    private void setFormState(FormState state) {
        switch (state) {
            case NEW_CAPITAL:
                saveCapitalBtn.setDisable(false);
                updateCapitalBtn.setDisable(true);
                deleteCapitalBtn.setDisable(true);
                break;
            case EDIT_CAPITAL:
                saveCapitalBtn.setDisable(true);
                updateCapitalBtn.setDisable(false);
                deleteCapitalBtn.setDisable(false);
                break;
            case NEW_PARTNER:
                savePartnerBtn.setDisable(false);
                updatePartnerBtn.setDisable(true);
                deletePartnerBtn.setDisable(true);
                break;
            case EDIT_PARTNER:
                savePartnerBtn.setDisable(true);
                updatePartnerBtn.setDisable(false);
                deletePartnerBtn.setDisable(false);
                break;
            case NEW_SHARE:
                saveShareBtn.setDisable(false);
                updateShareBtn.setDisable(true);
                deleteShareBtn.setDisable(true);
                break;
            case EDIT_SHARE:
                saveShareBtn.setDisable(true);
                updateShareBtn.setDisable(false);
                deleteShareBtn.setDisable(false);
                break;
        }
    }

    private enum FormState {
        NEW_CAPITAL, EDIT_CAPITAL,
        NEW_PARTNER, EDIT_PARTNER,
        NEW_SHARE, EDIT_SHARE
    }
}
