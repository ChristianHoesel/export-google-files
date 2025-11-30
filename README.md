# Google Photos Exporter

Ein Java-Programm mit moderner JavaFX-Benutzeroberfläche zum Exportieren von Fotos und Videos aus Google Photos mit Erhaltung aller Metadaten.

![Java Version](https://img.shields.io/badge/Java-25-orange)
![JavaFX](https://img.shields.io/badge/JavaFX-21-blue)
![License](https://img.shields.io/badge/License-Apache%202.0-green)
![Build](https://github.com/ChristianHoesel/export-google-files/actions/workflows/ci.yml/badge.svg)

## Funktionen

- **Moderne JavaFX-GUI** mit ansprechendem Design
- **Native Installer** für Windows (.msi), macOS (.dmg) und Linux (.deb) mit integriertem JDK
- **Export von Fotos und Videos** aus Google Photos
- **Vollständige Metadaten-Erhaltung** inklusive:
  - EXIF-Daten (Kamera, Objektiv, Einstellungen)
  - Album-Informationen
  - Personen-Tags (sofern verfügbar über die API)
  - Erstellungsdatum und -zeit
  - Standortinformationen
- **Zeitraum-Auswahl** mit Datumspicker
- **Flexible Ordnerstruktur** - Nach Datum oder Album organisiert
- **JSON-Metadaten-Dateien** für jedes exportierte Medium
- **Fortschrittsanzeige** während des Exports
- **Optionale Löschfunktion** nach erfolgreichem Export

## Screenshots

Die Anwendung bietet eine moderne, benutzerfreundliche Oberfläche:

- **Willkommensbildschirm** - Einfacher Einstieg mit Google-Verbindung
- **Album-Übersicht** - Alle Ihre Alben auf einen Blick
- **Export-Konfiguration** - Intuitive Einstellungen mit Datumspicker
- **Fortschrittsanzeige** - Live-Status während des Exports

## Installation

### Option 1: Native Installer (empfohlen)

Laden Sie den passenden Installer für Ihr Betriebssystem herunter - **keine Java-Installation erforderlich!**

| Betriebssystem | Download |
|----------------|----------|
| Windows | `GooglePhotosExporter-1.0.0.msi` |
| macOS | `GooglePhotosExporter-1.0.0.dmg` |
| Linux (Debian/Ubuntu) | `GooglePhotosExporter-1.0.0.deb` |

Die Installer enthalten das Java Runtime Environment (JDK 25) und können direkt installiert werden.

**Download:** Gehen Sie zu [Actions](../../actions) → Wählen Sie den neuesten erfolgreichen Build → Download Artifacts

### Option 2: Aus Quellcode bauen

#### Voraussetzungen

- **Java 25** oder höher
- Maven 3.6 oder höher

#### Repository klonen

```bash
git clone https://github.com/ChristianHoesel/export-google-files.git
cd export-google-files
```

## Google Cloud Projekt einrichten

1. Gehen Sie zur [Google Cloud Console](https://console.cloud.google.com/)
2. Erstellen Sie ein neues Projekt oder wählen Sie ein bestehendes
3. Aktivieren Sie die **Photos Library API**:
   - Navigieren Sie zu "APIs & Services" → "Library"
   - Suchen Sie nach "Photos Library API"
   - Klicken Sie auf "Enable"

4. Erstellen Sie OAuth 2.0 Credentials:
   - Navigieren Sie zu "APIs & Services" → "Credentials"
   - Klicken Sie auf "Create Credentials" → "OAuth client ID"
   - Wählen Sie "Desktop app" als Anwendungstyp
   - Laden Sie die JSON-Datei herunter
   - Benennen Sie die Datei in `credentials.json` um

5. Konfigurieren Sie den OAuth-Consent-Screen:
   - Navigieren Sie zu "APIs & Services" → "OAuth consent screen"
   - Fügen Sie Ihre E-Mail-Adresse als Testbenutzer hinzu

## Projekt bauen (nur für Entwickler)

```bash
mvn clean package
```

### Native Installer erstellen

```bash
# Windows (.msi)
jpackage --input target --name GooglePhotosExporter \
  --main-jar google-photos-export-1.0.0-SNAPSHOT-jar-with-dependencies.jar \
  --main-class de.christianhoesel.googlephotos.ui.GooglePhotosApp \
  --type msi --dest target/installer

# macOS (.dmg)
jpackage --input target --name GooglePhotosExporter \
  --main-jar google-photos-export-1.0.0-SNAPSHOT-jar-with-dependencies.jar \
  --main-class de.christianhoesel.googlephotos.ui.GooglePhotosApp \
  --type dmg --dest target/installer

# Linux (.deb)
jpackage --input target --name GooglePhotosExporter \
  --main-jar google-photos-export-1.0.0-SNAPSHOT-jar-with-dependencies.jar \
  --main-class de.christianhoesel.googlephotos.ui.GooglePhotosApp \
  --type deb --dest target/installer
```

### Credentials platzieren

Kopieren Sie die `credentials.json` Datei in das Verzeichnis, von dem aus Sie die Anwendung starten werden.

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

1. **🔗 Verbinden** - Mit Google Photos verbinden
2. **📁 Alben** - Alle Alben anzeigen
3. **⬇️ Export** - Export konfigurieren und starten
4. **⚙️ Einstellungen** - App-Einstellungen
5. **❓ Hilfe** - Hilfe und Dokumentation

### Export-Optionen

- **Ausgabeverzeichnis** - Wo die Dateien gespeichert werden
- **Zeitraum** - Start- und Enddatum mit Kalender-Widget
- **Medientypen** - Fotos, Videos oder beide
- **Ordnerstruktur** - Nach Datum (YYYY/MM) und/oder Album
- **Metadaten** - JSON-Dateien für jedes Medium erstellen
- **Löschen** - Optional nach Export löschen (mit Bestätigung)

## Ausgabestruktur

Je nach Konfiguration werden die Dateien wie folgt gespeichert:

```
Ausgabeverzeichnis/
├── 2023/
│   ├── 01/
│   │   ├── IMG_001.jpg
│   │   ├── IMG_001.jpg.json
│   │   ├── IMG_002.jpg
│   │   └── IMG_002.jpg.json
│   └── 02/
│       └── ...
└── 2024/
    └── ...
```

### Metadaten-Datei (JSON)

Für jede exportierte Datei wird eine JSON-Datei mit allen Metadaten erstellt:

```json
{
  "id": "ABC123...",
  "filename": "IMG_001.jpg",
  "mimeType": "image/jpeg",
  "creationTime": "2023-07-15T14:30:00",
  "width": 4032,
  "height": 3024,
  "albums": ["Urlaub 2023", "Familie"],
  "people": ["Max", "Anna"],
  "metadata": {
    "cameraMake": "Apple",
    "cameraModel": "iPhone 14 Pro",
    "focalLength": 6.86,
    "apertureFNumber": 1.78,
    "isoEquivalent": 50,
    "latitude": 48.8566,
    "longitude": 2.3522
  }
}
```

## Import in andere Dienste

### Synology Photos

1. Kopieren Sie die exportierten Dateien in den Synology Photos Ordner
2. Synology Photos indiziert automatisch die EXIF-Metadaten
3. Die JSON-Dateien können für zusätzliche Metadaten verwendet werden

### Photon Drive / Andere Cloud-Dienste

Die exportierten Dateien behalten ihre Original-Metadaten und können direkt hochgeladen werden.

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

- **Java 25** - Neueste Java-Version
- **JavaFX 21** - Moderne Desktop-GUI
- **Google Photos Library API** - Zugriff auf Google Photos
- **Maven** - Build-Management
- **SLF4J + Logback** - Logging

## Bekannte Einschränkungen

- Die Google Photos API erlaubt keinen programmatischen Zugriff auf Personen-Tags
- Die Löschfunktion ist aus Sicherheitsgründen mit Bestätigung versehen
- Große Bibliotheken können einige Zeit für den Export benötigen

## Lizenz

Apache License 2.0 - siehe [LICENSE](LICENSE) Datei

## Beitragen

Pull Requests sind willkommen! Für größere Änderungen bitte zuerst ein Issue erstellen.
