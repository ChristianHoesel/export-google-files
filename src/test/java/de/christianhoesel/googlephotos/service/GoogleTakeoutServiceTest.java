package de.christianhoesel.googlephotos.service;

import com.google.gson.Gson;
import de.christianhoesel.googlephotos.model.GoogleTakeoutMetadata;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for GoogleTakeoutService
 */
class GoogleTakeoutServiceTest {

    private GoogleTakeoutService service;
    private Gson gson;

    @BeforeEach
    void setUp() {
        service = new GoogleTakeoutService();
        gson = new Gson();
    }

    @Test
    void testScanEmptyDirectory(@TempDir Path tempDir) throws Exception {
        List<GoogleTakeoutService.MediaFileWithMetadata> results = 
            service.scanTakeoutDirectory(tempDir.toFile());
        
        assertTrue(results.isEmpty(), "Empty directory should return no results");
    }

    @Test
    void testScanDirectoryWithImageAndMetadata(@TempDir Path tempDir) throws Exception {
        // Create a test image file
        File imageFile = tempDir.resolve("test.jpg").toFile();
        Files.write(imageFile.toPath(), "fake image data".getBytes());

        // Create metadata JSON
        GoogleTakeoutMetadata metadata = new GoogleTakeoutMetadata();
        metadata.setTitle("test.jpg");
        metadata.setDescription("Test description");
        
        GoogleTakeoutMetadata.TimeInfo timeInfo = new GoogleTakeoutMetadata.TimeInfo();
        timeInfo.setTimestamp("1609459200"); // Jan 1, 2021
        timeInfo.setFormatted("Jan 1, 2021, 12:00:00 AM UTC");
        metadata.setPhotoTakenTime(timeInfo);

        File jsonFile = tempDir.resolve("test.jpg.json").toFile();
        try (FileWriter writer = new FileWriter(jsonFile)) {
            gson.toJson(metadata, writer);
        }

        // Scan directory
        List<GoogleTakeoutService.MediaFileWithMetadata> results = 
            service.scanTakeoutDirectory(tempDir.toFile());

        assertEquals(1, results.size(), "Should find one media file");
        
        GoogleTakeoutService.MediaFileWithMetadata result = results.get(0);
        assertEquals("test.jpg", result.getMediaFile().getName());
        assertNotNull(result.getMetadata(), "Metadata should be loaded");
        assertEquals("Test description", result.getMetadata().getDescription());
        assertTrue(result.isImage(), "Should be recognized as image");
    }

    @Test
    void testScanDirectoryWithImageWithoutMetadata(@TempDir Path tempDir) throws Exception {
        // Create a test image file without JSON
        File imageFile = tempDir.resolve("orphan.jpg").toFile();
        Files.write(imageFile.toPath(), "fake image data".getBytes());

        // Scan directory
        List<GoogleTakeoutService.MediaFileWithMetadata> results = 
            service.scanTakeoutDirectory(tempDir.toFile());

        assertEquals(1, results.size(), "Should find one media file");
        
        GoogleTakeoutService.MediaFileWithMetadata result = results.get(0);
        assertEquals("orphan.jpg", result.getMediaFile().getName());
        assertNull(result.getMetadata(), "Metadata should be null");
        assertTrue(result.isImage(), "Should be recognized as image");
    }

    @Test
    void testScanDirectoryWithVideo(@TempDir Path tempDir) throws Exception {
        // Create a test video file
        File videoFile = tempDir.resolve("video.mp4").toFile();
        Files.write(videoFile.toPath(), "fake video data".getBytes());

        // Create metadata JSON
        GoogleTakeoutMetadata metadata = new GoogleTakeoutMetadata();
        metadata.setTitle("video.mp4");
        
        File jsonFile = tempDir.resolve("video.mp4.json").toFile();
        try (FileWriter writer = new FileWriter(jsonFile)) {
            gson.toJson(metadata, writer);
        }

        // Scan directory
        List<GoogleTakeoutService.MediaFileWithMetadata> results = 
            service.scanTakeoutDirectory(tempDir.toFile());

        assertEquals(1, results.size(), "Should find one media file");
        
        GoogleTakeoutService.MediaFileWithMetadata result = results.get(0);
        assertTrue(result.isVideo(), "Should be recognized as video");
        assertFalse(result.isImage(), "Should not be recognized as image");
    }

    @Test
    void testCalculateStatistics(@TempDir Path tempDir) throws Exception {
        // Create test files
        File image1 = tempDir.resolve("img1.jpg").toFile();
        Files.write(image1.toPath(), "data".getBytes());
        
        GoogleTakeoutMetadata meta1 = new GoogleTakeoutMetadata();
        meta1.setTitle("img1.jpg");
        File json1 = tempDir.resolve("img1.jpg.json").toFile();
        try (FileWriter writer = new FileWriter(json1)) {
            gson.toJson(meta1, writer);
        }

        File image2 = tempDir.resolve("img2.jpg").toFile();
        Files.write(image2.toPath(), "data".getBytes());
        // No metadata for image2

        File video1 = tempDir.resolve("vid1.mp4").toFile();
        Files.write(video1.toPath(), "data".getBytes());
        
        GoogleTakeoutMetadata meta2 = new GoogleTakeoutMetadata();
        meta2.setTitle("vid1.mp4");
        GoogleTakeoutMetadata.GeoData geoData = new GoogleTakeoutMetadata.GeoData();
        geoData.setLatitude(48.8566);
        geoData.setLongitude(2.3522);
        meta2.setGeoData(geoData);
        File json2 = tempDir.resolve("vid1.mp4.json").toFile();
        try (FileWriter writer = new FileWriter(json2)) {
            gson.toJson(meta2, writer);
        }

        // Scan and calculate statistics
        List<GoogleTakeoutService.MediaFileWithMetadata> results = 
            service.scanTakeoutDirectory(tempDir.toFile());
        GoogleTakeoutService.ScanStatistics stats = service.calculateStatistics(results);

        assertEquals(3, stats.totalFiles);
        assertEquals(1, stats.imagesWithMetadata);
        assertEquals(1, stats.videosWithMetadata);
        assertEquals(1, stats.filesWithoutMetadata);
        assertEquals(1, stats.filesWithGeoData);
    }

    @Test
    void testInvalidDirectory() {
        File invalidDir = new File("/nonexistent/directory");
        
        assertThrows(IllegalArgumentException.class, () -> {
            service.scanTakeoutDirectory(invalidDir);
        });
    }
}
