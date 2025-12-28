package de.christianhoesel.googlephotos.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the PhotoItem model class.
 */
class PhotoItemTest {

    @Test
    void testPhotoItemCreation() {
        PhotoItem item = new PhotoItem();
        item.setId("test-id");
        item.setFilename("test.jpg");
        item.setMimeType("image/jpeg");
        
        assertEquals("test-id", item.getId());
        assertEquals("test.jpg", item.getFilename());
        assertEquals("image/jpeg", item.getMimeType());
    }

    @Test
    void testIsPhoto() {
        PhotoItem item = new PhotoItem();
        item.setMimeType("image/jpeg");
        assertTrue(item.isPhoto());
        assertFalse(item.isVideo());
        
        item.setMimeType("image/png");
        assertTrue(item.isPhoto());
        assertFalse(item.isVideo());
    }

    @Test
    void testIsVideo() {
        PhotoItem item = new PhotoItem();
        item.setMimeType("video/mp4");
        assertTrue(item.isVideo());
        assertFalse(item.isPhoto());
        
        item.setMimeType("video/avi");
        assertTrue(item.isVideo());
        assertFalse(item.isPhoto());
    }

    @Test
    void testAddAlbum() {
        PhotoItem item = new PhotoItem();
        item.addAlbum("Vacation 2023");
        item.addAlbum("Family");
        
        assertEquals(2, item.getAlbums().size());
        assertTrue(item.getAlbums().contains("Vacation 2023"));
        assertTrue(item.getAlbums().contains("Family"));
    }

    @Test
    void testAddPerson() {
        PhotoItem item = new PhotoItem();
        item.addPerson("John");
        item.addPerson("Jane");
        
        assertEquals(2, item.getPeople().size());
        assertTrue(item.getPeople().contains("John"));
        assertTrue(item.getPeople().contains("Jane"));
    }

    @Test
    void testCreationTime() {
        PhotoItem item = new PhotoItem();
        LocalDateTime now = LocalDateTime.now();
        item.setCreationTime(now);
        
        assertEquals(now, item.getCreationTime());
    }

    @Test
    void testDimensions() {
        PhotoItem item = new PhotoItem();
        item.setWidth(1920);
        item.setHeight(1080);
        
        assertEquals(1920, item.getWidth());
        assertEquals(1080, item.getHeight());
    }

    @Test
    void testMetadata() {
        PhotoItem item = new PhotoItem();
        PhotoMetadata metadata = new PhotoMetadata();
        metadata.setCameraMake("Canon");
        metadata.setCameraModel("EOS R5");
        
        item.setMetadata(metadata);
        
        assertNotNull(item.getMetadata());
        assertEquals("Canon", item.getMetadata().getCameraMake());
        assertEquals("EOS R5", item.getMetadata().getCameraModel());
    }

    @Test
    void testToString() {
        PhotoItem item = new PhotoItem();
        item.setId("123");
        item.setFilename("photo.jpg");
        item.setMimeType("image/jpeg");
        
        String str = item.toString();
        assertTrue(str.contains("123"));
        assertTrue(str.contains("photo.jpg"));
        assertTrue(str.contains("image/jpeg"));
    }
}
