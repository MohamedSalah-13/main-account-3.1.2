package com.hamza.account.reportData;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.model.domain.Company;
import com.hamza.account.view.DownLoadApplication;
import com.hamza.controlsfx.jasperData.JasperData;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.others.CssToColorHelper;
import javafx.scene.paint.Color;
import lombok.SneakyThrows;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.*;

import static com.hamza.account.config.Configs.FILE_REPORTS;
import static com.hamza.account.config.PropertiesName.*;

public class ReportCompany {

    public static final String COLLECTION_BEAN_PARAM = "CollectionBeanParam";
    protected final JasperData jasperData;

    public ReportCompany() {
        this.jasperData = new JasperData(getPrintPaperDirect());
    }

    protected int getCount() {
        int count = getCountPrintPrinterThermal();
        if (count == getSerialRecordModificationNumber()) {
            count = 1;
            setCountPrintPrinterThermal(count);
        }
        setCountPrintPrinterThermal(count + 1);
        return count;
    }

    /**
     * Retrieves and constructs a map representing company details including name, address, image, and contact information.
     * The map also contains additional pre-defined settings and configurations relevant to company reports.
     *
     * @return a HashMap<String, Object> containing company details and settings.
     */
    @SneakyThrows
    protected HashMap<String, Object> getCompany() {
        InputStream image = new Image_Setting().defaultBlog;

        String name = " ";
        String commercial = " ";
        String tel = " ";
        String address = " ";
        HashMap<String, Object> map = new HashMap<>();
        List<Company> companyList = Objects.requireNonNull(DownLoadApplication.getDaoFactory()).getCompanyDao().loadAll();
        if (companyList != null) {
            Optional<Company> list = companyList.stream().findFirst();
            if (list.isPresent()) {
                byte[] image1 = list.get().getImage();
                if (image1 != null)
                    image = new ByteArrayInputStream(image1);

                name = list.get().getName();
                commercial = list.get().getCommercial();
                tel = list.get().getTel();
                address = list.get().getAddress();
            }
        }

        map.put("compName", name);
        map.put("compAdd", commercial);
        map.put("compImage", image);
        map.put("compTel", tel);
        map.put("compAddress", address);
        map.put("number", Setting_Language.WORD_NUM);
        map.put("DesignCompanyName", "تصميم شركة فكرة للبرمجيات");
//        map.put("AddressAndTel", "قنا - نجع حمادي - 01002937820");
        map.put("AddressAndTel", " ");
        map.put("print-title", getSettingPrintReportTitle());

        Locale locale = new Locale("ar", "AR");
        map.put("REPORT_LOCALE", locale);
        map.put("REPORT_RESOURCE_BUNDLE", ResourceBundle.getBundle("words", locale));
        return map;
    }

    /**
     * Adds a header to the reports by inserting specific parameters into the provided map.
     *
     * @param map        The HashMap that will be populated with parameters.
     * @param reportName The name of the report to be used as a title.
     */
    protected void addHeaderToReports(HashMap<String, Object> map, String reportName) {
        String value = FILE_REPORTS.getAbsolutePath() + "\\" + JasperReportPaths.Report.HEADER;
        map.put("Parameter_title", value);
        map.put("title", reportName);
    }

    /**
     * Converts the color associated with "-fx-background-color-table" to its hexadecimal representation.
     *
     * @return The hexadecimal string representation of the color.
     */
    protected String getString(CssToColorHelper helper) {
        Color c = getNamedColor("-fx-background-color-table", helper);
        return String.format("#%02X%02X%02X",
                (int) (c.getRed() * 255),
                (int) (c.getGreen() * 255),
                (int) (c.getBlue() * 255));
    }

    /**
     * Retrieves a color specified by a CSS named color value.
     *
     * @param name   the CSS name of the color to retrieve
     * @param helper an instance of CssToColorHelper used to apply the CSS and fetch the color
     * @return the corresponding Color object representing the named color
     */
    protected Color getNamedColor(String name, CssToColorHelper helper) {
        helper.setStyle("-named-color: " + name + ";");
        helper.applyCss();
        return helper.getNamedColor();
    }

}
