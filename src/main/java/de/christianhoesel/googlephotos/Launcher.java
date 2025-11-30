package de.christianhoesel.googlephotos;

import de.christianhoesel.googlephotos.ui.GooglePhotosApp;

/**
 * Launcher class for the Google Photos Exporter application.
 * This class is needed because JavaFX applications cannot be launched directly
 * from a JAR when using jpackage. The launcher delegates to the actual Application class.
 */
public class Launcher {
    
    /**
     * Main entry point that launches the JavaFX application.
     * @param args command line arguments
     */
    public static void main(String[] args) {
        GooglePhotosApp.main(args);
    }
}
