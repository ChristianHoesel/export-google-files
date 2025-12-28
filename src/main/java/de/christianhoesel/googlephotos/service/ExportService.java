package de.christianhoesel.googlephotos.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.christianhoesel.googlephotos.model.ExportOptions;
import de.christianhoesel.googlephotos.model.PhotoItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for exporting photos and videos from Google Photos.
 */
public class ExportService {
    private static final Logger logger = LoggerFactory.getLogger(ExportService.class);
    
    private static final int BUFFER_SIZE = 8192;
    private static final DateTimeFormatter DATE_FOLDER_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM");
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    private final GooglePhotosService photosService;
    private final ExportOptions options;
    
    private AtomicInteger successCount;
    private AtomicInteger failCount;
    private AtomicInteger skipCount;

    public ExportService(GooglePhotosService photosService, ExportOptions options) {
        this.photosService = photosService;
        this.options = options;
        this.successCount = new AtomicInteger(0);
        this.failCount = new AtomicInteger(0);
        this.skipCount = new AtomicInteger(0);
    }

    /**
     * Exports all media items based on the configured options.
     * 
     * @param items List of items to export
     * @return Number of successfully exported items
     */
    public int exportItems(List<PhotoItem> items) {
        logger.info("Starting export of {} items to {}", items.size(), options.getOutputDirectory());
        
        // Create output directory
        try {
            Files.createDirectories(Paths.get(options.getOutputDirectory()));
        } catch (IOException e) {
            logger.error("Failed to create output directory", e);
            return 0;
        }
        
        int total = items.size();
        int current = 0;
        
        for (PhotoItem item : items) {
            current++;
            try {
                boolean exported = exportItem(item);
                if (exported) {
                    successCount.incrementAndGet();
                } else {
                    skipCount.incrementAndGet();
                }
            } catch (Exception e) {
                logger.error("Failed to export item: {} - {}", item.getFilename(), e.getMessage());
                failCount.incrementAndGet();
            }
            
            // Log progress every 10 items
            if (current % 10 == 0) {
                logger.info("Progress: {}/{} ({}%)", current, total, (current * 100) / total);
            }
        }
        
        logger.info("Export complete. Success: {}, Failed: {}, Skipped: {}", 
                successCount.get(), failCount.get(), skipCount.get());
        
        return successCount.get();
    }

    /**
     * Exports a single media item.
     * 
     * @param item The item to export
     * @return true if exported successfully, false if skipped
     * @throws IOException if there's an error during export
     */
    public boolean exportItem(PhotoItem item) throws IOException {
        // Determine output path
        Path outputPath = determineOutputPath(item);
        
        // Check if file already exists
        if (Files.exists(outputPath)) {
            logger.debug("Skipping existing file: {}", outputPath);
            return false;
        }
        
        // Create parent directories
        Files.createDirectories(outputPath.getParent());
        
        // Download the file
        String downloadUrl = photosService.getDownloadUrl(item);
        downloadFile(downloadUrl, outputPath);
        
        // Write metadata file if enabled
        if (options.isWriteMetadataFile()) {
            writeMetadataFile(item, outputPath);
        }
        
        logger.debug("Exported: {}", outputPath);
        return true;
    }

    /**
     * Determines the output path for a media item based on export options.
     * 
     * @param item The media item
     * @return Path where the file should be saved
     */
    private Path determineOutputPath(PhotoItem item) {
        StringBuilder pathBuilder = new StringBuilder(options.getOutputDirectory());
        
        // Add album folder if enabled
        if (options.isCreateAlbumFolders() && !item.getAlbums().isEmpty()) {
            String albumName = sanitizeFilename(item.getAlbums().get(0));
            pathBuilder.append(File.separator).append(albumName);
        }
        
        // Add date folder if enabled
        if (options.isCreateDateFolders() && item.getCreationTime() != null) {
            String dateFolder = item.getCreationTime().format(DATE_FOLDER_FORMAT);
            pathBuilder.append(File.separator).append(dateFolder);
        }
        
        // Add filename (ensure uniqueness by prepending ID if needed)
        String filename = sanitizeFilename(item.getFilename());
        pathBuilder.append(File.separator).append(filename);
        
        return Paths.get(pathBuilder.toString());
    }

    private static final String[] ALLOWED_DOWNLOAD_HOSTS = {
        "lh3.googleusercontent.com",
        "lh4.googleusercontent.com",
        "lh5.googleusercontent.com",
        "lh6.googleusercontent.com",
        "video.googleusercontent.com"
    };
    
    /**
     * Downloads a file from a URL.
     * Only allows downloads from trusted Google domains.
     * 
     * @param urlString The URL to download from
     * @param outputPath The path to save the file to
     * @throws IOException if download fails or URL is not from trusted domain
     */
    private void downloadFile(String urlString, Path outputPath) throws IOException {
        HttpURLConnection connection = null;
        try {
            URI uri = URI.create(urlString);
            
            // Validate that URL is from trusted Google domains
            String host = uri.getHost();
            if (host == null || !isAllowedHost(host)) {
                throw new IOException("Download URL is not from a trusted Google domain: " + host);
            }
            
            // Only allow HTTPS for security
            String scheme = uri.getScheme();
            if (!"https".equalsIgnoreCase(scheme)) {
                throw new IOException("Only HTTPS URLs are allowed for downloads");
            }
            
            connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(60000);
            // Disable automatic redirects to prevent redirect-based attacks
            connection.setInstanceFollowRedirects(false);
            
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("Server returned response code: " + responseCode);
            }
            
            try (InputStream in = new BufferedInputStream(connection.getInputStream());
                 OutputStream out = new BufferedOutputStream(Files.newOutputStream(outputPath))) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
    
    /**
     * Checks if the host is in the list of allowed download hosts.
     * 
     * @param host The host to check
     * @return true if the host is allowed
     */
    private boolean isAllowedHost(String host) {
        for (String allowed : ALLOWED_DOWNLOAD_HOSTS) {
            if (host.equalsIgnoreCase(allowed) || host.endsWith("." + allowed)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Writes a JSON metadata file alongside the media file.
     * 
     * @param item The media item
     * @param mediaPath The path of the downloaded media file
     * @throws IOException if writing fails
     */
    private void writeMetadataFile(PhotoItem item, Path mediaPath) throws IOException {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("id", item.getId());
        metadata.put("filename", item.getFilename());
        metadata.put("mimeType", item.getMimeType());
        metadata.put("description", item.getDescription());
        metadata.put("creationTime", item.getCreationTime() != null ? 
                item.getCreationTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null);
        metadata.put("width", item.getWidth());
        metadata.put("height", item.getHeight());
        metadata.put("albums", item.getAlbums());
        metadata.put("people", item.getPeople());
        
        if (item.getMetadata() != null) {
            Map<String, Object> photoMetadata = new HashMap<>();
            photoMetadata.put("cameraMake", item.getMetadata().getCameraMake());
            photoMetadata.put("cameraModel", item.getMetadata().getCameraModel());
            photoMetadata.put("focalLength", item.getMetadata().getFocalLength());
            photoMetadata.put("apertureFNumber", item.getMetadata().getApertureFNumber());
            photoMetadata.put("isoEquivalent", item.getMetadata().getIsoEquivalent());
            photoMetadata.put("exposureTime", item.getMetadata().getExposureTime());
            
            if (item.getMetadata().hasLocation()) {
                photoMetadata.put("latitude", item.getMetadata().getLatitude());
                photoMetadata.put("longitude", item.getMetadata().getLongitude());
                photoMetadata.put("locationName", item.getMetadata().getLocationName());
            }
            
            if (item.isVideo()) {
                photoMetadata.put("fps", item.getMetadata().getFps());
                photoMetadata.put("videoStatus", item.getMetadata().getVideoStatus());
            }
            
            metadata.put("metadata", photoMetadata);
        }
        
        // Write JSON file
        String jsonFilename = mediaPath.toString() + ".json";
        try (Writer writer = Files.newBufferedWriter(Paths.get(jsonFilename))) {
            gson.toJson(metadata, writer);
        }
    }

    /**
     * Sanitizes a filename by removing or replacing invalid characters.
     * Prevents directory traversal attacks and invalid filenames.
     * 
     * @param filename The original filename
     * @return Sanitized filename
     */
    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "unnamed_" + System.currentTimeMillis();
        }
        
        String sanitized = filename;
        
        // Remove directory traversal sequences
        sanitized = sanitized.replace("..", "_");
        
        // Replace path separators and invalid characters
        sanitized = sanitized.replaceAll("[\\\\/:*?\"<>|]", "_");
        
        // Remove leading dots (hidden files on Unix, potential issues)
        while (sanitized.startsWith(".")) {
            sanitized = sanitized.substring(1);
        }
        
        // Check for Windows reserved names (case-insensitive)
        String baseName = sanitized;
        int dotIndex = sanitized.lastIndexOf('.');
        if (dotIndex > 0) {
            baseName = sanitized.substring(0, dotIndex);
        }
        String[] reservedNames = {"CON", "PRN", "AUX", "NUL", 
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"};
        for (String reserved : reservedNames) {
            if (baseName.equalsIgnoreCase(reserved)) {
                sanitized = "_" + sanitized;
                break;
            }
        }
        
        // Ensure reasonable filename length (max 200 characters to leave room for paths)
        if (sanitized.length() > 200) {
            String extension = "";
            if (dotIndex > 0 && dotIndex < sanitized.length() - 1) {
                extension = sanitized.substring(dotIndex);
            }
            sanitized = sanitized.substring(0, 200 - extension.length()) + extension;
        }
        
        // If empty after sanitization, use default name
        if (sanitized.isEmpty()) {
            return "unnamed_" + System.currentTimeMillis();
        }
        
        return sanitized;
    }

    /**
     * Gets the export statistics.
     * 
     * @return Map containing success, fail, and skip counts
     */
    public Map<String, Integer> getStatistics() {
        Map<String, Integer> stats = new HashMap<>();
        stats.put("success", successCount.get());
        stats.put("failed", failCount.get());
        stats.put("skipped", skipCount.get());
        return stats;
    }

    /**
     * Resets the export statistics.
     */
    public void resetStatistics() {
        successCount.set(0);
        failCount.set(0);
        skipCount.set(0);
    }
}
