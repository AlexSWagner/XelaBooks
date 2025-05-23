package com.xelabooks.app.ui.onedrive

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.xelabooks.app.R
import com.xelabooks.app.data.OneDriveManager
import com.xelabooks.app.databinding.FragmentOnedriveBrowserBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import com.microsoft.identity.client.exception.MsalDeclinedScopeException
import android.util.Log

/**
 * Fragment for browsing OneDrive folders and files
 */
class OneDriveBrowserFragment : Fragment() {
    private var _binding: FragmentOnedriveBrowserBinding? = null
    private val binding get() = _binding!!
    
    private val args: OneDriveBrowserFragmentArgs by navArgs()
    private lateinit var oneDriveManager: OneDriveManager
    private lateinit var adapter: OneDriveItemAdapter
    
    // Navigation history for back button support
    private val folderHistory = mutableListOf<OneDriveManager.DriveItem>()
    
    private var currentFolderId = "root"
    private var currentFolderName = "OneDrive"
    
    // Folder stack to support back navigation
    private val folderStack = mutableListOf<Pair<String, String>>() // (id, name)
    
    // Track sign-in attempts to avoid infinite loops
    private var signInAttemptCount = 0
    private val MAX_SIGN_IN_ATTEMPTS = 3
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnedriveBrowserBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize OneDrive manager
        oneDriveManager = OneDriveManager(requireContext())
        
        // Setup toolbar
        binding.toolbar.setNavigationOnClickListener {
            navigateBack()
        }
        
        // Setup recycler view
        adapter = OneDriveItemAdapter(
            onItemClick = { item -> handleItemClick(item) },
            onItemLongClick = { item -> handleItemLongClick(item) }
        )
        
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
            adapter = this@OneDriveBrowserFragment.adapter
        }
        
        // Setup refresh layout
        binding.swipeRefresh.setOnRefreshListener {
            refreshCurrentFolder()
        }
        
        // Setup sign in button
        binding.buttonSignIn.setOnClickListener {
            signIn(false)
        }
        
        // Setup sign in with different account button
        binding.buttonSignInNewAccount.setOnClickListener {
            signIn(true)
        }
        
        // Setup import button
        binding.buttonImport.setOnClickListener {
            importCurrentFolder()
        }
        
        // Reset sign-in attempt counter
        signInAttemptCount = 0
        
        // Check if we're already signed in
        checkAuthentication()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    override fun onResume() {
        super.onResume()
        
        // Show a helpful message about permissions the first time
        if (!oneDriveManager.isSignedIn() && signInAttemptCount == 0) {
            Toast.makeText(
                requireContext(),
                "Please accept ALL permissions when signing in to OneDrive",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    private fun checkAuthentication() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                binding.progressBar.isVisible = true
                
                // Initialize the OneDrive manager
                val initResult = oneDriveManager.initialize()
                if (initResult.isFailure) {
                    throw initResult.exceptionOrNull() ?: Exception("Unknown initialization error")
                }
                
                // Check if signed in
                if (oneDriveManager.isSignedIn()) {
                    showSignedInState()
                    refreshCurrentFolder()
                } else {
                    showSignedOutState()
                }
                
                binding.progressBar.isVisible = false
            } catch (e: Exception) {
                binding.progressBar.isVisible = false
                showSignedOutState()
                showError("Failed to initialize: ${e.message}")
            }
        }
    }
    
    private fun signIn(forceNewAccount: Boolean = false) {
        if (signInAttemptCount >= MAX_SIGN_IN_ATTEMPTS) {
            showPermissionsDialog()
            return
        }
        
        signInAttemptCount++
        binding.progressBar.isVisible = true
        binding.buttonSignIn.isEnabled = false
        binding.buttonSignInNewAccount.isEnabled = false
        
        // Run sign-in in a background thread using coroutines
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // First, completely clear any existing accounts to avoid conflicts
                withContext(Dispatchers.IO) {
                    try {
                        oneDriveManager.forceSignOut()
                    } catch (e: Exception) {
                        // Ignore errors during sign out
                    }
                }
                
                // Now perform the sign-in on a background thread
                withContext(Dispatchers.IO) {
                    // Perform actual sign-in on UI thread for the activity reference,
                    // but ensure account checking happens on background thread
                    withContext(Dispatchers.Main) {
                        oneDriveManager.signIn(requireActivity(), forceNewAccount) { success, error ->
                            binding.progressBar.isVisible = false
                            binding.buttonSignIn.isEnabled = true
                            binding.buttonSignInNewAccount.isEnabled = true
                            
                            if (success) {
                                signInAttemptCount = 0 // Reset counter on success
                                showSignedInState()
                                refreshCurrentFolder()
                            } else {
                                Log.e("OneDriveBrowser", "Sign-in failed: $error")
                                if (error?.contains("Some requested permissions were not granted") == true) {
                                    // Show help for permission issues
                                    showPermissionsDialog()
                                } else if (error?.contains("unauthorized_client") == true || 
                                          error?.contains("does not exist") == true ||
                                          error?.contains("does not match") == true ||
                                          error?.contains("account mismatch") == true) {
                                    // For account mismatch, show a simplified message and auto-retry
                                    Log.d("OneDriveBrowser", "Account mismatch detected, auto-retrying with account selection")
                                    showError("Please select your account to continue")
                                    // Auto-retry with force new account after a short delay
                                    viewLifecycleOwner.lifecycleScope.launch {
                                        kotlinx.coroutines.delay(500) // Shorter delay
                                        if (signInAttemptCount < MAX_SIGN_IN_ATTEMPTS) {
                                            Log.d("OneDriveBrowser", "Auto-retrying sign-in with force account selection")
                                            signIn(true) // Force account selection
                                        } else {
                                            Log.w("OneDriveBrowser", "Max sign-in attempts reached")
                                            showPermissionsDialog()
                                        }
                                    }
                                } else {
                                    showError("Sign-in failed: $error")
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.isVisible = false
                    binding.buttonSignIn.isEnabled = true
                    binding.buttonSignInNewAccount.isEnabled = true
                    showError("Sign-in error: ${e.message}")
                }
            }
        }
    }
    
    private fun showPermissionsDialog() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Permissions Required")
            .setMessage("This app needs access to your OneDrive files to function properly. Please sign in and accept all requested permissions.")
            .setPositiveButton("Try Again") { _, _ ->
                // Reset counter and try again
                signInAttemptCount = 0
                signIn(false)
            }
            .setNegativeButton("Try Different Account") { _, _ ->
                // Reset counter and try with new account
                signInAttemptCount = 0
                signIn(true)
            }
            .setCancelable(false)
            .show()
    }
    
    private fun showUnauthorizedClientDialog() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Authentication Error")
            .setMessage("The app registration might not be correctly configured in Azure. Please ensure your app is registered for both work/school and personal Microsoft accounts, and the client ID is correct.")
            .setPositiveButton("Try Again") { _, _ ->
                // Reset counter and try again
                signInAttemptCount = 0
                // Force new account to clear cached state
                signIn(true)
            }
            .setNegativeButton("Cancel", null)
            .setCancelable(true)
            .show()
    }
    
    private fun signOut() {
        binding.progressBar.isVisible = true
        
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    oneDriveManager.signOut { success, error ->
                        viewLifecycleOwner.lifecycleScope.launch {
                            binding.progressBar.isVisible = false
                            
                            if (success) {
                                showSignedOutState()
                                clearData()
                            } else {
                                showError("Sign-out failed: $error")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.isVisible = false
                    showError("Error during sign-out: ${e.message}")
                }
            }
        }
    }
    
    private fun clearData() {
        adapter.submitList(emptyList())
        folderStack.clear()
        currentFolderId = "root"
        currentFolderName = "OneDrive"
        updateToolbarTitle()
    }
    
    private fun refreshCurrentFolder() {
        if (!oneDriveManager.isSignedIn()) {
            binding.swipeRefresh.isRefreshing = false
            return
        }
        
        binding.progressBar.isVisible = true
        binding.swipeRefresh.isRefreshing = true
        
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = oneDriveManager.listFolderContents(currentFolderId)
                binding.progressBar.isVisible = false
                binding.swipeRefresh.isRefreshing = false
                
                if (result.isSuccess) {
                    val items = result.getOrNull() ?: emptyList()
                    adapter.submitList(items)
                    
                    // Show empty view if needed
                    binding.emptyView.isVisible = items.isEmpty()
                    binding.recyclerView.isVisible = items.isNotEmpty()
                    
                    // Enable import button if there are audio files
                    binding.buttonImport.isEnabled = items.any { it.isAudioFile }
                } else {
                    val exception = result.exceptionOrNull()
                    if (exception?.message?.contains("unauthorized") == true || 
                        exception?.message?.contains("authentication") == true) {
                        // Try to sign in again if authentication failed
                        showError("Session expired. Please sign in again.")
                        showSignedOutState()
                    } else {
                        showError("Failed to list folder: ${exception?.message}")
                    }
                }
            } catch (e: Exception) {
                binding.progressBar.isVisible = false
                binding.swipeRefresh.isRefreshing = false
                showError("Error: ${e.message}")
            }
        }
    }
    
    private fun handleItemClick(item: OneDriveManager.DriveItem) {
        if (item.isFolder) {
            // Navigate to folder
            folderStack.add(Pair(currentFolderId, currentFolderName))
            currentFolderId = item.id
            currentFolderName = item.name
            updateToolbarTitle()
            refreshCurrentFolder()
        } else if (item.isAudioFile) {
            // Import single file
            downloadFile(item)
        }
    }
    
    private fun handleItemLongClick(item: OneDriveManager.DriveItem): Boolean {
        if (item.isAudioFile) {
            // Show options menu for the file
            val options = arrayOf("Import This File", "Cancel")
            
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(item.name)
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> downloadFile(item)
                    }
                }
                .show()
            
            return true
        }
        return false
    }
    
    private fun navigateBack(): Boolean {
        if (folderStack.isNotEmpty()) {
            // Pop from stack
            val (id, name) = folderStack.removeAt(folderStack.size - 1)
            currentFolderId = id
            currentFolderName = name
            updateToolbarTitle()
            refreshCurrentFolder()
            return true
        } else {
            // Go back in fragment navigation
            findNavController().popBackStack()
            return false
        }
    }
    
    private fun updateToolbarTitle() {
        binding.toolbar.title = currentFolderName
    }
    
    private fun showSignedInState() {
        binding.layoutSignIn.isVisible = false
        binding.layoutContent.isVisible = true
        binding.toolbar.menu.clear()
        binding.toolbar.inflateMenu(R.menu.menu_onedrive_browser)
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_sign_out -> {
                    signOut()
                    true
                }
                R.id.action_change_account -> {
                    signIn(true)
                    true
                }
                else -> false
            }
        }
    }
    
    private fun showSignedOutState() {
        binding.layoutSignIn.isVisible = true
        binding.layoutContent.isVisible = false
        binding.toolbar.menu.clear()
    }
    
    private fun showError(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }
    
    private fun downloadFile(item: OneDriveManager.DriveItem) {
        if (!item.isAudioFile) return
        
        val downloadDir = File(requireContext().filesDir, "downloads")
        if (!downloadDir.exists()) {
            downloadDir.mkdirs()
        }
        
        val destFile = File(downloadDir, "${System.currentTimeMillis()}_${item.name}")
        Log.d("OneDriveBrowser", "Starting download of ${item.name} to ${destFile.absolutePath}")
        
        binding.progressBar.isVisible = true
        binding.progressDownload.isVisible = true
        binding.progressDownload.progress = 0
        
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = oneDriveManager.downloadFile(item, destFile) { progress ->
                    binding.progressDownload.progress = progress
                }
                
                binding.progressBar.isVisible = false
                binding.progressDownload.isVisible = false
                
                if (result.isSuccess) {
                    // Navigate back to book edit with file
                    val resultFile = result.getOrNull()
                    if (resultFile != null) {
                        Log.d("OneDriveBrowser", "Download successful, file exists: ${resultFile.exists()}, path: ${resultFile.absolutePath}")
                        
                        // Determine if this is for book cover or chapter audio
                        if (args.isForCover && item.isImageFile) {
                            // Return cover image path
                            Log.d("OneDriveBrowser", "Setting selected_cover_path in savedStateHandle: ${resultFile.absolutePath}")
                            findNavController().previousBackStackEntry?.savedStateHandle?.set(
                                "selected_cover_path", resultFile.absolutePath
                            )
                            findNavController().popBackStack()
                        } else if (!args.isForCover && item.isAudioFile) {
                            // Return audio file path
                            Log.d("OneDriveBrowser", "Setting selected_audio_path in savedStateHandle: ${resultFile.absolutePath}")
                            findNavController().previousBackStackEntry?.savedStateHandle?.set(
                                "selected_audio_path", resultFile.absolutePath
                            )
                            Toast.makeText(requireContext(), "File downloaded successfully. Please fill in the book details.", Toast.LENGTH_LONG).show()
                            findNavController().popBackStack()
                        } else {
                            Log.w("OneDriveBrowser", "File type mismatch - isForCover: ${args.isForCover}, isImageFile: ${item.isImageFile}, isAudioFile: ${item.isAudioFile}")
                            showError("Selected file type is not valid for this operation")
                        }
                    } else {
                        Log.e("OneDriveBrowser", "Download result was success but file is null")
                        showError("Download completed but file not found")
                    }
                } else {
                    val error = result.exceptionOrNull()
                    Log.e("OneDriveBrowser", "Download failed", error)
                    showError("Failed to download file: ${error?.message}")
                }
            } catch (e: Exception) {
                Log.e("OneDriveBrowser", "Exception during download", e)
                binding.progressBar.isVisible = false
                binding.progressDownload.isVisible = false
                showError("Error downloading file: ${e.message}")
            }
        }
    }
    
    private fun importCurrentFolder() {
        // Show confirmation dialog
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Import Folder")
            .setMessage("This will import all audio files from the current folder. Continue?")
            .setPositiveButton("Import") { _, _ ->
                performFolderImport()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun performFolderImport() {
        val downloadDir = File(requireContext().filesDir, "downloads")
        if (!downloadDir.exists()) {
            downloadDir.mkdirs()
        }
        
        binding.progressBar.isVisible = true
        binding.progressDownload.isVisible = true
        binding.textDownloadStatus.isVisible = true
        binding.progressDownload.progress = 0
        
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                Log.d("OneDriveBrowser", "Starting folder import for folder: $currentFolderId")
                val result = oneDriveManager.downloadFolderContents(
                    currentFolderId,
                    downloadDir
                ) { current, total, fileName ->
                    val progressPercent = (current * 100) / total
                    binding.progressDownload.progress = progressPercent
                    binding.textDownloadStatus.text = "Downloading $current of $total: $fileName"
                    Log.d("OneDriveBrowser", "Download progress: $current/$total - $fileName")
                }
                
                binding.progressBar.isVisible = false
                binding.progressDownload.isVisible = false
                binding.textDownloadStatus.isVisible = false
                
                if (result.isSuccess) {
                    val downloadedFiles = result.getOrNull() ?: emptyList()
                    Log.d("OneDriveBrowser", "Successfully downloaded ${downloadedFiles.size} files")
                    
                    if (downloadedFiles.isNotEmpty()) {
                        // Return list of downloaded files
                        val filePaths = downloadedFiles.map { it.second.absolutePath }
                        Log.d("OneDriveBrowser", "Setting imported files in savedStateHandle: ${filePaths.size} paths")
                        findNavController().previousBackStackEntry?.savedStateHandle?.set(
                            "imported_audio_files", filePaths.toTypedArray()
                        )
                        
                        Toast.makeText(requireContext(), "Successfully downloaded ${downloadedFiles.size} files. Please fill in the book details to complete the import.", Toast.LENGTH_LONG).show()
                        
                        findNavController().popBackStack()
                    } else {
                        Log.w("OneDriveBrowser", "No files were downloaded from folder")
                        showError("No audio files were found in this folder")
                    }
                } else {
                    val error = result.exceptionOrNull()
                    Log.e("OneDriveBrowser", "Failed to import folder", error)
                    showError("Failed to import folder: ${error?.message}")
                }
            } catch (e: Exception) {
                Log.e("OneDriveBrowser", "Error importing folder", e)
                binding.progressBar.isVisible = false
                binding.progressDownload.isVisible = false
                binding.textDownloadStatus.isVisible = false
                showError("Error importing folder: ${e.message}")
            }
        }
    }
} 