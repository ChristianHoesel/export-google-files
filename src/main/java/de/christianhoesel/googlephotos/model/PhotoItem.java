package de.christianhoesel.googlephotos.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a photo or video item from Google Photos with all metadata.
 */
public class PhotoItem {
    private String id;
    private String filename;
    private String mimeType;
    private String description;
    private LocalDateTime creationTime;
    private String baseUrl;
    private long width;
    private long height;
    private List<String> albums;
    private List<String> people;
    private PhotoMetadata metadata;

    public PhotoItem() {
        this.albums = new ArrayList<>();
        this.people = new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getCreationTime() {
        return creationTime;
    }

    public void setCreationTime(LocalDateTime creationTime) {
        this.creationTime = creationTime;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public long getWidth() {
        return width;
    }

    public void setWidth(long width) {
        this.width = width;
    }

    public long getHeight() {
        return height;
    }

    public void setHeight(long height) {
        this.height = height;
    }

    public List<String> getAlbums() {
        return albums;
    }

    public void setAlbums(List<String> albums) {
        this.albums = albums;
    }

    public void addAlbum(String album) {
        if (this.albums == null) {
            this.albums = new ArrayList<>();
        }
        this.albums.add(album);
    }

    public List<String> getPeople() {
        return people;
    }

    public void setPeople(List<String> people) {
        this.people = people;
    }

    public void addPerson(String person) {
        if (this.people == null) {
            this.people = new ArrayList<>();
        }
        this.people.add(person);
    }

    public PhotoMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(PhotoMetadata metadata) {
        this.metadata = metadata;
    }

    public boolean isVideo() {
        return mimeType != null && mimeType.startsWith("video/");
    }

    public boolean isPhoto() {
        return mimeType != null && mimeType.startsWith("image/");
    }

    @Override
    public String toString() {
        return "PhotoItem{" +
                "id='" + id + '\'' +
                ", filename='" + filename + '\'' +
                ", mimeType='" + mimeType + '\'' +
                ", creationTime=" + creationTime +
                ", albums=" + albums +
                ", people=" + people +
                '}';
    }
}
