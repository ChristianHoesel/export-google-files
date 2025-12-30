# Google Takeout Photos Organizer

Ein Java-Programm mit moderner JavaFX-Benutzeroberfläche zum Verarbeiten von Google Takeout Exporten. Die App liest JSON-Metadaten, schreibt sie als EXIF- und XMP-Metadaten in die Fotos und organisiert die Dateien flexibel nach Monat, Album oder flach.

![Java Version](https://img.shields.io/badge/Java-21-orange)
![JavaFX](https://img.shields.io/badge/JavaFX-21-blue)
![License](https://img.shields.io/badge/License-Apache%202.0-green)
![Build](https://github.com/ChristianHoesel/export-google-files/actions/workflows/ci.yml/badge.svg)

## Funktionen

- **Moderne JavaFX-GUI** mit ansprechendem Design
- **Self-contained Executable** für Windows, macOS und Linux mit integriertem JDK - keine Installation erforderlich
- **Verarbeitet Google Takeout Exporte** lokal auf Ihrem Computer
- **Metadaten-Erhaltung und -Hinzufügung**:
  - Liest JSON-Metadaten von Google Takeout
  - Schreibt Aufnahmedatum/-zeit in EXIF-Daten (für JPEG-Bilder und Videos)
  - Schreibt Beschreibungen, Titel und Personen in EXIF und XMP
  - Schreibt Album-Namen aus Ordnerstruktur in EXIF und XMP
  - **Video-Metadaten**: XMP-Sidecar-Dateien für MP4/MOV Videos
  - Erhält Original-Metadaten
- **Flexible Organisation**:
  - Nach Monat organisieren (YYYY/MM)
  - Nach Album organisieren
  - Flach in einem Verzeichnis (keine Unterordner)
- **EXIF & XMP Metadaten**:
  - Dual-Format für maximale Kompatibilität
  - EXIF für Datum, Beschreibung, Titel (JPEG)
  - XMP für Personen (dc:subject) und Album (lr:hierarchicalSubject)
  - XMP-Sidecar-Dateien für Videos
- **Duplikaterkennung**:
  - Content-basierte Erkennung mittels SHA-256 Hash
  - Verhindert doppelte Dateien im Ausgabeverzeichnis
  - Drei Modi: Hash, Name+Größe, Nur Name
- **Motion Photos / Live Photos**:
  - Automatische Erkennung von Google Motion Photos
  - Extrahiert Video-Komponente aus eingebetteten Motion Photos
  - Speichert Foto und Video als separate Dateien mit Metadaten
  - Unterstützt Pixel-Phones und kompatible Geräte
- **Fortschrittsanzeige** während der Verarbeitung
- **Vorschau-Funktion** um zu sehen, was verarbeitet wird

## Screenshots

Die Anwendung bietet eine moderne, benutzerfreundliche Oberfläche:

- **Willkommensbildschirm** - Einfacher Einstieg
- **Verarbeitungs-Konfiguration** - Intuitive Einstellungen
- **Fortschrittsanzeige** - Live-Status während der Verarbeitung

## Wie es funktioniert

1. **Erstellen Sie einen Google Takeout Export**:
   - Gehen Sie zu [Google Takeout](https://takeout.google.com)
   - Wählen Sie "Google Photos" aus
   - Erstellen und laden Sie den Export herunter
   - Entpacken Sie die ZIP-Datei

2. **Verarbeiten Sie die Dateien**:
   - Starten Sie die Anwendung
   - Wählen Sie das entpackte Takeout-Verzeichnis
   - Wählen Sie ein Ausgabeverzeichnis
   - Wählen Sie Organisation: Nach Monat, Nach Album oder Flach
   - Wählen Sie ob EXIF & XMP Metadaten geschrieben werden sollen
   - Optional: Aktivieren Sie Duplikaterkennung
   - Klicken Sie auf "Verarbeiten"

3. **Ergebnis**:
   - Dateien werden organisiert (je nach gewählter Option)
   - JPEG-Bilder erhalten EXIF & XMP Metadaten
   - Videos erhalten XMP-Sidecar-Dateien (.xmp)
   - Motion Photos werden in Foto + Video aufgeteilt
   - Duplikate werden automatisch übersprungen (falls aktiviert)

## Download & Verwendung

### Portable Executable (empfohlen)

Laden Sie die passende ZIP-Datei für Ihr Betriebssystem herunter - **keine Installation oder Java erforderlich!**

| Betriebssystem | Download | Verwendung |
|----------------|----------|------------|
| Windows | `TakeoutProcessor-windows.zip` | Entpacken → `TakeoutProcessor.exe` starten |
| macOS | `TakeoutProcessor-macos.zip` | Entpacken → `TakeoutProcessor.app` starten |
| Linux | `TakeoutProcessor-linux.zip` | Entpacken → `./TakeoutProcessor` ausführen |

Die ZIP-Dateien enthalten das komplette JDK 21 - einfach entpacken und starten!

**Download:** Gehen Sie zu [Actions](../../actions) → Wählen Sie den neuesten erfolgreichen Build → Download Artifacts

### Aus Quellcode bauen

#### Voraussetzungen

- **Java 21** oder höher
- Maven 3.6 oder höher

#### Repository klonen

```bash
git clone https://github.com/ChristianHoesel/export-google-files.git
cd export-google-files
```

## Google Takeout Export erstellen

1. Gehen Sie zu [Google Takeout](https://takeout.google.com)
2. Wählen Sie "Google Photos" aus (deselektieren Sie andere Dienste)
3. Klicken Sie auf "Nächster Schritt"
4. Wählen Sie Ihre Export-Optionen:
   - Häufigkeit: "Einmaliger Export"
   - Dateityp: ZIP
   - Größe: Ihre Präferenz
5. Klicken Sie auf "Export erstellen"
6. Warten Sie auf die E-Mail von Google (kann Stunden oder Tage dauern)
7. Laden Sie die ZIP-Datei(en) herunter
8. Entpacken Sie die Dateien in einem lokalen Ordner

## Projekt bauen (nur für Entwickler)

```bash
mvn clean package
```

### Self-contained Executable erstellen

```bash
# Erst JAR bauen
mvn clean package

# Self-contained App erstellen (auf jeweiligem Betriebssystem ausführen)
jpackage --input target --name TakeoutProcessor \
  --main-jar google-photos-export-1.0.0-SNAPSHOT-jar-with-dependencies.jar \
  --main-class de.christianhoesel.googlephotos.ui.TakeoutProcessorApp \
  --type app-image --dest target/app

# Optional: ZIP erstellen
# Windows: Compress-Archive -Path "target/app/TakeoutProcessor" -DestinationPath "TakeoutProcessor-windows.zip"
# macOS/Linux: cd target/app && zip -r ../TakeoutProcessor.zip TakeoutProcessor*
```

## Verwendung

### GUI-Anwendung starten

```bash
# Mit JavaFX Maven Plugin
mvn javafx:run

# Oder als JAR
java -jar target/google-photos-export-1.0.0-SNAPSHOT-jar-with-dependencies.jar
```

### Navigation

Die Anwendung hat ein übersichtliches Seitenmenü:

1. **⚙️ Verarbeiten** - Google Takeout Dateien verarbeiten
2. **❓ Hilfe** - Hilfe und Dokumentation

### Verarbeitungs-Optionen

- **Takeout-Verzeichnis** - Der entpackte Google Takeout Export-Ordner
- **Ausgabeverzeichnis** - Wo die organisierten Dateien gespeichert werden
- **Organisation** - Wählen Sie zwischen:
  - **Nach Monat (YYYY/MM)** - Dateien in Jahres-/Monatsordner
  - **Nach Album** - Dateien nach Album aus Ordnerstruktur
  - **Flach** - Alle Dateien direkt im Ausgabeverzeichnis
- **Metadaten** - EXIF & XMP Metadaten zu JPEG-Bildern hinzufügen (An/Aus)
- **Modus** - Dateien kopieren oder verschieben

## Ausgabestruktur

Die Dateien werden je nach gewählter Organisation organisiert:

### Nach Monat (YYYY/MM)

```
Ausgabeverzeichnis/
├── 2023/
│   ├── 01/          (Januar 2023)
│   │   ├── IMG_001.jpg
│   │   ├── IMG_001.jpg.json
│   │   ├── IMG_002.jpg
│   │   └── IMG_002.jpg.json
│   ├── 02/          (Februar 2023)
│   │   └── ...
│   └── 12/          (Dezember 2023)
├── 2024/
│   └── ...
└── Unknown_Date/    (Dateien ohne Datum)
    └── ...
```

### Nach Album

```
Ausgabeverzeichnis/
├── Sommerurlaub/
│   ├── IMG_001.jpg
│   └── IMG_001.jpg.json
├── Familienfotos/
│   ├── IMG_002.jpg
│   └── IMG_002.jpg.json
└── No_Album/        (Dateien ohne Album)
    └── ...
```

### Flach

```
Ausgabeverzeichnis/
├── IMG_001.jpg
├── IMG_001.jpg.json
├── IMG_002.jpg
├── IMG_002.jpg.json
└── ...
```

### Metadaten in EXIF & XMP (JPEG-Bilder)

Für JPEG-Bilder werden Metadaten in beiden Formaten geschrieben:

**EXIF-Felder:**
- **Aufnahmedatum/-zeit** (EXIF DateTimeOriginal & DateTimeDigitized)
- **Beschreibung** (TIFF ImageDescription)
- **Titel** (TIFF DocumentName)
- **Personen** (TIFF Software - Format: "People: Name1, Name2")
- **Album** (TIFF Artist - Format: "Album: AlbumName")

**XMP-Felder:**
- **Personen** (dc:subject - Dublin Core Keywords, jeder Name einzeln)
- **Album** (lr:hierarchicalSubject - Lightroom Namespace)
- **Titel** (dc:title - Dublin Core)
- **Beschreibung** (dc:description - Dublin Core)

### JSON-Metadaten-Dateien

Die originalen Google Takeout JSON-Dateien bleiben erhalten und enthalten alle ursprünglichen Metadaten:

```json
{
  "title": "IMG_001.jpg",
  "description": "",
  "creationTime": {
    "timestamp": "1609459200",
    "formatted": "Jan 1, 2021, 12:00:00 AM UTC"
  },
  "photoTakenTime": {
    "timestamp": "1609459200",
    "formatted": "Jan 1, 2021, 12:00:00 AM UTC"
  },
  "geoData": {
    "latitude": 48.8566,
    "longitude": 2.3522
  }
}
```

## Import in andere Dienste

### Synology Photos

1. Kopieren Sie die organisierten Dateien in den Synology Photos Ordner
2. Synology Photos liest automatisch die EXIF-Metadaten
3. Bilder werden nach Datum sortiert angezeigt

### Apple Photos / iCloud

1. Importieren Sie die Dateien in Apple Photos
2. Die EXIF-Metadaten (Datum/Zeit) werden automatisch erkannt
3. Fotos werden in der Timeline korrekt einsortiert

### Andere Cloud-Dienste

Die Dateien behalten ihre EXIF-Metadaten und können direkt in jeden Cloud-Dienst oder Photo-Manager hochgeladen werden, der EXIF unterstützt.

## Entwicklung

### Projekt bauen

```bash
mvn clean compile
```

### Tests ausführen

```bash
mvn test
```

### JavaFX-Anwendung starten (Entwicklung)

```bash
mvn javafx:run
```

### JAR mit Abhängigkeiten erstellen

```bash
mvn package
```

## Technologie-Stack

- **Java 21** - Java LTS Version
- **JavaFX 21** - Moderne Desktop-GUI
- **Apache Commons Imaging** - EXIF & XMP Metadaten schreiben
- **Apache Commons Codec** - SHA-256 Hashing für Duplikaterkennung
- **Adobe XMP Core** - XMP-Packet-Generierung
- **Gson** - JSON-Parsing
- **Maven** - Build-Management
- **SLF4J + Logback** - Logging

## Was ist neu?

Diese Version wurde komplett umgebaut, um **lokal mit Google Takeout Exporten zu arbeiten**, anstatt die Google Photos API zu verwenden. Dies bietet mehrere Vorteile:

- ✅ Keine Google Cloud API-Konfiguration erforderlich
- ✅ Keine OAuth-Authentifizierung nötig
- ✅ Vollständig offline nach dem Download
- ✅ Schnellere Verarbeitung (keine API-Rate-Limits)
- ✅ Volle Kontrolle über Ihre Daten
- ✅ **Neu: Duplikaterkennung** mit Hash-Vergleich
- ✅ **Neu: Video-Metadaten** via XMP-Sidecar-Dateien
- ✅ **Neu: Motion Photos Unterstützung** - extrahiert Videos aus Live Photos

## Bekannte Einschränkungen

- GPS-Koordinaten werden aktuell nicht in EXIF geschrieben (Limitierung der Apache Commons Imaging Alpha-Version)
- EXIF Metadaten können nur für JPEG-Bilder geschrieben werden
- Videos erhalten XMP-Sidecar-Dateien (.xmp) anstatt eingebettete Metadaten

## Lizenz

Apache License 2.0 - siehe [LICENSE](LICENSE) Datei
