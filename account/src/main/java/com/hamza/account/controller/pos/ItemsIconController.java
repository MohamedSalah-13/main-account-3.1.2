package com.hamza.account.controller.pos;

import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.openFxml.FxmlPath;
import javafx.fxml.FXML;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.text.Text;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.io.ByteArrayInputStream;

@Log4j2
@RequiredArgsConstructor
@FxmlPath(pathFile = "pos/itemsIcon-view.fxml")
public class ItemsIconController {

    private final ItemsModel itemsModel;
    private final DaoFactory daoFactory;
    private final DataPublisher dataPublisher;
    @FXML
    private Text textName, textPrice;
    @FXML
    private ImageView imageView;
//    @FXML
//    private MenuItem menuAddImage;

    @FXML
    public void initialize() {
        otherSetting();
    }

    private void otherSetting() {
//        anchorPane.setPrefHeight(getPosInvoiceItemsSizeHeight());
//        anchorPane.setPrefWidth(getPosInvoiceItemsSizeWidth());

        textName.setText(itemsModel.getNameItem());
        textPrice.setText(String.valueOf(itemsModel.getSelPrice1()));
        var itemImage = itemsModel.getItem_image();
        if (itemImage != null && itemImage.length > 0) {
            imageView.setImage(new Image(new ByteArrayInputStream(itemImage)));
//            Platform.runLater(() -> {
//                try {
//                    Thread.sleep(100);
//                    setBackgroundImage(new ByteArrayInputStream(itemImage));
//                } catch (InterruptedException e) {
//                    log.error("Error sleeping thread", e);
//                }
//            });

        }

//        menuAddImage.setOnAction(event -> {
//            try {
//                new AddItemApplication(itemsModel.getId(), dataPublisher, daoFactory).start(new Stage());
//            } catch (Exception e) {
//                log.error("Error opening AddItemApplication", e);
//            }
//        });

        dataPublisher.getPublisherAddItem().addObserver(item -> {
            if (item != null) {
                if (item.getId() == itemsModel.getId()) {
                    itemsModel.setNameItem(item.getNameItem());
                    itemsModel.setSelPrice1(item.getSelPrice1());
                    itemsModel.setItem_image(item.getItem_image());
                    textName.setText(itemsModel.getNameItem());
                    textPrice.setText(String.valueOf(itemsModel.getSelPrice1()));
                    if (itemsModel.getItem_image() != null) {
                        imageView.setImage(new Image(new ByteArrayInputStream(itemsModel.getItem_image())));
                    }
                }
            }
        });
    }
}