package com.hamza.account.features.export;

import lombok.extern.log4j.Log4j2;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Where {@code new PdfExportService()} gets its {@link ReportSetup} from.
 * <p>
 * Twelve places print a PDF by constructing the service with no arguments, and every one of them has
 * to print in the shop's style. Handing a setup to each would put a settings read, a company read and
 * a language lookup in twelve screens; installing one source here at start-up puts them in one place,
 * the way {@code ConnectionManager.installSessionInitializer} and {@code SharedSettings.install} do.
 * Until something is installed - a test, a tool - every page is {@link ReportSetup#plain()}.
 * <p>
 * <b>Reading the setup never fails a print.</b> A source that throws - the database gone while the
 * company is read - is logged and answered with the plain setup: a report without the shop's
 * letterhead is a lesser page, a report that cannot be printed at all is a failure.
 */
@Log4j2
public final class ReportSetups {

    private static volatile Supplier<ReportSetup> source = ReportSetup::plain;

    private ReportSetups() {
    }

    public static void install(Supplier<ReportSetup> newSource) {
        source = Objects.requireNonNull(newSource, "source");
    }

    /** For tests: back to the plain setup. */
    public static void uninstall() {
        source = ReportSetup::plain;
    }

    public static ReportSetup current() {
        try {
            ReportSetup setup = source.get();
            return setup == null ? ReportSetup.plain() : setup;
        } catch (RuntimeException e) {
            log.warn("The report setup could not be read; this page prints in the default style", e);
            return ReportSetup.plain();
        }
    }
}
