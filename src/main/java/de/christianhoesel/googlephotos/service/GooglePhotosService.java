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
        
        try {
            String pageToken = null;
            do {
                // Check if thread was interrupted (task cancelled)
                if (Thread.currentThread().isInterrupted()) {
                    logger.info("Album loading cancelled by user");
                    throw new InterruptedException("Album loading was cancelled");
                }
                
                ListAlbumsRequest request = ListAlbumsRequest.newBuilder()
                        .setPageSize(50)
                        .setPageToken(pageToken != null ? pageToken : "")
                        .build();
                
                ListAlbumsResponse response = client.listAlbumsCallable().call(request);
                
                for (com.google.photos.types.proto.Album googleAlbum : response.getAlbumsList()) {
                    // Check if thread was interrupted
                    if (Thread.currentThread().isInterrupted()) {
                        logger.info("Album loading cancelled by user");
                        throw new InterruptedException("Album loading was cancelled");
                    }
                    
                    Album album = convertAlbum(googleAlbum);
                    albums.add(album);
                }
                
                pageToken = response.getNextPageToken();
            } while (pageToken != null && !pageToken.isEmpty());
            
            logger.info("Found {} albums", albums.size());
            return albums;
        } catch (InterruptedException e) {
            logger.info("Album loading operation was cancelled");
            Thread.currentThread().interrupt(); // Restore interrupt status
            return albums; // Return what we found so far
        } catch (com.google.api.gax.rpc.PermissionDeniedException e) {
            logger.error("PERMISSION_DENIED error occurred while listing albums.");
            logger.error("Required scope: https://www.googleapis.com/auth/photoslibrary.readonly");
            logger.error("Full error: {}", e.getMessage());
            throw new RuntimeException("Insufficient permissions to list albums. Please re-authenticate with proper scopes.", e);
        } catch (Exception e) {
            logger.error("Error listing albums", e);
            throw new RuntimeException("Failed to list albums", e);
        }
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
        logger.info("Export options - Photos: {}, Videos: {}, StartDate: {}, EndDate: {}", 
            options.isExportPhotos(), options.isExportVideos(), 
            options.getStartDate(), options.getEndDate());
        
        List<PhotoItem> items = new ArrayList<>();
        
        try {
            // Check if we need filters
            // IMPORTANT: Google Photos API requires BOTH start AND end date if using date filter!
            boolean hasDateFilter = (options.getStartDate() != null && options.getEndDate() != null);
            boolean hasMediaTypeFilter = (!options.isExportPhotos() || !options.isExportVideos());
            boolean needsFilter = hasDateFilter || hasMediaTypeFilter;
            
            logger.info("Has date filter: {}, Has media type filter: {}", hasDateFilter, hasMediaTypeFilter);
            
            // If user set only one date, log a warning
            if ((options.getStartDate() != null && options.getEndDate() == null) ||
                (options.getStartDate() == null && options.getEndDate() != null)) {
                logger.warn("Only one date boundary set - Google Photos API requires BOTH start AND end dates!");
                logger.warn("StartDate: {}, EndDate: {}", options.getStartDate(), options.getEndDate());
                logger.warn("Ignoring date filter and fetching all media items instead.");
            }
            
            // If no filters needed, use the simple list method which returns ALL media items
            if (!needsFilter) {
                logger.info("No filters specified - using listMediaItems to get all media");
                return listAllMediaItems();
            }
            
            // Otherwise use search with filters
            logger.info("Using searchMediaItems with filters");
            Filters.Builder filtersBuilder = Filters.newBuilder();
        
            // Set date filter if BOTH dates are specified
            if (hasDateFilter) {
                DateFilter.Builder dateFilterBuilder = DateFilter.newBuilder();
                DateRange.Builder dateRangeBuilder = DateRange.newBuilder();
                
                // Both dates are guaranteed to be non-null here (checked above)
                LocalDate start = options.getStartDate();
                dateRangeBuilder.setStartDate(Date.newBuilder()
                        .setYear(start.getYear())
                        .setMonth(start.getMonthValue())
                        .setDay(start.getDayOfMonth())
                        .build());
                logger.info("Start date filter: {}", start);
                
                LocalDate end = options.getEndDate();
                dateRangeBuilder.setEndDate(Date.newBuilder()
                        .setYear(end.getYear())
                        .setMonth(end.getMonthValue())
                        .setDay(end.getDayOfMonth())
                        .build());
                logger.info("End date filter: {}", end);
                
                dateFilterBuilder.addRanges(dateRangeBuilder.build());
                filtersBuilder.setDateFilter(dateFilterBuilder.build());
            }
            
            // Set media type filter only if not both selected
            if (hasMediaTypeFilter) {
                MediaTypeFilter.Builder mediaTypeFilterBuilder = MediaTypeFilter.newBuilder();
                if (options.isExportPhotos()) {
                    mediaTypeFilterBuilder.addMediaTypes(MediaTypeFilter.MediaType.PHOTO);
                    logger.info("Media type filter: PHOTO only");
                } else if (options.isExportVideos()) {
                    mediaTypeFilterBuilder.addMediaTypes(MediaTypeFilter.MediaType.VIDEO);
                    logger.info("Media type filter: VIDEO only");
                }
                filtersBuilder.setMediaTypeFilter(mediaTypeFilterBuilder.build());
            }
        
        // Search for media items with filters
        String pageToken = null;
        int totalFound = 0;
        int emptyPageCount = 0; // Counter for consecutive empty pages
        do {
            // Check if thread was interrupted (task cancelled)
            if (Thread.currentThread().isInterrupted()) {
                logger.info("Search cancelled by user");
                throw new InterruptedException("Search was cancelled");
            }
            
            SearchMediaItemsRequest.Builder requestBuilder = SearchMediaItemsRequest.newBuilder()
                    .setFilters(filtersBuilder.build())
                    .setPageSize(100);
            
            if (pageToken != null && !pageToken.isEmpty()) {
                requestBuilder.setPageToken(pageToken);
            }
            
            logger.debug("Sending search request with pageSize=100, hasPageToken={}", pageToken != null);
            
            var response = client.searchMediaItemsCallable().call(requestBuilder.build());
            
            int itemsInResponse = response.getMediaItemsCount();
            logger.debug("Received {} items in this page", itemsInResponse);
            
            // If we get 0 items, increment empty page counter
            if (itemsInResponse == 0) {
                emptyPageCount++;
                logger.warn("Received empty page (#{}) with filters. This might indicate no matching media.", emptyPageCount);
                
                // Safety: Stop after 3 consecutive empty pages
                if (emptyPageCount >= 3) {
                    logger.warn("Stopping after {} consecutive empty pages.", emptyPageCount);
                    break;
                }
            } else {
                emptyPageCount = 0; // Reset counter when we get items
            }
            
            for (MediaItem mediaItem : response.getMediaItemsList()) {
                // Check if thread was interrupted
                if (Thread.currentThread().isInterrupted()) {
                    logger.info("Search cancelled by user");
                    throw new InterruptedException("Search was cancelled");
                }
                
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
        } catch (InterruptedException e) {
            logger.info("Search operation was cancelled");
            Thread.currentThread().interrupt(); // Restore interrupt status
            return items; // Return what we found so far
        } catch (com.google.api.gax.rpc.PermissionDeniedException e) {
            logger.error("PERMISSION_DENIED error occurred. This usually means:");
            logger.error("1. The OAuth consent screen in Google Cloud Console doesn't have the required scopes");
            logger.error("2. The authentication tokens were created with insufficient scopes");
            logger.error("3. The API is not enabled in the Google Cloud Console");
            logger.error("Please check: https://console.cloud.google.com/apis/credentials/consent");
            logger.error("Required scope: https://www.googleapis.com/auth/photoslibrary.readonly");
            logger.error("Full error: {}", e.getMessage());
            throw new RuntimeException("Insufficient permissions to access Google Photos. Please check the logs for details.", e);
        } catch (Exception e) {
            logger.error("Error searching for media items", e);
            throw new RuntimeException("Failed to search media items", e);
        }
    }

    /**
     * Lists ALL media items in the user's Google Photos library without any filters.
     * This is used when no specific date range or media type filter is specified.
     * 
     * @return List of all photo items
     */
    private List<PhotoItem> listAllMediaItems() {
        logger.info("Listing all media items without filters...");
        List<PhotoItem> items = new ArrayList<>();
        
        try {
            String pageToken = null;
            int totalFound = 0;
            int emptyPageCount = 0; // Counter for consecutive empty pages
            
            do {
                // Check if thread was interrupted (task cancelled)
                if (Thread.currentThread().isInterrupted()) {
                    logger.info("List cancelled by user");
                    throw new InterruptedException("List was cancelled");
                }
                
                // Use the mediaItems.list API endpoint instead of search
                com.google.photos.library.v1.proto.ListMediaItemsRequest.Builder requestBuilder = 
                    com.google.photos.library.v1.proto.ListMediaItemsRequest.newBuilder()
                        .setPageSize(100);
                
                if (pageToken != null && !pageToken.isEmpty()) {
                    requestBuilder.setPageToken(pageToken);
                }
                
                logger.debug("Sending list request with pageSize=100");
                
                var response = client.listMediaItemsCallable().call(requestBuilder.build());
                
                int itemsInResponse = response.getMediaItemsCount();
                logger.debug("Received {} items in this page", itemsInResponse);
                
                // If we get 0 items, increment empty page counter
                if (itemsInResponse == 0) {
                    emptyPageCount++;
                    logger.warn("Received empty page (#{}) but nextPageToken is present. This might indicate an empty library.", emptyPageCount);
                    
                    // Safety: Stop after 3 consecutive empty pages to prevent infinite loops
                    if (emptyPageCount >= 3) {
                        logger.warn("Stopping after {} consecutive empty pages. Your Google Photos library might be empty.", emptyPageCount);
                        break;
                    }
                } else {
                    emptyPageCount = 0; // Reset counter when we get items
                }
                
                for (MediaItem mediaItem : response.getMediaItemsList()) {
                    // Check if thread was interrupted
                    if (Thread.currentThread().isInterrupted()) {
                        logger.info("List cancelled by user");
                        throw new InterruptedException("List was cancelled");
                    }
                    
                    PhotoItem item = convertMediaItem(mediaItem);
                    
                    // Add album information if we have the mapping
                    if (mediaItemToAlbums.containsKey(item.getId())) {
                        item.setAlbums(mediaItemToAlbums.get(item.getId()));
                    }
                    
                    items.add(item);
                    totalFound++;
                    
                    if (totalFound % 100 == 0) {
                        logger.info("Listed {} media items so far...", totalFound);
                    }
                }
                
                pageToken = response.getNextPageToken();
            } while (pageToken != null && !pageToken.isEmpty());
            
            logger.info("Listed {} media items total", items.size());
            return items;
            
        } catch (InterruptedException e) {
            logger.info("List operation was cancelled");
            Thread.currentThread().interrupt();
            return items;
        } catch (Exception e) {
            logger.error("Error listing all media items", e);
            throw new RuntimeException("Failed to list media items", e);
        }
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
                    // Convert Duration to total seconds as a double
                    // Duration has seconds and nanos (0-999,999,999) fields
                    long seconds = photo.getExposureTime().getSeconds();
                    int nanos = photo.getExposureTime().getNanos();
                    double totalSeconds = seconds + (nanos / 1_000_000_000.0);
                    
                    // Format exposure time appropriately based on duration
                    if (totalSeconds >= 1.0) {
                        photoMetadata.setExposureTime(String.format("%.1fs", totalSeconds));
                    } else if (totalSeconds > 0) {
                        // Express as fraction for short exposures (e.g., 1/250s)
                        double reciprocal = 1.0 / totalSeconds;
                        photoMetadata.setExposureTime(String.format("1/%.0fs", reciprocal));
                    } else {
                        photoMetadata.setExposureTime("0s");
                    }
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
