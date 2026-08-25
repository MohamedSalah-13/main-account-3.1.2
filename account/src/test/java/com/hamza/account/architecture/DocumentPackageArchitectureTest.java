package com.hamza.account.architecture;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps {@code com.hamza.account.document} a package that depends on the screens and
 * the features, never the other way round.
 * <p>
 * It is the seam that holds two packages apart. {@code DataInterface} exposes the save
 * operation as {@code saveInvoice(InvoiceSaveCommand)}, so {@code interfaces.api} has to
 * see {@code features.invoice}. {@code InvoiceSaveService} in turn needs
 * {@link com.hamza.account.document.InvoiceBuy} and
 * {@link com.hamza.account.document.TotalsAndPurchaseList} - and while those lived in
 * {@code interfaces.api}, the two packages imported each other. They were moved here
 * precisely because this package imports neither and both already imported it.
 * <p>
 * The moment anything under {@code document/} imports {@code interfaces.*} or
 * {@code features.invoice}, that cycle is back - and nothing else would say so, since
 * javac compiles a package cycle without complaint.
 */
class DocumentPackageArchitectureTest {

    /**
     * {@code features.events} is deliberately allowed: {@code DocumentType} reads
     * {@code InvoiceSide} and {@code PartyKind} from it, and that package is a leaf -
     * it imports nothing from {@code account} but one model. Widening this list is a
     * decision to make on purpose, not by accident.
     */
    private static final Pattern FORBIDDEN_IMPORT = Pattern.compile(
            "(?m)^import\\s+(com\\.hamza\\.account\\.(?:interfaces\\.[\\w.]+"
                    + "|features\\.invoice)\\.[\\w.*]+)\\s*;");

    @Test
    void documentPackageDependsOnNothingThatDependsOnIt() {
        var offences = new TreeSet<String>();
        List<String> files = SourceTree.javaFiles(SourceTree.javaPackage("document"));

        assertTrue(files.size() > 5,
                "expected the document package to be found and non-trivial, saw " + files);

        for (String file : files) {
            String source = SourceTree.withoutComments(SourceTree.readJava(file));
            Matcher matcher = FORBIDDEN_IMPORT.matcher(source);
            while (matcher.find()) {
                offences.add(file + " imports " + matcher.group(1));
            }
        }

        assertTrue(offences.isEmpty(),
                "com.hamza.account.document must not import interfaces.* or features.invoice - "
                        + "both of those import it, so this would restore the package cycle that "
                        + "moving InvoiceBuy and TotalsAndPurchaseList here removed. Offending "
                        + "imports:\n  " + String.join("\n  ", offences));
    }

    /**
     * The other half of the same rule, stated from the side that would notice first:
     * the save pipeline must not reach back into the screen seam.
     */
    @Test
    void invoiceFeatureDoesNotDependOnTheScreenSeam() {
        var offences = new TreeSet<String>();
        Pattern interfacesImport = Pattern.compile(
                "(?m)^import\\s+(com\\.hamza\\.account\\.interfaces\\.[\\w.*]+)\\s*;");

        for (String file : SourceTree.javaFiles(SourceTree.javaPackage("features", "invoice"))) {
            String source = SourceTree.withoutComments(SourceTree.readJava(file));
            Matcher matcher = interfacesImport.matcher(source);
            while (matcher.find()) {
                offences.add(file + " imports " + matcher.group(1));
            }
        }

        assertTrue(offences.isEmpty(),
                "features.invoice must not import interfaces.* - interfaces.api imports "
                        + "InvoiceSaveCommand/InvoiceSaveResult from here, so an import back "
                        + "makes the two packages mutually dependent. Put what is needed in "
                        + "com.hamza.account.document instead, the way InvoiceBuy and "
                        + "TotalsAndPurchaseList are. Offending imports:\n  "
                        + String.join("\n  ", offences));
    }
}
