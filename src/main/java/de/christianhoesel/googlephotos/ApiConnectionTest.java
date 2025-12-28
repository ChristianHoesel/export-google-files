package de.christianhoesel.googlephotos;

import com.google.photos.library.v1.PhotosLibraryClient;
import com.google.photos.library.v1.proto.ListMediaItemsRequest;
import com.google.photos.library.v1.proto.ListAlbumsRequest;
import de.christianhoesel.googlephotos.service.GoogleAuthService;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Simple test to verify Google Photos API connectivity and permissions
 */
public class ApiConnectionTest {
    
    public static void main(String[] args) {
        System.out.println("=== Google Photos API Connection Test ===\n");
        
        try {
            // 1. Authenticate
            System.out.println("Step 1: Authenticating...");
            GoogleAuthService authService = new GoogleAuthService();
            PhotosLibraryClient client = authService.authorize();
            System.out.println("✓ Authentication successful\n");
            
            // 2. Check token scopes (requires access to the credential)
            System.out.println("Step 2: Checking OAuth2 Token Info...");
            System.out.println("  Note: Cannot directly access token from PhotosLibraryClient");
            System.out.println("  Please verify in Google Cloud Console that these scopes are enabled:");
            System.out.println("  - https://www.googleapis.com/auth/photoslibrary.readonly");
            System.out.println("  - https://www.googleapis.com/auth/photoslibrary.readonly.appcreateddata");
            System.out.println();
            
            // 3. Test listAlbums
            System.out.println("Step 3: Testing listAlbums()...");
            try {
                var albumRequest = ListAlbumsRequest.newBuilder()
                    .setPageSize(10)
                    .build();
                var albumResponse = client.listAlbumsCallable().call(albumRequest);
                System.out.println("✓ listAlbums call successful");
                System.out.println("  Albums count: " + albumResponse.getAlbumsCount());
                System.out.println("  Has next page: " + !albumResponse.getNextPageToken().isEmpty());
                System.out.println("  Next page token length: " + albumResponse.getNextPageToken().length());
                if (albumResponse.getAlbumsCount() > 0) {
                    System.out.println("  First album: " + albumResponse.getAlbums(0).getTitle());
                } else {
                    System.out.println("  ⚠ No albums returned!");
                }
            } catch (Exception e) {
                System.err.println("✗ listAlbums FAILED: " + e.getMessage());
                e.printStackTrace();
            }
            System.out.println();
            
            // 4. Test listMediaItems
            System.out.println("Step 4: Testing listMediaItems()...");
            try {
                var mediaRequest = ListMediaItemsRequest.newBuilder()
                    .setPageSize(10)
                    .build();
                var mediaResponse = client.listMediaItemsCallable().call(mediaRequest);
                System.out.println("✓ listMediaItems call successful");
                System.out.println("  Media items count: " + mediaResponse.getMediaItemsCount());
                System.out.println("  Has next page: " + !mediaResponse.getNextPageToken().isEmpty());
                System.out.println("  Next page token length: " + mediaResponse.getNextPageToken().length());
                if (mediaResponse.getMediaItemsCount() > 0) {
                    var firstItem = mediaResponse.getMediaItems(0);
                    System.out.println("  First item: " + firstItem.getFilename());
                    System.out.println("  MIME type: " + firstItem.getMimeType());
                } else {
                    System.out.println("  ⚠ No media items returned!");
                }
            } catch (Exception e) {
                System.err.println("✗ listMediaItems FAILED: " + e.getMessage());
                e.printStackTrace();
            }
            System.out.println();
            
            // 5. Summary and diagnostics
            System.out.println("=== DIAGNOSTICS ===");
            System.out.println();
            System.out.println("If you see 0 items but your Google Photos has content:");
            System.out.println();
            System.out.println("1. VERIFY YOUR GOOGLE ACCOUNT:");
            System.out.println("   - Open https://photos.google.com in your browser");
            System.out.println("   - Check which account is logged in");
            System.out.println("   - Verify there are photos/albums visible");
            System.out.println();
            System.out.println("2. CHECK OAUTH CONSENT SCREEN:");
            System.out.println("   - Go to: https://console.cloud.google.com/apis/credentials/consent");
            System.out.println("   - Click 'EDIT APP'");
            System.out.println("   - Go to 'Scopes' section");
            System.out.println("   - Click 'ADD OR REMOVE SCOPES'");
            System.out.println("   - Search for 'Google Photos Library API'");
            System.out.println("   - Make sure BOTH scopes are checked:");
            System.out.println("     ☐ .../auth/photoslibrary.readonly");
            System.out.println("     ☐ .../auth/photoslibrary.readonly.appcreateddata");
            System.out.println("   - Click 'UPDATE' and 'SAVE'");
            System.out.println();
            System.out.println("3. VERIFY API IS ENABLED:");
            System.out.println("   - Go to: https://console.cloud.google.com/apis/library/photoslibrary.googleapis.com");
            System.out.println("   - Make sure it shows 'API ENABLED'");
            System.out.println();
            System.out.println("4. RE-AUTHENTICATE:");
            System.out.println("   - Delete tokens folder: del /Q tokens\\*.*");
            System.out.println("   - Run the app again");
            System.out.println("   - Make sure to ALLOW ALL permissions when asked");
            System.out.println();
            
            // Close
            authService.close();
            System.out.println("✓ Test completed!");
            
        } catch (Exception e) {
            System.err.println("\n✗ Test FAILED!");
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
