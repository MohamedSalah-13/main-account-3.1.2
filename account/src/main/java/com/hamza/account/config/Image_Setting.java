package com.hamza.account.config;

import com.hamza.account.Main;

import java.io.InputStream;

public class Image_Setting {

    private static final String BASE_2X_PATH = "2x/";
    private static final String PNG_EXTENSION = ".png";
    private static final String JPG_EXTENSION = ".jpg";
    public final InputStream pay = getResourceAsStream("icons8-payment-96", ImageFormat.PNG, ImageTheme.NONE);
    public final InputStream account = getResourceAsStream("account1", ImageFormat.PNG, ImageTheme.NONE);
    public final InputStream barcode = getResourceAsStream("icons8-barcode-100", ImageFormat.PNG, ImageTheme.NONE);
    public final InputStream database = getResourceAsStream("database", ImageFormat.PNG, ImageTheme.NONE);
    public final InputStream defaultBlog = getResourceAsStream("default-blog", ImageFormat.PNG, ImageTheme.NONE);
    public final InputStream evaluation = getResourceAsStream("evaluation", ImageFormat.PNG, ImageTheme.NONE);
    public final InputStream folder = getResourceAsStream("folder-management", ImageFormat.PNG, ImageTheme.NONE);
    public final InputStream tools = getResourceAsStream("tools", ImageFormat.PNG, ImageTheme.NONE);
    public final InputStream trash = getResourceAsStream("trash", ImageFormat.PNG, ImageTheme.NONE);
    public final InputStream save = getResourceAsStream("icons8-save-100", ImageFormat.PNG, ImageTheme.NONE);
    public final InputStream cancel = getResourceAsStream("icons8-cancel-100", ImageFormat.PNG, ImageTheme.NONE);
    public final InputStream select = getResourceAsStream("icons8-select-100", ImageFormat.PNG, ImageTheme.NONE);
    public final InputStream search = getResourceAsStream("icons8-search-100", ImageFormat.PNG, ImageTheme.NONE);
    public final InputStream export = getResourceAsStream("icons8-export-80", ImageFormat.PNG, ImageTheme.NONE);
    public final InputStream show = getResourceAsStream("icons8-show-property-100", ImageFormat.PNG, ImageTheme.NONE);
    public final InputStream duplicate = getResourceAsStream("icons8-duplicate-100", ImageFormat.PNG, ImageTheme.NONE);
    public final InputStream about = getResourceAsStream("icons8-about-100", ImageFormat.PNG, ImageTheme.NONE);
    public final InputStream font = getResourceAsStream("icons8-fonts-100", ImageFormat.PNG, ImageTheme.NONE);
    public final InputStream option = getResourceAsStream("icons8-option-100", ImageFormat.PNG, ImageTheme.NONE);
    public final InputStream exit = getResourceAsStream("icons8-exit-100", ImageFormat.PNG, ImageTheme.NONE);
    public final InputStream erase = getResourceAsStream("icons8-erase-100", ImageFormat.PNG, ImageTheme.NONE);
    public final InputStream details = getResourceAsStream("icons8-details-96", ImageFormat.PNG, ImageTheme.NONE);
    //----------------------------------------- white -----------------------------------------//
    public final InputStream delete = getResourceAsStream("icons8-delete-100", ImageFormat.PNG, ImageTheme.NONE);
    public final InputStream update = getResourceAsStream("icons8-edit-100", ImageFormat.PNG, ImageTheme.NONE);
    public final InputStream add = getResourceAsStream("icons8-add-100", ImageFormat.PNG, ImageTheme.NONE);
    public final InputStream print = getResourceAsStream("icons8-print-100", ImageFormat.PNG, ImageTheme.NONE);
    public final InputStream refresh = getResourceAsStream("icons8-refresh-100", ImageFormat.PNG, ImageTheme.NONE);
    public final InputStream setting = getResourceAsStream("icons8-settings-100", ImageFormat.PNG, ImageTheme.NONE);
    public final InputStream personCustomer = getResourceAsStream("icons8-customers-96", ImageFormat.PNG, ImageTheme.NONE);
    public final InputStream personSup = getResourceAsStream("icons8-buying-100", ImageFormat.PNG, ImageTheme.NONE);
    public final InputStream vertical_align_bottom = getResourceAsStream("icons8-general-ledger-80", ImageFormat.PNG, ImageTheme.NONE);
    public final InputStream vertical_align_top = getResourceAsStream("icons8-accounting-100", ImageFormat.PNG, ImageTheme.NONE);
    public final InputStream treasuryWhite = getResourceAsStream("icons8-bank-100", ImageFormat.PNG, ImageTheme.NONE);
    public final InputStream reports = getResourceAsStream("icons8-document-100", ImageFormat.PNG, ImageTheme.NONE);
    public final InputStream totals = getResourceAsStream("icons8-bill-100", ImageFormat.PNG, ImageTheme.NONE);
    public final InputStream totals2 = getResourceAsStream("icons8-bill-100 (1)", ImageFormat.PNG, ImageTheme.NONE);
    public final InputStream homeWhite = getResourceAsStream("icons8-home-page-80", ImageFormat.PNG, ImageTheme.NONE);
    public final InputStream shoppingSalesPOS = getResourceAsStream("icons8-best-sales-100", ImageFormat.PNG, ImageTheme.NONE);
    public final InputStream shoppingSales = getResourceAsStream("icons8-best-sales-64", ImageFormat.PNG, ImageTheme.NONE);
    public final InputStream shoppingPurchase = getResourceAsStream("icons8-purchase-94", ImageFormat.PNG, ImageTheme.NONE);
    public final InputStream itemWhite = getResourceAsStream("icons8-pink-basket-with-products-100", ImageFormat.PNG, ImageTheme.NONE);
    public final InputStream calcWhite = getResourceAsStream("icons8-calculator-94", ImageFormat.PNG, ImageTheme.NONE);
    public final InputStream alarmWhite = getResourceAsStream("icons8-alert-100", ImageFormat.PNG, ImageTheme.NONE);
    //----------------------------------------- black -----------------------------------------//
    public final InputStream addBlack24 = getResourceAsStream("outline_add_black_24dp", ImageFormat.PNG, ImageTheme.BLACK);

    private InputStream getResourceAsStream(String imageName, ImageFormat format, ImageTheme theme) {
        String fullPath = theme.path + imageName + format.extension;
        return Main.class.getResourceAsStream("image/" + fullPath);
    }

    public enum ImageFormat {
        PNG(PNG_EXTENSION),
        JPG(JPG_EXTENSION);

        private final String extension;

        ImageFormat(String extension) {
            this.extension = extension;
        }
    }

    public enum ImageTheme {
        BLACK(BASE_2X_PATH + "black/"),
        WHITE(BASE_2X_PATH + "white/"),
        NONE("");

        private final String path;

        ImageTheme(String path) {
            this.path = path;
        }
    }
}



