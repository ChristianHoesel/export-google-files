package de.christianhoesel.googlephotos.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the PhotoMetadata model class.
 */
class PhotoMetadataTest {

    @Test
    void testCameraInfo() {
        PhotoMetadata metadata = new PhotoMetadata();
        metadata.setCameraMake("Canon");
        metadata.setCameraModel("EOS R5");
        metadata.setFocalLength(50.0);
        metadata.setApertureFNumber(1.8);
        metadata.setIsoEquivalent(400);
        metadata.setExposureTime("1/250s");
        
        assertEquals("Canon", metadata.getCameraMake());
        assertEquals("EOS R5", metadata.getCameraModel());
        assertEquals(50.0, metadata.getFocalLength());
        assertEquals(1.8, metadata.getApertureFNumber());
        assertEquals(400, metadata.getIsoEquivalent());
        assertEquals("1/250s", metadata.getExposureTime());
    }

    @Test
    void testLocationInfo() {
        PhotoMetadata metadata = new PhotoMetadata();
        assertFalse(metadata.hasLocation());
        
        metadata.setLatitude(48.8566);
        metadata.setLongitude(2.3522);
        metadata.setLocationName("Paris, France");
        
        assertTrue(metadata.hasLocation());
        assertEquals(48.8566, metadata.getLatitude());
        assertEquals(2.3522, metadata.getLongitude());
        assertEquals("Paris, France", metadata.getLocationName());
    }

    @Test
    void testVideoMetadata() {
        PhotoMetadata metadata = new PhotoMetadata();
        metadata.setFps(60.0);
        metadata.setVideoStatus("READY");
        
        assertEquals(60.0, metadata.getFps());
        assertEquals("READY", metadata.getVideoStatus());
    }

    @Test
    void testHasLocationPartial() {
        PhotoMetadata metadata = new PhotoMetadata();
        
        // Only latitude set
        metadata.setLatitude(48.8566);
        assertFalse(metadata.hasLocation());
        
        // Only longitude set
        metadata = new PhotoMetadata();
        metadata.setLongitude(2.3522);
        assertFalse(metadata.hasLocation());
        
        // Both set
        metadata.setLatitude(48.8566);
        assertTrue(metadata.hasLocation());
    }

    @Test
    void testToString() {
        PhotoMetadata metadata = new PhotoMetadata();
        metadata.setCameraMake("Nikon");
        metadata.setCameraModel("D850");
        
        String str = metadata.toString();
        assertTrue(str.contains("Nikon"));
        assertTrue(str.contains("D850"));
    }
}
