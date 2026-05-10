package com.hamza.account.controller.setting;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.dataSetting.AddDataController;
import com.hamza.account.controller.dataSetting.AddDataInterface;
import com.hamza.account.controller.dataSetting.impl.AddDataStocks;
import com.hamza.account.controller.dataSetting.impl.AddDataTreasury;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Company;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.account.service.StockService;
import com.hamza.account.service.TreasuryService;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.observer.Publisher;
import com.hamza.controlsfx.util.ImageChoose;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

import static com.hamza.controlsfx.others.TextFormat.createNumericTextFormatter;
import static com.hamza.controlsfx.others.Utils.whenEnterPressed;
import static com.hamza.controlsfx.util.ImageChoose.createIcon;


@Log4j2
@FxmlPath(pathFile = "include/settingTabCompany.fxml")
@RequiredArgsConstructor
public class SettingCompanyController implements Initializable {

    private final ImageChoose imageChoose = new ImageChoose();
    private final DaoFactory daoFactory;
    private final DataPublisher dataPublisher;
    private final StockService stockService = ServiceRegistry.get(StockService.class);
    private final TreasuryService treasuryService = ServiceRegistry.get(TreasuryService.class);
    private final Image defaultImage = new Image(new Image_Setting().defaultBlog);
    private Company company = new Company();
    private Publisher<String> publisherUpdateCompany;
    private int comp_id;
    @FXML
    private Button btnAddImage, btnSave, btnClearImage;
    @FXML
    private ImageView imageView;
    @FXML
    private Label labelName, labelAddress, labelTel, labelTax, labelCom;
    @FXML
    private TextField textAddress, textCom, textNameCompany, textTax, textTel;
    @FXML
    private HBox hbox;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        publisherUpdateCompany = dataPublisher.getPublisherUpdateCompany();
        getCompany();
        otherSetting();
        // add image if insert new before select data
        btnClearImage.fire();
        action();
        addDataToSetting();
        buttonGraphic();
    }

    private void buttonGraphic() {
        var images = new Image_Setting();
        btnClearImage.setGraphic(createIcon(images.erase));
        btnAddImage.setGraphic(createIcon(images.folder));
        btnSave.setGraphic(createIcon(images.save));
    }

    private void addDataToSetting() {
        // add stocks
        var addDataStocks = new AddDataStocks();
        addDataStocks.setStockService(stockService);
        addDataStocks.setPublisherAddStock(dataPublisher.getPublisherAddStock());
        addDataStocks.setDaoFactory(daoFactory);
        addData(addDataStocks);

        // add treasury
        var addDataTreasury = new AddDataTreasury();
        addDataTreasury.setTreasuryService(treasuryService);
        addDataTreasury.setPublisherAddTreasury(dataPublisher.getPublisherAddTreasury());
        addData(addDataTreasury);
    }

    private void otherSetting() {
        labelName.setText(Setting_Language.WORD_NAME);
        labelAddress.setText(Setting_Language.WORD_ADDRESS);
        labelTel.setText(Setting_Language.WORD_TEL);
        labelTax.setText(Setting_Language.WORD_TAX);
        labelCom.setText(Setting_Language.WORD_COMM);

        textNameCompany.setPromptText(Setting_Language.WORD_NAME);
        textAddress.setPromptText(Setting_Language.WORD_ADDRESS);
        textTel.setPromptText(Setting_Language.WORD_TEL);
        textTax.setPromptText(Setting_Language.WORD_TAX);
        textCom.setPromptText(Setting_Language.WORD_COMM);

        labelTel.setText(Setting_Language.WORD_TEL);
        labelTax.setText(Setting_Language.WORD_TAX);
        labelCom.setText(Setting_Language.WORD_COMM);

        btnAddImage.setText(Setting_Language.WORD_CHOOSE_FILE);
        btnClearImage.setText(Setting_Language.WORD_DELETE);
        btnSave.setText(Setting_Language.WORD_SAVE);
        whenEnterPressed(textNameCompany, textAddress, textTel, textTax, textCom);
        textTel.setTextFormatter(createNumericTextFormatter());
        imageView.setPreserveRatio(true);
    }

    private void action() {
        btnClearImage.setOnAction(event -> imageView.setImage(defaultImage));
        btnAddImage.setOnAction(event -> {
            try {
                imageChoose.onAddImage(imageView);
            } catch (FileNotFoundException e) {
                errorLog(e);
            }
        });

        btnSave.setOnAction(actionEvent -> updateCompany());
//        btnSave.setDisable(true);

//        configureTextFieldListeners();

    }

    private void configureTextFieldListeners() {
        textNameCompany.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue.isEmpty()) {
                return;
            }
            company.setName(newValue);
            update();
        });
        textAddress.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue.isEmpty()) {
                return;
            }
            company.setAddress(newValue);
            update();
        });
        textTel.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue.isEmpty()) {
                return;
            }
            company.setTel(newValue);
            update();
        });
        textTax.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue.isEmpty()) {
                return;
            }
            company.setTax(newValue);
            update();
        });
        textCom.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue.isEmpty()) {
                return;
            }
            company.setCommercial(newValue);
            update();
        });

        imageView.imageProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null) {
                company.setImage(null);
            } else {
                try {
                    company.setImage(imageChoose.convertFxImageToBytes(newValue));
                } catch (IOException e) {
                    errorLog(e);
                    company.setImage(null);
                }
            }
            update();
        });
    }

    private void getCompany() {
        try {
            var first = daoFactory.getCompanyDao().loadAll().stream().filter(company1 -> company1.getId() == 1).findFirst();
            if (first.isPresent()) {
                company = first.get();
                textNameCompany.setText(company.getName());
                textTel.setText(company.getTel());
                textAddress.setText(company.getAddress());
                textCom.setText(company.getCommercial());
                textTax.setText(company.getTax());
                comp_id = company.getId();

                var image = company.getImage();
                if (image != null) {
                    imageView.setImage(new Image(new ByteArrayInputStream(image)));
                }

            } else {
                new Thread(() -> {
                    try {
                        int insert = daoFactory.getCompanyDao().insert(new Company());
                        if (insert == 1) {
                            publisherUpdateCompany.notifyObservers();
                        }
                    } catch (DaoException e) {
                        errorLog(e);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }).start();

            }
        } catch (Exception e) {
            errorLog(e);
        }
    }

    private void updateCompany() {
        if (AllAlerts.confirmSave()) {

            try {
                Company model = new Company();
                model.setId(comp_id);
                model.setName(textNameCompany.getText());
                model.setTel(textTel.getText());
                model.setAddress(textAddress.getText());
                model.setTax(textTax.getText());
                model.setCommercial(textCom.getText());
                model.setImage(imageView.getImage() != null ? imageChoose.convertFxImageToBytes(imageView.getImage()) : null);

                int update = daoFactory.getCompanyDao().update(model);
                if (update == 1) {
                    AllAlerts.alertSave();
                    publisherUpdateCompany.notifyObservers();
                }
            } catch (Exception e) {
                if (e.getMessage().contains("Data truncation: Data too long for column")) {
                    AllAlerts.alertError(Setting_Language.ERROR_DATA);
                    getCompany();
                } else
                    AllAlerts.alertError(e.getMessage());
            }
        }
    }

    private void update() {
        try {
            int update = daoFactory.getCompanyDao().update(company);
            if (update == 1) {
                publisherUpdateCompany.notifyObservers();
            }
        } catch (DaoException e) {
            errorLog(e);
        }
    }

    private void addData(AddDataInterface addDataInterface) {
        try {
            Pane pane = new OpenFxmlApplication(new AddDataController(addDataInterface)).getPane();
            hbox.getChildren().add(pane);
        } catch (IOException e) {
            errorLog(e);
        }
    }

    private void errorLog(Exception e) {
        AllAlerts.alertError(e.getMessage());
        log.error(e.getMessage(), e.getCause());
    }
}
