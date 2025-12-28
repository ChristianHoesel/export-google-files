package de.christianhoesel.googlephotos.export;

import de.christianhoesel.googlephotos.model.Album;
import de.christianhoesel.googlephotos.model.ExportOptions;
import de.christianhoesel.googlephotos.model.PhotoItem;
import de.christianhoesel.googlephotos.service.ExportService;
import de.christianhoesel.googlephotos.service.GoogleAuthService;
import de.christianhoesel.googlephotos.service.GooglePhotosService;
import com.google.photos.library.v1.PhotosLibraryClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Main application class for Google Photos Exporter.
 * Provides a simple console-based user interface for exporting photos and videos
 * from Google Photos with metadata preservation.
 */
public class GooglePhotosExporter {
    private static final Logger logger = LoggerFactory.getLogger(GooglePhotosExporter.class);
    
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
    private final BufferedReader reader;
    private GoogleAuthService authService;
    private GooglePhotosService photosService;
    private ExportOptions options;

    public GooglePhotosExporter() {
        this.reader = new BufferedReader(new InputStreamReader(System.in));
        this.options = new ExportOptions();
    }

    /**
     * Main entry point for the application.
     * 
     * @param args Command line arguments (not used)
     */
    public static void main(String[] args) {
        GooglePhotosExporter exporter = new GooglePhotosExporter();
        try {
            exporter.run();
        } catch (Exception e) {
            logger.error("Application error", e);
            System.err.println("\nFehler: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Runs the main application loop.
     */
    public void run() {
        printWelcome();
        
        try {
            // Step 1: Authenticate
            if (!authenticate()) {
                System.out.println("\nAuthentifizierung fehlgeschlagen. Beende Anwendung.");
                return;
            }
            
            // Main menu loop
            boolean running = true;
            while (running) {
                printMainMenu();
                String choice = readLine();
                
                switch (choice) {
                    case "1":
                        configureExport();
                        break;
                    case "2":
                        showAlbums();
                        break;
                    case "3":
                        previewExport();
                        break;
                    case "4":
                        startExport();
                        break;
                    case "5":
                        showCurrentSettings();
                        break;
                    case "6":
                    case "q":
                    case "Q":
                        running = false;
                        break;
                    default:
                        System.out.println("\nUngültige Auswahl. Bitte erneut versuchen.");
                }
            }
        } finally {
            cleanup();
        }
        
        System.out.println("\nAuf Wiedersehen!");
    }

    /**
     * Prints the welcome message.
     */
    private void printWelcome() {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║           Google Photos Exporter v1.0                       ║");
        System.out.println("║                                                            ║");
        System.out.println("║  Exportieren Sie Fotos und Videos aus Google Photos        ║");
        System.out.println("║  mit allen Metadaten (Album, Personen, EXIF, etc.)        ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
        System.out.println();
    }

    /**
     * Prints the main menu.
     */
    private void printMainMenu() {
        System.out.println("\n╔═══════════════════════════════════════╗");
        System.out.println("║           Hauptmenü                    ║");
        System.out.println("╠═══════════════════════════════════════╣");
        System.out.println("║  1. Export konfigurieren              ║");
        System.out.println("║  2. Alben anzeigen                    ║");
        System.out.println("║  3. Vorschau (Anzahl der Dateien)     ║");
        System.out.println("║  4. Export starten                    ║");
        System.out.println("║  5. Aktuelle Einstellungen anzeigen   ║");
        System.out.println("║  6. Beenden                           ║");
        System.out.println("╚═══════════════════════════════════════╝");
        System.out.print("\nIhre Wahl: ");
    }

    /**
     * Authenticates with Google Photos API.
     * 
     * @return true if authentication was successful
     */
    private boolean authenticate() {
        System.out.println("Authentifizierung mit Google Photos...");
        System.out.println("(Ein Browser-Fenster wird geöffnet für die Anmeldung)");
        System.out.println();
        
        try {
            authService = new GoogleAuthService();
            PhotosLibraryClient client = authService.authorize();
            photosService = new GooglePhotosService(client);
            
            System.out.println("✓ Erfolgreich mit Google Photos verbunden!");
            return true;
        } catch (Exception e) {
            logger.error("Authentication failed", e);
            System.err.println("\n✗ Authentifizierung fehlgeschlagen: " + e.getMessage());
            System.err.println("\nStellen Sie sicher, dass die Datei 'credentials.json' im");
            System.err.println("Anwendungsverzeichnis vorhanden ist.");
            return false;
        }
    }

    /**
     * Configures the export settings through user interaction.
     */
    private void configureExport() {
        System.out.println("\n=== Export Konfiguration ===\n");
        
        // Output directory
        System.out.print("Ausgabeverzeichnis [" + getDefaultOutputDir() + "]: ");
        String outputDir = readLine();
        if (outputDir.isEmpty()) {
            outputDir = getDefaultOutputDir();
        }
        options.setOutputDirectory(outputDir);
        
        // Date range
        System.out.println("\nZeitraum für den Export:");
        System.out.print("Startdatum (YYYY-MM-DD, leer für alle): ");
        String startDateStr = readLine();
        if (!startDateStr.isEmpty()) {
            try {
                options.setStartDate(LocalDate.parse(startDateStr, DATE_FORMAT));
            } catch (DateTimeParseException e) {
                System.out.println("Ungültiges Datum, wird übersprungen.");
            }
        }
        
        System.out.print("Enddatum (YYYY-MM-DD, leer für heute): ");
        String endDateStr = readLine();
        if (!endDateStr.isEmpty()) {
            try {
                options.setEndDate(LocalDate.parse(endDateStr, DATE_FORMAT));
            } catch (DateTimeParseException e) {
                System.out.println("Ungültiges Datum, wird übersprungen.");
            }
        }
        
        // Media types
        System.out.print("\nFotos exportieren? (J/n): ");
        options.setExportPhotos(!readLine().toLowerCase().startsWith("n"));
        
        System.out.print("Videos exportieren? (J/n): ");
        options.setExportVideos(!readLine().toLowerCase().startsWith("n"));
        
        // Folder structure
        System.out.print("\nOrdner nach Datum erstellen? (J/n): ");
        options.setCreateDateFolders(!readLine().toLowerCase().startsWith("n"));
        
        System.out.print("Ordner nach Album erstellen? (j/N): ");
        options.setCreateAlbumFolders(readLine().toLowerCase().startsWith("j"));
        
        // Metadata options
        System.out.print("\nMetadaten-Dateien erstellen? (J/n): ");
        options.setWriteMetadataFile(!readLine().toLowerCase().startsWith("n"));
        
        // Delete option
        System.out.println("\n⚠️  ACHTUNG: Die Löschfunktion ist permanent!");
        System.out.print("Dateien nach Export löschen? (j/N): ");
        options.setDeleteAfterExport(readLine().toLowerCase().startsWith("j"));
        
        if (options.isDeleteAfterExport()) {
            System.out.print("Sind Sie sicher? (ja eingeben zur Bestätigung): ");
            if (!"ja".equals(readLine().toLowerCase())) {
                options.setDeleteAfterExport(false);
                System.out.println("Löschfunktion deaktiviert.");
            }
        }
        
        // Build album mapping if album folders are enabled
        if (options.isCreateAlbumFolders()) {
            System.out.println("\nLade Album-Informationen...");
            photosService.buildMediaItemAlbumMapping();
        }
        
        System.out.println("\n✓ Konfiguration gespeichert!");
    }

    /**
     * Shows a list of albums in the user's Google Photos library.
     */
    private void showAlbums() {
        System.out.println("\n=== Ihre Alben ===\n");
        
        try {
            List<Album> albums = photosService.listAlbums();
            
            if (albums.isEmpty()) {
                System.out.println("Keine Alben gefunden.");
                return;
            }
            
            System.out.printf("%-40s %10s%n", "Album Name", "Anzahl");
            System.out.println("─".repeat(52));
            
            for (Album album : albums) {
                String title = album.getTitle();
                if (title.length() > 38) {
                    title = title.substring(0, 35) + "...";
                }
                System.out.printf("%-40s %10d%n", title, album.getMediaItemsCount());
            }
            
            System.out.println("─".repeat(52));
            System.out.println("Gesamt: " + albums.size() + " Alben");
        } catch (Exception e) {
            logger.error("Error listing albums", e);
            System.err.println("Fehler beim Laden der Alben: " + e.getMessage());
        }
    }

    /**
     * Shows a preview of items to be exported.
     */
    private void previewExport() {
        System.out.println("\n=== Export Vorschau ===\n");
        
        if (options.getOutputDirectory() == null) {
            System.out.println("Bitte zuerst die Export-Einstellungen konfigurieren (Option 1).");
            return;
        }
        
        System.out.println("Suche Medien basierend auf den Einstellungen...");
        
        try {
            List<PhotoItem> items = photosService.searchMediaItems(options);
            
            long photoCount = items.stream().filter(PhotoItem::isPhoto).count();
            long videoCount = items.stream().filter(PhotoItem::isVideo).count();
            
            System.out.println("\nGefundene Medien:");
            System.out.println("  Fotos: " + photoCount);
            System.out.println("  Videos: " + videoCount);
            System.out.println("  Gesamt: " + items.size());
            
            if (!items.isEmpty()) {
                System.out.println("\nErste 5 Dateien:");
                items.stream().limit(5).forEach(item -> 
                    System.out.println("  - " + item.getFilename() + 
                            (item.getCreationTime() != null ? " (" + item.getCreationTime().format(DATE_FORMAT) + ")" : "")));
            }
        } catch (Exception e) {
            logger.error("Error during preview", e);
            System.err.println("Fehler bei der Vorschau: " + e.getMessage());
        }
    }

    /**
     * Starts the actual export process.
     */
    private void startExport() {
        System.out.println("\n=== Export starten ===\n");
        
        if (options.getOutputDirectory() == null) {
            System.out.println("Bitte zuerst die Export-Einstellungen konfigurieren (Option 1).");
            return;
        }
        
        System.out.println("Aktuelle Einstellungen:");
        showCurrentSettings();
        
        System.out.print("\nExport starten? (J/n): ");
        if (readLine().toLowerCase().startsWith("n")) {
            System.out.println("Export abgebrochen.");
            return;
        }
        
        try {
            System.out.println("\nSuche Medien...");
            List<PhotoItem> items = photosService.searchMediaItems(options);
            
            if (items.isEmpty()) {
                System.out.println("Keine Medien zum Exportieren gefunden.");
                return;
            }
            
            System.out.println("Gefunden: " + items.size() + " Medien");
            System.out.println("\nStarte Export...\n");
            
            ExportService exportService = new ExportService(photosService, options);
            int exported = exportService.exportItems(items);
            
            var stats = exportService.getStatistics();
            
            System.out.println("\n╔════════════════════════════════════════╗");
            System.out.println("║         Export abgeschlossen!          ║");
            System.out.println("╠════════════════════════════════════════╣");
            System.out.printf("║  Erfolgreich: %-24d║%n", stats.get("success"));
            System.out.printf("║  Übersprungen: %-23d║%n", stats.get("skipped"));
            System.out.printf("║  Fehlgeschlagen: %-21d║%n", stats.get("failed"));
            System.out.println("╚════════════════════════════════════════╝");
            
            if (options.isDeleteAfterExport() && exported > 0) {
                System.out.println("\n⚠️  Die Löschfunktion von Google Photos ist noch nicht implementiert.");
                System.out.println("   Bitte löschen Sie die Dateien manuell, wenn gewünscht.");
            }
            
        } catch (Exception e) {
            logger.error("Error during export", e);
            System.err.println("Fehler beim Export: " + e.getMessage());
        }
    }

    /**
     * Shows the current export settings.
     */
    private void showCurrentSettings() {
        System.out.println("\n┌─────────────────────────────────────────┐");
        System.out.println("│       Aktuelle Einstellungen            │");
        System.out.println("├─────────────────────────────────────────┤");
        System.out.println("│ Ausgabeverzeichnis: " + 
                (options.getOutputDirectory() != null ? options.getOutputDirectory() : "nicht gesetzt"));
        System.out.println("│ Startdatum: " + 
                (options.getStartDate() != null ? options.getStartDate().format(DATE_FORMAT) : "alle"));
        System.out.println("│ Enddatum: " + 
                (options.getEndDate() != null ? options.getEndDate().format(DATE_FORMAT) : "heute"));
        System.out.println("│ Fotos exportieren: " + (options.isExportPhotos() ? "Ja" : "Nein"));
        System.out.println("│ Videos exportieren: " + (options.isExportVideos() ? "Ja" : "Nein"));
        System.out.println("│ Ordner nach Datum: " + (options.isCreateDateFolders() ? "Ja" : "Nein"));
        System.out.println("│ Ordner nach Album: " + (options.isCreateAlbumFolders() ? "Ja" : "Nein"));
        System.out.println("│ Metadaten-Dateien: " + (options.isWriteMetadataFile() ? "Ja" : "Nein"));
        System.out.println("│ Nach Export löschen: " + (options.isDeleteAfterExport() ? "⚠️ JA" : "Nein"));
        System.out.println("└─────────────────────────────────────────┘");
    }

    /**
     * Gets the default output directory.
     * 
     * @return Default output directory path
     */
    private String getDefaultOutputDir() {
        String userHome = System.getProperty("user.home");
        return userHome + "/GooglePhotosExport";
    }

    /**
     * Reads a line from user input.
     * 
     * @return User input string
     */
    private String readLine() {
        try {
            String line = reader.readLine();
            return line != null ? line.trim() : "";
        } catch (IOException e) {
            logger.error("Error reading input", e);
            return "";
        }
    }

    /**
     * Cleans up resources.
     */
    private void cleanup() {
        if (authService != null) {
            authService.close();
        }
        try {
            reader.close();
        } catch (IOException e) {
            // Ignore
        }
    }
}
