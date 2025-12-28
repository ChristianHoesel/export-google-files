package de.christianhoesel.googlephotos.model;

import java.time.LocalDate;

/**
 * Configuration options for the export process.
 */
public class ExportOptions {
    private LocalDate startDate;
    private LocalDate endDate;
    private String outputDirectory;
    private boolean exportPhotos;
    private boolean exportVideos;
    private boolean deleteAfterExport;
    private boolean preserveMetadata;
    private boolean createAlbumFolders;
    private boolean createDateFolders;
    private boolean writeMetadataFile;

    public ExportOptions() {
        // Set defaults
        this.exportPhotos = true;
        this.exportVideos = true;
        this.deleteAfterExport = false;
        this.preserveMetadata = true;
        this.createAlbumFolders = false;
        this.createDateFolders = true;
        this.writeMetadataFile = true;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public String getOutputDirectory() {
        return outputDirectory;
    }

    public void setOutputDirectory(String outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    public boolean isExportPhotos() {
        return exportPhotos;
    }

    public void setExportPhotos(boolean exportPhotos) {
        this.exportPhotos = exportPhotos;
    }

    public boolean isExportVideos() {
        return exportVideos;
    }

    public void setExportVideos(boolean exportVideos) {
        this.exportVideos = exportVideos;
    }

    public boolean isDeleteAfterExport() {
        return deleteAfterExport;
    }

    public void setDeleteAfterExport(boolean deleteAfterExport) {
        this.deleteAfterExport = deleteAfterExport;
    }

    public boolean isPreserveMetadata() {
        return preserveMetadata;
    }

    public void setPreserveMetadata(boolean preserveMetadata) {
        this.preserveMetadata = preserveMetadata;
    }

    public boolean isCreateAlbumFolders() {
        return createAlbumFolders;
    }

    public void setCreateAlbumFolders(boolean createAlbumFolders) {
        this.createAlbumFolders = createAlbumFolders;
    }

    public boolean isCreateDateFolders() {
        return createDateFolders;
    }

    public void setCreateDateFolders(boolean createDateFolders) {
        this.createDateFolders = createDateFolders;
    }

    public boolean isWriteMetadataFile() {
        return writeMetadataFile;
    }

    public void setWriteMetadataFile(boolean writeMetadataFile) {
        this.writeMetadataFile = writeMetadataFile;
    }

    @Override
    public String toString() {
        return "ExportOptions{" +
                "startDate=" + startDate +
                ", endDate=" + endDate +
                ", outputDirectory='" + outputDirectory + '\'' +
                ", exportPhotos=" + exportPhotos +
                ", exportVideos=" + exportVideos +
                ", deleteAfterExport=" + deleteAfterExport +
                ", preserveMetadata=" + preserveMetadata +
                ", createAlbumFolders=" + createAlbumFolders +
                ", createDateFolders=" + createDateFolders +
                ", writeMetadataFile=" + writeMetadataFile +
                '}';
    }
}
