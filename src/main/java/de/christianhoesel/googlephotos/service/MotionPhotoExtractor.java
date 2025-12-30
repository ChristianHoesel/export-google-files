package de.christianhoesel.googlephotos.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.internal.xmp.XMPException;
import com.adobe.internal.xmp.XMPMeta;
import com.adobe.internal.xmp.XMPMetaFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;

/**
 * Extracts video components from Google Motion Photos (photos with embedded video).
 * 
 * Motion Photos can have video data embedded in two ways:
 * 1. Motion Photo V1: Video at the end of JPEG with a trailer
 * 2. Motion Photo V2: Video location specified in XMP metadata
 */
public class MotionPhotoExtractor {
    private static final Logger logger = LoggerFactory.getLogger(MotionPhotoExtractor.class);
    
    // Motion Photo V1 trailer marker
    private static final byte[] MOTION_PHOTO_TRAILER = "MotionPhoto_Data".getBytes();
    private static final int TRAILER_SIZE = 4 + 4; // offset (4 bytes) + length (4 bytes)
    
    /**
     * Checks if a file is a Motion Photo.
     */
    public boolean isMotionPhoto(File file) {
        if (!isJpeg(file)) {
            return false;
        }
        
        try {
            // Check for Motion Photo markers in XMP
            if (hasMotionPhotoXmp(file)) {
                return true;
            }
            
            // Check for Motion Photo V1 trailer
            if (hasMotionPhotoTrailer(file)) {
                return true;
            }
            
        } catch (Exception e) {
            logger.debug("Error checking Motion Photo markers: {}", e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Extracts the video component from a Motion Photo and saves it as a separate file.
     * 
     * @param motionPhotoFile The Motion Photo file
     * @return The extracted video file, or null if extraction failed
     */
    public File extractVideo(File motionPhotoFile) {
        if (!isMotionPhoto(motionPhotoFile)) {
            return null;
        }
        
        try {
            // Try XMP-based extraction first (Motion Photo V2)
            File videoFile = extractVideoFromXmp(motionPhotoFile);
            if (videoFile != null) {
                logger.info("Extracted Motion Photo video (XMP): {}", videoFile.getName());
                return videoFile;
            }
            
            // Try trailer-based extraction (Motion Photo V1)
            videoFile = extractVideoFromTrailer(motionPhotoFile);
            if (videoFile != null) {
                logger.info("Extracted Motion Photo video (trailer): {}", videoFile.getName());
                return videoFile;
            }
            
        } catch (Exception e) {
            logger.warn("Failed to extract video from Motion Photo {}: {}", motionPhotoFile.getName(), e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Checks if file has Motion Photo markers in XMP metadata.
     */
    private boolean hasMotionPhotoXmp(File file) {
        try {
            // Read file and look for GCamera namespace in XMP
            byte[] fileBytes = Files.readAllBytes(file.toPath());
            String content = new String(fileBytes, "UTF-8");
            
            // Look for GCamera:MicroVideo or GCamera:MotionPhoto markers
            return content.contains("GCamera:MicroVideo") || 
                   content.contains("GCamera:MotionPhoto") ||
                   content.contains("GCamera:MicroVideoOffset");
                   
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Checks if file has Motion Photo V1 trailer.
     */
    private boolean hasMotionPhotoTrailer(File file) {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            if (raf.length() < MOTION_PHOTO_TRAILER.length + TRAILER_SIZE) {
                return false;
            }
            
            // Check for trailer marker at the end
            raf.seek(raf.length() - MOTION_PHOTO_TRAILER.length - TRAILER_SIZE);
            byte[] marker = new byte[MOTION_PHOTO_TRAILER.length];
            raf.readFully(marker);
            
            return java.util.Arrays.equals(marker, MOTION_PHOTO_TRAILER);
            
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Extracts video using XMP metadata (Motion Photo V2).
     */
    private File extractVideoFromXmp(File motionPhotoFile) throws IOException {
        try {
            // Parse XMP to find video offset and length
            byte[] fileBytes = Files.readAllBytes(motionPhotoFile.toPath());
            String content = new String(fileBytes, "UTF-8");
            
            // Look for XMP packet with GCamera namespace
            int xmpStart = content.indexOf("<x:xmpmeta");
            int xmpEnd = content.indexOf("</x:xmpmeta>") + "</x:xmpmeta>".length();
            
            if (xmpStart < 0 || xmpEnd < 0) {
                return null;
            }
            
            String xmpString = content.substring(xmpStart, xmpEnd);
            XMPMeta xmpMeta = XMPMetaFactory.parseFromString(xmpString);
            
            String gcameraNS = "http://ns.google.com/photos/1.0/camera/";
            
            // Try to get offset and length
            try {
                int offset = Integer.parseInt(xmpMeta.getPropertyString(gcameraNS, "MicroVideoOffset"));
                
                // Video is at the end of file, starting at offset from end
                int videoStart = fileBytes.length - offset;
                
                if (videoStart < 0 || videoStart >= fileBytes.length) {
                    logger.warn("Invalid video offset in Motion Photo: {}", offset);
                    return null;
                }
                
                // Create output file
                File videoFile = createVideoFileName(motionPhotoFile);
                
                // Extract video bytes
                try (FileOutputStream fos = new FileOutputStream(videoFile)) {
                    fos.write(fileBytes, videoStart, offset);
                }
                
                return videoFile;
                
            } catch (XMPException | NumberFormatException e) {
                logger.debug("Could not parse XMP video offset: {}", e.getMessage());
                return null;
            }
            
        } catch (Exception e) {
            logger.debug("XMP-based extraction failed: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Extracts video using trailer marker (Motion Photo V1).
     */
    private File extractVideoFromTrailer(File motionPhotoFile) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(motionPhotoFile, "r")) {
            long fileLength = raf.length();
            
            // Read trailer
            raf.seek(fileLength - TRAILER_SIZE);
            int offset = raf.readInt();
            int length = raf.readInt();
            
            // Validate
            if (offset <= 0 || length <= 0 || offset + length > fileLength) {
                logger.warn("Invalid trailer data in Motion Photo");
                return null;
            }
            
            // Extract video
            File videoFile = createVideoFileName(motionPhotoFile);
            byte[] videoData = new byte[length];
            
            raf.seek(offset);
            raf.readFully(videoData);
            
            try (FileOutputStream fos = new FileOutputStream(videoFile)) {
                fos.write(videoData);
            }
            
            return videoFile;
            
        } catch (Exception e) {
            logger.debug("Trailer-based extraction failed: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Creates the output video filename based on the photo filename.
     */
    private File createVideoFileName(File photoFile) {
        String name = photoFile.getName();
        String baseName;
        
        int lastDot = name.lastIndexOf('.');
        if (lastDot > 0) {
            baseName = name.substring(0, lastDot);
        } else {
            baseName = name;
        }
        
        return new File(photoFile.getParentFile(), baseName + ".mp4");
    }
    
    /**
     * Checks if file is a JPEG.
     */
    private boolean isJpeg(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".jpg") || name.endsWith(".jpeg");
    }
}
