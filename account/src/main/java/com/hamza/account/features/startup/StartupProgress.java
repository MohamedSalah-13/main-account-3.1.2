package com.hamza.account.features.startup;

/**
 * What the start-up work tells whoever is watching it. The work calls it from its own thread and never
 * waits on the answer; {@link #SILENT} is for a start with nobody watching - the manual's screenshot
 * harness stands the application up without the window.
 */
public interface StartupProgress {

    /** A step starts; whatever was running is done, and a step passed over on the way counts as done. */
    void begin(StartupStep step);

    /** A line under the running step - one migration of many. The previous detail line is done. */
    void detail(String messageKey, String... arguments);

    /** How far through the running step, 0 to 1, for a step that knows (the migrations). */
    void fraction(double done);

    /** Everything ran. */
    void finish();

    /** The running step failed; what it failed with is the window's to say. */
    void fail();

    StartupProgress SILENT = new StartupProgress() {
        @Override
        public void begin(StartupStep step) {
        }

        @Override
        public void detail(String messageKey, String... arguments) {
        }

        @Override
        public void fraction(double done) {
        }

        @Override
        public void finish() {
        }

        @Override
        public void fail() {
        }
    };
}
