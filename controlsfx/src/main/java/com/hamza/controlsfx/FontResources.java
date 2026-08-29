package com.hamza.controlsfx;

import java.io.InputStream;

/** Opens bundled font resources from the module that owns them. */
public final class FontResources {

    private static final String ROOT = "/com/hamza/controlsfx/font/";

    private FontResources() {
    }

    public static InputStream open(String relativePath) {
        return FontResources.class.getResourceAsStream(ROOT + relativePath);
    }
}