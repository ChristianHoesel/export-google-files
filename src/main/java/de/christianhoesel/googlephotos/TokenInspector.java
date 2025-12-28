package de.christianhoesel.googlephotos;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;

/**
 * Tool to inspect the current OAuth token and its scopes
 */
public class TokenInspector {
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";
    private static final String CREDENTIALS_FILE_PATH = "credentials.json";
    private static final List<String> SCOPES = Arrays.asList("https://www.googleapis.com/auth/photoslibrary.readonly");

    public static void main(String[] args) {
        try {
            System.out.println("=== Google Photos OAuth Token Inspector ===\n");

            // Load client secrets
            GoogleClientSecrets clientSecrets;
            try (FileInputStream in = new FileInputStream(CREDENTIALS_FILE_PATH)) {
                clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));
            }

            NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

            // Build flow
            GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                    httpTransport, JSON_FACTORY, clientSecrets, SCOPES)
                    .setDataStoreFactory(new FileDataStoreFactory(new File(TOKENS_DIRECTORY_PATH)))
                    .setAccessType("offline")
                    .build();

            // Load existing credential
            Credential credential = flow.loadCredential("user");

            if (credential == null) {
                System.out.println("❌ No stored credentials found in tokens/ directory");
                return;
            }

            System.out.println("✓ Credentials found!");
            System.out.println("\nToken Information:");
            System.out.println("==================");
            System.out.println("Access Token: " + (credential.getAccessToken() != null ? "Present (length: " + credential.getAccessToken().length() + ")" : "Missing"));
            System.out.println("Refresh Token: " + (credential.getRefreshToken() != null ? "Present" : "Missing"));
            System.out.println("Expires In: " + credential.getExpiresInSeconds() + " seconds");
            
            if (credential.getAccessToken() != null) {
                System.out.println("\nAccess Token (first 50 chars): " + credential.getAccessToken().substring(0, Math.min(50, credential.getAccessToken().length())) + "...");
            }

            // Try to get token info from Google
            System.out.println("\n⚠️  Note: To see which scopes are actually in the token,");
            System.out.println("visit: https://www.googleapis.com/oauth2/v1/tokeninfo?access_token=" + credential.getAccessToken());
            System.out.println("\nOr use curl:");
            System.out.println("curl \"https://www.googleapis.com/oauth2/v1/tokeninfo?access_token=" + credential.getAccessToken() + "\"");

        } catch (Exception e) {
            System.err.println("❌ Error inspecting token: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
