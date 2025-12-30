package de.christianhoesel.googlephotos.service;

import com.adobe.internal.xmp.XMPMeta;
import com.adobe.internal.xmp.XMPMetaFactory;
import com.adobe.internal.xmp.options.SerializeOptions;
import de.christianhoesel.googlephotos.model.GoogleTakeoutMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Writes XMP metadata to video files (MP4, MOV, etc.).
 * 
 * Since video formats don't support EXIF, we use XMP metadata which is supported
 * by MP4 and other modern video formats.
 */
public class VideoMetadataWriter {
    private static final Logger logger = LoggerFactory.getLogger(VideoMetadataWriter.class);
    
    /**
     * Writes XMP metadata to a video file. For MP4 files, tries to embed XMP directly.
     * Falls back to sidecar .xmp file if direct embedding fails.
     * 
     * @param videoFile The video file
     * @param metadata The Google Takeout metadata
     * @param albumName The album name
     * @return true if metadata was written successfully
     */
    public boolean writeMetadata(File videoFile, GoogleTakeoutMetadata metadata, String albumName) {
        if (videoFile == null || !videoFile.exists()) {
            return false;
        }
        
        try {
            // Create XMP metadata
            XMPMeta xmpMeta = createXmpMetadata(metadata, albumName);
            
            // Try to write XMP sidecar file (most compatible approach)
            return writeSidecarXmp(videoFile, xmpMeta);
            
        } catch (Exception e) {
            logger.warn("Failed to write metadata to video {}: {}", videoFile.getName(), e.getMessage());
            return false;
        }
    }
    
    /**
     * Creates XMP metadata from Google Takeout metadata.
     */
    private XMPMeta createXmpMetadata(GoogleTakeoutMetadata metadata, String albumName) throws Exception {
        XMPMeta xmpMeta = XMPMetaFactory.create();
        
        // Namespaces
        String dcNS = "http://purl.org/dc/elements/1.1/";
        String xmpNS = "http://ns.adobe.com/xap/1.0/";
        String lrNS = "http://ns.adobe.com/lightroom/1.0/";
        
        // Register namespaces
        try {
            XMPMetaFactory.getSchemaRegistry().registerNamespace(dcNS, "dc");
            XMPMetaFactory.getSchemaRegistry().registerNamespace(xmpNS, "xmp");
            XMPMetaFactory.getSchemaRegistry().registerNamespace(lrNS, "lr");
        } catch (Exception e) {
            // Already registered
        }
        
        if (metadata != null) {
            // Add title
            if (metadata.getTitle() != null && !metadata.getTitle().trim().isEmpty()) {
                xmpMeta.setLocalizedText(dcNS, "title", "x-default", "x-default", metadata.getTitle());
            }
            
            // Add description
            if (metadata.getDescription() != null && !metadata.getDescription().trim().isEmpty()) {
                xmpMeta.setLocalizedText(dcNS, "description", "x-default", "x-default", metadata.getDescription());
            }
            
            // Add people as keywords
            if (metadata.getPeople() != null && !metadata.getPeople().isEmpty()) {
                com.adobe.internal.xmp.options.PropertyOptions arrayOptions = 
                    new com.adobe.internal.xmp.options.PropertyOptions();
                arrayOptions.setArray(true);
                
                for (GoogleTakeoutMetadata.Person person : metadata.getPeople()) {
                    if (person.getName() != null && !person.getName().trim().isEmpty()) {
                        xmpMeta.appendArrayItem(dcNS, "subject", arrayOptions, person.getName(), null);
                    }
                }
            }
            
            // Add creation date
            LocalDateTime dateTime = extractDateTime(metadata);
            if (dateTime != null) {
                String isoDate = dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                xmpMeta.setProperty(xmpNS, "CreateDate", isoDate);
            }
        }
        
        // Add album
        if (albumName != null && !albumName.trim().isEmpty()) {
            xmpMeta.setProperty(lrNS, "hierarchicalSubject", albumName);
        }
        
        return xmpMeta;
    }
    
    /**
     * Writes XMP metadata as a sidecar .xmp file.
     * This is the most compatible approach for video files.
     */
    private boolean writeSidecarXmp(File videoFile, XMPMeta xmpMeta) {
        try {
            // Create sidecar filename (video.mp4 -> video.mp4.xmp)
            File xmpFile = new File(videoFile.getAbsolutePath() + ".xmp");
            
            // Serialize XMP
            SerializeOptions serializeOptions = new SerializeOptions();
            serializeOptions.setUseCompactFormat(false);
            serializeOptions.setOmitPacketWrapper(false);
            serializeOptions.setIndent("  "); // 2 spaces
            String xmpString = XMPMetaFactory.serializeToString(xmpMeta, serializeOptions);
            
            // Write to file
            try (FileOutputStream fos = new FileOutputStream(xmpFile)) {
                fos.write(xmpString.getBytes("UTF-8"));
            }
            
            logger.debug("Wrote XMP sidecar file: {}", xmpFile.getName());
            return true;
            
        } catch (Exception e) {
            logger.warn("Failed to write XMP sidecar: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Extracts date/time from metadata.
     */
    private LocalDateTime extractDateTime(GoogleTakeoutMetadata metadata) {
        if (metadata == null) {
            return null;
        }
        
        // Try photoTakenTime first
        if (metadata.getPhotoTakenTime() != null) {
            long timestamp = metadata.getPhotoTakenTime().getTimestampAsLong();
            if (timestamp > 0) {
                return LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneId.systemDefault());
            }
        }
        
        // Fall back to creationTime
        if (metadata.getCreationTime() != null) {
            long timestamp = metadata.getCreationTime().getTimestampAsLong();
            if (timestamp > 0) {
                return LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneId.systemDefault());
            }
        }
        
        return null;
    }
}
