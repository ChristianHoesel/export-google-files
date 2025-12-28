package de.christianhoesel.googlephotos.ui;

import java.io.File;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.photos.library.v1.PhotosLibraryClient;

import de.christianhoesel.googlephotos.model.Album;
import de.christianhoesel.googlephotos.model.ExportOptions;
import de.christianhoesel.googlephotos.model.PhotoItem;
import de.christianhoesel.googlephotos.service.ExportService;
import de.christianhoesel.googlephotos.service.GoogleAuthService;
import de.christianhoesel.googlephotos.service.GooglePhotosService;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

/**
 * JavaFX Application for Google Photos Export.
 * Provides a modern, user-friendly GUI for exporting photos and videos
 * from Google Photos with metadata preservation.
 */
public class GooglePhotosApp extends Application {
    private static final Logger logger = LoggerFactory.getLogger(GooglePhotosApp.class);

    // Services
    private GoogleAuthService authService;
    private GooglePhotosService photosService;
    private ExportOptions exportOptions;
    
    // Task executor
    private ExecutorService executorService;

    // UI Components
    private Stage primaryStage;
    private BorderPane mainLayout;
    private VBox sidePanel;
    private StackPane contentArea;
    private Label statusLabel;
    private ProgressBar progressBar;
    private Button cancelButton;

    // State
    private boolean isAuthenticated = false;
    private Task<?> currentTask = null;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        this.exportOptions = new ExportOptions();
        
        // Initialize executor service for background tasks
        this.executorService = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r);
            thread.setDaemon(true); // Daemon threads don't prevent JVM shutdown
            thread.setName("GooglePhotosExporter-Worker");
            return thread;
        });

        primaryStage.setTitle("Google Photos Exporter");
        primaryStage.setMinWidth(1000);
        primaryStage.setMinHeight(700);

        createMainLayout();

        Scene scene = new Scene(mainLayout, 1200, 800);
        scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        
        primaryStage.setScene(scene);
        primaryStage.show();

        // Show welcome screen
        showWelcomeScreen();
    }

    private void createMainLayout() {
        mainLayout = new BorderPane();
        mainLayout.getStyleClass().add("main-layout");

        // Create header
        HBox header = createHeader();
        mainLayout.setTop(header);

        // Create side panel
        sidePanel = createSidePanel();
        mainLayout.setLeft(sidePanel);

        // Create content area
        contentArea = new StackPane();
        contentArea.getStyleClass().add("content-area");
        contentArea.setPadding(new Insets(20));
        mainLayout.setCenter(contentArea);

        // Create status bar
        HBox statusBar = createStatusBar();
        mainLayout.setBottom(statusBar);
    }

    private HBox createHeader() {
        HBox header = new HBox(15);
        header.getStyleClass().add("header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(15, 25, 15, 25));

        // Logo/Icon
        Label logoLabel = new Label("📷");
        logoLabel.setFont(Font.font("System", FontWeight.BOLD, 28));

        // Title
        Label titleLabel = new Label("Google Photos Exporter");
        titleLabel.getStyleClass().add("header-title");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 22));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Connection status
        Label connectionLabel = new Label("⚫ Nicht verbunden");
        connectionLabel.setId("connectionStatus");
        connectionLabel.getStyleClass().add("connection-status");

        header.getChildren().addAll(logoLabel, titleLabel, spacer, connectionLabel);
        return header;
    }

    private VBox createSidePanel() {
        VBox panel = new VBox(5);
        panel.getStyleClass().add("side-panel");
        panel.setPrefWidth(220);
        panel.setPadding(new Insets(15, 10, 15, 10));

        Label menuTitle = new Label("Navigation");
        menuTitle.getStyleClass().add("menu-title");
        menuTitle.setPadding(new Insets(0, 0, 10, 10));

        // Menu buttons
        Button btnConnect = createMenuButton("🔗 Verbinden", this::handleConnect);
        Button btnAlbums = createMenuButton("📁 Alben", this::showAlbumsView);
        Button btnExport = createMenuButton("⬇️ Export", this::showExportView);
        Button btnSettings = createMenuButton("⚙️ Einstellungen", this::showSettingsView);
        Button btnHelp = createMenuButton("❓ Hilfe", this::showHelpView);

        panel.getChildren().addAll(menuTitle, btnConnect, btnAlbums, btnExport, btnSettings, btnHelp);
        return panel;
    }

    private Button createMenuButton(String text, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("menu-button");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setOnAction(e -> action.run());
        return button;
    }

    private HBox createStatusBar() {
        HBox statusBar = new HBox(15);
        statusBar.getStyleClass().add("status-bar");
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setPadding(new Insets(8, 15, 8, 15));

        statusLabel = new Label("Bereit");
        statusLabel.getStyleClass().add("status-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(200);
        progressBar.setVisible(false);

        cancelButton = new Button("✖ Abbrechen");
        cancelButton.getStyleClass().add("danger-button");
        cancelButton.setVisible(false);
        cancelButton.setOnAction(e -> cancelCurrentTask());

        statusBar.getChildren().addAll(statusLabel, spacer, progressBar, cancelButton);
        return statusBar;
    }

    private void showWelcomeScreen() {
        VBox welcomeBox = new VBox(25);
        welcomeBox.setAlignment(Pos.CENTER);
        welcomeBox.getStyleClass().add("welcome-screen");

        Label welcomeIcon = new Label("📷");
        welcomeIcon.setFont(Font.font("System", 72));

        Label welcomeTitle = new Label("Willkommen beim Google Photos Exporter");
        welcomeTitle.getStyleClass().add("welcome-title");
        welcomeTitle.setFont(Font.font("System", FontWeight.BOLD, 28));

        Label welcomeSubtitle = new Label("Exportieren Sie Ihre Fotos und Videos mit allen Metadaten");
        welcomeSubtitle.getStyleClass().add("welcome-subtitle");
        welcomeSubtitle.setFont(Font.font("System", 16));

        VBox featureBox = new VBox(10);
        featureBox.setAlignment(Pos.CENTER);
        featureBox.setPadding(new Insets(20));

        String[] features = {
            "✓ Vollständige Metadaten-Erhaltung (EXIF, Album, Personen)",
            "✓ Zeitraum-basierte Auswahl",
            "✓ Flexible Ordnerstruktur",
            "✓ JSON-Metadaten-Dateien"
        };

        for (String feature : features) {
            Label featureLabel = new Label(feature);
            featureLabel.getStyleClass().add("feature-label");
            featureBox.getChildren().add(featureLabel);
        }

        Button connectButton = new Button("🔗 Mit Google Photos verbinden");
        connectButton.getStyleClass().add("primary-button");
        connectButton.setFont(Font.font("System", FontWeight.BOLD, 16));
        connectButton.setOnAction(e -> handleConnect());

        welcomeBox.getChildren().addAll(welcomeIcon, welcomeTitle, welcomeSubtitle, featureBox, connectButton);
        setContent(welcomeBox);
    }

    private void cancelCurrentTask() {
        if (currentTask != null && currentTask.isRunning()) {
            logger.info("Cancelling current task");
            currentTask.cancel();
            setStatus("Vorgang abgebrochen");
            progressBar.setVisible(false);
            cancelButton.setVisible(false);
            currentTask = null;
        }
    }

    private void handleConnect() {
        if (isAuthenticated) {
            showInfo("Bereits verbunden", "Sie sind bereits mit Google Photos verbunden.");
            return;
        }

        setStatus("Verbindung wird hergestellt...");
        progressBar.setVisible(true);
        progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        cancelButton.setVisible(true);

        Task<Boolean> connectTask = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                authService = new GoogleAuthService();
                PhotosLibraryClient client = authService.authorize();
                photosService = new GooglePhotosService(client);
                return true;
            }
        };

        currentTask = connectTask;

        connectTask.setOnSucceeded(e -> {
            isAuthenticated = true;
            progressBar.setVisible(false);
            cancelButton.setVisible(false);
            setStatus("Verbunden mit Google Photos");
            updateConnectionStatus(true);
            showExportView();
            currentTask = null;
        });

        connectTask.setOnFailed(e -> {
            progressBar.setVisible(false);
            cancelButton.setVisible(false);
            setStatus("Verbindung fehlgeschlagen");
            currentTask = null;
            Throwable ex = connectTask.getException();
            logger.error("Connection failed", ex);
            showError("Verbindungsfehler",
                "Die Verbindung zu Google Photos konnte nicht hergestellt werden.\n\n" +
                "Bitte stellen Sie sicher, dass die Datei 'credentials.json' im Anwendungsverzeichnis vorhanden ist.\n\n" +
                "Fehler: " + ex.getMessage());
        });

        connectTask.setOnCancelled(e -> {
            progressBar.setVisible(false);
            cancelButton.setVisible(false);
            setStatus("Verbindung abgebrochen");
            currentTask = null;
        });

        executorService.submit(connectTask);
    }

    private void updateConnectionStatus(boolean connected) {
        Label connectionLabel = (Label) mainLayout.lookup("#connectionStatus");
        if (connectionLabel != null) {
            if (connected) {
                connectionLabel.setText("🟢 Verbunden");
                connectionLabel.setStyle("-fx-text-fill: #27ae60;");
            } else {
                connectionLabel.setText("⚫ Nicht verbunden");
                connectionLabel.setStyle("-fx-text-fill: #95a5a6;");
            }
        }
    }

    private void showAlbumsView() {
        if (!checkAuthenticated()) return;

        VBox albumsView = new VBox(15);
        albumsView.getStyleClass().add("albums-view");

        Label title = new Label("📁 Ihre Alben");
        title.getStyleClass().add("view-title");
        title.setFont(Font.font("System", FontWeight.BOLD, 24));

        // Create table for albums
        TableView<Album> albumTable = new TableView<>();
        albumTable.getStyleClass().add("albums-table");
        albumTable.setPlaceholder(new Label("Lade Alben..."));

        TableColumn<Album, String> nameCol = new TableColumn<>("Album Name");
        nameCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getTitle()));
        nameCol.setPrefWidth(400);

        TableColumn<Album, Long> countCol = new TableColumn<>("Anzahl Medien");
        countCol.setCellValueFactory(data -> new javafx.beans.property.SimpleLongProperty(data.getValue().getMediaItemsCount()).asObject());
        countCol.setPrefWidth(150);

        albumTable.getColumns().addAll(nameCol, countCol);
        VBox.setVgrow(albumTable, Priority.ALWAYS);

        Button refreshButton = new Button("🔄 Aktualisieren");
        refreshButton.getStyleClass().add("secondary-button");
        refreshButton.setOnAction(e -> loadAlbums(albumTable));

        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.getChildren().add(refreshButton);

        albumsView.getChildren().addAll(title, albumTable, buttonBox);
        setContent(albumsView);

        // Load albums
        loadAlbums(albumTable);
    }

    private void loadAlbums(TableView<Album> table) {
        setStatus("Lade Alben...");
        progressBar.setVisible(true);
        progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        cancelButton.setVisible(true);

        Task<List<Album>> loadTask = new Task<>() {
            @Override
            protected List<Album> call() {
                return photosService.listAlbums();
            }
        };

        currentTask = loadTask;

        loadTask.setOnSucceeded(e -> {
            List<Album> albums = loadTask.getValue();
            ObservableList<Album> items = FXCollections.observableArrayList(albums);
            table.setItems(items);
            progressBar.setVisible(false);
            cancelButton.setVisible(false);
            currentTask = null;
            setStatus(albums.size() + " Alben gefunden");
        });

        loadTask.setOnFailed(e -> {
            progressBar.setVisible(false);
            cancelButton.setVisible(false);
            currentTask = null;
            setStatus("Fehler beim Laden der Alben");
            showError("Fehler", "Die Alben konnten nicht geladen werden: " + loadTask.getException().getMessage());
        });

        loadTask.setOnCancelled(e -> {
            progressBar.setVisible(false);
            cancelButton.setVisible(false);
            currentTask = null;
            setStatus("Laden der Alben abgebrochen");
        });

        executorService.submit(loadTask);
    }

    private void showExportView() {
        if (!checkAuthenticated()) return;

        VBox exportView = new VBox(20);
        exportView.getStyleClass().add("export-view");
        exportView.setPadding(new Insets(10));

        Label title = new Label("⬇️ Export Konfiguration");
        title.getStyleClass().add("view-title");
        title.setFont(Font.font("System", FontWeight.BOLD, 24));

        // Create form grid
        GridPane form = new GridPane();
        form.setHgap(15);
        form.setVgap(15);
        form.getStyleClass().add("export-form");

        int row = 0;

        // Output directory
        Label outputLabel = new Label("Ausgabeverzeichnis:");
        TextField outputField = new TextField(exportOptions.getOutputDirectory() != null ?
            exportOptions.getOutputDirectory() : System.getProperty("user.home") + "/GooglePhotosExport");
        outputField.setPrefWidth(400);
        Button browseButton = new Button("📂 Durchsuchen");
        browseButton.getStyleClass().add("secondary-button");
        browseButton.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Ausgabeverzeichnis wählen");
            File dir = chooser.showDialog(primaryStage);
            if (dir != null) {
                outputField.setText(dir.getAbsolutePath());
            }
        });
        HBox outputBox = new HBox(10, outputField, browseButton);
        form.add(outputLabel, 0, row);
        form.add(outputBox, 1, row++);

        // Date range
        Label dateLabel = new Label("Zeitraum:");
        DatePicker startDate = new DatePicker();
        startDate.setPromptText("Startdatum (beide oder keines)");
        DatePicker endDate = new DatePicker();
        endDate.setPromptText("Enddatum (beide oder keines)");
        // Don't set default values - leave both empty for "all media"
        
        // Add tooltip to explain the requirement
        Tooltip dateTooltip = new Tooltip("Für einen Datumsfilter müssen BEIDE Felder gesetzt werden.\nLassen Sie beide leer für alle Medien.");
        startDate.setTooltip(dateTooltip);
        endDate.setTooltip(dateTooltip);
        
        // Warning label (hidden by default)
        Label dateWarning = new Label("⚠ Beide Datumswerte erforderlich oder beide leer lassen");
        dateWarning.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 11px;");
        dateWarning.setVisible(false);
        dateWarning.setManaged(false);
        
        VBox dateVBox = new VBox(5);
        HBox dateBox = new HBox(10, startDate, new Label("bis"), endDate);
        dateBox.setAlignment(Pos.CENTER_LEFT);
        dateVBox.getChildren().addAll(dateBox, dateWarning);
        
        form.add(dateLabel, 0, row);
        form.add(dateVBox, 1, row++);

        // Media types
        Label mediaLabel = new Label("Medientypen:");
        CheckBox photosCheck = new CheckBox("Fotos");
        photosCheck.setSelected(true);
        CheckBox videosCheck = new CheckBox("Videos");
        videosCheck.setSelected(true);
        HBox mediaBox = new HBox(20, photosCheck, videosCheck);
        form.add(mediaLabel, 0, row);
        form.add(mediaBox, 1, row++);

        // Folder structure
        Label folderLabel = new Label("Ordnerstruktur:");
        CheckBox dateFoldersCheck = new CheckBox("Nach Datum (YYYY/MM)");
        dateFoldersCheck.setSelected(true);
        CheckBox albumFoldersCheck = new CheckBox("Nach Album");
        HBox folderBox = new HBox(20, dateFoldersCheck, albumFoldersCheck);
        form.add(folderLabel, 0, row);
        form.add(folderBox, 1, row++);

        // Metadata options
        Label metaLabel = new Label("Metadaten:");
        CheckBox metaFileCheck = new CheckBox("JSON-Metadaten-Dateien erstellen");
        metaFileCheck.setSelected(true);
        form.add(metaLabel, 0, row);
        form.add(metaFileCheck, 1, row++);

        // Delete option
        Label deleteLabel = new Label("Nach Export:");
        CheckBox deleteCheck = new CheckBox("⚠️ Dateien nach Export löschen");
        deleteCheck.getStyleClass().add("danger-checkbox");
        form.add(deleteLabel, 0, row);
        form.add(deleteCheck, 1, row++);

        // Preview and Export buttons
        HBox buttonBox = new HBox(15);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.setPadding(new Insets(20, 0, 0, 0));

        Button previewButton = new Button("🔍 Vorschau");
        previewButton.getStyleClass().add("secondary-button");
        previewButton.setOnAction(e -> {
            if (validateAndUpdateExportOptions(outputField, startDate, endDate, photosCheck, videosCheck,
                dateFoldersCheck, albumFoldersCheck, metaFileCheck, deleteCheck, dateWarning)) {
                showPreview();
            }
        });

        Button exportButton = new Button("⬇️ Export starten");
        exportButton.getStyleClass().add("primary-button");
        exportButton.setOnAction(e -> {
            if (validateAndUpdateExportOptions(outputField, startDate, endDate, photosCheck, videosCheck,
                dateFoldersCheck, albumFoldersCheck, metaFileCheck, deleteCheck, dateWarning)) {
                startExport();
            }
        });

        buttonBox.getChildren().addAll(previewButton, exportButton);

        exportView.getChildren().addAll(title, form, buttonBox);
        setContent(exportView);
    }

    private boolean validateAndUpdateExportOptions(TextField outputField, DatePicker startDate, DatePicker endDate,
                                                   CheckBox photosCheck, CheckBox videosCheck,
                                                   CheckBox dateFoldersCheck, CheckBox albumFoldersCheck,
                                                   CheckBox metaFileCheck, CheckBox deleteCheck,
                                                   Label dateWarning) {
        exportOptions.setOutputDirectory(outputField.getText());
        
        // Validate date range - both or none must be set
        LocalDate start = startDate.getValue();
        LocalDate end = endDate.getValue();
        
        if ((start != null && end == null) || (start == null && end != null)) {
            // Only one date is set - show visual feedback
            logger.warn("Date validation: Only one date boundary set");
            
            // Show warning label
            dateWarning.setVisible(true);
            dateWarning.setManaged(true);
            
            // Add red border to date pickers
            startDate.setStyle("-fx-border-color: #e74c3c; -fx-border-width: 2px;");
            endDate.setStyle("-fx-border-color: #e74c3c; -fx-border-width: 2px;");
            
            // Scroll to the date fields if possible
            setStatus("⚠ Bitte setzen Sie beide Datumswerte oder lassen Sie beide leer");
            
            return false; // Validation failed
        } else {
            // Valid input - hide warning and remove red borders
            dateWarning.setVisible(false);
            dateWarning.setManaged(false);
            startDate.setStyle("");
            endDate.setStyle("");
        }
        
        exportOptions.setStartDate(start);
        exportOptions.setEndDate(end);
        exportOptions.setExportPhotos(photosCheck.isSelected());
        exportOptions.setExportVideos(videosCheck.isSelected());
        exportOptions.setCreateDateFolders(dateFoldersCheck.isSelected());
        exportOptions.setCreateAlbumFolders(albumFoldersCheck.isSelected());
        exportOptions.setWriteMetadataFile(metaFileCheck.isSelected());
        exportOptions.setDeleteAfterExport(deleteCheck.isSelected());
        
        return true; // Validation successful
    }

    private void updateExportOptions(TextField outputField, DatePicker startDate, DatePicker endDate,
                                     CheckBox photosCheck, CheckBox videosCheck,
                                     CheckBox dateFoldersCheck, CheckBox albumFoldersCheck,
                                     CheckBox metaFileCheck, CheckBox deleteCheck) {
        exportOptions.setOutputDirectory(outputField.getText());
        exportOptions.setStartDate(startDate.getValue());
        exportOptions.setEndDate(endDate.getValue());
        exportOptions.setExportPhotos(photosCheck.isSelected());
        exportOptions.setExportVideos(videosCheck.isSelected());
        exportOptions.setCreateDateFolders(dateFoldersCheck.isSelected());
        exportOptions.setCreateAlbumFolders(albumFoldersCheck.isSelected());
        exportOptions.setWriteMetadataFile(metaFileCheck.isSelected());
        exportOptions.setDeleteAfterExport(deleteCheck.isSelected());
    }

    private void showPreview() {
        setStatus("Suche Medien...");
        progressBar.setVisible(true);
        progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        cancelButton.setVisible(true);

        Task<List<PhotoItem>> previewTask = new Task<>() {
            @Override
            protected List<PhotoItem> call() {
                return photosService.searchMediaItems(exportOptions);
            }
        };

        currentTask = previewTask;

        previewTask.setOnSucceeded(e -> {
            List<PhotoItem> items = previewTask.getValue();
            progressBar.setVisible(false);
            cancelButton.setVisible(false);
            currentTask = null;

            long photoCount = items.stream().filter(PhotoItem::isPhoto).count();
            long videoCount = items.stream().filter(PhotoItem::isVideo).count();

            setStatus("Vorschau: " + items.size() + " Medien gefunden");

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Export Vorschau");
            alert.setHeaderText("Gefundene Medien");
            alert.setContentText(String.format(
                "Fotos: %d\nVideos: %d\nGesamt: %d\n\nDiese Medien werden exportiert.",
                photoCount, videoCount, items.size()));
            alert.showAndWait();
        });

        previewTask.setOnFailed(e -> {
            progressBar.setVisible(false);
            cancelButton.setVisible(false);
            currentTask = null;
            setStatus("Fehler bei der Vorschau");
            showError("Fehler", "Die Vorschau konnte nicht erstellt werden: " + previewTask.getException().getMessage());
        });

        previewTask.setOnCancelled(e -> {
            progressBar.setVisible(false);
            cancelButton.setVisible(false);
            currentTask = null;
            setStatus("Vorschau abgebrochen");
        });

        executorService.submit(previewTask);
    }

    private void startExport() {
        if (exportOptions.isDeleteAfterExport()) {
            Alert confirm = new Alert(Alert.AlertType.WARNING);
            confirm.setTitle("Warnung");
            confirm.setHeaderText("Löschfunktion aktiviert");
            confirm.setContentText("Sie haben die Löschfunktion aktiviert. Exportierte Dateien werden nach dem Export gelöscht.\n\nSind Sie sicher?");
            confirm.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);

            if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) {
                return;
            }
        }

        // Show export progress dialog
        showExportProgress();
    }

    private void showExportProgress() {
        VBox progressView = new VBox(20);
        progressView.setAlignment(Pos.CENTER);
        progressView.setPadding(new Insets(40));
        progressView.getStyleClass().add("progress-view");

        Label title = new Label("⬇️ Export läuft...");
        title.getStyleClass().add("view-title");
        title.setFont(Font.font("System", FontWeight.BOLD, 24));

        ProgressBar exportProgress = new ProgressBar(0);
        exportProgress.setPrefWidth(500);
        exportProgress.setPrefHeight(25);

        Label progressLabel = new Label("Initialisiere...");
        progressLabel.getStyleClass().add("progress-label");

        Label statsLabel = new Label("");
        statsLabel.getStyleClass().add("stats-label");

        Button exportCancelButton = new Button("❌ Abbrechen");
        exportCancelButton.getStyleClass().add("danger-button");

        progressView.getChildren().addAll(title, exportProgress, progressLabel, statsLabel, exportCancelButton);
        setContent(progressView);

        // Start export task
        Task<Void> exportTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("Suche Medien...");
                List<PhotoItem> items = photosService.searchMediaItems(exportOptions);

                if (items.isEmpty()) {
                    updateMessage("Keine Medien gefunden.");
                    return null;
                }

                updateMessage("Starte Export von " + items.size() + " Medien...");
                ExportService exportService = new ExportService(photosService, exportOptions);

                int total = items.size();
                int current = 0;

                for (PhotoItem item : items) {
                    if (isCancelled()) break;

                    current++;
                    final int finalCurrent = current;
                    Platform.runLater(() -> {
                        exportProgress.setProgress((double) finalCurrent / total);
                        progressLabel.setText("Exportiere: " + item.getFilename());
                        statsLabel.setText(String.format("%d / %d (%.0f%%)", finalCurrent, total, (double) finalCurrent / total * 100));
                    });

                    try {
                        exportService.exportItem(item);
                    } catch (Exception e) {
                        logger.warn("Failed to export item: {}", item.getFilename(), e);
                    }
                }

                var stats = exportService.getStatistics();
                final String resultMsg = String.format("Export abgeschlossen!\n\nErfolgreich: %d\nÜbersprungen: %d\nFehlgeschlagen: %d",
                    stats.get("success"), stats.get("skipped"), stats.get("failed"));
                Platform.runLater(() -> progressLabel.setText(resultMsg));

                return null;
            }
        };

        currentTask = exportTask;

        exportCancelButton.setOnAction(e -> {
            exportTask.cancel();
            currentTask = null;
            showExportView();
        });

        exportTask.setOnSucceeded(e -> {
            exportCancelButton.setText("✓ Fertig - Zurück");
            exportCancelButton.getStyleClass().remove("danger-button");
            exportCancelButton.getStyleClass().add("primary-button");
            exportCancelButton.setOnAction(ev -> showExportView());
            setStatus("Export abgeschlossen");
            currentTask = null;
        });

        exportTask.setOnFailed(e -> {
            progressLabel.setText("Export fehlgeschlagen: " + exportTask.getException().getMessage());
            exportCancelButton.setText("Zurück");
            setStatus("Export fehlgeschlagen");
            currentTask = null;
        });

        exportTask.setOnCancelled(e -> {
            progressLabel.setText("Export abgebrochen");
            exportCancelButton.setText("Zurück");
            setStatus("Export abgebrochen");
            currentTask = null;
        });

        executorService.submit(exportTask);
    }

    private void showSettingsView() {
        VBox settingsView = new VBox(20);
        settingsView.getStyleClass().add("settings-view");
        settingsView.setPadding(new Insets(10));

        Label title = new Label("⚙️ Einstellungen");
        title.getStyleClass().add("view-title");
        title.setFont(Font.font("System", FontWeight.BOLD, 24));

        // Settings form
        GridPane form = new GridPane();
        form.setHgap(15);
        form.setVgap(15);

        int row = 0;

        Label authLabel = new Label("Authentifizierung:");
        Button logoutButton = new Button("🔓 Abmelden");
        logoutButton.getStyleClass().add("secondary-button");
        logoutButton.setOnAction(e -> handleLogout());
        form.add(authLabel, 0, row);
        form.add(logoutButton, 1, row++);

        Label infoLabel = new Label("Info:");
        Label versionLabel = new Label("Version 1.0.0 - Java 25");
        form.add(infoLabel, 0, row);
        form.add(versionLabel, 1, row++);

        settingsView.getChildren().addAll(title, form);
        setContent(settingsView);
    }

    private void handleLogout() {
        if (authService != null) {
            try {
                authService.clearTokens();
                authService.close();
            } catch (Exception e) {
                logger.warn("Error during logout", e);
            }
        }
        authService = null;
        photosService = null;
        isAuthenticated = false;
        updateConnectionStatus(false);
        setStatus("Abgemeldet");
        showWelcomeScreen();
    }

    private void showHelpView() {
        VBox helpView = new VBox(20);
        helpView.getStyleClass().add("help-view");
        helpView.setPadding(new Insets(10));

        Label title = new Label("❓ Hilfe");
        title.getStyleClass().add("view-title");
        title.setFont(Font.font("System", FontWeight.BOLD, 24));

        TextArea helpText = new TextArea();
        helpText.setEditable(false);
        helpText.setWrapText(true);
        helpText.setPrefHeight(400);
        helpText.setText("""
            GOOGLE PHOTOS EXPORTER - HILFE
            
            1. VERBINDUNG HERSTELLEN
               - Klicken Sie auf "Verbinden" im Seitenmenü
               - Ein Browser-Fenster öffnet sich für die Google-Anmeldung
               - Erlauben Sie den Zugriff auf Ihre Google Photos
            
            2. EXPORT KONFIGURIEREN
               - Wählen Sie ein Ausgabeverzeichnis
               - Definieren Sie optional einen Zeitraum
               - Wählen Sie die gewünschten Medientypen (Fotos/Videos)
               - Konfigurieren Sie die Ordnerstruktur
            
            3. EXPORT STARTEN
               - Klicken Sie auf "Vorschau" um die Anzahl der Dateien zu sehen
               - Klicken Sie auf "Export starten" um den Export zu beginnen
            
            4. METADATEN
               JSON-Dateien werden für jedes Medium erstellt mit:
               - EXIF-Daten (Kamera, Objektiv, etc.)
               - Album-Zugehörigkeit
               - Erstellungsdatum
               - Abmessungen
            
            VORAUSSETZUNGEN:
            - credentials.json Datei im Anwendungsverzeichnis
            - Aktive Internetverbindung
            - Google Account mit Google Photos
            
            Bei Problemen prüfen Sie bitte:
            - Ist die credentials.json vorhanden?
            - Haben Sie Internetzugang?
            - Sind die Google APIs aktiviert?
            """);

        helpView.getChildren().addAll(title, helpText);
        VBox.setVgrow(helpText, Priority.ALWAYS);
        setContent(helpView);
    }

    private void setContent(javafx.scene.Node node) {
        contentArea.getChildren().clear();
        contentArea.getChildren().add(node);
    }

    private void setStatus(String message) {
        Platform.runLater(() -> statusLabel.setText(message));
    }

    private boolean checkAuthenticated() {
        if (!isAuthenticated) {
            showInfo("Nicht verbunden", "Bitte verbinden Sie sich zuerst mit Google Photos.");
            return false;
        }
        return true;
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    @Override
    public void stop() {
        logger.info("Shutting down application...");
        
        // Cancel any running task
        if (currentTask != null && currentTask.isRunning()) {
            logger.info("Cancelling running task before shutdown");
            currentTask.cancel();
        }
        
        // Shutdown executor service
        if (executorService != null) {
            logger.info("Shutting down executor service");
            executorService.shutdownNow();
            try {
                if (!executorService.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    logger.warn("Executor service did not terminate in time");
                }
            } catch (InterruptedException e) {
                logger.warn("Interrupted while waiting for executor service shutdown", e);
                Thread.currentThread().interrupt();
            }
        }
        
        // Close auth service
        if (authService != null) {
            authService.close();
        }
        
        logger.info("Application shutdown complete");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
