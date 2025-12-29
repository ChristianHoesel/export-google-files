package de.christianhoesel.googlephotos.model;

import java.util.List;

/**
 * Represents metadata from Google Takeout JSON files.
 * Google Takeout creates a .json file for each photo/video with metadata.
 */
public class GoogleTakeoutMetadata {
    private String title;
    private String description;
    private String imageViews;
    private TimeInfo creationTime;
    private TimeInfo photoTakenTime;
    private GeoData geoData;
    private GeoData geoDataExif;
    private List<Person> people;
    private String url;
    private GooglePhotosOrigin googlePhotosOrigin;

    public static class TimeInfo {
        private String timestamp; // Unix timestamp as string
        private String formatted;

        public String getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(String timestamp) {
            this.timestamp = timestamp;
        }

        public String getFormatted() {
            return formatted;
        }

        public void setFormatted(String formatted) {
            this.formatted = formatted;
        }

        public long getTimestampAsLong() {
            try {
                return Long.parseLong(timestamp);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
    }

    public static class GeoData {
        private double latitude;
        private double longitude;
        private double altitude;
        private double latitudeSpan;
        private double longitudeSpan;

        public double getLatitude() {
            return latitude;
        }

        public void setLatitude(double latitude) {
            this.latitude = latitude;
        }

        public double getLongitude() {
            return longitude;
        }

        public void setLongitude(double longitude) {
            this.longitude = longitude;
        }

        public double getAltitude() {
            return altitude;
        }

        public void setAltitude(double altitude) {
            this.altitude = altitude;
        }

        public double getLatitudeSpan() {
            return latitudeSpan;
        }

        public void setLatitudeSpan(double latitudeSpan) {
            this.latitudeSpan = latitudeSpan;
        }

        public double getLongitudeSpan() {
            return longitudeSpan;
        }

        public void setLongitudeSpan(double longitudeSpan) {
            this.longitudeSpan = longitudeSpan;
        }

        public boolean hasValidCoordinates() {
            // Note: (0.0, 0.0) is a valid location in the Gulf of Guinea,
            // but in the context of Google Photos, it typically indicates
            // that no GPS data was available. We use this heuristic.
            return (latitude != 0.0 || longitude != 0.0);
        }
    }

    public static class Person {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class GooglePhotosOrigin {
        private MobileUpload mobileUpload;

        public MobileUpload getMobileUpload() {
            return mobileUpload;
        }

        public void setMobileUpload(MobileUpload mobileUpload) {
            this.mobileUpload = mobileUpload;
        }
    }

    public static class MobileUpload {
        private DeviceFolder deviceFolder;
        private String deviceType;

        public DeviceFolder getDeviceFolder() {
            return deviceFolder;
        }

        public void setDeviceFolder(DeviceFolder deviceFolder) {
            this.deviceFolder = deviceFolder;
        }

        public String getDeviceType() {
            return deviceType;
        }

        public void setDeviceType(String deviceType) {
            this.deviceType = deviceType;
        }
    }

    public static class DeviceFolder {
        private String localFolderName;

        public String getLocalFolderName() {
            return localFolderName;
        }

        public void setLocalFolderName(String localFolderName) {
            this.localFolderName = localFolderName;
        }
    }

    // Getters and setters

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getImageViews() {
        return imageViews;
    }

    public void setImageViews(String imageViews) {
        this.imageViews = imageViews;
    }

    public TimeInfo getCreationTime() {
        return creationTime;
    }

    public void setCreationTime(TimeInfo creationTime) {
        this.creationTime = creationTime;
    }

    public TimeInfo getPhotoTakenTime() {
        return photoTakenTime;
    }

    public void setPhotoTakenTime(TimeInfo photoTakenTime) {
        this.photoTakenTime = photoTakenTime;
    }

    public GeoData getGeoData() {
        return geoData;
    }

    public void setGeoData(GeoData geoData) {
        this.geoData = geoData;
    }

    public GeoData getGeoDataExif() {
        return geoDataExif;
    }

    public void setGeoDataExif(GeoData geoDataExif) {
        this.geoDataExif = geoDataExif;
    }

    public List<Person> getPeople() {
        return people;
    }

    public void setPeople(List<Person> people) {
        this.people = people;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public GooglePhotosOrigin getGooglePhotosOrigin() {
        return googlePhotosOrigin;
    }

    public void setGooglePhotosOrigin(GooglePhotosOrigin googlePhotosOrigin) {
        this.googlePhotosOrigin = googlePhotosOrigin;
    }
}
