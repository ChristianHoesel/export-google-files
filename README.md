# Google Photos Exporter

Ein Java-Programm zum Exportieren von Fotos und Videos aus Google Photos mit Erhaltung aller Metadaten.

## Funktionen

- **Export von Fotos und Videos** aus Google Photos
- **Vollständige Metadaten-Erhaltung** inklusive:
  - EXIF-Daten (Kamera, Objektiv, Einstellungen)
  - Album-Informationen
  - Personen-Tags (sofern verfügbar über die API)
  - Erstellungsdatum und -zeit
  - Standortinformationen
- **Zeitraum-Auswahl** - Export nur bestimmter Zeiträume möglich
- **Flexible Ordnerstruktur** - Nach Datum oder Album organisiert
- **JSON-Metadaten-Dateien** für jedes exportierte Medium
- **Einfache Benutzerführung** über die Kommandozeile
- **Optionale Löschfunktion** nach erfolgreichem Export

## Voraussetzungen

- Java 17 oder höher
- Maven 3.6 oder höher
- Google Cloud Projekt mit aktivierter Photos Library API
- OAuth 2.0 Credentials (credentials.json)

## Installation

### 1. Repository klonen

```bash
git clone https://github.com/ChristianHoesel/export-google-files.git
cd export-google-files
```

### 2. Google Cloud Projekt einrichten

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

### 3. Projekt bauen

```bash
mvn clean package
```

### 4. Credentials platzieren

Kopieren Sie die `credentials.json` Datei in das Verzeichnis, von dem aus Sie die Anwendung starten werden.

## Verwendung

### Anwendung starten

```bash
java -jar target/google-photos-export-1.0.0-SNAPSHOT-jar-with-dependencies.jar
```

### Menüoptionen

1. **Export konfigurieren** - Einstellungen für den Export festlegen:
   - Ausgabeverzeichnis
   - Zeitraum (Start- und Enddatum)
   - Medientypen (Fotos, Videos oder beide)
   - Ordnerstruktur (nach Datum/Album)
   - Metadaten-Export
   - Löschoption

2. **Alben anzeigen** - Liste aller Alben in Ihrem Google Photos

3. **Vorschau** - Zeigt an, wie viele Dateien exportiert werden würden

4. **Export starten** - Führt den Export durch

5. **Aktuelle Einstellungen anzeigen** - Zeigt die aktuellen Konfigurationsoptionen

6. **Beenden** - Schließt die Anwendung

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

### JAR mit Abhängigkeiten erstellen

```bash
mvn package
```

## Bekannte Einschränkungen

- Die Google Photos API erlaubt keinen programmatischen Zugriff auf Personen-Tags
- Die Löschfunktion ist aus Sicherheitsgründen deaktiviert (nur Vorwarnung)
- Große Bibliotheken können einige Zeit für den Export benötigen

## Lizenz

Apache License 2.0 - siehe [LICENSE](LICENSE) Datei

## Beitragen

Pull Requests sind willkommen! Für größere Änderungen bitte zuerst ein Issue erstellen.
