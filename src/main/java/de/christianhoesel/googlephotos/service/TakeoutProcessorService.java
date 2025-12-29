package de.christianhoesel.googlephotos.service;

import de.christianhoesel.googlephotos.model.GoogleTakeoutMetadata;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.common.ImageMetadata;
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata;
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter;
import org.apache.commons.imaging.formats.tiff.TiffImageMetadata;
import org.apache.commons.imaging.formats.tiff.constants.*;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfo;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputDirectory;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service to process Google Takeout files, add metadata, and organize into monthly folders.
 */
public class TakeoutProcessorService {
    private static final Logger logger = LoggerFactory.getLogger(TakeoutProcessorService.class);
    
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger errorCount = new AtomicInteger(0);
    private final AtomicInteger skippedCount = new AtomicInteger(0);
    
    /**
     * Processing options.
     */
    public static class ProcessingOptions {
        private File outputDirectory;
        private boolean copyFiles = true; // true = copy, false = move
        private boolean addMetadata = true;
        private boolean organizeByMonth = true;
        
        public File getOutputDirectory() {
            return outputDirectory;
        }
        
        public void setOutputDirectory(File outputDirectory) {
            this.outputDirectory = outputDirectory;
        }
        
        public boolean isCopyFiles() {
            return copyFiles;
        }
        
        public void setCopyFiles(boolean copyFiles) {
            this.copyFiles = copyFiles;
        }
        
        public boolean isAddMetadata() {
            return addMetadata;
        }
        
        public void setAddMetadata(boolean addMetadata) {
            this.addMetadata = addMetadata;
        }
        
        public boolean isOrganizeByMonth() {
            return organizeByMonth;
        }
        
        public void setOrganizeByMonth(boolean organizeByMonth) {
            this.organizeByMonth = organizeByMonth;
        }
    }
    
    /**
     * Processes a single media file with metadata.
     * 
     * @param fileWithMetadata The media file with its metadata
     * @param options Processing options
     * @return The destination file
     * @throws IOException if processing fails
     */
    public File processMediaFile(
        GoogleTakeoutService.MediaFileWithMetadata fileWithMetadata,
        ProcessingOptions options
    ) throws IOException {
        File mediaFile = fileWithMetadata.getMediaFile();
        GoogleTakeoutMetadata metadata = fileWithMetadata.getMetadata();
        
        logger.debug("Processing: {}", mediaFile.getName());
        
        // Determine destination path
        File destDir = determineDestinationDirectory(metadata, options);
        destDir.mkdirs();
        
        File destFile = new File(destDir, mediaFile.getName());
        
        // Handle duplicate filenames
        destFile = ensureUniqueFilename(destFile);
        
        // For JPEG images with metadata, write EXIF data
        if (options.isAddMetadata() && 
            metadata != null && 
            fileWithMetadata.isImage() && 
            isJpeg(mediaFile)) {
            
            try {
                writeExifMetadata(mediaFile, destFile, metadata);
                logger.debug("Added EXIF metadata to: {}", destFile.getName());
            } catch (Exception e) {
                logger.warn("Failed to write EXIF for {}: {}. Copying without metadata.", 
                    mediaFile.getName(), e.getMessage());
                // Fall back to simple copy
                copyFile(mediaFile, destFile);
            }
        } else {
            // For videos or files without metadata, just copy
            if (options.isCopyFiles()) {
                copyFile(mediaFile, destFile);
            } else {
                moveFile(mediaFile, destFile);
            }
        }
        
        return destFile;
    }
    
    /**
     * Determines the destination directory based on metadata and options.
     */
    private File determineDestinationDirectory(
        GoogleTakeoutMetadata metadata, 
        ProcessingOptions options
    ) {
        File baseDir = options.getOutputDirectory();
        
        if (!options.isOrganizeByMonth()) {
            return baseDir;
        }
        
        // Try to get date from metadata
        LocalDateTime dateTime = extractDateTime(metadata);
        
        if (dateTime != null) {
            // Create YYYY/MM folder structure
            String year = String.valueOf(dateTime.getYear());
            String month = String.format("%02d", dateTime.getMonthValue());
            return new File(new File(baseDir, year), month);
        } else {
            // No date available, put in "Unknown" folder
            return new File(baseDir, "Unknown_Date");
        }
    }
    
    /**
     * Extracts date/time from Google Takeout metadata.
     * Prefers photoTakenTime over creationTime.
     */
    private LocalDateTime extractDateTime(GoogleTakeoutMetadata metadata) {
        if (metadata == null) {
            return null;
        }
        
        // Try photoTakenTime first
        if (metadata.getPhotoTakenTime() != null) {
            long timestamp = metadata.getPhotoTakenTime().getTimestampAsLong();
            if (timestamp > 0) {
                return LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(timestamp),
                    ZoneId.systemDefault()
                );
            }
        }
        
        // Fall back to creationTime
        if (metadata.getCreationTime() != null) {
            long timestamp = metadata.getCreationTime().getTimestampAsLong();
            if (timestamp > 0) {
                return LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(timestamp),
                    ZoneId.systemDefault()
                );
            }
        }
        
        return null;
    }
    
    /**
     * Writes EXIF metadata to a JPEG file.
     */
    private void writeExifMetadata(
        File sourceFile, 
        File destFile, 
        GoogleTakeoutMetadata metadata
    ) throws Exception {
        
        // Read existing metadata
        TiffOutputSet outputSet = null;
        try {
            ImageMetadata imageMetadata = Imaging.getMetadata(sourceFile);
            if (imageMetadata instanceof JpegImageMetadata) {
                JpegImageMetadata jpegMetadata = (JpegImageMetadata) imageMetadata;
                TiffImageMetadata exif = jpegMetadata.getExif();
                if (exif != null) {
                    outputSet = exif.getOutputSet();
                }
            }
        } catch (Exception e) {
            logger.debug("No existing EXIF data in {}", sourceFile.getName());
        }
        
        // Create new output set if none exists
        if (outputSet == null) {
            outputSet = new TiffOutputSet();
        }
        
        // Get or create EXIF directory
        TiffOutputDirectory exifDirectory = outputSet.getOrCreateExifDirectory();
        TiffOutputDirectory rootDirectory = outputSet.getOrCreateRootDirectory();
        
        // Write date/time
        LocalDateTime dateTime = extractDateTime(metadata);
        if (dateTime != null) {
            String exifDateTime = dateTime.format(DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss"));
            exifDirectory.removeField(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL);
            exifDirectory.add(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL, exifDateTime);
            exifDirectory.removeField(ExifTagConstants.EXIF_TAG_DATE_TIME_DIGITIZED);
            exifDirectory.add(ExifTagConstants.EXIF_TAG_DATE_TIME_DIGITIZED, exifDateTime);
        }
        
        // Write GPS coordinates
        if (metadata.getGeoDataExif() != null && metadata.getGeoDataExif().hasValidCoordinates()) {
            GoogleTakeoutMetadata.GeoData geoData = metadata.getGeoDataExif();
            
            // For now, skip GPS writing as the alpha version of commons-imaging has limited support
            // The date/time metadata is the most important for organizing files
            logger.debug("GPS data available but skipped for {}", sourceFile.getName());
        }
        
        // Write description if available
        if (metadata.getDescription() != null && !metadata.getDescription().trim().isEmpty()) {
            rootDirectory.removeField(TiffTagConstants.TIFF_TAG_IMAGE_DESCRIPTION);
            rootDirectory.add(TiffTagConstants.TIFF_TAG_IMAGE_DESCRIPTION, metadata.getDescription());
        }
        
        // Write people/keywords if available
        // Note: EXIF doesn't have a standard "people" field. We append people names to the description
        // or store them in the Software field as a workaround
        if (metadata.getPeople() != null && !metadata.getPeople().isEmpty()) {
            StringBuilder peopleStr = new StringBuilder();
            for (int i = 0; i < metadata.getPeople().size(); i++) {
                if (i > 0) peopleStr.append(", ");
                peopleStr.append(metadata.getPeople().get(i).getName());
            }
            
            // Store people in the Software field (not ideal but works for preservation)
            String peopleTag = "People: " + peopleStr;
            try {
                rootDirectory.removeField(TiffTagConstants.TIFF_TAG_SOFTWARE);
                rootDirectory.add(TiffTagConstants.TIFF_TAG_SOFTWARE, peopleTag);
                logger.debug("Added {} people to EXIF for {}", metadata.getPeople().size(), sourceFile.getName());
            } catch (Exception e) {
                logger.warn("Could not write people to EXIF for {}: {}", sourceFile.getName(), e.getMessage());
            }
        }
        
        // Write title if available and different from filename
        if (metadata.getTitle() != null && !metadata.getTitle().trim().isEmpty() 
            && !metadata.getTitle().equals(sourceFile.getName())) {
            // Store title in DocumentName field
            try {
                rootDirectory.removeField(TiffTagConstants.TIFF_TAG_DOCUMENT_NAME);
                rootDirectory.add(TiffTagConstants.TIFF_TAG_DOCUMENT_NAME, metadata.getTitle());
            } catch (Exception e) {
                logger.debug("Could not write title to EXIF: {}", e.getMessage());
            }
        }
        
        // Write to destination file
        try (FileOutputStream fos = new FileOutputStream(destFile);
             BufferedOutputStream bos = new BufferedOutputStream(fos)) {
            
            new ExifRewriter().updateExifMetadataLossless(sourceFile, bos, outputSet);
        }
    }
    
    /**
     * Checks if a file is a JPEG image.
     */
    private boolean isJpeg(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".jpg") || name.endsWith(".jpeg");
    }
    
    /**
     * Ensures the filename is unique by appending a number if needed.
     * Limits the number of attempts to avoid infinite loops.
     */
    private File ensureUniqueFilename(File file) {
        if (!file.exists()) {
            return file;
        }
        
        String name = file.getName();
        String baseName;
        String extension;
        
        int lastDot = name.lastIndexOf('.');
        if (lastDot > 0) {
            baseName = name.substring(0, lastDot);
            extension = name.substring(lastDot);
        } else {
            baseName = name;
            extension = "";
        }
        
        int counter = 1;
        File uniqueFile;
        final int MAX_ATTEMPTS = 10000; // Prevent infinite loops
        
        do {
            uniqueFile = new File(file.getParentFile(), baseName + "_" + counter + extension);
            counter++;
            
            if (counter > MAX_ATTEMPTS) {
                // Use timestamp-based naming as fallback
                String timestamp = String.valueOf(System.currentTimeMillis());
                uniqueFile = new File(file.getParentFile(), baseName + "_" + timestamp + extension);
                break;
            }
        } while (uniqueFile.exists());
        
        return uniqueFile;
    }
    
    /**
     * Copies a file.
     */
    private void copyFile(File source, File dest) throws IOException {
        Files.copy(source.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
    
    /**
     * Moves a file.
     */
    private void moveFile(File source, File dest) throws IOException {
        Files.move(source.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
    
    /**
     * Processes all files in a list.
     */
    public void processAllFiles(
        java.util.List<GoogleTakeoutService.MediaFileWithMetadata> files,
        ProcessingOptions options,
        ProgressCallback callback
    ) {
        successCount.set(0);
        errorCount.set(0);
        skippedCount.set(0);
        
        int total = files.size();
        int current = 0;
        
        for (GoogleTakeoutService.MediaFileWithMetadata file : files) {
            current++;
            
            if (callback != null) {
                callback.onProgress(current, total, file.getMediaFile().getName());
            }
            
            try {
                processMediaFile(file, options);
                successCount.incrementAndGet();
            } catch (Exception e) {
                logger.error("Failed to process {}: {}", file.getMediaFile().getName(), e.getMessage());
                errorCount.incrementAndGet();
            }
        }
        
        if (callback != null) {
            callback.onComplete(successCount.get(), errorCount.get(), skippedCount.get());
        }
    }
    
    /**
     * Callback interface for progress updates.
     */
    public interface ProgressCallback {
        void onProgress(int current, int total, String currentFile);
        void onComplete(int success, int errors, int skipped);
    }
    
    /**
     * Gets processing statistics.
     */
    public int getSuccessCount() {
        return successCount.get();
    }
    
    public int getErrorCount() {
        return errorCount.get();
    }
    
    public int getSkippedCount() {
        return skippedCount.get();
    }
}
