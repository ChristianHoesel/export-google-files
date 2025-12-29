package de.christianhoesel.googlephotos.service;

import com.google.gson.Gson;
import de.christianhoesel.googlephotos.model.GoogleTakeoutMetadata;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.common.ImageMetadata;
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata;
import org.apache.commons.imaging.formats.tiff.TiffImageMetadata;
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TakeoutProcessorService EXIF writing functionality
 */
class TakeoutProcessorServiceTest {

    private TakeoutProcessorService service;
    private Gson gson;

    @BeforeEach
    void setUp() {
        service = new TakeoutProcessorService();
        gson = new Gson();
    }

    @Test
    void testProcessMediaFileWithPeople(@TempDir Path tempDir) throws Exception {
        // Create a simple test JPEG image
        File sourceImage = tempDir.resolve("test.jpg").toFile();
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(img, "jpg", sourceImage);

        // Create metadata with people
        GoogleTakeoutMetadata metadata = new GoogleTakeoutMetadata();
        metadata.setTitle("test.jpg");
        metadata.setDescription("Test photo with people");
        
        GoogleTakeoutMetadata.TimeInfo timeInfo = new GoogleTakeoutMetadata.TimeInfo();
        timeInfo.setTimestamp("1609459200"); // Jan 1, 2021
        metadata.setPhotoTakenTime(timeInfo);

        // Add people
        List<GoogleTakeoutMetadata.Person> people = new ArrayList<>();
        GoogleTakeoutMetadata.Person person1 = new GoogleTakeoutMetadata.Person();
        person1.setName("Alice");
        people.add(person1);
        GoogleTakeoutMetadata.Person person2 = new GoogleTakeoutMetadata.Person();
        person2.setName("Bob");
        people.add(person2);
        metadata.setPeople(people);

        File jsonFile = tempDir.resolve("test.jpg.json").toFile();
        try (FileWriter writer = new FileWriter(jsonFile)) {
            gson.toJson(metadata, writer);
        }

        // Create MediaFileWithMetadata
        GoogleTakeoutService.MediaFileWithMetadata fileWithMetadata = 
            new GoogleTakeoutService.MediaFileWithMetadata(sourceImage, jsonFile, metadata);

        // Process the file
        File outputDir = tempDir.resolve("output").toFile();
        outputDir.mkdirs();
        
        TakeoutProcessorService.ProcessingOptions options = 
            new TakeoutProcessorService.ProcessingOptions();
        options.setOutputDirectory(outputDir);
        options.setOrganizeByMonth(true);
        options.setAddMetadata(true);
        options.setCopyFiles(true);

        File resultFile = service.processMediaFile(fileWithMetadata, options);

        // Verify the file was created
        assertTrue(resultFile.exists(), "Output file should exist");

        // Read EXIF from the output file and verify metadata was written
        ImageMetadata imageMetadata = Imaging.getMetadata(resultFile);
        assertNotNull(imageMetadata, "Should have metadata");
        
        if (imageMetadata instanceof JpegImageMetadata) {
            JpegImageMetadata jpegMetadata = (JpegImageMetadata) imageMetadata;
            TiffImageMetadata exif = jpegMetadata.getExif();
            
            if (exif != null) {
                // Check for description
                String[] descriptionArray = exif.getFieldValue(TiffTagConstants.TIFF_TAG_IMAGE_DESCRIPTION);
                if (descriptionArray != null && descriptionArray.length > 0) {
                    assertEquals("Test photo with people", descriptionArray[0]);
                }
                
                // Check for people in Software field
                String[] softwareArray = exif.getFieldValue(TiffTagConstants.TIFF_TAG_SOFTWARE);
                if (softwareArray != null && softwareArray.length > 0) {
                    String software = softwareArray[0];
                    assertNotNull(software, "Software field should contain people data");
                    assertTrue(software.contains("Alice"), "Should contain Alice");
                    assertTrue(software.contains("Bob"), "Should contain Bob");
                }
            }
        }
    }

    @Test
    void testDetermineDestinationDirectory(@TempDir Path tempDir) throws Exception {
        // Create a test file
        File sourceImage = tempDir.resolve("test.jpg").toFile();
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(img, "jpg", sourceImage);

        // Create metadata with date
        GoogleTakeoutMetadata metadata = new GoogleTakeoutMetadata();
        GoogleTakeoutMetadata.TimeInfo timeInfo = new GoogleTakeoutMetadata.TimeInfo();
        timeInfo.setTimestamp("1609459200"); // Jan 1, 2021 00:00:00 UTC
        metadata.setPhotoTakenTime(timeInfo);

        File jsonFile = tempDir.resolve("test.jpg.json").toFile();
        try (FileWriter writer = new FileWriter(jsonFile)) {
            gson.toJson(metadata, writer);
        }

        GoogleTakeoutService.MediaFileWithMetadata fileWithMetadata = 
            new GoogleTakeoutService.MediaFileWithMetadata(sourceImage, jsonFile, metadata);

        File outputDir = tempDir.resolve("output").toFile();
        outputDir.mkdirs();
        
        TakeoutProcessorService.ProcessingOptions options = 
            new TakeoutProcessorService.ProcessingOptions();
        options.setOutputDirectory(outputDir);
        options.setOrganizeByMonth(true);
        options.setAddMetadata(true);

        File resultFile = service.processMediaFile(fileWithMetadata, options);

        // Check that file was organized into 2021/01 folder
        String path = resultFile.getAbsolutePath();
        assertTrue(path.contains("2021"), "Path should contain year 2021");
        assertTrue(path.contains("01"), "Path should contain month 01");
    }

    @Test
    void testFileWithoutMetadata(@TempDir Path tempDir) throws Exception {
        // Create a test file without metadata
        File sourceImage = tempDir.resolve("orphan.jpg").toFile();
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(img, "jpg", sourceImage);

        GoogleTakeoutService.MediaFileWithMetadata fileWithMetadata = 
            new GoogleTakeoutService.MediaFileWithMetadata(sourceImage, null, null);

        File outputDir = tempDir.resolve("output").toFile();
        outputDir.mkdirs();
        
        TakeoutProcessorService.ProcessingOptions options = 
            new TakeoutProcessorService.ProcessingOptions();
        options.setOutputDirectory(outputDir);
        options.setOrganizeByMonth(true);
        options.setAddMetadata(false); // No metadata to add
        options.setCopyFiles(true);

        File resultFile = service.processMediaFile(fileWithMetadata, options);

        // Check that file went to Unknown_Date folder
        String path = resultFile.getAbsolutePath();
        assertTrue(path.contains("Unknown_Date"), "Path should contain Unknown_Date folder");
        assertTrue(resultFile.exists(), "File should be copied");
    }

    @Test
    void testOrganizeByMonthDisabled(@TempDir Path tempDir) throws Exception {
        // Create a test file with date metadata
        File sourceImage = tempDir.resolve("test.jpg").toFile();
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(img, "jpg", sourceImage);

        GoogleTakeoutMetadata metadata = new GoogleTakeoutMetadata();
        GoogleTakeoutMetadata.TimeInfo timeInfo = new GoogleTakeoutMetadata.TimeInfo();
        timeInfo.setTimestamp("1609459200"); // Jan 1, 2021
        metadata.setPhotoTakenTime(timeInfo);

        File jsonFile = tempDir.resolve("test.jpg.json").toFile();
        try (FileWriter writer = new FileWriter(jsonFile)) {
            gson.toJson(metadata, writer);
        }

        GoogleTakeoutService.MediaFileWithMetadata fileWithMetadata = 
            new GoogleTakeoutService.MediaFileWithMetadata(sourceImage, jsonFile, metadata);

        File outputDir = tempDir.resolve("output").toFile();
        outputDir.mkdirs();
        
        TakeoutProcessorService.ProcessingOptions options = 
            new TakeoutProcessorService.ProcessingOptions();
        options.setOutputDirectory(outputDir);
        options.setOrganizeByMonth(false); // Disable organization
        options.setAddMetadata(true);
        options.setCopyFiles(true);

        File resultFile = service.processMediaFile(fileWithMetadata, options);

        // Check that file is in the root output directory, not in a dated subfolder
        assertEquals(outputDir, resultFile.getParentFile(), 
            "File should be in root output directory when organization is disabled");
        assertTrue(resultFile.exists(), "File should be copied");
    }

    @Test
    void testAlbumMetadataWriting(@TempDir Path tempDir) throws Exception {
        // Create a test image in an album folder
        File albumDir = tempDir.resolve("My Vacation").toFile();
        albumDir.mkdirs();
        
        File sourceImage = new File(albumDir, "test.jpg");
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(img, "jpg", sourceImage);

        // Create metadata
        GoogleTakeoutMetadata metadata = new GoogleTakeoutMetadata();
        metadata.setTitle("test.jpg");
        metadata.setDescription("Vacation photo");
        
        GoogleTakeoutMetadata.TimeInfo timeInfo = new GoogleTakeoutMetadata.TimeInfo();
        timeInfo.setTimestamp("1609459200");
        metadata.setPhotoTakenTime(timeInfo);

        File jsonFile = new File(albumDir, "test.jpg.json");
        try (FileWriter writer = new FileWriter(jsonFile)) {
            gson.toJson(metadata, writer);
        }

        // Create MediaFileWithMetadata with album name
        GoogleTakeoutService.MediaFileWithMetadata fileWithMetadata = 
            new GoogleTakeoutService.MediaFileWithMetadata(sourceImage, jsonFile, metadata, "My Vacation");

        File outputDir = tempDir.resolve("output").toFile();
        outputDir.mkdirs();
        
        TakeoutProcessorService.ProcessingOptions options = 
            new TakeoutProcessorService.ProcessingOptions();
        options.setOutputDirectory(outputDir);
        options.setOrganizeByMonth(false);
        options.setAddMetadata(true);
        options.setCopyFiles(true);

        File resultFile = service.processMediaFile(fileWithMetadata, options);

        // Verify file was created
        assertTrue(resultFile.exists(), "Output file should exist");

        // Read EXIF and verify album was written
        ImageMetadata imageMetadata = Imaging.getMetadata(resultFile);
        if (imageMetadata instanceof JpegImageMetadata) {
            JpegImageMetadata jpegMetadata = (JpegImageMetadata) imageMetadata;
            TiffImageMetadata exif = jpegMetadata.getExif();
            
            if (exif != null) {
                String[] artistArray = exif.getFieldValue(TiffTagConstants.TIFF_TAG_ARTIST);
                if (artistArray != null && artistArray.length > 0) {
                    String artist = artistArray[0];
                    assertNotNull(artist, "Artist field should contain album data");
                    assertTrue(artist.contains("My Vacation"), "Should contain album name 'My Vacation'");
                }
            }
        }
    }
}
