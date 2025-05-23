package com.xelabooks.app.data

import android.app.Activity
import android.content.Context
import android.util.Log
import com.xelabooks.app.R
import com.microsoft.identity.client.*
import com.microsoft.identity.client.exception.MsalClientException
import com.microsoft.identity.client.exception.MsalException
import com.microsoft.identity.client.exception.MsalServiceException
import com.microsoft.identity.client.exception.MsalUiRequiredException
import com.microsoft.identity.client.exception.MsalDeclinedScopeException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import com.microsoft.identity.client.ICurrentAccountResult

/**
 * OneDrive manager that connects to Microsoft Graph API with folder navigation
 */
class OneDriveManager(private val context: Context) {
    private val TAG = "OneDriveManager"
    private var mSingleAccountApp: ISingleAccountPublicClientApplication? = null
    private var currentAccessToken: String? = null
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    
    /**
     * Represents an item in OneDrive (file or folder)
     */
    data class DriveItem(
        val id: String,
        val name: String,
        val size: Long,
        val isFolder: Boolean,
        val downloadUrl: String = "",
        val parentId: String = "root", // Default to root if not specified
        val path: String = ""          // Full path to item
    ) {
        val isAudioFile: Boolean
            get() = !isFolder && AUDIO_EXTENSIONS.any { name.lowercase().endsWith(it) }
            
        val isImageFile: Boolean
            get() = !isFolder && IMAGE_EXTENSIONS.any { name.lowercase().endsWith(it) }
    }
    
    /**
     * Initialize the OneDrive MSAL library
     */
    suspend fun initialize(): Result<ISingleAccountPublicClientApplication> = 
        suspendCancellableCoroutine { continuation ->
            // If already initialized, return the existing instance
            if (mSingleAccountApp != null) {
                Log.d(TAG, "MSAL application already initialized")
                continuation.resume(Result.success(mSingleAccountApp!!))
                return@suspendCancellableCoroutine
            }
            
            // Create a new instance
            PublicClientApplication.createSingleAccountPublicClientApplication(
                context,
                R.raw.auth_config_single_account,
                object : IPublicClientApplication.ISingleAccountApplicationCreatedListener {
                    override fun onCreated(application: ISingleAccountPublicClientApplication) {
                        mSingleAccountApp = application
                        Log.d(TAG, "MSAL application created successfully")
                        continuation.resume(Result.success(application))
                    }
                    
                    override fun onError(exception: MsalException) {
                        Log.e(TAG, "Failed to create MSAL application", exception)
                        continuation.resume(Result.failure(exception))
                    }
                }
            )
        }

    /**
     * Get the current signed-in account if any
     * Must be called from a background thread
     */
    private suspend fun getCurrentAccount(): IAccount? = withContext(Dispatchers.IO) {
        try {
            val currentAccountResult = mSingleAccountApp?.currentAccount
            currentAccountResult?.currentAccount
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current account", e)
            null
        }
    }
    
    /**
     * Force removal of all accounts from the token cache
     * Safe to call from any thread
     */
    fun forceSignOut() {
        try {
            // Use the standard signOut method, but clear any local state as well
            mSingleAccountApp?.let { app ->
                app.signOut(object : ISingleAccountPublicClientApplication.SignOutCallback {
                    override fun onSignOut() {
                        Log.d(TAG, "Account signed out successfully")
                    }
                    
                    override fun onError(exception: MsalException) {
                        // Don't log "no signed in account" as error since it's expected in some cases
                        if (exception.message?.contains("no signed in account") == true) {
                            Log.d(TAG, "No account to sign out (already signed out)")
                        } else {
                            Log.e(TAG, "Error signing out account", exception)
                        }
                    }
                })
            }
            
            // Clear local state regardless of sign-out result
            currentAccessToken = null
            Log.d(TAG, "Cleared all local authentication state")
        } catch (e: Exception) {
            Log.e(TAG, "Error during force sign-out", e)
            // Still clear local state
            currentAccessToken = null
        }
    }
    
    /**
     * Sign in to OneDrive with silent acquisition first if possible
     * @param forceNewAccount If true, forces a new account selection
     * Note: This must be called from a background thread for the account checking
     */
    fun signIn(activity: Activity, forceNewAccount: Boolean = false, callback: (Boolean, String?) -> Unit) {
        if (mSingleAccountApp == null) {
            callback(false, "MSAL application not initialized. Call initialize() first")
            return
        }
        
        // Define required scopes - removed offline_access as it's being declined
        val scopes = arrayOf(
            "Files.Read.All", 
            "User.Read"
        )
        
        Log.d(TAG, "Signing in with scopes: ${scopes.joinToString()}")
        
        // Always do a clean sign-in to avoid account mismatch issues
        // Force sign out first to clear any cached state
        try {
            mSingleAccountApp?.signOut(object : ISingleAccountPublicClientApplication.SignOutCallback {
                override fun onSignOut() {
                    Log.d(TAG, "Successfully signed out before new sign-in")
                    // Now perform interactive sign-in
                    interactiveSignIn(activity, scopes, null, callback, true) // Always force account selection
                }
                
                override fun onError(exception: MsalException) {
                    Log.d(TAG, "Sign-out before sign-in completed (may have been no account): ${exception.message}")
                    // Continue with sign-in even if sign-out failed
                    interactiveSignIn(activity, scopes, null, callback, true) // Always force account selection
                }
            })
        } catch (e: Exception) {
            Log.d(TAG, "Exception during pre-sign-in sign-out: ${e.message}")
            // Continue with sign-in even if sign-out failed
            interactiveSignIn(activity, scopes, null, callback, true) // Always force account selection
        }
    }
    
    /**
     * Perform interactive sign-in
     */
    private fun interactiveSignIn(
        activity: Activity, 
        scopes: Array<String>, 
        loginHint: String?, 
        callback: (Boolean, String?) -> Unit,
        forceNewAccount: Boolean = false
    ) {
        try {
            Log.d(TAG, "Starting interactive sign-in with scopes: ${scopes.joinToString()}")
            
            // Create acquire token parameters
            val parameters = AcquireTokenParameters.Builder()
                .startAuthorizationFromActivity(activity)
                .withScopes(scopes.toList())
                .withCallback(object : AuthenticationCallback {
                    override fun onSuccess(authenticationResult: IAuthenticationResult) {
                        // Log granted scopes
                        val grantedScopes = authenticationResult.accessToken
                        Log.d(TAG, "Interactive sign-in successful")
                        
                        currentAccessToken = authenticationResult.accessToken
                        Log.d(TAG, "Successfully signed in interactively")
                        callback(true, null)
                    }
                    
                    override fun onError(exception: MsalException) {
                        Log.e(TAG, "Interactive sign-in failed", exception)
                        var message = "Authentication failed"
                        when (exception) {
                            is MsalClientException -> {
                                if (exception.message?.contains("does not match") == true) {
                                    message = "Please try signing in again. If this persists, try using a different account."
                                } else {
                                    message = "Client error: ${exception.message}"
                                }
                            }
                            is MsalServiceException -> message = "Service error: ${exception.message}"
                            is MsalDeclinedScopeException -> {
                                // This happens when some scopes were declined
                                message = "Required permissions were declined. Please ensure Files.Read.Selected is removed from your Azure app registration and only Files.Read.All is enabled."
                            }
                            else -> message = "Authentication error: ${exception.message}"
                        }
                        callback(false, message)
                    }
                    
                    override fun onCancel() {
                        Log.d(TAG, "Interactive sign-in was cancelled")
                        callback(false, "Authentication was cancelled")
                    }
                })
            
            // Add login hint if available
            if (!loginHint.isNullOrEmpty()) {
                Log.d(TAG, "Using login hint: $loginHint")
                parameters.withLoginHint(loginHint)
            }
            
            // Only force account selection if explicitly requested
            if (forceNewAccount) {
                parameters.withPrompt(Prompt.SELECT_ACCOUNT)
            }
            
            // Launch the interactive authentication
            mSingleAccountApp!!.acquireToken(parameters.build())
        } catch (e: Exception) {
            Log.e(TAG, "Error during interactive sign-in", e)
            callback(false, "Error starting authentication: ${e.message}")
        }
    }
    
    /**
     * Sign out from OneDrive
     */
    fun signOut(callback: (Boolean, String?) -> Unit) {
        if (mSingleAccountApp == null) {
            Log.d(TAG, "Cannot sign out - MSAL not initialized")
            callback(false, "MSAL application not initialized")
            return
        }
        
        try {
            // Just sign out directly without checking current account
            mSingleAccountApp!!.signOut(object : ISingleAccountPublicClientApplication.SignOutCallback {
                override fun onSignOut() {
                    currentAccessToken = null
                    Log.d(TAG, "Successfully signed out")
                    callback(true, null)
                }
                
                override fun onError(exception: MsalException) {
                    Log.e(TAG, "Failed to sign out", exception)
                    // Still clear token locally
                    currentAccessToken = null
                    callback(false, "Sign-out from service failed, but local state cleared")
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error during sign-out", e)
            // Still clear token locally on error
            currentAccessToken = null
            callback(false, "Error during sign-out, but local state cleared: ${e.message}")
        }
    }
    
    /**
     * Check if user is signed in to OneDrive
     */
    fun isSignedIn(): Boolean {
        return mSingleAccountApp != null && currentAccessToken != null
    }

    /**
     * List contents of a folder in OneDrive (files and subfolders)
     * @param folderId ID of the folder to list contents from, defaults to "root" for root folder
     */
    suspend fun listFolderContents(folderId: String = "root"): Result<List<DriveItem>> = withContext(Dispatchers.IO) {
        try {
            if (mSingleAccountApp == null || currentAccessToken == null) {
                return@withContext Result.failure(Exception("Not initialized or signed in"))
            }
            
            // Construct URL for folder contents
            val apiUrl = if (folderId == "root") {
                "https://graph.microsoft.com/v1.0/me/drive/root/children"
            } else {
                "https://graph.microsoft.com/v1.0/me/drive/items/$folderId/children"
            }
            
            Log.d(TAG, "Listing folder contents: $apiUrl")
            
            // Make API request
            val request = Request.Builder()
                .url("$apiUrl?select=id,name,size,folder,file,parentReference,@microsoft.graph.downloadUrl&\$top=999")
                .addHeader("Authorization", "Bearer $currentAccessToken")
                .get()
                .build()
            
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "Failed to list folder contents: ${response.code} - $errorBody")
                return@withContext Result.failure(Exception("Failed to list folder: ${response.code}"))
            }
            
            val responseBody = response.body?.string()
            if (responseBody == null) {
                return@withContext Result.failure(Exception("Empty response body"))
            }
            
            // Parse response
            val items = mutableListOf<DriveItem>()
            val json = JSONObject(responseBody)
            val values = json.getJSONArray("value")
            
            for (i in 0 until values.length()) {
                val item = values.getJSONObject(i)
                val id = item.getString("id")
                val name = item.getString("name")
                val size = item.optLong("size", 0)
                val isFolder = item.has("folder")
                val parentReference = item.optJSONObject("parentReference")
                val parentId = parentReference?.optString("id") ?: "root"
                val path = parentReference?.optString("path", "") ?: ""
                
                // Get download URL for files
                val downloadUrl = if (!isFolder) {
                    item.optString("@microsoft.graph.downloadUrl", "")
                } else {
                    ""
                }
                
                items.add(DriveItem(id, name, size, isFolder, downloadUrl, parentId, path))
            }
            
            // Sort by folders first, then by name
            val sortedItems = items.sortedWith(compareBy({ !it.isFolder }, { it.name.lowercase() }))
            
            Log.d(TAG, "Found ${sortedItems.size} items in folder $folderId")
            return@withContext Result.success(sortedItems)
        } catch (e: Exception) {
            Log.e(TAG, "Error listing folder contents", e)
            return@withContext Result.failure(e)
        }
    }
    
    /**
     * Search for audio files in OneDrive
     */
    suspend fun searchAudioFiles(query: String = ""): Result<List<DriveItem>> = withContext(Dispatchers.IO) {
        try {
            if (mSingleAccountApp == null || currentAccessToken == null) {
                return@withContext Result.failure(Exception("Not initialized or signed in"))
            }
            
            val audioExtensionsQuery = AUDIO_EXTENSIONS.joinToString(" OR ") { ext ->
                if (query.isNotEmpty()) {
                    "name:$query AND name:$ext"
                } else {
                    "name:$ext"
                }
            }
            
            // Build search URL
            val apiUrl = "https://graph.microsoft.com/v1.0/me/drive/root/search(q='$audioExtensionsQuery')"
            
            Log.d(TAG, "Searching for audio files: $apiUrl")
            
            // Make API request
            val request = Request.Builder()
                .url("$apiUrl?select=id,name,size,file,parentReference,@microsoft.graph.downloadUrl&\$top=999")
                .addHeader("Authorization", "Bearer $currentAccessToken")
                .get()
                .build()
            
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "Failed to search audio files: ${response.code} - $errorBody")
                return@withContext Result.failure(Exception("Failed to search files: ${response.code}"))
            }
            
            val responseBody = response.body?.string()
            if (responseBody == null) {
                return@withContext Result.failure(Exception("Empty response body"))
            }
            
            // Parse response
            val items = mutableListOf<DriveItem>()
            val json = JSONObject(responseBody)
            val values = json.getJSONArray("value")
            
            for (i in 0 until values.length()) {
                val item = values.getJSONObject(i)
                // Only include files, not folders
                if (item.has("file")) {
                    val id = item.getString("id")
                    val name = item.getString("name")
                    val size = item.optLong("size", 0)
                    val parentReference = item.optJSONObject("parentReference")
                    val parentId = parentReference?.optString("id") ?: "root"
                    val path = parentReference?.optString("path", "") ?: ""
                    
                    // Get download URL
                    val downloadUrl = item.optString("@microsoft.graph.downloadUrl", "")
                    
                    // Only add if it has a download URL and is an audio file
                    if (downloadUrl.isNotEmpty() && AUDIO_EXTENSIONS.any { name.lowercase().endsWith(it) }) {
                        items.add(DriveItem(id, name, size, false, downloadUrl, parentId, path))
                    }
                }
            }
            
            Log.d(TAG, "Found ${items.size} audio files")
            return@withContext Result.success(items)
        } catch (e: Exception) {
            Log.e(TAG, "Error searching audio files", e)
            return@withContext Result.failure(e)
        }
    }
    
    /**
     * Download a file from OneDrive
     */
    suspend fun downloadFile(
        item: DriveItem,
        destFile: File,
        progressCallback: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (mSingleAccountApp == null || currentAccessToken == null) {
                return@withContext Result.failure(Exception("Not initialized or signed in"))
            }
            
            if (item.isFolder) {
                return@withContext Result.failure(Exception("Cannot download a folder"))
            }
            
            if (item.downloadUrl.isEmpty()) {
                return@withContext Result.failure(Exception("Download URL not available"))
            }
            
            // Download the file
            val url = URL(item.downloadUrl)
            val connection = url.openConnection()
            val contentLength = connection.contentLength
            
            var bytesRead = 0L
            val buffer = ByteArray(8192)
            
            connection.getInputStream().use { input ->
                FileOutputStream(destFile).use { output ->
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesRead += read
                        
                        // Update progress
                        if (contentLength > 0) {
                            val progress = ((bytesRead.toDouble() / contentLength) * 100).toInt()
                            withContext(Dispatchers.Main) {
                                progressCallback(progress)
                            }
                        }
                    }
                }
            }
            
            // Final progress update
            withContext(Dispatchers.Main) {
                progressCallback(100)
            }
            
            Log.d(TAG, "File downloaded successfully: ${destFile.absolutePath}")
            return@withContext Result.success(destFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading file", e)
            return@withContext Result.failure(e)
        }
    }
    
    /**
     * Download multiple files from a folder
     * @return List of successfully downloaded files with their item metadata
     */
    suspend fun downloadFolderContents(
        folderId: String,
        destFolder: File,
        progressCallback: (Int, Int, String) -> Unit // (currentItem, totalItems, currentFileName)
    ): Result<List<Pair<DriveItem, File>>> = withContext(Dispatchers.IO) {
        try {
            if (mSingleAccountApp == null || currentAccessToken == null) {
                return@withContext Result.failure(Exception("Not initialized or signed in"))
            }
            
            // First, list all files in the folder
            val folderContentsResult = listFolderContents(folderId)
            if (folderContentsResult.isFailure) {
                return@withContext Result.failure(folderContentsResult.exceptionOrNull() 
                    ?: Exception("Failed to list folder contents"))
            }
            
            val folderContents = folderContentsResult.getOrNull() ?: emptyList()
            
            // Filter to audio files only
            val audioFiles = folderContents.filter { it.isAudioFile }
            
            if (audioFiles.isEmpty()) {
                return@withContext Result.failure(Exception("No audio files found in this folder"))
            }
            
            // Ensure destination folder exists
            if (!destFolder.exists()) {
                destFolder.mkdirs()
            }
            
            // Track successful downloads
            val downloadedFiles = mutableListOf<Pair<DriveItem, File>>()
            
            // Download each file
            audioFiles.forEachIndexed { index, item ->
                val progress = index + 1
                val total = audioFiles.size
                
                // Update progress on main thread
                withContext(Dispatchers.Main) {
                    progressCallback(progress, total, item.name)
                }
                
                val destFile = File(destFolder, "${System.currentTimeMillis()}_${item.name}")
                
                val downloadResult = downloadFile(item, destFile) { fileProgress ->
                    // No need to report individual file progress here
                }
                
                if (downloadResult.isSuccess) {
                    downloadedFiles.add(Pair(item, destFile))
                }
            }
            
            Log.d(TAG, "Downloaded ${downloadedFiles.size}/${audioFiles.size} files from folder")
            return@withContext Result.success(downloadedFiles)
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading folder contents", e)
            return@withContext Result.failure(e)
        }
    }
    
    companion object {
        // Supported audio file extensions
        val AUDIO_EXTENSIONS = listOf(".mp3", ".m4a", ".wav", ".ogg", ".flac", ".aac", ".wma", ".opus", ".m4b")
        
        // Supported image file extensions for cover art
        val IMAGE_EXTENSIONS = listOf(".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp")
        
        // This is for reference only - actual client ID is loaded from auth_config_single_account.json
        private const val CLIENT_ID = "3be7a0ba-69d5-4009-b65b-249b091631d3"
    }
} 