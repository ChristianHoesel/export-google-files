package de.christianhoesel.googlephotos.model;

/**
 * Represents a Google Photos album.
 */
public class Album {
    private String id;
    private String title;
    private String productUrl;
    private long mediaItemsCount;
    private String coverPhotoBaseUrl;
    private String coverPhotoMediaItemId;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getProductUrl() {
        return productUrl;
    }

    public void setProductUrl(String productUrl) {
        this.productUrl = productUrl;
    }

    public long getMediaItemsCount() {
        return mediaItemsCount;
    }

    public void setMediaItemsCount(long mediaItemsCount) {
        this.mediaItemsCount = mediaItemsCount;
    }

    public String getCoverPhotoBaseUrl() {
        return coverPhotoBaseUrl;
    }

    public void setCoverPhotoBaseUrl(String coverPhotoBaseUrl) {
        this.coverPhotoBaseUrl = coverPhotoBaseUrl;
    }

    public String getCoverPhotoMediaItemId() {
        return coverPhotoMediaItemId;
    }

    public void setCoverPhotoMediaItemId(String coverPhotoMediaItemId) {
        this.coverPhotoMediaItemId = coverPhotoMediaItemId;
    }

    @Override
    public String toString() {
        return "Album{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", mediaItemsCount=" + mediaItemsCount +
                '}';
    }
}
