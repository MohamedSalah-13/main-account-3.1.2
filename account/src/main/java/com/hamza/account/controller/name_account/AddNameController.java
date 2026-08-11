package com.hamza.account.controller.name_account;

import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.LoadOtherData;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.events.AccountChanged;
import com.hamza.account.features.events.NameChanged;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.opening.OpeningBalanceGuard;
import com.hamza.account.opening.OpeningBalanceRegistry;
import com.hamza.account.opening.OpeningBalanceRule;
import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.base.BaseTotals;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Area;
import com.hamza.account.model.domain.SelPriceTypeModel;
import com.hamza.account.openFxml.AddInterface;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.service.AreaService;
import com.hamza.account.service.SelPriceItemService;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.DaoList;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.controlsfx.others.Utils;
import javafx.application.Platform;
import javafx.beans.binding.BooleanBinding;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

import static com.hamza.controlsfx.language.Setting_Language.generate;
import static com.hamza.controlsfx.others.TextFormat.createNumericTextFormatter;
import static com.hamza.controlsfx.others.Utils.setTextFormatter;

/**
 * @param <T1> for purchase or sales or purchase return or sales return
 * @param <T2> for Totals (purchase or sales or purchase return or sales return)
 * @param <T3> for Names (Customers or Suppliers)
 * @param <T4> for Accounts (Customers or Suppliers)
 */

@FxmlPath(pathFile = "addName.fxml")
@Log4j2
public class AddNameController<T1 extends BasePurchasesAndSales, T2 extends BaseTotals, T3 extends BaseNames, T4 extends BaseAccount>
        extends LoadOtherData<T1, T2, T3, T4> implements AddInterface {

    private final DaoList<T3> interFace;
    private final int id;
    private final EventBus eventBus = ServiceRegistry.get(EventBus.class);
    private final AreaService areaService = ServiceRegistry.get(AreaService.class);
    private final SelPriceItemService selPriceItemService = ServiceRegistry.get(SelPriceItemService.class);

    @FXML
    private Label labelCode, labelName, labelTel, labelAddress, labelBalance, labelLimit, labelOthers, labelSelPrice, labelArea;
    @FXML
    private TextField txtCode, txtName, txtTel, txtAddress, txtBalance, txtLimit;
    @FXML
    private TextArea txtOther;
    @FXML
    private ComboBox<String> comboSelPrice, comboArea;

    public AddNameController(DataInterface<T1, T2, T3, T4> dataInterface
            , DaoFactory daoFactory, DataPublisher dataPublisher
            , int id) throws Exception {
        super(dataInterface, daoFactory, dataPublisher);
        this.id = id;
        this.interFace = nameAndAccountInterface.nameDao();
    }

    @FXML
    public void initialize() {
        otherSetting();
        resetData();
        selectData();
    }

    @Override
    public void otherSetting() {

        labelCode.setText(Setting_Language.WORD_CODE);
        labelName.setText(Setting_Language.WORD_NAME);
        labelTel.setText(Setting_Language.WORD_TEL);
        labelAddress.setText(Setting_Language.WORD_ADDRESS);
        labelBalance.setText(Setting_Language.FIRST_BALANCE);
        labelLimit.setText(Setting_Language.LIMIT);
        labelOthers.setText(Setting_Language.WORD_OTHERS);
        labelSelPrice.setText(Setting_Language.WORD_SEL_PRICE);
        labelArea.setText(Setting_Language.AREA);
        /* -------------------------------------- PromptText -------------------------------------- */
        txtCode.setPromptText(Setting_Language.WORD_CODE);
        txtName.setPromptText(Setting_Language.WORD_NAME);
        txtTel.setPromptText(Setting_Language.WORD_TEL);
        txtAddress.setPromptText(Setting_Language.WORD_ADDRESS);
        txtBalance.setPromptText(Setting_Language.FIRST_BALANCE);
        txtLimit.setPromptText(Setting_Language.LIMIT);
        comboSelPrice.setPromptText(Setting_Language.WORD_SEL_PRICE);
        comboArea.setPromptText(Setting_Language.AREA);
        txtOther.setPromptText(Setting_Language.WORD_OTHERS);

        /* -------------------------------------- comboSetting -------------------------------------- */

        comboSelPrice.getItems().addAll(getPriceType().stream().map(SelPriceTypeModel::getName).toList());

        /* -------------------------------------- show data for customer -------------------------------------- */
        var b = !dataInterface.designInterface().showDataForCustomer();
        txtLimit.setDisable(b);
        comboSelPrice.setDisable(b);


        /* -------------------------------------- other -------------------------------------- */

        // select first data
        Optional<String> first = getPriceType().stream().filter(priceTypeModel -> priceTypeModel.getId() == 1).map(SelPriceTypeModel::getName).findFirst();
        first.ifPresent(s -> comboSelPrice.getSelectionModel().select(s));

        Platform.runLater(() -> txtName.requestFocus());
        setTextFormatter(txtBalance, txtLimit);
        txtTel.setTextFormatter(createNumericTextFormatter());

        getAreas().stream().map(Area::getArea_name).toList().forEach(comboArea.getItems()::add);
        comboArea.getSelectionModel().selectFirst();

    }

    @Override
    public int insertData() throws Exception {

        // add sel price type
        String selectedItem = comboSelPrice.getSelectionModel().getSelectedItem();
        SelPriceTypeModel dataByString = daoFactory.getItemsSelPriceDao().getDataByString(selectedItem);

        // get area
        Area area2 = new Area();
        Optional<Area> areas = getAreas().stream()
                .filter(area -> area.getArea_name().equals(comboArea.getSelectionModel().getSelectedItem()))
                .findFirst();
        if (areas.isPresent())
            area2 = areas.get();

        // object data
        T3 t3 = nameData.objectT(txtName.getText(), txtTel.getText(), txtAddress.getText()
                , txtOther.getText(), Double.parseDouble(txtLimit.getText()), Double.parseDouble(txtBalance.getText())
                , dataByString, area2);

        if (id > 0) {
            nameData.setId(t3, id);
            return interFace.update(t3);
        } else {
            nameData.setId(t3, 0);
            return dataInterface.nameAndAccountInterface().nameDao().insert(t3);
        }
    }

    @Override
    public void afterSaved() {
        Thread thread = new Thread(() -> {
            try {
                dataInterface.loadNameAndAccount();
                Thread.sleep(1000);
                if (eventBus != null) {
                    var kind = nameAndAccountInterface.partyKind();
                    eventBus.publish(new NameChanged(kind));
                    eventBus.publish(new AccountChanged(kind));
                }
            } catch (InterruptedException e) {
                log.error(e.getMessage(), e);
            }
        });
        thread.setDaemon(true);
        thread.start();
        resetData();
    }

    @Override
    public void selectData() {
        if (id > 0) {

            Optional<T3> dataALlList = null;
            try {
                dataALlList = nameAndAccountInterface.nameList()
                        .stream()
                        .filter(e -> nameData.getId(e) == id).findFirst();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            if (dataALlList.isPresent()) {
                T3 t3 = dataALlList.get();
                int id1 = nameData.getId(t3);
                txtCode.setText(String.valueOf(id1));
                txtName.setText(nameData.getName(t3));
                txtTel.setText(nameData.getTel(t3));
                txtAddress.setText(nameData.getAddress(t3));
                txtBalance.setText(String.valueOf(nameData.firstBalance(t3)));
                txtLimit.setText(String.valueOf(nameData.limit(t3)));
                txtOther.setText(nameData.getNotes(t3));
                comboArea.getSelectionModel().select(nameData.getArea(t3).getArea_name());

                if (dataInterface.designInterface().showDataForCustomer()) {
                    comboSelPrice.getSelectionModel().select(nameData.getPriceType(t3));
                }

                lockOpeningBalanceIfMoved(id1);
            }
        }
    }

    /**
     * Greys the opening-balance field once this customer or supplier has moved, and says
     * why.
     * <p>
     * The rule is applied in {@code CustomerDao.update} and {@code SuppliersDao.update},
     * where the row is written - a disabled field is a hint, and the same save is
     * reachable from anything holding the DAO. This is so the user finds out before
     * typing rather than by having the save refused afterwards.
     * <p>
     * The screen serves both sides, so which rule applies comes from
     * {@code partyKind()}, the same answer the events are filtered on. A failure to read
     * it leaves the field enabled: the DAO still refuses a change, so the worst case is
     * a message at the wrong moment rather than a rewritten history.
     */
    private void lockOpeningBalanceIfMoved(int id) {
        try {
            OpeningBalanceRule rule = nameAndAccountInterface.partyKind() == PartyKind.CUSTOMER
                    ? OpeningBalanceRegistry.CUSTOMERS
                    : OpeningBalanceRegistry.SUPPLIERS;

            if (!OpeningBalanceGuard.shared().isLocked(rule, id)) {
                txtBalance.setDisable(false);
                txtBalance.setTooltip(null);
                return;
            }
            txtBalance.setDisable(true);
            txtBalance.setTooltip(new Tooltip(
                    "رصيد أول المدة مقفل: يوجد حركات على هذا الحساب."
                    + "\nتغييره يعيد حساب الرصيد في كل تاريخ سابق."
                    + "\n" + rule.correction()));
        } catch (DaoException e) {
            log.error("Failed to read the opening-balance lock for {}", id, e);
        }
    }

    @Override
    public void resetData() {
        txtCode.setText(generate);
        txtLimit.setText("0");
        txtBalance.setText("0");
        txtOther.clear();
        Utils.clearAll(txtName, txtTel, txtAddress);
        // A blank form is a new row, and a new row has moved nothing.
        txtBalance.setDisable(false);
        txtBalance.setTooltip(null);
    }

    @NotNull
    @Override
    public BooleanBinding checkDataToEnableButton() {
        BooleanBinding binding = (txtName.textProperty().isEmpty());

        // this use for customer only
        if (dataInterface.designInterface().showDataForCustomer()) {
            binding.or(comboSelPrice.getSelectionModel().selectedItemProperty().isNull());
        }
        return binding;
    }

    private List<Area> getAreas() {
        try {
            return areaService.fetchAllAreas();
        } catch (DaoException e) {
            log.error(e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public String styleSheet() {
        return dataInterface.designInterface().styleSheet();
    }

    private List<SelPriceTypeModel> getPriceType() {
        try {
            return selPriceItemService.getSelPriceTypeList();
        } catch (DaoException e) {
            log.error(e.getMessage(), e);
            return List.of();
        }
    }
}
