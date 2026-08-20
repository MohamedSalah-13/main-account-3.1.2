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
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Area;
import com.hamza.account.model.domain.SelPriceTypeModel;
import com.hamza.account.openFxml.AddInterface;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.service.AreaService;
import com.hamza.account.service.SelPriceItemService;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.DaoList;
import com.hamza.controlsfx.language.LanguageManager;
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

import static com.hamza.controlsfx.others.TextFormat.createNumericTextFormatter;
import static com.hamza.controlsfx.others.Utils.setTextFormatter;

/**
 * @param <T3> for Names (Customers or Suppliers)
 * @param <T4> for Accounts (Customers or Suppliers)
 */

@FxmlPath(pathFile = "addName.fxml")
@Log4j2
public class AddNameController<T3 extends BaseNames, T4 extends BaseAccount>
        extends LoadOtherData<T3, T4> implements AddInterface {

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

    public AddNameController(DataInterface<?, ?, T3, T4> dataInterface
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

        var lm = LanguageManager.getInstance();
        labelCode.setText(lm.getString("code"));
        labelName.setText(lm.getString("name"));
        labelTel.setText(lm.getString("tel"));
        labelAddress.setText(lm.getString("address"));
        labelBalance.setText(lm.getString("firstBalance"));
        labelLimit.setText(lm.getString("column.limit"));
        labelOthers.setText(lm.getString("others"));
        labelSelPrice.setText(lm.getString("selPrice"));
        labelArea.setText(lm.getString("party.area"));
        /* -------------------------------------- PromptText -------------------------------------- */
        txtCode.setPromptText(lm.getString("code"));
        txtName.setPromptText(lm.getString("name"));
        txtTel.setPromptText(lm.getString("tel"));
        txtAddress.setPromptText(lm.getString("address"));
        txtBalance.setPromptText(lm.getString("firstBalance"));
        txtLimit.setPromptText(lm.getString("column.limit"));
        comboSelPrice.setPromptText(lm.getString("selPrice"));
        comboArea.setPromptText(lm.getString("party.area"));
        txtOther.setPromptText(lm.getString("others"));

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
            t3.setId(id);
            return dataInterface.nameAndAccountInterface().saveName(t3);
        } else {
            t3.setId(0);
            return dataInterface.nameAndAccountInterface().saveName(t3);
        }
    }

    @Override
    public void afterSaved() {
        Thread thread = new Thread(() -> {
            try {
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
                        .filter(e -> e.getId() == id).findFirst();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            if (dataALlList.isPresent()) {
                T3 t3 = dataALlList.get();
                int id1 = t3.getId();
                txtCode.setText(String.valueOf(id1));
                txtName.setText(t3.getName());
                txtTel.setText(t3.getTel());
                txtAddress.setText(t3.getAddress());
                txtBalance.setText(String.valueOf(t3.getFirst_balance()));
                txtLimit.setText(String.valueOf(nameData.limit(t3)));
                txtOther.setText(t3.getNotes());
                comboArea.getSelectionModel().select(t3.getArea().getArea_name());

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
                    LanguageManager.getInstance().getString("party.balance.locked.tooltip", rule.correction())));
        } catch (DaoException e) {
            log.error("Failed to read the opening-balance lock for {}", id, e);
        }
    }

    @Override
    public void resetData() {
        txtCode.setText(LanguageManager.getInstance().getString("item.code.generate"));
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

    private List<SelPriceTypeModel> getPriceType() {
        try {
            return selPriceItemService.getSelPriceTypeList();
        } catch (DaoException e) {
            log.error(e.getMessage(), e);
            return List.of();
        }
    }
}
