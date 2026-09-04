package com.hamza.account.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A service that moves treasury cash passes through {@code ShiftGate}.
 * <p>
 * Six services do today, and until now that list lived only in {@code docs/shift-plan.md}
 * §5 - which makes it a wish, not a rule. Nothing failed when a seventh forgot. The cost of
 * forgetting is not a crash: the movement simply saves with no {@code shift_id}, so the
 * drawer it came out of never counts it, and the cashier answers at close for a difference
 * nobody can explain. It is silent, and it is only visible in the money.
 * <p>
 * The gate is also what refuses a cash movement when the mode is {@code REQUIRED} and no
 * shift is open, and what stops a movement already attributed to a shift from being deleted
 * unattributed once the mode relaxes to {@code OPTIONAL}. Skipping it skips all three.
 */
class ShiftGateArchitectureTest {

    private static final List<Path> SERVICE_ROOTS = List.of(
            Path.of("src", "main", "java", "com", "hamza", "account", "service"),
            Path.of("src", "main", "java", "com", "hamza", "account", "features"));

    /** Covers fields, accessors and generic seams: {@code someDao.insert(...)}, {@code someDao().update(...)}. */
    private static final Pattern DAO_WRITE = Pattern.compile(
            "(?s).*(?:[A-Za-z0-9_]+Dao)(?:\\(\\))?\\s*\\.\\s*"
                    + "(?:insert[A-Za-z0-9_]*|update|deleteById)\\s*\\(.*");

    /**
     * Services that write a row and know about a treasury, yet legitimately never ask the
     * gate. Each is here for a reason a regex cannot see, so each is named with it.
     */
    private static final Set<String> CASH_WRITERS_WITHOUT_THE_GATE = Set.of(
            // Fine: it writes the treasury itself - the name, the type, the opening balance -
            // and never a movement of cash. Nothing here belongs to a drawer.
            "TreasuryService",

            // Fine: it is the shift service. Asking the gate would be circular - the gate
            // exists to find the shift this class opens and closes.
            "UserShiftService",

            // Fine: it does not resolve a shift, it is handed one. Both callers -
            // AccountCustomerService.save and AccountSupplierService.save - pass the shift
            // the gate already gave them, and the fee is written in their transaction.
            "WalletFeeService",

            // Fine: everything it writes happens after the close snapshot is stored, and is
            // deliberately attributed to no shift. Attributing the settlement or the handover
            // transfer would append to a drawer that has already been answered for.
            "ShiftCashHandoverService");

    @Test
    void everyServiceThatMovesTreasuryCashPassesThroughTheShiftGate() throws IOException {
        var offenders = new TreeSet<String>();
        for (Path root : SERVICE_ROOTS) {
            if (!Files.isDirectory(root)) continue;
            try (var files = Files.walk(root)) {
                files.filter(path -> path.toString().endsWith("Service.java"))
                        .filter(ShiftGateArchitectureTest::movesTreasuryCash)
                        .filter(path -> !read(path).contains("ShiftGate"))
                        .forEach(path -> offenders.add(name(path)));
            }
        }

        var unexpected = new TreeSet<>(offenders);
        unexpected.removeAll(CASH_WRITERS_WITHOUT_THE_GATE);
        assertTrue(unexpected.isEmpty(),
                "these services write a row and name a treasury, but never ask ShiftGate: "
                        + unexpected + ". Take a ShiftGate in the constructor and call "
                        + "requireCashAction/requireTreasuryAction before the write, so the "
                        + "movement carries the shift that produced it - or, if it genuinely "
                        + "belongs to no drawer, add it to CASH_WRITERS_WITHOUT_THE_GATE with "
                        + "the reason. See docs/shift-plan.md §5.");

        var stale = new TreeSet<>(CASH_WRITERS_WITHOUT_THE_GATE);
        stale.removeAll(offenders);
        assertTrue(stale.isEmpty(),
                "these are on the exception list but reach the gate now, or no longer write "
                        + "cash at all: " + stale + ". Delete them from "
                        + "CASH_WRITERS_WITHOUT_THE_GATE - a list that outlives what it "
                        + "excuses is how an exception becomes invisible.");
    }

    /**
     * The six that do pass through it are named, so deleting the gate from one of them fails
     * here rather than quietly shrinking the rule to whatever the code happens to do.
     */
    @Test
    void theServicesKnownToNeedTheGateStillHaveIt() throws IOException {
        var missing = new TreeSet<String>();
        for (String service : List.of("InvoiceSaveService", "TreasuryCashService",
                "TreasuryTransferService", "AccountCustomerService",
                "AccountSupplierService", "ExpensesDetailsService")) {
            if (find(service).stream().noneMatch(path -> read(path).contains("ShiftGate"))) {
                missing.add(service);
            }
        }
        assertTrue(missing.isEmpty(),
                "these services moved treasury cash through ShiftGate and no longer do: "
                        + missing + ". If that is deliberate, say why in docs/shift-plan.md §5 "
                        + "and change this list in the same review.");
    }

    private static boolean movesTreasuryCash(Path path) {
        String source = read(path);
        return DAO_WRITE.matcher(source).matches() && source.toLowerCase().contains("treasury");
    }

    private static List<Path> find(String simpleName) throws IOException {
        var found = new java.util.ArrayList<Path>();
        for (Path root : SERVICE_ROOTS) {
            if (!Files.isDirectory(root)) continue;
            try (var files = Files.walk(root)) {
                files.filter(path -> name(path).equals(simpleName)).forEach(found::add);
            }
        }
        assertTrue(!found.isEmpty(), simpleName + " no longer exists - update this test");
        return found;
    }

    private static String name(Path path) {
        return path.getFileName().toString().replace(".java", "");
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
