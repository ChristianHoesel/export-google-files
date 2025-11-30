package de.christianhoesel.googlephotos.service;

import com.google.photos.library.v1.PhotosLibraryClient;
import com.google.photos.library.v1.proto.DateFilter;
import com.google.photos.library.v1.proto.Filters;
import com.google.photos.library.v1.proto.ListAlbumsRequest;
import com.google.photos.library.v1.proto.ListAlbumsResponse;
import com.google.photos.library.v1.proto.MediaTypeFilter;
import com.google.photos.library.v1.proto.SearchMediaItemsRequest;
import com.google.photos.types.proto.DateRange;
import com.google.photos.types.proto.MediaItem;
import com.google.photos.types.proto.MediaMetadata;
import com.google.type.Date;
import de.christianhoesel.googlephotos.model.Album;
import de.christianhoesel.googlephotos.model.ExportOptions;
import de.christianhoesel.googlephotos.model.PhotoItem;
import de.christianhoesel.googlephotos.model.PhotoMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for interacting with Google Photos Library API.
 */
public class GooglePhotosService {
    private static final Logger logger = LoggerFactory.getLogger(GooglePhotosService.class);
    
    private final PhotosLibraryClient client;
    private Map<String, List<String>> mediaItemToAlbums;

    public GooglePhotosService(PhotosLibraryClient client) {
        this.client = client;
        this.mediaItemToAlbums = new HashMap<>();
    }

    /**
     * Lists all albums in the user's Google Photos library.
     * 
     * @return List of albums
     */
    public List<Album> listAlbums() {
        logger.info("Fetching albums from Google Photos...");
        List<Album> albums = new ArrayList<>();
        
        String pageToken = null;
        do {
            ListAlbumsRequest request = ListAlbumsRequest.newBuilder()
                    .setPageSize(50)
                    .setPageToken(pageToken != null ? pageToken : "")
                    .build();
            
            ListAlbumsResponse response = client.listAlbumsCallable().call(request);
            
            for (com.google.photos.types.proto.Album googleAlbum : response.getAlbumsList()) {
                Album album = convertAlbum(googleAlbum);
                albums.add(album);
            }
            
            pageToken = response.getNextPageToken();
        } while (pageToken != null && !pageToken.isEmpty());
        
        logger.info("Found {} albums", albums.size());
        return albums;
    }

    /**
     * Builds a mapping of media item IDs to album names.
     * This is useful for determining which albums each photo belongs to.
     */
    public void buildMediaItemAlbumMapping() {
        logger.info("Building media item to album mapping...");
        mediaItemToAlbums = new HashMap<>();
        
        List<Album> albums = listAlbums();
        for (Album album : albums) {
            try {
                List<PhotoItem> items = listMediaItemsInAlbum(album.getId());
                for (PhotoItem item : items) {
                    mediaItemToAlbums.computeIfAbsent(item.getId(), k -> new ArrayList<>())
                            .add(album.getTitle());
                }
            } catch (Exception e) {
                logger.warn("Could not fetch items for album: {}", album.getTitle(), e);
            }
        }
        logger.info("Album mapping complete for {} media items", mediaItemToAlbums.size());
    }

    /**
     * Lists all media items in a specific album.
     * 
     * @param albumId The album ID
     * @return List of photo items in the album
     */
    public List<PhotoItem> listMediaItemsInAlbum(String albumId) {
        List<PhotoItem> items = new ArrayList<>();
        
        String pageToken = null;
        do {
            SearchMediaItemsRequest.Builder requestBuilder = SearchMediaItemsRequest.newBuilder()
                    .setAlbumId(albumId)
                    .setPageSize(100);
            
            if (pageToken != null && !pageToken.isEmpty()) {
                requestBuilder.setPageToken(pageToken);
            }
            
            var response = client.searchMediaItemsCallable().call(requestBuilder.build());
            
            for (MediaItem mediaItem : response.getMediaItemsList()) {
                PhotoItem item = convertMediaItem(mediaItem);
                items.add(item);
            }
            
            pageToken = response.getNextPageToken();
        } while (pageToken != null && !pageToken.isEmpty());
        
        return items;
    }

    /**
     * Searches for media items based on export options.
     * 
     * @param options Export options containing date range and filters
     * @return List of photo items matching the criteria
     */
    public List<PhotoItem> searchMediaItems(ExportOptions options) {
        logger.info("Searching for media items...");
        List<PhotoItem> items = new ArrayList<>();
        
        Filters.Builder filtersBuilder = Filters.newBuilder();
        
        // Set date filter if date range is specified
        if (options.getStartDate() != null || options.getEndDate() != null) {
            DateFilter.Builder dateFilterBuilder = DateFilter.newBuilder();
            
            // Use date ranges
            DateRange.Builder dateRangeBuilder = 
                    DateRange.newBuilder();
            
            if (options.getStartDate() != null) {
                LocalDate start = options.getStartDate();
                dateRangeBuilder.setStartDate(Date.newBuilder()
                        .setYear(start.getYear())
                        .setMonth(start.getMonthValue())
                        .setDay(start.getDayOfMonth())
                        .build());
            } else {
                // Default to a very old date
                dateRangeBuilder.setStartDate(Date.newBuilder()
                        .setYear(1900)
                        .setMonth(1)
                        .setDay(1)
                        .build());
            }
            
            if (options.getEndDate() != null) {
                LocalDate end = options.getEndDate();
                dateRangeBuilder.setEndDate(Date.newBuilder()
                        .setYear(end.getYear())
                        .setMonth(end.getMonthValue())
                        .setDay(end.getDayOfMonth())
                        .build());
            } else {
                // Default to today
                LocalDate today = LocalDate.now();
                dateRangeBuilder.setEndDate(Date.newBuilder()
                        .setYear(today.getYear())
                        .setMonth(today.getMonthValue())
                        .setDay(today.getDayOfMonth())
                        .build());
            }
            
            dateFilterBuilder.addRanges(dateRangeBuilder.build());
            filtersBuilder.setDateFilter(dateFilterBuilder.build());
        }
        
        // Set media type filter
        MediaTypeFilter.Builder mediaTypeFilterBuilder = MediaTypeFilter.newBuilder();
        if (options.isExportPhotos() && options.isExportVideos()) {
            mediaTypeFilterBuilder.addMediaTypes(MediaTypeFilter.MediaType.ALL_MEDIA);
        } else if (options.isExportPhotos()) {
            mediaTypeFilterBuilder.addMediaTypes(MediaTypeFilter.MediaType.PHOTO);
        } else if (options.isExportVideos()) {
            mediaTypeFilterBuilder.addMediaTypes(MediaTypeFilter.MediaType.VIDEO);
        }
        filtersBuilder.setMediaTypeFilter(mediaTypeFilterBuilder.build());
        
        // Search for media items
        String pageToken = null;
        int totalFound = 0;
        do {
            SearchMediaItemsRequest.Builder requestBuilder = SearchMediaItemsRequest.newBuilder()
                    .setFilters(filtersBuilder.build())
                    .setPageSize(100);
            
            if (pageToken != null && !pageToken.isEmpty()) {
                requestBuilder.setPageToken(pageToken);
            }
            
            var response = client.searchMediaItemsCallable().call(requestBuilder.build());
            
            for (MediaItem mediaItem : response.getMediaItemsList()) {
                PhotoItem item = convertMediaItem(mediaItem);
                
                // Add album information if we have the mapping
                if (mediaItemToAlbums.containsKey(item.getId())) {
                    item.setAlbums(mediaItemToAlbums.get(item.getId()));
                }
                
                items.add(item);
                totalFound++;
                
                if (totalFound % 100 == 0) {
                    logger.info("Found {} media items so far...", totalFound);
                }
            }
            
            pageToken = response.getNextPageToken();
        } while (pageToken != null && !pageToken.isEmpty());
        
        logger.info("Found {} media items total", items.size());
        return items;
    }

    /**
     * Gets the download URL for a media item.
     * For photos: baseUrl + "=d" for original quality
     * For videos: baseUrl + "=dv" for original quality video
     * 
     * @param item The photo item
     * @return Download URL for original quality
     */
    public String getDownloadUrl(PhotoItem item) {
        String baseUrl = item.getBaseUrl();
        if (item.isVideo()) {
            return baseUrl + "=dv";
        } else {
            return baseUrl + "=d";
        }
    }

    /**
     * Gets the albums for a specific media item.
     * 
     * @param mediaItemId The media item ID
     * @return List of album names
     */
    public List<String> getAlbumsForMediaItem(String mediaItemId) {
        return mediaItemToAlbums.getOrDefault(mediaItemId, new ArrayList<>());
    }

    private Album convertAlbum(com.google.photos.types.proto.Album googleAlbum) {
        Album album = new Album();
        album.setId(googleAlbum.getId());
        album.setTitle(googleAlbum.getTitle());
        album.setProductUrl(googleAlbum.getProductUrl());
        album.setMediaItemsCount(googleAlbum.getMediaItemsCount());
        album.setCoverPhotoBaseUrl(googleAlbum.getCoverPhotoBaseUrl());
        album.setCoverPhotoMediaItemId(googleAlbum.getCoverPhotoMediaItemId());
        return album;
    }

    private PhotoItem convertMediaItem(MediaItem mediaItem) {
        PhotoItem item = new PhotoItem();
        item.setId(mediaItem.getId());
        item.setFilename(mediaItem.getFilename());
        item.setMimeType(mediaItem.getMimeType());
        item.setDescription(mediaItem.getDescription());
        item.setBaseUrl(mediaItem.getBaseUrl());
        
        MediaMetadata metadata = mediaItem.getMediaMetadata();
        if (metadata != null) {
            // Set dimensions
            item.setWidth(metadata.getWidth());
            item.setHeight(metadata.getHeight());
            
            // Set creation time
            if (metadata.hasCreationTime()) {
                com.google.protobuf.Timestamp timestamp = metadata.getCreationTime();
                LocalDateTime creationTime = LocalDateTime.ofEpochSecond(
                        timestamp.getSeconds(), 
                        timestamp.getNanos(), 
                        ZoneId.systemDefault().getRules().getOffset(java.time.Instant.now())
                );
                item.setCreationTime(creationTime);
            }
            
            // Extract photo/video specific metadata
            PhotoMetadata photoMetadata = new PhotoMetadata();
            
            if (metadata.hasPhoto()) {
                var photo = metadata.getPhoto();
                photoMetadata.setCameraMake(photo.getCameraMake());
                photoMetadata.setCameraModel(photo.getCameraModel());
                photoMetadata.setFocalLength(photo.getFocalLength());
                photoMetadata.setApertureFNumber(photo.getApertureFNumber());
                photoMetadata.setIsoEquivalent(photo.getIsoEquivalent());
                if (photo.hasExposureTime()) {
                    photoMetadata.setExposureTime(String.format("%.4fs", 
                            photo.getExposureTime().getSeconds() + 
                            photo.getExposureTime().getNanos() / 1_000_000_000.0));
                }
            }
            
            if (metadata.hasVideo()) {
                var video = metadata.getVideo();
                photoMetadata.setCameraMake(video.getCameraMake());
                photoMetadata.setCameraModel(video.getCameraModel());
                photoMetadata.setFps(video.getFps());
                photoMetadata.setVideoStatus(video.getStatus().name());
            }
            
            item.setMetadata(photoMetadata);
        }
        
        return item;
    }
}
