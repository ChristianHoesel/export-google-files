package de.christianhoesel.googlephotos.ui;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.christianhoesel.googlephotos.service.GoogleTakeoutService;
import de.christianhoesel.googlephotos.service.TakeoutProcessorService;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
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
 * JavaFX Application for Google Takeout Processor.
 * Processes local Google Takeout exports, adds metadata to images, and organizes into monthly folders.
 */
public class TakeoutProcessorApp extends Application {
    private static final String SECONDARY_BUTTON = "secondary-button";

	private static final String DANGER_BUTTON = "danger-button";

	private static final String PRIMARY_BUTTON = "primary-button";

	private static final String GOOGLE_TAKEOUT_PROCESSOR = "Google Takeout Processor";

	private static final String SYSTEM = "System";

	private static final Logger logger = LoggerFactory.getLogger(TakeoutProcessorApp.class);

    private ExecutorService executorService;
    private Stage primaryStage;
    private BorderPane mainLayout;
    private Label statusLabel;
    private ProgressBar progressBar;
    private Button cancelButton;
    private Task<?> currentTask = null;

    // Processing options
    private File takeoutDirectory;
    private File outputDirectory;
    private boolean copyFiles = true;
    private TakeoutProcessorService.OrganizationMode organizationMode = TakeoutProcessorService.OrganizationMode.BY_MONTH;
    private boolean addMetadata = true;
    private boolean skipDuplicates = true;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        this.executorService = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r);
            thread.setDaemon(true);
            thread.setName("TakeoutProcessor-Worker");
            return thread;
        });

        primaryStage.setTitle(GOOGLE_TAKEOUT_PROCESSOR);
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(650);

        createMainLayout();

        Scene scene = new Scene(mainLayout, 1000, 700);

        // Load CSS if available
        try {
            var cssResource = getClass().getResource("/styles/app.css");
            if (cssResource != null) {
                scene.getStylesheets().add(cssResource.toExternalForm());
            }
        } catch (Exception e) {
            logger.warn("Could not load CSS stylesheet: {}", e.getMessage());
        }

        primaryStage.setScene(scene);
        primaryStage.show();

        showWelcomeScreen();
    }

    private void createMainLayout() {
        mainLayout = new BorderPane();
        mainLayout.getStyleClass().add("main-layout");

        // Header
        HBox header = createHeader();
        mainLayout.setTop(header);

        // Side panel
        VBox sidePanel = createSidePanel();
        mainLayout.setLeft(sidePanel);

        // Content area
        StackPane contentArea = new StackPane();
        contentArea.getStyleClass().add("content-area");
        contentArea.setPadding(new Insets(20));
        contentArea.setId("contentArea");
        mainLayout.setCenter(contentArea);

        // Status bar
        HBox statusBar = createStatusBar();
        mainLayout.setBottom(statusBar);
    }

    private HBox createHeader() {
        HBox header = new HBox(15);
        header.getStyleClass().add("header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(15, 25, 15, 25));

        Label logoLabel = new Label("📷");
        logoLabel.setFont(Font.font(SYSTEM, FontWeight.BOLD, 28));

        Label titleLabel = new Label(GOOGLE_TAKEOUT_PROCESSOR);
        titleLabel.getStyleClass().add("header-title");
        titleLabel.setFont(Font.font(SYSTEM, FontWeight.BOLD, 22));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        header.getChildren().addAll(logoLabel, titleLabel, spacer);
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

        Button btnProcess = createMenuButton("⚙ Verarbeiten", this::showProcessView);
        Button btnHelp = createMenuButton("❓ Hilfe", this::showHelpView);

        panel.getChildren().addAll(menuTitle, btnProcess, btnHelp);
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
        cancelButton.getStyleClass().add(DANGER_BUTTON);
        cancelButton.setVisible(false);
        cancelButton.setOnAction(e -> cancelCurrentTask());

        statusBar.getChildren().addAll(statusLabel, spacer, progressBar, cancelButton);
        return statusBar;
    }

    private void showWelcomeScreen() {
        VBox welcomeBox = new VBox(25);
        welcomeBox.setAlignment(Pos.CENTER);
        welcomeBox.getStyleClass().add("welcome-screen");

        Label welcomeIcon = new Label("📦");
        welcomeIcon.setFont(Font.font(SYSTEM, 72));

        Label welcomeTitle = new Label(GOOGLE_TAKEOUT_PROCESSOR);
        welcomeTitle.getStyleClass().add("welcome-title");
        welcomeTitle.setFont(Font.font(SYSTEM, FontWeight.BOLD, 28));

        Label welcomeSubtitle = new Label("Metadaten zu Bildern hinzufügen und in Monatsordner organisieren");
        welcomeSubtitle.getStyleClass().add("welcome-subtitle");
        welcomeSubtitle.setFont(Font.font(SYSTEM, 16));
        welcomeSubtitle.setWrapText(true);
        welcomeSubtitle.setMaxWidth(600);
        welcomeSubtitle.setAlignment(Pos.CENTER);

        VBox featureBox = new VBox(10);
        featureBox.setAlignment(Pos.CENTER);
        featureBox.setPadding(new Insets(20));

        String[] features = {
            "✓ Liest Google Takeout JSON-Metadaten",
            "✓ Schreibt Datum/Zeit in EXIF-Daten",
            "✓ Organisiert Dateien nach Monat (YYYY/MM)",
            "✓ Verarbeitet Bilder und Videos"
        };

        for (String feature : features) {
            Label featureLabel = new Label(feature);
            featureLabel.getStyleClass().add("feature-label");
            featureBox.getChildren().add(featureLabel);
        }

        Button startButton = new Button("⚙ Verarbeitung starten");
        startButton.getStyleClass().add(PRIMARY_BUTTON);
        startButton.setFont(Font.font(SYSTEM, FontWeight.BOLD, 16));
        startButton.setOnAction(e -> showProcessView());

        welcomeBox.getChildren().addAll(welcomeIcon, welcomeTitle, welcomeSubtitle, featureBox, startButton);
        setContent(welcomeBox);
    }

    private void showProcessView() {
        VBox processView = new VBox(20);
        processView.getStyleClass().add("export-view");
        processView.setPadding(new Insets(10));

        Label title = new Label("⚙ Google Takeout verarbeiten");
        title.getStyleClass().add("view-title");
        title.setFont(Font.font(SYSTEM, FontWeight.BOLD, 24));

        // Form
        GridPane form = new GridPane();
        form.setHgap(15);
        form.setVgap(15);
        form.getStyleClass().add("export-form");

        int row = 0;

        // Takeout directory
        Label takeoutLabel = new Label("Takeout-Verzeichnis:");
        TextField takeoutField = new TextField();
        takeoutField.setPromptText("Wähle den Google Takeout Export-Ordner");
        takeoutField.setPrefWidth(400);
        takeoutField.setEditable(false);
        Button browseTakeout = new Button("📂 Durchsuchen");
        browseTakeout.getStyleClass().add(SECONDARY_BUTTON);
        browseTakeout.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Takeout-Verzeichnis wählen");
            File dir = chooser.showDialog(primaryStage);
            if (dir != null) {
                takeoutDirectory = dir;
                takeoutField.setText(dir.getAbsolutePath());
            }
        });
        HBox takeoutBox = new HBox(10, takeoutField, browseTakeout);
        form.add(takeoutLabel, 0, row);
        form.add(takeoutBox, 1, row++);

        // Output directory
        Label outputLabel = new Label("Ausgabeverzeichnis:");
        TextField outputField = new TextField(System.getProperty("user.home") + "/TakeoutOrganized");
        outputField.setPrefWidth(400);
        Button browseOutput = new Button("📂 Durchsuchen");
        browseOutput.getStyleClass().add(SECONDARY_BUTTON);
        browseOutput.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Ausgabeverzeichnis wählen");
            File dir = chooser.showDialog(primaryStage);
            if (dir != null) {
                outputField.setText(dir.getAbsolutePath());
            }
        });
        HBox outputBox = new HBox(10, outputField, browseOutput);
        form.add(outputLabel, 0, row);
        form.add(outputBox, 1, row++);

        // Copy or move
        Label modeLabel = new Label("Modus:");
        CheckBox copyCheck = new CheckBox("Dateien kopieren (nicht verschieben)");
        copyCheck.setSelected(true);
        form.add(modeLabel, 0, row);
        form.add(copyCheck, 1, row++);

        // Organization options with radio buttons
        Label organizeLabel = new Label("Organisation:");
        ToggleGroup organizationGroup = new ToggleGroup();

        RadioButton organizeByMonthRadio = new RadioButton("Nach Monat (YYYY/MM)");
        organizeByMonthRadio.setToggleGroup(organizationGroup);
        organizeByMonthRadio.setSelected(true);
        organizeByMonthRadio.setTooltip(new Tooltip("Dateien nach Aufnahmedatum in Jahr/Monat Ordner sortieren"));

        RadioButton organizeByAlbumRadio = new RadioButton("Nach Album");
        organizeByAlbumRadio.setToggleGroup(organizationGroup);
        organizeByAlbumRadio.setTooltip(new Tooltip("Dateien nach Album-Namen aus Ordnerstruktur sortieren"));

        RadioButton organizeFlatRadio = new RadioButton("Flach (keine Unterordner)");
        organizeFlatRadio.setToggleGroup(organizationGroup);
        organizeFlatRadio.setTooltip(new Tooltip("Alle Dateien direkt im Ausgabeverzeichnis"));

        VBox organizationBox = new VBox(5, organizeByMonthRadio, organizeByAlbumRadio, organizeFlatRadio);
        form.add(organizeLabel, 0, row);
        form.add(organizationBox, 1, row++);

        // Metadata options
        Label metadataLabel = new Label("Metadaten:");
        CheckBox addMetadataCheck = new CheckBox("EXIF & XMP-Metadaten zu Bildern hinzufügen");
        addMetadataCheck.setSelected(true);
        addMetadataCheck.setTooltip(new Tooltip("Schreibt Datum, Beschreibung, Titel, Personen und Album in EXIF & XMP"));
        form.add(metadataLabel, 0, row);
        form.add(addMetadataCheck, 1, row++);

        // Duplicate detection option
        Label duplicateLabel = new Label("Duplikate:");
        CheckBox skipDuplicatesCheck = new CheckBox("Duplikate überspringen (Hash-Vergleich)");
        skipDuplicatesCheck.setSelected(true);
        skipDuplicatesCheck.setTooltip(new Tooltip("Erkennt und überspringt Duplikate basierend auf Dateiinhalt (SHA-256)"));
        form.add(duplicateLabel, 0, row);
        form.add(skipDuplicatesCheck, 1, row++);

        // Buttons
        HBox buttonBox = new HBox(15);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.setPadding(new Insets(20, 0, 0, 0));

        Button scanButton = new Button("🔍 Vorschau");
        scanButton.getStyleClass().add(SECONDARY_BUTTON);
        scanButton.setOnAction(e -> {
            if (takeoutDirectory == null) {
                showError("Fehler", "Bitte wähle zuerst ein Takeout-Verzeichnis.");
                return;
            }
            scanTakeout();
        });

        Button processButton = new Button("▶ Verarbeiten");
        processButton.getStyleClass().add(PRIMARY_BUTTON);
        processButton.setOnAction(e -> {
            if (takeoutDirectory == null) {
                showError("Fehler", "Bitte wähle zuerst ein Takeout-Verzeichnis.");
                return;
            }
            if (outputField.getText().trim().isEmpty()) {
                showError("Fehler", "Bitte wähle ein Ausgabeverzeichnis.");
                return;
            }
            outputDirectory = new File(outputField.getText());
            copyFiles = copyCheck.isSelected();

            // Determine organization mode from radio buttons
            if (organizeByMonthRadio.isSelected()) {
                organizationMode = TakeoutProcessorService.OrganizationMode.BY_MONTH;
            } else if (organizeByAlbumRadio.isSelected()) {
                organizationMode = TakeoutProcessorService.OrganizationMode.BY_ALBUM;
            } else {
                organizationMode = TakeoutProcessorService.OrganizationMode.FLAT;
            }

            addMetadata = addMetadataCheck.isSelected();
            skipDuplicates = skipDuplicatesCheck.isSelected();
            startProcessing();
        });

        buttonBox.getChildren().addAll(scanButton, processButton);

        processView.getChildren().addAll(title, form, buttonBox);
        setContent(processView);
    }

    private void scanTakeout() {
        setStatus("Scanne Takeout-Verzeichnis...");
        progressBar.setVisible(true);
        progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        cancelButton.setVisible(true);

        Task<GoogleTakeoutService.ScanStatistics> scanTask = new Task<>() {
            @Override
            protected GoogleTakeoutService.ScanStatistics call() throws Exception {
                GoogleTakeoutService service = new GoogleTakeoutService();
                List<GoogleTakeoutService.MediaFileWithMetadata> files =
                    service.scanTakeoutDirectory(takeoutDirectory);
                return service.calculateStatistics(files);
            }
        };

        currentTask = scanTask;

        scanTask.setOnSucceeded(e -> {
            GoogleTakeoutService.ScanStatistics stats = scanTask.getValue();
            progressBar.setVisible(false);
            cancelButton.setVisible(false);
            currentTask = null;
            setStatus("Scan abgeschlossen");

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Scan-Ergebnisse");
            alert.setHeaderText("Gefundene Dateien");
            alert.setContentText(String.format(
                "Gesamt: %d Dateien\n" +
                "Bilder mit Metadaten: %d\n" +
                "Videos mit Metadaten: %d\n" +
                "Dateien ohne Metadaten: %d\n" +
                "Dateien mit GPS: %d",
                stats.totalFiles, stats.imagesWithMetadata, stats.videosWithMetadata,
                stats.filesWithoutMetadata, stats.filesWithGeoData
            ));
            alert.showAndWait();
        });

        scanTask.setOnFailed(e -> {
            progressBar.setVisible(false);
            cancelButton.setVisible(false);
            currentTask = null;
            setStatus("Scan fehlgeschlagen");
            showError("Fehler", "Scan fehlgeschlagen: " + scanTask.getException().getMessage());
        });

        scanTask.setOnCancelled(e -> {
            progressBar.setVisible(false);
            cancelButton.setVisible(false);
            currentTask = null;
            setStatus("Scan abgebrochen");
        });

        executorService.submit(scanTask);
    }

    private void startProcessing() {
        showProcessingProgress();
    }

    private void showProcessingProgress() {
        VBox progressView = new VBox(20);
        progressView.setAlignment(Pos.CENTER);
        progressView.setPadding(new Insets(40));
        progressView.getStyleClass().add("progress-view");

        Label title = new Label("⚙ Verarbeitung läuft...");
        title.getStyleClass().add("view-title");
        title.setFont(Font.font(SYSTEM, FontWeight.BOLD, 24));

        ProgressBar procProgressBar = new ProgressBar(0);
        procProgressBar.setPrefWidth(500);
        procProgressBar.setPrefHeight(25);

        Label progressLabel = new Label("Initialisiere...");
        progressLabel.getStyleClass().add("progress-label");

        Label statsLabel = new Label("");
        statsLabel.getStyleClass().add("stats-label");

        Button procCancelButton = new Button("❌ Abbrechen");
        procCancelButton.getStyleClass().add(DANGER_BUTTON);

        progressView.getChildren().addAll(title, procProgressBar, progressLabel, statsLabel, procCancelButton);
        setContent(progressView);

        // Start processing task
        Task<Void> processTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("Scanne Dateien...");

                GoogleTakeoutService takeoutService = new GoogleTakeoutService();
                List<GoogleTakeoutService.MediaFileWithMetadata> files =
                    takeoutService.scanTakeoutDirectory(takeoutDirectory);

                if (files.isEmpty()) {
                    updateMessage("Keine Dateien gefunden.");
                    return null;
                }

                updateMessage("Starte Verarbeitung von " + files.size() + " Dateien...");

                TakeoutProcessorService processorService = new TakeoutProcessorService();
                TakeoutProcessorService.ProcessingOptions options =
                    new TakeoutProcessorService.ProcessingOptions();
                options.setOutputDirectory(outputDirectory);
                options.setCopyFiles(copyFiles);
                options.setAddMetadata(addMetadata);
                options.setOrganizationMode(organizationMode);
                options.setSkipDuplicates(skipDuplicates);

                processorService.processAllFiles(files, options, new TakeoutProcessorService.ProgressCallback() {
                    @Override
                    public void onProgress(int current, int total, String currentFile) {
                        if (isCancelled()) {
							return;
						}

                        Platform.runLater(() -> {
                            procProgressBar.setProgress((double) current / total);
                            progressLabel.setText("Verarbeite: " + currentFile);
                            statsLabel.setText(String.format("%d / %d (%.0f%%)",
                                current, total, (double) current / total * 100));
                        });
                    }

                    @Override
                    public void onComplete(int success, int errors, int skipped) {
                        Platform.runLater(() -> {
                            String msg = String.format(
                                "Verarbeitung abgeschlossen!\n\n" +
                                "Erfolgreich: %d\n" +
                                "Fehlgeschlagen: %d\n" +
                                "Übersprungen: %d",
                                success, errors, skipped
                            );
                            progressLabel.setText(msg);
                        });
                    }
                });

                return null;
            }
        };

        currentTask = processTask;

        procCancelButton.setOnAction(e -> {
            processTask.cancel();
            currentTask = null;
            showProcessView();
        });

        processTask.setOnSucceeded(e -> {
            procCancelButton.setText("✓ Fertig - Zurück");
            procCancelButton.getStyleClass().remove(DANGER_BUTTON);
            procCancelButton.getStyleClass().add(PRIMARY_BUTTON);
            procCancelButton.setOnAction(ev -> showProcessView());
            setStatus("Verarbeitung abgeschlossen");
            currentTask = null;
        });

        processTask.setOnFailed(e -> {
            progressLabel.setText("Verarbeitung fehlgeschlagen: " + processTask.getException().getMessage());
            procCancelButton.setText("Zurück");
            setStatus("Verarbeitung fehlgeschlagen");
            currentTask = null;
        });

        processTask.setOnCancelled(e -> {
            progressLabel.setText("Verarbeitung abgebrochen");
            procCancelButton.setText("Zurück");
            setStatus("Verarbeitung abgebrochen");
            currentTask = null;
        });

        executorService.submit(processTask);
    }

    private void showHelpView() {
        VBox helpView = new VBox(20);
        helpView.getStyleClass().add("help-view");
        helpView.setPadding(new Insets(10));

        Label title = new Label("❓ Hilfe");
        title.getStyleClass().add("view-title");
        title.setFont(Font.font(SYSTEM, FontWeight.BOLD, 24));

        TextArea helpText = new TextArea();
        helpText.setEditable(false);
        helpText.setWrapText(true);
        helpText.setPrefHeight(400);
        helpText.setText("""
            GOOGLE TAKEOUT PROCESSOR - HILFE

            1. GOOGLE TAKEOUT EXPORT ERSTELLEN
               - Gehe zu https://takeout.google.com
               - Wähle "Google Photos" aus
               - Erstelle den Export und lade ihn herunter
               - Entpacke die ZIP-Datei

            2. VERARBEITUNG STARTEN
               - Klicke auf "Verarbeiten" im Menü
               - Wähle das entpackte Takeout-Verzeichnis
               - Wähle ein Ausgabeverzeichnis
               - Wähle die gewünschten Optionen:
                 * Kopieren/Verschieben
                 * Organisation: Nach Monat / Nach Album / Flach
                 * EXIF & XMP-Metadaten hinzufügen
               - Klicke auf "Vorschau" um zu sehen, was gefunden wurde
               - Klicke auf "Verarbeiten" um zu starten

            3. WAS PASSIERT?
               Die App:
               - Scannt alle Bilder und Videos im Takeout-Ordner
               - Liest die JSON-Metadaten-Dateien
               - Schreibt optional Metadaten in EXIF & XMP (für JPEGs)
               - Organisiert Dateien nach gewählter Option
               - Kopiert oder verschiebt die Dateien

            4. METADATEN
               Folgende Metadaten werden verarbeitet (wenn aktiviert):

               EXIF-Felder:
               - Aufnahmedatum/-zeit (EXIF DateTimeOriginal)
               - Erstellungsdatum/-zeit (EXIF DateTimeDigitized)
               - Beschreibung (EXIF ImageDescription)
               - Titel (EXIF DocumentName)
               - Personen (EXIF Software als "People: Name1, Name2")
               - Album (EXIF Artist als "Album: AlbumName")

               XMP-Felder:
               - Personen (XMP dc:subject Keywords)
               - Album (XMP lr:hierarchicalSubject)
               - Titel (XMP dc:title)
               - Beschreibung (XMP dc:description)

               Hinweis: Album-Namen werden aus der Ordnerstruktur des
               Takeout-Exports extrahiert. XMP ist ein moderner Standard
               für erweiterte Metadaten.

            5. ORDNERSTRUKTUR
               Nach Monat (YYYY/MM):
               <Ausgabeverzeichnis>/
                 2021/
                   01/  (Januar 2021)
                   02/  (Februar 2021)
                 2022/
                   12/  (Dezember 2022)
                 Unknown_Date/  (Dateien ohne Datum)

               Nach Album (mit Datum-Präfix):
               <Ausgabeverzeichnis>/
                 2023-07 Sommerurlaub/
                 2023-12 Familienfotos/
                 2024-03 Geburtstag 2023/
                 No_Album/  (Dateien ohne Album und Datum)

               Hinweis: Bei "Nach Album" wird das Aufnahmedatum (YYYY-MM)
               vor den Albumnamen gesetzt, um eine chronologische Sortierung
               zu ermöglichen.

               Flach (keine Unterordner):
               <Ausgabeverzeichnis>/
                 [Alle Dateien direkt im Hauptordner]

            HINWEISE:
            - JPEG-Bilder erhalten EXIF & XMP-Metadaten
            - Videos werden organisiert, aber Metadaten nicht geändert
            - Dateien ohne Metadaten landen im "Unknown_Date" Ordner
            - Der Vorgang kann bei vielen Dateien lange dauern
            """);

        helpView.getChildren().addAll(title, helpText);
        VBox.setVgrow(helpText, Priority.ALWAYS);
        setContent(helpView);
    }

    private void setContent(javafx.scene.Node node) {
        StackPane contentArea = (StackPane) mainLayout.lookup("#contentArea");
        if (contentArea != null) {
            contentArea.getChildren().clear();
            contentArea.getChildren().add(node);
        }
    }

    private void setStatus(String message) {
        Platform.runLater(() -> statusLabel.setText(message));
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

        if (currentTask != null && currentTask.isRunning()) {
            logger.info("Cancelling running task before shutdown");
            currentTask.cancel();
        }

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

        logger.info("Application shutdown complete");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
