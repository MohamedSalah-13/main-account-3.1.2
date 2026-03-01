package com.hamza.controlsfx.others;

import com.hamza.controlsfx.HelloApplication;

import java.io.InputStream;

public class ImageSetting {
    public final InputStream ARROW_UPWARD = stream("arrow_upward.png");
    public final InputStream ARROW_DOWNWARD = stream("arrow_downward.png");
    public final InputStream PASSWORD = stream("password.png");
    public final InputStream PASSWORD2 = stream("password2.png");
    public final InputStream IMAGE_PASS = stream("outline_password.png");
    public final InputStream IMAGE_MINUS = stream("minus.png");
    public final InputStream IMAGE_MAX = stream("squares24.png");
    public final InputStream IMAGE_MINI = stream("squareMini.png");
    public final InputStream CANCEL_IMAGE = stream("cancel.png");
    public final InputStream inputStream = stream("tools.png");
    public final InputStream show = stream("outline_visibility_off_black_24dp.png");
    public final InputStream hide = stream("outline_visibility_black_24dp.png");

    private InputStream stream(String string) {
        return HelloApplication.class.getResourceAsStream("image/" + string);
    }
}
