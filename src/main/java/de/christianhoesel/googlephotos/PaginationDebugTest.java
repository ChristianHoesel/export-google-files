package de.christianhoesel.googlephotos;

import com.google.photos.library.v1.PhotosLibraryClient;
import com.google.photos.library.v1.proto.ListMediaItemsRequest;
import de.christianhoesel.googlephotos.service.GoogleAuthService;

/**
 * Debug tool to investigate the pagination token issue
 */
public class PaginationDebugTest {
    
    public static void main(String[] args) {
        System.out.println("=== Pagination Token Debug Test ===\n");
        
        try {
            // Authenticate
            System.out.println("Authenticating...");
            GoogleAuthService authService = new GoogleAuthService();
            PhotosLibraryClient client = authService.authorize();
            System.out.println("✓ Authenticated\n");
            
            // Make request
            System.out.println("Making listMediaItems request...");
            var request = ListMediaItemsRequest.newBuilder()
                .setPageSize(100)
                .build();
            
            var response = client.listMediaItemsCallable().call(request);
            
            // Analyze response
            System.out.println("\n=== RESPONSE ANALYSIS ===");
            System.out.println("Items count: " + response.getMediaItemsCount());
            System.out.println("Has next page token: " + !response.getNextPageToken().isEmpty());
            System.out.println("Next page token length: " + response.getNextPageToken().length());
            System.out.println("Next page token (first 50 chars): " + 
                (response.getNextPageToken().length() > 0 ? 
                    response.getNextPageToken().substring(0, Math.min(50, response.getNextPageToken().length())) : "EMPTY"));
            
            System.out.println("\n=== DIAGNOSIS ===");
            
            if (response.getMediaItemsCount() == 0 && !response.getNextPageToken().isEmpty()) {
                System.out.println("⚠⚠⚠ CRITICAL FINDING ⚠⚠⚠");
                System.out.println();
                System.out.println("The API returns:");
                System.out.println("  - 0 items");
                System.out.println("  - BUT a pagination token");
                System.out.println();
                System.out.println("This is ABNORMAL behavior and indicates:");
                System.out.println();
                System.out.println("1. SCOPE PROBLEM (Most Likely!)");
                System.out.println("   The token HAS access to the API (no Permission Denied)");
                System.out.println("   BUT does NOT have the right scope to see the media items!");
                System.out.println("   The API knows there are MORE pages but can't show them due to scope restrictions.");
                System.out.println();
                System.out.println("2. PARTIAL PERMISSIONS");
                System.out.println("   The OAuth consent screen might have:");
                System.out.println("   - Only ONE of the two required scopes");
                System.out.println("   - OR the scopes are not properly saved");
                System.out.println();
                System.out.println("=== ACTION REQUIRED ===");
                System.out.println();
                System.out.println("Go to: https://console.cloud.google.com/apis/credentials/consent");
                System.out.println("1. Click 'EDIT APP'");
                System.out.println("2. Go to Scopes section");
                System.out.println("3. Verify BOTH scopes are listed:");
                System.out.println("   - .../auth/photoslibrary.readonly");
                System.out.println("   - .../auth/photoslibrary.readonly.appcreateddata");
                System.out.println("4. If NOT both visible: Click 'ADD OR REMOVE SCOPES'");
                System.out.println("5. Search 'photos' and add BOTH");
                System.out.println("6. Click 'UPDATE'");
                System.out.println("7. Click 'SAVE AND CONTINUE' until the end");
                System.out.println("8. Delete tokens: del /Q tokens\\*.*");
                System.out.println("9. Run this test again");
                System.out.println();
                System.out.println("The pagination token being present while items = 0");
                System.out.println("is a SMOKING GUN for incorrect scopes!");
            } else if (response.getMediaItemsCount() > 0) {
                System.out.println("✓ Everything looks normal!");
                System.out.println("  Items returned: " + response.getMediaItemsCount());
            } else if (response.getNextPageToken().isEmpty()) {
                System.out.println("⚠ API returns 0 items and no pagination token");
                System.out.println("This means the library is genuinely empty.");
            }
            
            // Try to follow the pagination token if present
            if (response.getMediaItemsCount() == 0 && !response.getNextPageToken().isEmpty()) {
                System.out.println("\n=== ATTEMPTING TO FOLLOW PAGINATION TOKEN ===");
                System.out.println("(This will likely also return 0 items due to scope issue)");
                
                try {
                    var request2 = ListMediaItemsRequest.newBuilder()
                        .setPageSize(100)
                        .setPageToken(response.getNextPageToken())
                        .build();
                    
                    var response2 = client.listMediaItemsCallable().call(request2);
                    System.out.println("Page 2 items: " + response2.getMediaItemsCount());
                    System.out.println("Page 2 has next token: " + !response2.getNextPageToken().isEmpty());
                    
                    if (response2.getMediaItemsCount() == 0 && !response2.getNextPageToken().isEmpty()) {
                        System.out.println("\n✗ CONFIRMED: Pagination continues but returns 0 items!");
                        System.out.println("This is DEFINITIVE PROOF of a scope/permission issue!");
                    }
                } catch (Exception e) {
                    System.out.println("Error following token: " + e.getMessage());
                }
            }
            
            authService.close();
            
        } catch (Exception e) {
            System.err.println("\n✗ Test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
