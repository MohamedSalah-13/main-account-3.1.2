package com.hamza.account.table;

import com.hamza.account.config.PropertiesName;
import com.hamza.account.features.company.CompanyService;
import com.hamza.account.features.export.ReportLabels;
import com.hamza.account.features.export.ReportLetterhead;
import com.hamza.account.features.export.ReportSetup;
import com.hamza.account.features.export.ReportSetups;
import com.hamza.account.features.export.ReportStyle;
import com.hamza.account.features.export.ReportStyleCodec;
import com.hamza.account.features.rbac.CurrentUser;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Company;
import com.hamza.account.model.domain.Users;
import com.hamza.controlsfx.language.LanguageManager;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.List;

/**
 * The shop's {@link ReportSetup}, put together from where each part lives: the style from the shared
 * settings, the company from its row, the words from the reader's language, and the name from whoever
 * is signed in.
 * <p>
 * {@link #install()} hands it to {@link ReportSetups} at start-up, so every {@code new PdfExportService()}
 * prints in it - it is read afresh for every file, so a change on the settings screen, or on another
 * till, reaches the next report printed without a restart.
 * <p>
 * The company is read only when a report will print it. It costs one query, and every report prints
 * off the JavaFX thread; the preview on the settings screen reads it once, on a worker.
 */
@Log4j2
public final class ShopReportSetup {

    private ShopReportSetup() {
    }

    public static void install() {
        ReportSetups.install(ShopReportSetup::current);
    }

    /** The stored style, and the company only if that style prints it. */
    public static ReportSetup current() {
        ReportStyle style = storedStyle();
        return new ReportSetup(style, style.showLetterhead() ? letterhead() : ReportLetterhead.EMPTY,
                labels(), userName(), rightToLeft());
    }

    /** A setup that holds the company whatever the style says - the preview needs it to switch the letterhead on. */
    public static ReportSetup forPreview(ReportStyle style) {
        return new ReportSetup(style, letterhead(), labels(), userName(), rightToLeft());
    }

    public static ReportStyle storedStyle() {
        return ReportStyleCodec.decode(PropertiesName.getReportPdfStyle());
    }

    public static void store(ReportStyle style) {
        PropertiesName.setReportPdfStyle(ReportStyleCodec.encode(style));
    }

    public static ReportLabels labels() {
        LanguageManager language = LanguageManager.getInstance();
        return new ReportLabels(
                language.getString("report.pdf.printed.at"),
                language.getString("report.pdf.printed.by"),
                language.getString("report.pdf.page.of"),
                language.getString("report.pdf.footer.default"));
    }

    /**
     * The company's name, address, telephone and picture, or nothing when it cannot be read: a report
     * without its letterhead is a lesser page, a report that fails because of it is no page at all.
     */
    static ReportLetterhead letterhead() {
        try {
            Company company = new CompanyService(DaoFactory.INSTANCE).load();
            if (company == null) {
                return ReportLetterhead.EMPTY;
            }
            List<String> lines = new ArrayList<>();
            lines.add(company.getAddress());
            if (company.getTel() != null && !company.getTel().isBlank()) {
                lines.add(LanguageManager.getInstance().getString("invoice.pdf.phone") + ": " + company.getTel().strip());
            }
            return new ReportLetterhead(company.getName(), lines, company.getImage());
        } catch (Exception e) {
            log.warn("The company could not be read; the report prints without its letterhead", e);
            return ReportLetterhead.EMPTY;
        }
    }

    /** A report runs the way its reader reads - which is the language the program is in. */
    private static boolean rightToLeft() {
        return LanguageManager.getInstance().isRtl();
    }

    private static String userName() {
        Users user = CurrentUser.getOrNull();
        return user == null || user.getUsername() == null ? "" : user.getUsername();
    }
}
