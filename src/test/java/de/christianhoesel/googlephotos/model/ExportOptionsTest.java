package de.christianhoesel.googlephotos.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the ExportOptions model class.
 */
class ExportOptionsTest {

    @Test
    void testDefaultValues() {
        ExportOptions options = new ExportOptions();
        
        assertTrue(options.isExportPhotos());
        assertTrue(options.isExportVideos());
        assertFalse(options.isDeleteAfterExport());
        assertTrue(options.isPreserveMetadata());
        assertFalse(options.isCreateAlbumFolders());
        assertTrue(options.isCreateDateFolders());
        assertTrue(options.isWriteMetadataFile());
    }

    @Test
    void testDateRange() {
        ExportOptions options = new ExportOptions();
        LocalDate start = LocalDate.of(2023, 1, 1);
        LocalDate end = LocalDate.of(2023, 12, 31);
        
        options.setStartDate(start);
        options.setEndDate(end);
        
        assertEquals(start, options.getStartDate());
        assertEquals(end, options.getEndDate());
    }

    @Test
    void testOutputDirectory() {
        ExportOptions options = new ExportOptions();
        options.setOutputDirectory("/home/user/photos");
        
        assertEquals("/home/user/photos", options.getOutputDirectory());
    }

    @Test
    void testMediaTypeFilters() {
        ExportOptions options = new ExportOptions();
        
        options.setExportPhotos(true);
        options.setExportVideos(false);
        
        assertTrue(options.isExportPhotos());
        assertFalse(options.isExportVideos());
    }

    @Test
    void testDeleteAfterExport() {
        ExportOptions options = new ExportOptions();
        assertFalse(options.isDeleteAfterExport());
        
        options.setDeleteAfterExport(true);
        assertTrue(options.isDeleteAfterExport());
    }

    @Test
    void testFolderOptions() {
        ExportOptions options = new ExportOptions();
        
        options.setCreateAlbumFolders(true);
        options.setCreateDateFolders(false);
        
        assertTrue(options.isCreateAlbumFolders());
        assertFalse(options.isCreateDateFolders());
    }

    @Test
    void testToString() {
        ExportOptions options = new ExportOptions();
        options.setOutputDirectory("/test/path");
        options.setExportPhotos(true);
        
        String str = options.toString();
        assertTrue(str.contains("/test/path"));
        assertTrue(str.contains("exportPhotos=true"));
    }
}
