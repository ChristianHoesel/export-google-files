package de.christianhoesel.googlephotos.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Detects duplicate files using various strategies (content hash, name+size, name only).
 */
public class DuplicateDetector {
    private static final Logger logger = LoggerFactory.getLogger(DuplicateDetector.class);

    private final Map<String, String> hashCache = new HashMap<>();
    private final DuplicateDetectionMode mode;

    public enum DuplicateDetectionMode {
        HASH,           // Compare file content using SHA-256 hash
        NAME_AND_SIZE,  // Compare filename and file size
        NAME_ONLY       // Compare filename only
    }

    public DuplicateDetector(DuplicateDetectionMode mode) {
        this.mode = mode;
    }

    /**
     * Checks if a file is a duplicate based on the configured detection mode.
     *
     * @param file The file to check
     * @param destDir The destination directory where duplicates might exist
     * @return true if the file is a duplicate, false otherwise
     */
    public boolean isDuplicate(File file, File destDir) {
        if (file == null || !file.exists()) {
            return false;
        }

        try {
            String key = generateKey(file);

            // Check if we've seen this key before
            if (hashCache.containsKey(key)) {
                logger.debug("Duplicate detected: {} (key: {})", file.getName(), key);
                return true;
            }

            // Check if file with same key exists in destination
            if (existsInDestination(file, destDir)) {
                logger.debug("Duplicate found in destination: {}", file.getName());
                hashCache.put(key, file.getAbsolutePath());
                return true;
            }

            // Not a duplicate, remember this key
            hashCache.put(key, file.getAbsolutePath());
            return false;

        } catch (Exception e) {
            logger.warn("Error checking for duplicate: {}", file.getName(), e);
            return false; // On error, assume not duplicate and let it process
        }
    }

    /**
     * Generates a unique key for the file based on the detection mode.
     */
    private String generateKey(File file) throws IOException {
        switch (mode) {
            case HASH:
                return calculateHash(file);

            case NAME_AND_SIZE:
                return file.getName() + "_" + file.length();

            case NAME_ONLY:
            default:
                return file.getName();
        }
    }

    /**
     * Calculates SHA-256 hash of the file content.
     */
    private String calculateHash(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            return DigestUtils.sha256Hex(fis);
        }
    }

    /**
     * Checks if a file with the same name exists in the destination directory structure.
     */
    private boolean existsInDestination(File file, File destDir) {
        // For NAME_ONLY mode, check if file with same name exists anywhere in dest
        if (mode == DuplicateDetectionMode.NAME_ONLY) {
            return findFileByName(destDir, file.getName());
        }

        // For other modes, we rely on the hash cache
        return false;
    }

    /**
     * Recursively searches for a file by name in the destination directory.
     */
    private boolean findFileByName(File dir, String fileName) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return false;
        }

        File[] files = dir.listFiles();
        if (files == null) {
            return false;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                if (findFileByName(file, fileName)) {
                    return true;
                }
            } else if (file.getName().equals(fileName)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Clears the duplicate detection cache.
     */
    public void clearCache() {
        hashCache.clear();
    }

    /**
     * Returns the number of detected duplicates.
     */
    public int getDuplicateCount() {
        return hashCache.size();
    }
}
