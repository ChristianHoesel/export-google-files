package de.christianhoesel.googlephotos.service;

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
import com.google.auth.http.HttpTransportFactory;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.UserCredentials;
import com.google.photos.library.v1.PhotosLibraryClient;
import com.google.photos.library.v1.PhotosLibrarySettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

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
	 * 
	 * @return PhotosLibraryClient for accessing Google Photos API
	 * @throws IOException              if credentials file is not found
	 * @throws GeneralSecurityException if there's a security issue
	 */
	public PhotosLibraryClient authorize() throws IOException, GeneralSecurityException {
		logger.info("Starting Google Photos authentication...");

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
		try (InputStream in = new FileInputStream(CREDENTIALS_FILE_PATH)) {
			clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));
		}

		// Build flow and trigger user authorization request
		GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(httpTransport, JSON_FACTORY,
				clientSecrets, SCOPES).setDataStoreFactory(new FileDataStoreFactory(new File(TOKENS_DIRECTORY_PATH)))
				.setAccessType("offline").build();

		// Use port 0 for dynamic port allocation to avoid conflicts
		LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(0).build();

		credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
		logger.info("Successfully authenticated with Google Photos API");
		logger.info("Access token: {}", credential.getAccessToken() != null ? "present" : "missing");
		logger.info("Refresh token: {}", credential.getRefreshToken() != null ? "present" : "missing");

		// Create an HttpTransportFactory for com.google.auth
		HttpTransportFactory transportFactory = () -> new com.google.api.client.http.javanet.NetHttpTransport();

		// Create UserCredentials with all required parameters
		UserCredentials userCredentials = UserCredentials.newBuilder()
				.setClientId(clientSecrets.getDetails().getClientId())
				.setClientSecret(clientSecrets.getDetails().getClientSecret())
				.setRefreshToken(credential.getRefreshToken())
				.setAccessToken(new AccessToken(credential.getAccessToken(),
						new Date(credential.getExpirationTimeMilliseconds())))
				.setHttpTransportFactory(transportFactory)
				.build();

		// Create PhotosLibrarySettings with the credentials
		PhotosLibrarySettings settings = PhotosLibrarySettings.newBuilder()
				.setCredentialsProvider(FixedCredentialsProvider.create(userCredentials)).build();

		photosLibraryClient = PhotosLibraryClient.initialize(settings);
		logger.info("PhotosLibraryClient initialized successfully");
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
