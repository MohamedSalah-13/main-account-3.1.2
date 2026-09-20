package com.hamza.account.architecture;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the defect seen on 2026-09-20 on a customer database upgraded to V73: a user who
 * held {@code sales.create} and had sold for years could not open the sales screen, because
 * the screen filled its warehouse combo through {@code StockService.getStocks()}, which
 * requires {@code stock.show} - a permission no migration had ever granted to anybody, the
 * seeded {@code DEFAULT_SALES_CASHIER} included.
 * <p>
 * The rule: <b>what an invoice screen reads to draw itself asks for no permission beyond the
 * document's own.</b> A combo is filled through a read meant for a combo
 * ({@code stocksForPicker}, {@code getActiveTreasuryModelList}, {@code delegates}), never
 * through the read that belongs to a management screen. Every service method the classes
 * under {@code controller/invoice} call is resolved to its source, and one whose body asks
 * {@code require} has to be listed below with the reason it cannot stop the screen opening.
 * The list fails in both directions, so it cannot become fiction.
 * <p>
 * A source scan rather than a run: the screen needs a JavaFX toolkit, and a run signed in as
 * user 1 would prove nothing - user 1 bypasses every permission.
 * {@code StockServicePermissionTest} is the half that does run.
 */
class InvoiceScreenPermissionArchitectureTest {

    /** {@code Service.method} -> why a permission refusal there cannot stop an invoice screen. */
    private static final Map<String, String> GUARDED_CALLS_THAT_CANNOT_BLOCK_THE_SCREEN = Map.of(
            "PartyStatementService.currentBalance",
            "ReturnEntryCoordinator.partyBalance catches it: no balance means no warning",
            "PartyStatementService.balanceAfterMovement",
            "InvoicePrintDocumentBuilder catches the refusal: the invoice prints without the balance",
            "TotalsReportService.run",
            "a report button on the totals screen, asking that screen's own show permission");

    private static final Pattern SERVICE_FIELD = Pattern.compile(
            "\\b(\\w+Service)\\s+(\\w+)\\s*(?:=|;)");
    private static final Pattern INLINE_SERVICE_CALL = Pattern.compile(
            "new\\s+(\\w+Service)\\s*\\(\\s*\\)\\s*(?:\\.|::)\\s*(\\w+)");

    @Test
    void theInvoiceScreensReadNothingThatAsksForAnotherScreensPermission() {
        List<String> everything = SourceTree.javaFiles(SourceTree.MAIN_JAVA);
        var guarded = new TreeMap<String, String>();
        var resolved = new TreeSet<String>();

        for (String file : SourceTree.javaFiles(SourceTree.javaPackage("controller", "invoice"))) {
            String source = SourceTree.withoutComments(SourceTree.readJava(file));
            for (String call : serviceCallsIn(source)) {
                String service = call.substring(0, call.indexOf('.'));
                String method = call.substring(call.indexOf('.') + 1);
                String serviceSource = sourceOf(service, everything);
                if (serviceSource == null) continue;
                List<String> bodies = methodBodies(serviceSource, method);
                if (bodies.isEmpty()) continue;
                resolved.add(call);
                if (bodies.stream().anyMatch(InvoiceScreenPermissionArchitectureTest::asksPermission)) {
                    guarded.put(call, file);
                }
            }
        }

        assertEquals(new TreeSet<>(GUARDED_CALLS_THAT_CANNOT_BLOCK_THE_SCREEN.keySet()), guarded.keySet(),
                "An invoice screen calls a service read that requires a permission. A user holding "
                        + "only the document's own permissions - which is every cashier upgraded from "
                        + "the legacy system - is then refused the whole screen. Fill a combo through an "
                        + "unguarded picker read (StockService.stocksForPicker is the example), or list "
                        + "the call here with the reason it cannot block. Guarded calls found: " + guarded);

        // A scan that resolves nothing passes everything; these three are the combos.
        assertTrue(resolved.containsAll(List.of("StockService.stocksForPicker",
                        "TreasuryService.getActiveTreasuryModelList", "EmployeeService.delegates")),
                "The scan no longer finds the invoice screen's three combo reads, so it is "
                        + "checking nothing. Resolved: " + resolved);
    }

    @Test
    void thePickerReadStaysUnguardedAndTheManagementReadStaysGuarded() {
        String source = SourceTree.withoutComments(
                SourceTree.readJava("com/hamza/account/service/StockService.java"));
        assertFalse(methodBodies(source, "stocksForPicker").stream()
                        .anyMatch(InvoiceScreenPermissionArchitectureTest::asksPermission),
                "stocksForPicker fills the warehouse combo of six screens, each guarded by its own "
                        + "permission; requiring stock.show here locks every one of them.");
        assertTrue(methodBodies(source, "getStocks").stream()
                        .allMatch(body -> body.contains("STOCK_SHOW")),
                "getStocks is the warehouses screen's read and is what stock.show guards.");
    }

    private static TreeSet<String> serviceCallsIn(String source) {
        var calls = new TreeSet<String>();
        Matcher fields = SERVICE_FIELD.matcher(source);
        while (fields.find()) {
            String type = fields.group(1);
            Matcher uses = Pattern.compile("\\b" + Pattern.quote(fields.group(2))
                    + "\\s*(?:\\.|::)\\s*(\\w+)").matcher(source);
            while (uses.find()) calls.add(type + "." + uses.group(1));
        }
        Matcher inline = INLINE_SERVICE_CALL.matcher(source);
        while (inline.find()) calls.add(inline.group(1) + "." + inline.group(2));
        return calls;
    }

    private static String sourceOf(String simpleName, List<String> files) {
        return files.stream()
                .filter(file -> file.replace('\\', '/').endsWith("/" + simpleName + ".java"))
                .findFirst()
                .map(file -> SourceTree.withoutComments(SourceTree.readJava(file)))
                .orElse(null);
    }

    /** Every overload's body; a private helper it calls for the permission is followed one level. */
    private static List<String> methodBodies(String source, String method) {
        var bodies = new java.util.ArrayList<String>();
        Matcher declaration = Pattern.compile(
                "(?:public|protected|private|static|default)[^;{}()=]*?\\b" + Pattern.quote(method)
                        + "\\s*\\([^)]*\\)[^;{]*\\{").matcher(source);
        while (declaration.find()) {
            int depth = 1;
            int index = declaration.end();
            while (index < source.length() && depth > 0) {
                char c = source.charAt(index++);
                if (c == '{') depth++;
                else if (c == '}') depth--;
            }
            String body = source.substring(declaration.end(), index);
            Matcher helper = Pattern.compile("\\b(require\\w*)\\s*\\(").matcher(body);
            StringBuilder followed = new StringBuilder(body);
            while (helper.find()) {
                if (!helper.group(1).equals(method) && !helper.group(1).equals("require")) {
                    methodBodies(source, helper.group(1)).forEach(followed::append);
                }
            }
            bodies.add(followed.toString());
        }
        return bodies;
    }

    private static boolean asksPermission(String body) {
        return body.contains("AuthorizationGuard.require(");
    }
}
