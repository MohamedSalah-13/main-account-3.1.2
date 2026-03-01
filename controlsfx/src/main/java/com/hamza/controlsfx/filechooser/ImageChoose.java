package com.hamza.controlsfx.filechooser;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import lombok.Getter;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Objects;

public class ImageChoose {

    // Extract constant
    //TODO 11/23/2025 6:31 AM Mohamed: show in real time true
    public static final boolean ADD_IMAGE = true;
    public static final double ICON_SIZE = 25.0;
    @Getter
    private String path;

    // Extract function
    public static ImageView createIcon(InputStream inputStream) {
        if (Objects.equals(inputStream, InputStream.nullInputStream())) {
            return null;
        }
        ImageView imageView = new ImageView(new Image(inputStream));
        imageView.setFitWidth(ICON_SIZE);
        imageView.setFitHeight(ICON_SIZE);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        if (ADD_IMAGE) {
            return imageView;
        }
        return null;
    }

    public void onAddImage(ImageView imageView) throws FileNotFoundException {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.gif", "*.jpeg"));
        fileChooser.setTitle("Select Image");
        fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));
        File selectedFile = fileChooser.showOpenDialog(null);
        if (selectedFile != null) {
            InputStream inputStream = new FileInputStream(selectedFile);
            Image image = new Image(inputStream);
            imageView.setImage(image);
            path = selectedFile.getAbsolutePath();
        } else {
//            imageView.setImage(null);
            path = null;
        }
    }

    public byte[] convertFxImageToBytes(final Image fxImage) throws IOException {
        try (var byteOutputStream = new ByteArrayOutputStream()) {
            BufferedImage bufferedImage = SwingFXUtils.fromFXImage(fxImage, null);
            ImageIO.write(bufferedImage, "png", byteOutputStream);
            return byteOutputStream.toByteArray();
        }
    }
}
