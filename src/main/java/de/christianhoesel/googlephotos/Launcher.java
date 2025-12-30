package de.christianhoesel.googlephotos;

import de.christianhoesel.googlephotos.ui.TakeoutProcessorApp;

/**
 * Launcher class for the Google Takeout Processor application.
 * This class is needed because JavaFX applications cannot be launched directly
 * from a JAR when using jpackage. The launcher delegates to the actual Application class.
 */
public class Launcher {

    /**
     * Main entry point that launches the JavaFX application.
     * @param args command line arguments
     */
    public static void main(String[] args) {
        TakeoutProcessorApp.main(args);
    }
}
