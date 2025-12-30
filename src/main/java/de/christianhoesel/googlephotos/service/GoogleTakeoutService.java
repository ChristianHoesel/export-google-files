package de.christianhoesel.googlephotos.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import de.christianhoesel.googlephotos.model.GoogleTakeoutMetadata;

/**
 * Service to process Google Takeout exports.
 * Scans directories for media files and their associated JSON metadata files.
 */
public class GoogleTakeoutService {
    private static final Logger logger = LoggerFactory.getLogger(GoogleTakeoutService.class);

    // Common image and video extensions
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
        ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp", ".heic", ".heif"
    );

    private static final Set<String> VIDEO_EXTENSIONS = Set.of(
        ".mp4", ".mov", ".avi", ".mkv", ".wmv", ".flv", ".webm", ".m4v", ".3gp"
    );

    private final Gson gson;

    public GoogleTakeoutService() {
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();
    }

    /**
     * Represents a media file with its associated metadata.
     */
    public static class MediaFileWithMetadata {
        private final File mediaFile;
        private final File jsonFile;
        private final GoogleTakeoutMetadata metadata;
        private final String albumName;

        public MediaFileWithMetadata(File mediaFile, File jsonFile, GoogleTakeoutMetadata metadata) {
            this(mediaFile, jsonFile, metadata, null);
        }

        public MediaFileWithMetadata(File mediaFile, File jsonFile, GoogleTakeoutMetadata metadata, String albumName) {
            this.mediaFile = mediaFile;
            this.jsonFile = jsonFile;
            this.metadata = metadata;
            this.albumName = albumName;
        }

        public File getMediaFile() {
            return mediaFile;
        }

        public File getJsonFile() {
            return jsonFile;
        }

        public GoogleTakeoutMetadata getMetadata() {
            return metadata;
        }

        public String getAlbumName() {
            return albumName;
        }

        public boolean isImage() {
            String name = mediaFile.getName().toLowerCase();
            return IMAGE_EXTENSIONS.stream().anyMatch(name::endsWith);
        }

        public boolean isVideo() {
            String name = mediaFile.getName().toLowerCase();
            return VIDEO_EXTENSIONS.stream().anyMatch(name::endsWith);
        }
    }

    /**
     * Scans a directory recursively for media files and their metadata.
     *
     * @param takeoutDirectory The root directory of the Google Takeout export
     * @return List of media files with their metadata
     * @throws IOException if scanning fails
     */
    public List<MediaFileWithMetadata> scanTakeoutDirectory(File takeoutDirectory) throws IOException {
        logger.info("Scanning Takeout directory: {}", takeoutDirectory.getAbsolutePath());

        if (!takeoutDirectory.exists() || !takeoutDirectory.isDirectory()) {
            throw new IllegalArgumentException("Invalid directory: " + takeoutDirectory);
        }

        List<MediaFileWithMetadata> results = new ArrayList<>();

        // Find all media files
        try (Stream<Path> paths = Files.walk(takeoutDirectory.toPath())) {
            List<File> mediaFiles = paths
                .filter(Files::isRegularFile)
                .map(Path::toFile)
                .filter(this::isMediaFile)
                .collect(Collectors.toList());

            logger.info("Found {} media files", mediaFiles.size());

            // For each media file, find its JSON metadata and extract album name
            for (File mediaFile : mediaFiles) {
                String albumName = extractAlbumName(takeoutDirectory, mediaFile);
                File jsonFile = findJsonForMediaFile(mediaFile);
                if (jsonFile != null && jsonFile.exists()) {
                    try {
                        GoogleTakeoutMetadata metadata = parseMetadataFile(jsonFile);
                        results.add(new MediaFileWithMetadata(mediaFile, jsonFile, metadata, albumName));
                    } catch (Exception e) {
                        logger.warn("Failed to parse metadata for {}: {}",
                            mediaFile.getName(), e.getMessage());
                        // Include the file anyway without metadata
                        results.add(new MediaFileWithMetadata(mediaFile, null, null, albumName));
                    }
                } else {
                    logger.debug("No metadata file found for {}", mediaFile.getName());
                    results.add(new MediaFileWithMetadata(mediaFile, null, null, albumName));
                }
            }
        }

        logger.info("Successfully processed {} media files with metadata", results.size());
        return results;
    }

    /**
     * Checks if a file is a media file (image or video).
     */
    private boolean isMediaFile(File file) {
        String name = file.getName().toLowerCase();

        // Skip JSON files
        if (name.endsWith(".json")) {
            return false;
        }

        return IMAGE_EXTENSIONS.stream().anyMatch(name::endsWith) ||
               VIDEO_EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    /**
     * Extracts album name from folder structure.
     * Google Takeout organizes photos in folders that represent albums.
     * If the file is not in the root takeout directory, the parent folder name is considered the album.
     */
    private String extractAlbumName(File takeoutRoot, File mediaFile) {
        File parentDir = mediaFile.getParentFile();
        if (parentDir == null || parentDir.equals(takeoutRoot)) {
            return null; // No album (file in root)
        }

        // Get the immediate parent folder name as album name
        String albumName = parentDir.getName();

        // Ignore common Google Takeout folder names that aren't albums
        if (albumName.equals("Google Photos") ||
            albumName.equals("Takeout") ||
            albumName.equals("Photos from") ||
            albumName.matches("\\d{4}") || // Year folders like "2023"
            albumName.matches("\\d{4}-\\d{2}-\\d{2}")) { // Date folders

            // Check if there's a grandparent folder that might be the album
            File grandParent = parentDir.getParentFile();
            if (grandParent != null && !grandParent.equals(takeoutRoot)) {
                String grandParentName = grandParent.getName();
                if (!grandParentName.equals("Google Photos") &&
                    !grandParentName.equals("Takeout")) {
                    return grandParentName;
                }
            }
            return null;
        }

        return albumName;
    }

    /**
     * Finds the JSON metadata file for a media file.
     * Google Takeout creates JSON files with names like "photo.jpg.json"
     */
    private File findJsonForMediaFile(File mediaFile) {
        // Most common case: filename.ext.json
        File jsonFile = new File(mediaFile.getParentFile(), mediaFile.getName() + ".json");
        if (jsonFile.exists()) {
            return jsonFile;
        }

        // Sometimes Google creates edited versions with numbers: filename(1).ext and filename.ext.json
        // Try to find any JSON file that starts with the base name
        String baseName = getBaseNameWithoutExtension(mediaFile.getName());
        File parentDir = mediaFile.getParentFile();

        File[] potentialJsonFiles = parentDir.listFiles((dir, name) ->
            name.toLowerCase().startsWith(baseName.toLowerCase()) && name.toLowerCase().endsWith(".json")
        );

        if (potentialJsonFiles != null && potentialJsonFiles.length > 0) {
            // Return the first match
            return potentialJsonFiles[0];
        }

        return null;
    }

    /**
     * Gets the base name of a file without extension.
     * e.g., "photo.jpg" -> "photo", "video(1).mp4" -> "video(1)"
     */
    private String getBaseNameWithoutExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0) {
            return filename.substring(0, lastDot);
        }
        return filename;
    }

    /**
     * Parses a Google Takeout JSON metadata file.
     */
    private GoogleTakeoutMetadata parseMetadataFile(File jsonFile) throws IOException {
        // Use UTF-8 explicitly to handle special characters correctly
        try (InputStreamReader reader = new InputStreamReader(
                new FileInputStream(jsonFile), StandardCharsets.UTF_8)) {
            return gson.fromJson(reader, GoogleTakeoutMetadata.class);
        }
    }

    /**
     * Gets statistics about the scanned files.
     */
    public static class ScanStatistics {
        public int totalFiles;
        public int imagesWithMetadata;
        public int videosWithMetadata;
        public int filesWithoutMetadata;
        public int filesWithGeoData;

        @Override
        public String toString() {
            return String.format(
                "Total: %d, Images: %d, Videos: %d, Without metadata: %d, With GPS: %d",
                totalFiles, imagesWithMetadata, videosWithMetadata, filesWithoutMetadata, filesWithGeoData
            );
        }
    }

    /**
     * Calculates statistics for a list of media files.
     */
    public ScanStatistics calculateStatistics(List<MediaFileWithMetadata> files) {
        ScanStatistics stats = new ScanStatistics();
        stats.totalFiles = files.size();

        for (MediaFileWithMetadata file : files) {
            if (file.getMetadata() == null) {
                stats.filesWithoutMetadata++;
            } else {
                if (file.isImage()) {
                    stats.imagesWithMetadata++;
                } else if (file.isVideo()) {
                    stats.videosWithMetadata++;
                }

                // Check for geo data
                GoogleTakeoutMetadata.GeoData geoData = file.getMetadata().getGeoData();
                if (geoData != null && geoData.hasValidCoordinates()) {
                    stats.filesWithGeoData++;
                }
            }
        }

        return stats;
    }
}
