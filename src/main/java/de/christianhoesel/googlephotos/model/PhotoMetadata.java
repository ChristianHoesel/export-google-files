package de.christianhoesel.googlephotos.model;

/**
 * Represents detailed metadata for a photo or video.
 */
public class PhotoMetadata {
    // Camera information
    private String cameraMake;
    private String cameraModel;
    private double focalLength;
    private double apertureFNumber;
    private int isoEquivalent;
    private String exposureTime;

    // Location information
    private Double latitude;
    private Double longitude;
    private String locationName;

    // Video-specific metadata
    private double fps;
    private String videoStatus;

    public String getCameraMake() {
        return cameraMake;
    }

    public void setCameraMake(String cameraMake) {
        this.cameraMake = cameraMake;
    }

    public String getCameraModel() {
        return cameraModel;
    }

    public void setCameraModel(String cameraModel) {
        this.cameraModel = cameraModel;
    }

    public double getFocalLength() {
        return focalLength;
    }

    public void setFocalLength(double focalLength) {
        this.focalLength = focalLength;
    }

    public double getApertureFNumber() {
        return apertureFNumber;
    }

    public void setApertureFNumber(double apertureFNumber) {
        this.apertureFNumber = apertureFNumber;
    }

    public int getIsoEquivalent() {
        return isoEquivalent;
    }

    public void setIsoEquivalent(int isoEquivalent) {
        this.isoEquivalent = isoEquivalent;
    }

    public String getExposureTime() {
        return exposureTime;
    }

    public void setExposureTime(String exposureTime) {
        this.exposureTime = exposureTime;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public String getLocationName() {
        return locationName;
    }

    public void setLocationName(String locationName) {
        this.locationName = locationName;
    }

    public double getFps() {
        return fps;
    }

    public void setFps(double fps) {
        this.fps = fps;
    }

    public String getVideoStatus() {
        return videoStatus;
    }

    public void setVideoStatus(String videoStatus) {
        this.videoStatus = videoStatus;
    }

    public boolean hasLocation() {
        return latitude != null && longitude != null;
    }

    @Override
    public String toString() {
        return "PhotoMetadata{" +
                "cameraMake='" + cameraMake + '\'' +
                ", cameraModel='" + cameraModel + '\'' +
                ", focalLength=" + focalLength +
                ", apertureFNumber=" + apertureFNumber +
                ", isoEquivalent=" + isoEquivalent +
                ", latitude=" + latitude +
                ", longitude=" + longitude +
                '}';
    }
}
