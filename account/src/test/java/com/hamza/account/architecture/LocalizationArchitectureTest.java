package com.hamza.account.architecture;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards rule ق-ل5 of {@code docs/new-code-rules.md}: a service does not build
 * user-facing text.
 * <p>
 * This is not a translation nicety. {@code LanguageManager} is a static singleton
 * holding <b>one language for the whole process</b>. On a server the language
 * becomes per-request, exactly as {@code CurrentUser} does (ق-ج2), so an Arabic
 * literal inside a service is a breach of the same separation - it is the reason
 * the code cannot be lifted onto an endpoint unchanged. The scan is limited to
 * {@code features/}, which is the package meant to become endpoints first.
 * <p>
 * A service should throw a message <i>key</i> with arguments and let the UI edge
 * format it.
 */
class LocalizationArchitectureTest {

    private static final Pattern ARABIC_LITERAL = Pattern.compile("\"[^\"\\n]*[\\u0600-\\u06FF][^\"\\n]*\"");

    /**
     * Files under {@code features/} that still hold Arabic literals, with the count
     * each held when the rule was written. A file may only <b>go down</b>; reaching
     * zero means striking it off, which {@link #theDebtListStaysHonest()} enforces.
     */
    private static final Set<String> FEATURES_WITH_ARABIC_LITERALS = Set.of(
            "choiceDialoge/ChangeUserName.java",
            "company/CompanyLogo.java",
            "company/CompanyService.java",
            "export/ExcelExportService.java",
            "export/PdfExportService.java",
            "export/ReportExportService.java",
            "inventory/InventoryColumns.java",
            "inventory/StockFilter.java",
            "invoice/InvoiceItemSelectionService.java",
            "invoice/InvoiceLineAssembler.java",
            "invoice/InvoiceLineEditService.java",
            "invoice/InvoiceLineService.java",
            "invoice/InvoicePaymentTerms.java",
            "invoice/InvoiceSaveService.java",
            "invoice/InvoiceSaveValidator.java",
            "invoice/JdbcInvoiceNumberAllocator.java",
            "key_setting/UpdateQuantity.java",
            "notification/BackupHealthSource.java",
            "notification/CreditLimitSource.java",
            "notification/LowStockSource.java",
            "notification/NotificationCategories.java",
            "notification/StockLevelAlert.java",
            "notification/TreasuryBalanceSource.java",
            "rbac/JdbcRbacRepository.java",
            "rbac/RbacAccessDecision.java",
            "rbac/RbacService.java",
            "returns/ReturnLinkGuard.java",
            "returns/ReturnReasonReportService.java",
            "stockcount/StockCountService.java",
            "stockcount/StockCountStatus.java");

    private static final String FEATURES_PREFIX = "com/hamza/account/features/";

    /** Paths relative to {@code features/}, for every file holding an Arabic literal. */
    private static Set<String> featuresHoldingArabicLiterals() {
        var offenders = new TreeSet<String>();
        for (String file : SourceTree.javaFiles(SourceTree.javaPackage("features"))) {
            String source = SourceTree.withoutComments(SourceTree.readJava(file));
            if (ARABIC_LITERAL.matcher(source).find()) {
                offenders.add(file.substring(FEATURES_PREFIX.length()));
            }
        }
        return offenders;
    }

    @Test
    void noNewServiceBuildsUserFacingText() {
        var unexpected = new TreeSet<>(featuresHoldingArabicLiterals());
        unexpected.removeAll(FEATURES_WITH_ARABIC_LITERALS);
        assertTrue(unexpected.isEmpty(),
                "A service throws a message key with arguments; the UI edge formats it (docs/new-code-rules.md, section 5 rule 5). "
                        + "LanguageManager holds one language per process, so a literal here cannot "
                        + "answer a per-request language later. New offenders: " + unexpected);
    }

    @Test
    void theDebtListStaysHonest() {
        var cleaned = new TreeSet<>(FEATURES_WITH_ARABIC_LITERALS);
        cleaned.removeAll(featuresHoldingArabicLiterals());
        assertTrue(cleaned.isEmpty(),
                "These files no longer hold Arabic literals - strike them off "
                        + "FEATURES_WITH_ARABIC_LITERALS so the remaining debt stays accurate: " + cleaned);
    }

    @Test
    void theTotalOnlyGoesDown() {
        int total = 0;
        for (String file : SourceTree.javaFiles(SourceTree.javaPackage("features"))) {
            Matcher matcher = ARABIC_LITERAL.matcher(SourceTree.withoutComments(SourceTree.readJava(file)));
            while (matcher.find()) {
                total++;
            }
        }
        assertTrue(total <= ARABIC_LITERAL_BASELINE,
                "Arabic literals under features/ may only decrease (docs/new-code-rules.md, section 5 rule 5). Baseline "
                        + ARABIC_LITERAL_BASELINE + ", found " + total
                        + ". If you removed some, lower ARABIC_LITERAL_BASELINE to " + total + ".");
    }

    /** Literals under {@code features/} when the rule was written. Only ever lower this. */
    private static final int ARABIC_LITERAL_BASELINE = 264;
}
