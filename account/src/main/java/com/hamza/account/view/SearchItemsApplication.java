package com.hamza.account.view;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.others.SearchItemsController;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.base.BaseTotals;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.account.table.StageDimensions;
import com.hamza.controlsfx.language.Setting_Language;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

@Getter
@Log4j2
public class SearchItemsApplication<T1 extends BasePurchasesAndSales, T2 extends BaseTotals, T3 extends BaseNames, T4 extends BaseAccount>
        extends Application {

    private final SearchItemsController<T1, T2, T3, T4> searchItems;

    public SearchItemsApplication(DataInterface<T1, T2, T3, T4> dataInterface, DaoFactory daoFactory, String stockName) throws Exception {
        searchItems = new SearchItemsController<>(dataInterface, daoFactory, stockName);
    }

    @Override
    public void start(Stage stage) throws Exception {
        Scene scene = new SceneAll(new OpenFxmlApplication(searchItems).getPane());
        stage.setScene(scene);
        stage.setTitle(Setting_Language.WORD_SEARCH);
        stage.getIcons().add(new javafx.scene.image.Image(new Image_Setting().setting));
        stage.setResizable(true);
        stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        stage.show();
        StageDimensions.stageDimensions(getClass(), stage);
    }

}
