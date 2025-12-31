package de.christianhoesel.googlephotos.service;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.common.ImageMetadata;
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata;
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter;
import org.apache.commons.imaging.formats.jpeg.xmp.JpegXmpRewriter;
import org.apache.commons.imaging.formats.tiff.TiffImageMetadata;
import org.apache.commons.imaging.formats.tiff.constants.ExifTagConstants;
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputDirectory;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.internal.xmp.XMPException;
import com.adobe.internal.xmp.XMPMeta;
import com.adobe.internal.xmp.XMPMetaFactory;
import com.adobe.internal.xmp.options.SerializeOptions;

import de.christianhoesel.googlephotos.model.GoogleTakeoutMetadata;
import de.christianhoesel.googlephotos.model.GoogleTakeoutMetadata.Person;

/**
 * Service to process Google Takeout files, add metadata, and organize into
 * monthly folders.
 */
public class TakeoutProcessorService {
	private static final Logger logger = LoggerFactory.getLogger(TakeoutProcessorService.class);

	private final AtomicInteger successCount = new AtomicInteger(0);
	private final AtomicInteger errorCount = new AtomicInteger(0);
	private final AtomicInteger skippedCount = new AtomicInteger(0);

	private final MotionPhotoExtractor motionPhotoExtractor = new MotionPhotoExtractor();
	private final VideoMetadataWriter videoMetadataWriter = new VideoMetadataWriter();

	/**
	 * Organization mode for files.
	 */
	public enum OrganizationMode {
		BY_MONTH, // Organize into YYYY/MM folders
		BY_ALBUM, // Organize into album folders
		FLAT // All files in root output directory
	}

	/**
	 * Processing options.
	 */
	public static class ProcessingOptions {
		private File outputDirectory;
		private boolean copyFiles = true; // true = copy, false = move
		private boolean addMetadata = true;
		private OrganizationMode organizationMode = OrganizationMode.BY_MONTH;
		private boolean skipDuplicates = true; // Skip duplicate files
		private DuplicateDetector.DuplicateDetectionMode duplicateDetectionMode = DuplicateDetector.DuplicateDetectionMode.HASH;

		public File getOutputDirectory() {
			return outputDirectory;
		}

		public void setOutputDirectory(File outputDirectory) {
			this.outputDirectory = outputDirectory;
		}

		public boolean isCopyFiles() {
			return copyFiles;
		}

		public void setCopyFiles(boolean copyFiles) {
			this.copyFiles = copyFiles;
		}

		public boolean isAddMetadata() {
			return addMetadata;
		}

		public void setAddMetadata(boolean addMetadata) {
			this.addMetadata = addMetadata;
		}

		public OrganizationMode getOrganizationMode() {
			return organizationMode;
		}

		public void setOrganizationMode(OrganizationMode organizationMode) {
			this.organizationMode = organizationMode;
		}

		public boolean isSkipDuplicates() {
			return skipDuplicates;
		}

		public void setSkipDuplicates(boolean skipDuplicates) {
			this.skipDuplicates = skipDuplicates;
		}

		public DuplicateDetector.DuplicateDetectionMode getDuplicateDetectionMode() {
			return duplicateDetectionMode;
		}

		public void setDuplicateDetectionMode(DuplicateDetector.DuplicateDetectionMode duplicateDetectionMode) {
			this.duplicateDetectionMode = duplicateDetectionMode;
		}

		// Backwards compatibility helpers
		public boolean isOrganizeByMonth() {
			return organizationMode == OrganizationMode.BY_MONTH;
		}

		public void setOrganizeByMonth(boolean organizeByMonth) {
			if (organizeByMonth) {
				this.organizationMode = OrganizationMode.BY_MONTH;
			} else {
				this.organizationMode = OrganizationMode.FLAT;
			}
		}
	}

	/**
	 * Processes a single media file with metadata.
	 *
	 * @param fileWithMetadata The media file with its metadata
	 * @param options          Processing options
	 * @return The destination file
	 * @throws IOException if processing fails
	 */
	public File processMediaFile(GoogleTakeoutService.MediaFileWithMetadata fileWithMetadata, ProcessingOptions options)
			throws IOException {
		File mediaFile = fileWithMetadata.getMediaFile();
		GoogleTakeoutMetadata metadata = fileWithMetadata.getMetadata();
		String albumName = fileWithMetadata.getAlbumName();

		logger.debug("Processing: {}", mediaFile.getName());

		// Determine destination path
		File destDir = determineDestinationDirectory(metadata, albumName, options);
		destDir.mkdirs();

		File destFile = new File(destDir, mediaFile.getName());

		// Handle duplicate filenames
		destFile = ensureUniqueFilename(destFile);

		// Check for Motion Photo and extract video if present
		if (fileWithMetadata.isImage() && motionPhotoExtractor.isMotionPhoto(mediaFile)) {
			logger.info("Detected Motion Photo: {}", mediaFile.getName());

			// Process the photo part first
			processPhotoFile(mediaFile, destFile, metadata, albumName, options);

			// Extract and process video part
			try {
				File extractedVideo = motionPhotoExtractor.extractVideo(mediaFile);
				if (extractedVideo != null && extractedVideo.exists()) {
					// Determine destination for video
					File videoDestDir = destDir;
					File videoDestFile = new File(videoDestDir, extractedVideo.getName());
					videoDestFile = ensureUniqueFilename(videoDestFile);

					// Move/copy video to destination first
					if (options.isCopyFiles()) {
						copyFile(extractedVideo, videoDestFile);
					} else {
						moveFile(extractedVideo, videoDestFile);
					}

					// Write metadata to final video file
					if (options.isAddMetadata()) {
						videoMetadataWriter.writeMetadata(videoDestFile, metadata, albumName);
					}

					// Clean up temp extracted video if it's different from destination
					if (extractedVideo.exists() && !extractedVideo.equals(videoDestFile)) {
						extractedVideo.delete();
					}

					logger.info("Extracted and processed Motion Photo video: {}", videoDestFile.getName());
				}
			} catch (Exception e) {
				logger.warn("Failed to extract video from Motion Photo {}: {}", mediaFile.getName(), e.getMessage());
			}

			return destFile;
		}

		// For JPEG images with metadata, write EXIF & XMP data
		if (options.isAddMetadata() && (metadata != null || albumName != null) && fileWithMetadata.isImage()
				&& isJpeg(mediaFile)) {

			try {
				writeExifMetadata(mediaFile, destFile, metadata, albumName);
				logger.debug("Added EXIF & XMP metadata to: {}", destFile.getName());
			} catch (Exception e) {
				logger.warn("Failed to write EXIF for {}: {}. Copying without metadata.", mediaFile.getName(),
						e.getMessage());
				// Fall back to simple copy
				copyFile(mediaFile, destFile);
			}
		} else if (fileWithMetadata.isVideo()) {
			// For videos, copy first then write XMP sidecar
			if (options.isCopyFiles()) {
				copyFile(mediaFile, destFile);
			} else {
				moveFile(mediaFile, destFile);
			}

			// Write XMP metadata to video
			if (options.isAddMetadata() && (metadata != null || albumName != null)) {
				try {
					videoMetadataWriter.writeMetadata(destFile, metadata, albumName);
					logger.debug("Added XMP metadata to video: {}", destFile.getName());
				} catch (Exception e) {
					logger.warn("Failed to write metadata to video {}: {}", destFile.getName(), e.getMessage());
				}
			}
		} else {
			// For other files, just copy/move
			if (options.isCopyFiles()) {
				copyFile(mediaFile, destFile);
			} else {
				moveFile(mediaFile, destFile);
			}
		}

		return destFile;
	}

	/**
	 * Processes the photo part of a file (helper for Motion Photo processing).
	 */
	private void processPhotoFile(File mediaFile, File destFile, GoogleTakeoutMetadata metadata, String albumName,
			ProcessingOptions options) throws IOException {
		if (options.isAddMetadata() && (metadata != null || albumName != null) && isJpeg(mediaFile)) {
			try {
				writeExifMetadata(mediaFile, destFile, metadata, albumName);
			} catch (Exception e) {
				logger.warn("Failed to write EXIF for Motion Photo {}: {}", mediaFile.getName(), e.getMessage());
				copyFile(mediaFile, destFile);
			}
		} else {
			if (options.isCopyFiles()) {
				copyFile(mediaFile, destFile);
			} else {
				moveFile(mediaFile, destFile);
			}
		}
	}

	/**
	 * Determines the destination directory based on metadata, album, and options.
	 */
	private File determineDestinationDirectory(GoogleTakeoutMetadata metadata, String albumName,
			ProcessingOptions options) {
		File baseDir = options.getOutputDirectory();

		switch (options.getOrganizationMode()) {
		case BY_ALBUM:
			// Organize by album with hierarchical year/month/album structure
			LocalDateTime albumDateTime = extractDateTime(metadata);
			String album = (albumName != null && !albumName.trim().isEmpty()) ? albumName : "No_Album";

			if (albumDateTime != null) {
				// Create YYYY/MM/Album folder structure
				String year = String.valueOf(albumDateTime.getYear());
				String month = String.format("%02d", albumDateTime.getMonthValue());
				return Paths.get(baseDir.toString(), year, month, album).toFile();
			} else {
				// No date available, put in "Unknown_Date/Album" folder
				return Paths.get(baseDir.toString(), "Unknown_Date", album).toFile();
			}

		case BY_MONTH:
			// Organize by year/month
			LocalDateTime dateTime = extractDateTime(metadata);
			if (dateTime != null) {
				// Create YYYY/MM folder structure
				String year = String.valueOf(dateTime.getYear());
				String month = String.format("%02d", dateTime.getMonthValue());
				return Paths.get(baseDir.toString(), year, month).toFile();
			} else {
				// No date available, put in "Unknown_Date" folder
				return new File(baseDir, "Unknown_Date");
			}

		case FLAT:
		default:
			// All files in root directory
			return baseDir;
		}
	}

	/**
	 * Extracts date/time from Google Takeout metadata. Prefers photoTakenTime over
	 * creationTime.
	 */
	private LocalDateTime extractDateTime(GoogleTakeoutMetadata metadata) {
		if (metadata == null) {
			return null;
		}

		// Try photoTakenTime first
		if (metadata.getPhotoTakenTime() != null) {
			long timestamp = metadata.getPhotoTakenTime().getTimestampAsLong();
			if (timestamp > 0) {
				return LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneId.systemDefault());
			}
		}

		// Fall back to creationTime
		if (metadata.getCreationTime() != null) {
			long timestamp = metadata.getCreationTime().getTimestampAsLong();
			if (timestamp > 0) {
				return LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneId.systemDefault());
			}
		}

		return null;
	}

	/**
	 * Writes EXIF metadata to a JPEG file.
	 *
	 * @throws IOException
	 * @throws FileNotFoundException
	 */
	private void writeExifMetadata(File sourceFile, File destFile, GoogleTakeoutMetadata metadata, String albumName)
			throws FileNotFoundException, IOException {

		// Read existing metadata
		TiffOutputSet outputSet = null;
		try {
			ImageMetadata imageMetadata = Imaging.getMetadata(sourceFile);
			if (imageMetadata instanceof JpegImageMetadata jpegMetadata) {
				TiffImageMetadata exif = jpegMetadata.getExif();
				if (exif != null) {
					outputSet = exif.getOutputSet();
				}
			}
		} catch (Exception e) {
			logger.debug("No existing EXIF data in {}", sourceFile.getName());
		}

		// Create new output set if none exists
		if (outputSet == null) {
			outputSet = new TiffOutputSet();
		}

		// Get or create EXIF directory
		TiffOutputDirectory exifDirectory = outputSet.getOrCreateExifDirectory();
		TiffOutputDirectory rootDirectory = outputSet.getOrCreateRootDirectory();

		// Write date/time
		LocalDateTime dateTime = extractDateTime(metadata);
		if (dateTime != null) {
			String exifDateTime = dateTime.format(DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss"));
			exifDirectory.removeField(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL);
			exifDirectory.add(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL, exifDateTime);
			exifDirectory.removeField(ExifTagConstants.EXIF_TAG_DATE_TIME_DIGITIZED);
			exifDirectory.add(ExifTagConstants.EXIF_TAG_DATE_TIME_DIGITIZED, exifDateTime);
		}

		// Write GPS coordinates
		if (metadata.getGeoDataExif() != null && metadata.getGeoDataExif().hasValidCoordinates()) {
			GoogleTakeoutMetadata.GeoData geoData = metadata.getGeoDataExif();

			// For now, skip GPS writing as the alpha version of commons-imaging has limited
			// support
			// The date/time metadata is the most important for organizing files
			logger.debug("GPS data available but skipped for {}", sourceFile.getName());
		}

		// Write description if available
		if (metadata.getDescription() != null && !metadata.getDescription().trim().isEmpty()) {
			rootDirectory.removeField(TiffTagConstants.TIFF_TAG_IMAGE_DESCRIPTION);
			rootDirectory.add(TiffTagConstants.TIFF_TAG_IMAGE_DESCRIPTION, metadata.getDescription());
		}

		// Write people/keywords if available
		// Note: EXIF doesn't have a standard "people" field. We append people names to
		// the description
		// or store them in the Software field as a workaround
		if (metadata.getPeople() != null && !metadata.getPeople().isEmpty()) {
			StringBuilder peopleStr = new StringBuilder();
			for (int i = 0; i < metadata.getPeople().size(); i++) {
				if (i > 0) {
					peopleStr.append(", ");
				}
				peopleStr.append(metadata.getPeople().get(i).getName());
			}

			// Store people in the Software field (not ideal but works for preservation)
			String peopleTag = "People: " + peopleStr;
			try {
				rootDirectory.removeField(TiffTagConstants.TIFF_TAG_SOFTWARE);
				rootDirectory.add(TiffTagConstants.TIFF_TAG_SOFTWARE, peopleTag);
				logger.debug("Added {} people to EXIF for {}", metadata.getPeople().size(), sourceFile.getName());
			} catch (Exception e) {
				logger.warn("Could not write people to EXIF for {}: {}", sourceFile.getName(), e.getMessage());
			}
		}

		// Write title if available and different from filename
		if (metadata.getTitle() != null && !metadata.getTitle().trim().isEmpty()
				&& !metadata.getTitle().equals(sourceFile.getName())) {
			// Store title in DocumentName field
			try {
				rootDirectory.removeField(TiffTagConstants.TIFF_TAG_DOCUMENT_NAME);
				rootDirectory.add(TiffTagConstants.TIFF_TAG_DOCUMENT_NAME, metadata.getTitle());
			} catch (Exception e) {
				logger.debug("Could not write title to EXIF: {}", e.getMessage());
			}
		}

		// Write album name if available
		// EXIF doesn't have a standard album field, but we can use XPKeywords or Artist
		// field
		// Many photo managers recognize Artist or Copyright fields for organization
		if (albumName != null && !albumName.trim().isEmpty()) {
			try {
				// Use Artist field to store album name (common practice in photo management)
				rootDirectory.removeField(TiffTagConstants.TIFF_TAG_ARTIST);
				rootDirectory.add(TiffTagConstants.TIFF_TAG_ARTIST, "Album: " + albumName);
				logger.debug("Added album '{}' to EXIF for {}", albumName, sourceFile.getName());
			} catch (Exception e) {
				logger.debug("Could not write album to EXIF: {}", e.getMessage());
			}
		}

		// Write to destination file with EXIF
		File tempFile = new File(destFile.getParentFile(), destFile.getName() + ".exif.tmp");
		try (FileOutputStream fos = new FileOutputStream(tempFile);
				BufferedOutputStream bos = new BufferedOutputStream(fos)) {

			new ExifRewriter().updateExifMetadataLossless(sourceFile, bos, outputSet);
		}

		// Now add XMP metadata
		try {
			writeXmpMetadata(tempFile, destFile, metadata, albumName);
			// Delete temp file after successful XMP write
			tempFile.delete();
			logger.debug("EXIF and XMP metadata written to: {}", destFile.getName());
		} catch (Exception e) {
			logger.warn("Could not write XMP metadata for {}: {}. Using EXIF-only version.", sourceFile.getName(),
					e.getMessage());
			// If XMP writing fails, just rename temp file to dest
			if (!tempFile.renameTo(destFile)) {
				Files.copy(tempFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
				tempFile.delete();
			}
		}
	}

	/**
	 * Writes XMP metadata to a JPEG file using JpegXmpRewriter. XMP is a modern
	 * standard for storing extended metadata that doesn't fit well in EXIF.
	 *
	 * @throws XMPException
	 * @throws IOException
	 */
	private void writeXmpMetadata(File sourceFile, File destFile, GoogleTakeoutMetadata metadata, String albumName)
			throws XMPException, IOException {

		// Create XMP metadata
		XMPMeta xmpMeta = XMPMetaFactory.create();

		// Dublin Core namespace for basic metadata
		String dcNS = "http://purl.org/dc/elements/1.1/";
		String xmpNS = "http://ns.adobe.com/xap/1.0/";
		String lrNS = "http://ns.adobe.com/lightroom/1.0/";

		// Register namespaces
		try {
			XMPMetaFactory.getSchemaRegistry().registerNamespace(dcNS, "dc");
			XMPMetaFactory.getSchemaRegistry().registerNamespace(xmpNS, "xmp");
			XMPMetaFactory.getSchemaRegistry().registerNamespace(lrNS, "lr");
		} catch (XMPException e) {
			// Namespaces might already be registered
		}

		// Add people as dc:subject (keywords)
		if (metadata != null && metadata.getPeople() != null && !metadata.getPeople().isEmpty()) {
			com.adobe.internal.xmp.options.PropertyOptions arrayOptions = new com.adobe.internal.xmp.options.PropertyOptions();
			arrayOptions.setArray(true);

			for (Person person : metadata.getPeople()) {
				if (person.getName() != null && !person.getName().trim().isEmpty()) {
					xmpMeta.appendArrayItem(dcNS, "subject", arrayOptions, person.getName(), null);
					logger.debug("Added person '{}' to XMP keywords", person.getName());
				}
			}
		}

		// Add album name
		if (albumName != null && !albumName.trim().isEmpty()) {
			xmpMeta.setProperty(lrNS, "hierarchicalSubject", albumName);
			logger.debug("Added album '{}' to XMP", albumName);
		}

		// Add title
		if (metadata != null && metadata.getTitle() != null && !metadata.getTitle().trim().isEmpty()) {
			xmpMeta.setLocalizedText(dcNS, "title", "x-default", "x-default", metadata.getTitle());
		}

		// Add description
		if (metadata != null && metadata.getDescription() != null && !metadata.getDescription().trim().isEmpty()) {
			xmpMeta.setLocalizedText(dcNS, "description", "x-default", "x-default", metadata.getDescription());
		}

		// Serialize XMP to string
		SerializeOptions serializeOptions = new SerializeOptions();
		serializeOptions.setUseCompactFormat(true);
		serializeOptions.setOmitPacketWrapper(true);
		String xmpXml = XMPMetaFactory.serializeToString(xmpMeta, serializeOptions);

		// Use JpegXmpRewriter to properly insert XMP into JPEG
		JpegXmpRewriter xmpRewriter = new JpegXmpRewriter();
		try (FileInputStream fis = new FileInputStream(sourceFile);
				FileOutputStream fos = new FileOutputStream(destFile);
				BufferedOutputStream bos = new BufferedOutputStream(fos)) {

			xmpRewriter.updateXmpXml(fis, bos, xmpXml);
			logger.debug("XMP metadata written using JpegXmpRewriter to: {}", destFile.getName());
		}
	}

	/**
	 * Checks if a file is a JPEG image.
	 */
	private boolean isJpeg(File file) {
		String name = file.getName().toLowerCase();
		return name.endsWith(".jpg") || name.endsWith(".jpeg");
	}

	/**
	 * Ensures the filename is unique by appending a number if needed. Limits the
	 * number of attempts to avoid infinite loops.
	 */
	private File ensureUniqueFilename(File file) {
		if (!file.exists()) {
			return file;
		}

		String name = file.getName();
		String baseName;
		String extension;

		int lastDot = name.lastIndexOf('.');
		if (lastDot > 0) {
			baseName = name.substring(0, lastDot);
			extension = name.substring(lastDot);
		} else {
			baseName = name;
			extension = "";
		}

		int counter = 1;
		File uniqueFile;
		final int MAX_ATTEMPTS = 10000; // Prevent infinite loops

		do {
			uniqueFile = new File(file.getParentFile(), baseName + "_" + counter + extension);
			counter++;

			if (counter > MAX_ATTEMPTS) {
				// Use timestamp-based naming as fallback
				String timestamp = String.valueOf(System.currentTimeMillis());
				uniqueFile = new File(file.getParentFile(), baseName + "_" + timestamp + extension);
				break;
			}
		} while (uniqueFile.exists());

		return uniqueFile;
	}

	/**
	 * Copies a file.
	 */
	private void copyFile(File source, File dest) throws IOException {
		Files.copy(source.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
	}

	/**
	 * Moves a file.
	 */
	private void moveFile(File source, File dest) throws IOException {
		Files.move(source.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
	}

	/**
	 * Processes all files in a list.
	 */
	public void processAllFiles(java.util.List<GoogleTakeoutService.MediaFileWithMetadata> files,
			ProcessingOptions options, ProgressCallback callback) {
		successCount.set(0);
		errorCount.set(0);
		skippedCount.set(0);

		// Initialize duplicate detector if enabled
		DuplicateDetector duplicateDetector = null;
		if (options.isSkipDuplicates()) {
			duplicateDetector = new DuplicateDetector(options.getDuplicateDetectionMode());
			logger.info("Duplicate detection enabled with mode: {}", options.getDuplicateDetectionMode());
		}

		int total = files.size();
		int current = 0;

		for (GoogleTakeoutService.MediaFileWithMetadata file : files) {
			current++;

			if (callback != null) {
				callback.onProgress(current, total, file.getMediaFile().getName());
			}

			// Check for duplicates if enabled
			if (duplicateDetector != null
					&& duplicateDetector.isDuplicate(file.getMediaFile(), options.getOutputDirectory())) {
				logger.debug("Skipping duplicate: {}", file.getMediaFile().getName());
				skippedCount.incrementAndGet();
				continue;
			}

			try {
				processMediaFile(file, options);
				successCount.incrementAndGet();
			} catch (Exception e) {
				logger.error("Failed to process {}: {}", file.getMediaFile().getName(), e.getMessage());
				errorCount.incrementAndGet();
			}
		}

		if (callback != null) {
			callback.onComplete(successCount.get(), errorCount.get(), skippedCount.get());
		}
	}

	/**
	 * Callback interface for progress updates.
	 */
	public interface ProgressCallback {
		void onProgress(int current, int total, String currentFile);

		void onComplete(int success, int errors, int skipped);
	}

	/**
	 * Gets processing statistics.
	 */
	public int getSuccessCount() {
		return successCount.get();
	}

	public int getErrorCount() {
		return errorCount.get();
	}

	public int getSkippedCount() {
		return skippedCount.get();
	}
}
