package com.hamza.account.controller.reports;

import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.LoadData;
import com.hamza.account.controller.reports.model.Cards;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.otherSetting.MaskerPaneSetting;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.table.TableColumnAnnotation;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.StackPane;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.List;

@Deprecated
@Log4j2
@FxmlPath(pathFile = "reports/report-items-count.fxml")
public class ReportItemsController2 extends LoadData {

    private final ObservableList<Cards> observableList = FXCollections.observableArrayList();
    private final ObservableList<ItemsModel> itemsObservableList = FXCollections.observableArrayList();
    @FXML
    private TableView<Cards> tableView;
    @FXML
    private Button btnSearch;
    @FXML
    private Label labelYear;
    @FXML
    private ComboBox<Integer> comboYear, chooseMonth;
    @FXML
    private StackPane stackPane;
    private MaskerPaneSetting maskerPaneSetting;

    public ReportItemsController2(DaoFactory daoFactory, DataPublisher dataPublisher) throws Exception {
        super(daoFactory, dataPublisher);
    }

    @FXML
    public void initialize() {
        maskerPaneSetting = new MaskerPaneSetting(stackPane);
        itemsObservableList.setAll(itemsService.getMainItemsList());
        getTable();
        nameSetting();
        action();
    }

    private void getTable() {
        tableView.getColumns().clear();
        new TableColumnAnnotation().getTable(tableView, Cards.class);
        tableView.setTableMenuButtonVisible(true);
    }

    private void nameSetting() {
        labelYear.setText(Setting_Language.WORD_YEAR);
        comboYear.getItems().setAll(List.of(2024, 2025));
        chooseMonth.getItems().setAll(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12));
        comboYear.getSelectionModel().selectFirst();
        chooseMonth.getSelectionModel().selectFirst();
    }


    private void action() {
        btnSearch.setOnAction(actionEvent -> refreshTable());
    }

    private void refreshTable() {
        maskerPaneSetting.showMaskerPane(() -> {
            try {
                observableList.clear();
                observableList.addAll(cardsList());
                tableView.setItems(observableList);
            } catch (Exception e) {
                log.error("Error refreshing table: {}", e.getMessage());
                AllAlerts.alertError(e.getMessage());
            }
        });
    }


    private List<Cards> cardsList() {
        // first get items sold in year then month
        var selectedItem = comboYear.getSelectionModel().getSelectedItem();
        var selectedMonth = chooseMonth.getSelectionModel().getSelectedItem();


        List<ItemsSold> listItemsSold = new ArrayList<>();


        List<Cards> cardsList = new ArrayList<>();

        for (int i = 1; i <= 31; i++) {
            var cards = new Cards();
            int finalI = i;
            var list = listItemsSold.stream().filter(itemsSold -> itemsSold.getId() == finalI).toList();
            cards.setId(i);
            cards.setNameItem(list.getFirst().getNameItem());
            cards.setDay1(list.stream().filter(itemsSold -> itemsSold.getDay() == 1).mapToDouble(ItemsSold::getQuantity).sum());
            cards.setDay2(list.stream().filter(cardItems1 -> cardItems1.getDay() == 2).mapToDouble(ItemsSold::getQuantity).sum());
            cards.setDay3(list.stream().filter(cardItems1 -> cardItems1.getDay() == 3).mapToDouble(ItemsSold::getQuantity).sum());
            cards.setDay4(list.stream().filter(cardItems1 -> cardItems1.getDay() == 4).mapToDouble(ItemsSold::getQuantity).sum());
            cards.setDay5(list.stream().filter(cardItems1 -> cardItems1.getDay() == 5).mapToDouble(ItemsSold::getQuantity).sum());
            cards.setDay6(list.stream().filter(cardItems1 -> cardItems1.getDay() == 6).mapToDouble(ItemsSold::getQuantity).sum());
            cards.setDay7(list.stream().filter(cardItems1 -> cardItems1.getDay() == 7).mapToDouble(ItemsSold::getQuantity).sum());
            cards.setDay8(list.stream().filter(cardItems1 -> cardItems1.getDay() == 8).mapToDouble(ItemsSold::getQuantity).sum());
            cards.setDay9(list.stream().filter(cardItems1 -> cardItems1.getDay() == 9).mapToDouble(ItemsSold::getQuantity).sum());
            cards.setDay10(list.stream().filter(cardItems1 -> cardItems1.getDay() == 10).mapToDouble(ItemsSold::getQuantity).sum());
            cards.setDay11(list.stream().filter(cardItems1 -> cardItems1.getDay() == 11).mapToDouble(ItemsSold::getQuantity).sum());
            cards.setDay12(list.stream().filter(cardItems1 -> cardItems1.getDay() == 12).mapToDouble(ItemsSold::getQuantity).sum());
            cards.setDay13(list.stream().filter(cardItems1 -> cardItems1.getDay() == 13).mapToDouble(ItemsSold::getQuantity).sum());
            cards.setDay14(list.stream().filter(cardItems1 -> cardItems1.getDay() == 14).mapToDouble(ItemsSold::getQuantity).sum());
            cards.setDay15(list.stream().filter(cardItems1 -> cardItems1.getDay() == 15).mapToDouble(ItemsSold::getQuantity).sum());
            cards.setDay16(list.stream().filter(cardItems1 -> cardItems1.getDay() == 16).mapToDouble(ItemsSold::getQuantity).sum());
            cards.setDay17(list.stream().filter(cardItems1 -> cardItems1.getDay() == 17).mapToDouble(ItemsSold::getQuantity).sum());
            cards.setDay18(list.stream().filter(cardItems1 -> cardItems1.getDay() == 18).mapToDouble(ItemsSold::getQuantity).sum());
            cards.setDay19(list.stream().filter(cardItems1 -> cardItems1.getDay() == 19).mapToDouble(ItemsSold::getQuantity).sum());
            cards.setDay20(list.stream().filter(cardItems1 -> cardItems1.getDay() == 20).mapToDouble(ItemsSold::getQuantity).sum());
            cards.setDay21(list.stream().filter(cardItems1 -> cardItems1.getDay() == 21).mapToDouble(ItemsSold::getQuantity).sum());
            cards.setDay22(list.stream().filter(cardItems1 -> cardItems1.getDay() == 22).mapToDouble(ItemsSold::getQuantity).sum());
            cards.setDay23(list.stream().filter(cardItems1 -> cardItems1.getDay() == 23).mapToDouble(ItemsSold::getQuantity).sum());
            cards.setDay24(list.stream().filter(cardItems1 -> cardItems1.getDay() == 24).mapToDouble(ItemsSold::getQuantity).sum());
            cards.setDay25(list.stream().filter(cardItems1 -> cardItems1.getDay() == 25).mapToDouble(ItemsSold::getQuantity).sum());
            cards.setDay26(list.stream().filter(cardItems1 -> cardItems1.getDay() == 26).mapToDouble(ItemsSold::getQuantity).sum());
            cards.setDay27(list.stream().filter(cardItems1 -> cardItems1.getDay() == 27).mapToDouble(ItemsSold::getQuantity).sum());
            cards.setDay28(list.stream().filter(cardItems1 -> cardItems1.getDay() == 28).mapToDouble(ItemsSold::getQuantity).sum());
            cards.setDay29(list.stream().filter(cardItems1 -> cardItems1.getDay() == 29).mapToDouble(ItemsSold::getQuantity).sum());
            cards.setDay30(list.stream().filter(cardItems1 -> cardItems1.getDay() == 30).mapToDouble(ItemsSold::getQuantity).sum());
            cards.setDay31(list.stream().filter(cardItems1 -> cardItems1.getDay() == 31).mapToDouble(ItemsSold::getQuantity).sum());
            cards.setTotals(list.stream().mapToDouble(ItemsSold::getQuantity).sum());
            cardsList.add(cards);
        }
        return cardsList;
    }

}

@Deprecated
@Setter
@Getter
class ItemsSold {
    private int day;
    private int id;
    private String nameItem;
    private double quantity;
}

