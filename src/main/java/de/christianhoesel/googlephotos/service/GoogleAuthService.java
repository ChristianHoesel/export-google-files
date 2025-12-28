package de.christianhoesel.googlephotos.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.UserCredentials;
import com.google.photos.library.v1.PhotosLibraryClient;
import com.google.photos.library.v1.PhotosLibrarySettings;

/**
 * Service for handling Google Photos API authentication.
 */
public class GoogleAuthService {
	private static final Logger logger = LoggerFactory.getLogger(GoogleAuthService.class);

	private static final String APPLICATION_NAME = "Google Photos Exporter";
	private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
	private static final String TOKENS_DIRECTORY_PATH = "tokens";
	private static final String CREDENTIALS_FILE_PATH = "credentials.json";

	// Scopes required for accessing Google Photos
	// Using both readonly scopes to ensure gRPC API access
	private static final List<String> SCOPES = Arrays.asList(
			"https://www.googleapis.com/auth/photoslibrary.readonly",
			"https://www.googleapis.com/auth/photoslibrary.readonly.appcreateddata");

	private PhotosLibraryClient photosLibraryClient;
	private NetHttpTransport httpTransport;
	private Credential credential;
	private GoogleClientSecrets clientSecrets;

	/**
	 * Initializes the authentication service and creates a PhotosLibraryClient.
	 * Based on the official Google Photos Library sample implementation.
	 * 
	 * @return PhotosLibraryClient for accessing Google Photos API
	 * @throws IOException              if credentials file is not found
	 * @throws GeneralSecurityException if there's a security issue
	 */
	public PhotosLibraryClient authorize() throws IOException, GeneralSecurityException {
		logger.info("Starting Google Photos authentication...");
		logger.info("Using official Google Photos Library authentication pattern");

		// Check if credentials file exists
		Path credentialsPath = Paths.get(CREDENTIALS_FILE_PATH);
		if (!Files.exists(credentialsPath)) {
			logger.error("Credentials file not found at: {}", credentialsPath.toAbsolutePath());
			throw new FileNotFoundException(
					"Credentials file not found. Please download credentials.json from Google Cloud Console "
							+ "and place it in: " + credentialsPath.toAbsolutePath());
		}

		httpTransport = GoogleNetHttpTransport.newTrustedTransport();

		// Load client secrets
		GoogleClientSecrets clientSecrets;
		try (InputStream in = new FileInputStream(CREDENTIALS_FILE_PATH)) {
			clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));
		}
		
		String clientId = clientSecrets.getDetails().getClientId();
		String clientSecret = clientSecrets.getDetails().getClientSecret();

		logger.info("Client ID: {}", clientId);
		logger.info("Scopes: {}", SCOPES);

		// Build OAuth flow - EXACTLY like Google's official sample
		GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
				httpTransport,
				JSON_FACTORY,
				clientSecrets,
				SCOPES)
				.setDataStoreFactory(new FileDataStoreFactory(new File(TOKENS_DIRECTORY_PATH)))
				.setAccessType("offline")
				.build();

		// Use port 0 for dynamic port allocation
		LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(0).build();
		
		credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
		
		logger.info("✓ OAuth flow completed");
		logger.info("✓ Refresh token: {}", credential.getRefreshToken() != null ? "present" : "MISSING");

		// Create UserCredentials - CRITICAL: Use ONLY refreshToken like Google's sample!
		// Do NOT set AccessToken - let the library handle token refresh automatically
		UserCredentials userCredentials = UserCredentials.newBuilder()
				.setClientId(clientId)
				.setClientSecret(clientSecret)
				.setRefreshToken(credential.getRefreshToken())
				// NOTE: No .setAccessToken() call - this is intentional!
				.build();

		logger.info("✓ UserCredentials created with refresh token");

		// Create PhotosLibrarySettings
		PhotosLibrarySettings settings = PhotosLibrarySettings.newBuilder()
				.setCredentialsProvider(FixedCredentialsProvider.create(userCredentials))
				.build();

		photosLibraryClient = PhotosLibraryClient.initialize(settings);
		logger.info("✓ PhotosLibraryClient initialized");

		// Test API access immediately
		logger.info("=== TESTING API ACCESS ===");
		try {
			var testRequest = com.google.photos.library.v1.proto.ListMediaItemsRequest.newBuilder()
					.setPageSize(5)
					.build();
			var testResponse = photosLibraryClient.listMediaItemsCallable().call(testRequest);

			int count = testResponse.getMediaItemsCount();
			logger.info("✓✓✓ API TEST SUCCESSFUL!");
			logger.info("  → Returned {} items", count);
			
			if (count > 0) {
				logger.info("  → SUCCESS! Your account works!");
				logger.info("  → First photo: {}", testResponse.getMediaItems(0).getFilename());
			} else {
				logger.warn("⚠ API returned 0 items");
				logger.warn("  → Check if you selected the correct Google account");
				logger.warn("  → Verify your account has photos at https://photos.google.com");
			}

		} catch (com.google.api.gax.rpc.PermissionDeniedException e) {
			logger.error("✗✗✗ PERMISSION DENIED!");
			logger.error("Scopes are missing in Google Cloud Console OAuth Consent Screen");
			logger.error("Fix at: https://console.cloud.google.com/apis/credentials/consent");
			throw new RuntimeException("PERMISSION DENIED - Add scopes in Cloud Console", e);
		} catch (Exception e) {
			logger.error("✗ API test failed: {}", e.getMessage());
			throw new RuntimeException("API test failed", e);
		}

		return photosLibraryClient;
	}

	/**
	 * Gets the current PhotosLibraryClient or creates a new one if not initialized.
	 * 
	 * @return PhotosLibraryClient instance
	 * @throws IOException              if there's an IO error
	 * @throws GeneralSecurityException if there's a security error
	 */
	public PhotosLibraryClient getClient() throws IOException, GeneralSecurityException {
		if (photosLibraryClient == null) {
			return authorize();
		}
		return photosLibraryClient;
	}

	/**
	 * Closes the PhotosLibraryClient connection.
	 */
	public void close() {
		if (photosLibraryClient != null) {
			try {
				photosLibraryClient.close();
				logger.info("Google Photos client connection closed");
			} catch (Exception e) {
				logger.warn("Error closing PhotosLibraryClient", e);
			}
		}
	}

	/**
	 * Clears stored authentication tokens.
	 * 
	 * @throws IOException if there's an error deleting tokens
	 */
	public void clearTokens() throws IOException {
		Path tokensPath = Paths.get(TOKENS_DIRECTORY_PATH);
		if (Files.exists(tokensPath)) {
			try (Stream<Path> s = Files.walk(tokensPath)) {
				s.sorted((a, b) -> -a.compareTo(b)).forEach(path -> {
					try {
						Files.delete(path);
					} catch (IOException e) {
						logger.warn("Could not delete token file: {}", path);
					}
				});
			}
			logger.info("Authentication tokens cleared");
		}
	}
}
