package com.hamza.controlsfx.button;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.io.InputStream;

import static com.hamza.controlsfx.util.ImageChoose.ADD_IMAGE;

public class ImageDesign extends ImageView {

    public ImageDesign(InputStream stream, int size) {
        if (ADD_IMAGE) {
            setImage(new Image(stream));
            setFitHeight(size);
            setFitWidth(size);
        }
    }

    public ImageDesign(InputStream stream) {
        this(stream, 20);
    }
}
