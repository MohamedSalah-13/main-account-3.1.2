package com.hamza.controlsfx.others;

import com.hamza.controlsfx.DialogApplication;

import java.io.InputStream;

public class ImageSetting {

    public final InputStream IMAGE_PASS = stream("outline_password.png");
    public final InputStream IMAGE_MINUS = stream("minus.png");
    public final InputStream inputStream = stream("tools.png");
    public final InputStream show = stream("outline_visibility_off_black_24dp.png");
    public final InputStream hide = stream("outline_visibility_black_24dp.png");

    private InputStream stream(String string) {
        return DialogApplication.class.getResourceAsStream("image/" + string);
    }
}
