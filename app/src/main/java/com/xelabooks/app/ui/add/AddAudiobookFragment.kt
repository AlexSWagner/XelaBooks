package com.xelabooks.app.ui.add

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.xelabooks.app.R
import com.xelabooks.app.adapter.ChapterAdapter
import com.xelabooks.app.databinding.FragmentAddAudiobookBinding
import com.xelabooks.app.model.AudioBook
import com.xelabooks.app.model.Chapter
import com.xelabooks.app.viewmodel.AudioBookViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.Collections

private const val TAG = "AddAudiobookFragment"

class AddAudiobookFragment : Fragment() {

    private var _binding: FragmentAddAudiobookBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var viewModel: AudioBookViewModel
    
    private var audioFileUri: Uri? = null
    private var coverImageUri: Uri? = null

    // Cloud service variables
    private lateinit var googleSignInClient: GoogleSignInClient
    private var driveServiceHelper: Drive? = null
    
    // Flag to track selected source
    private enum class AudioSource { LOCAL, GOOGLE_DRIVE, ONEDRIVE }
    private var selectedSource = AudioSource.LOCAL
    
    private val chapters = mutableListOf<Chapter>()
    private lateinit var chapterAdapter: ChapterAdapter
    
    // Flag to determine if we're in multi-chapter mode
    private var isMultiChapterMode = false
    
    // Permission request launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.entries.all { it.value }) {
            // All permissions granted
            Log.d(TAG, "All required permissions granted")
            when (selectedSource) {
                AudioSource.LOCAL -> openAudioFilePicker()
                else -> {} // Do nothing for other sources
            }
        } else {
            // Some permissions denied
            Log.w(TAG, "Some permissions were denied: $permissions")
            showPermissionDeniedDialog()
        }
    }

    // Activity result launcher for audio file selection
    private val selectAudioLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d(TAG, "Audio file selection result received: resultCode=${result.resultCode}")
        
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            Log.d(TAG, "Audio file URI returned: $uri")
            
            if (uri != null) {
                try {
                    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    requireContext().contentResolver.takePersistableUriPermission(uri, flags)
                    Log.d(TAG, "Persisted permission granted for URI: $uri")
                    
                    // Check if we can actually read from this URI
                    val test = requireContext().contentResolver.openInputStream(uri)
                    if (test != null) {
                        test.close()
                        Log.d(TAG, "Successfully opened input stream from URI")
                    } else {
                        Log.w(TAG, "Could not open input stream from URI")
                    }
                    
                    // If in multi-chapter mode, show chapter title dialog
                    if (isMultiChapterMode) {
                        showChapterTitleDialog(uri)
                    } else {
                        // Single file mode
                        audioFileUri = uri
                        val filename = getFileName(uri)
                        Log.d(TAG, "Setting selected file name: $filename")
                        binding.tvSelectedFile.text = filename
                        
                        // Show the file card with animation
                        binding.cardSelectedFile.visibility = View.VISIBLE
                        binding.cardSelectedFile.alpha = 0f
                        binding.cardSelectedFile.animate().alpha(1f).setDuration(300).start()
                    }
                    
                    updateSaveButtonState()
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing selected audio file", e)
                    Toast.makeText(requireContext(), "Error accessing selected file: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.e(TAG, "No URI returned from file picker")
                Toast.makeText(requireContext(), "Failed to select audio file", Toast.LENGTH_SHORT).show()
            }
        } else if (result.resultCode == Activity.RESULT_CANCELED) {
            Log.d(TAG, "Audio file selection was cancelled by user")
        } else {
            Log.w(TAG, "Unexpected result code: ${result.resultCode}")
            Toast.makeText(requireContext(), "File selection failed", Toast.LENGTH_SHORT).show()
        }
    }

    // Activity result launcher for cover image selection
    private val selectCoverLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                // Take persistent URI permission
                try {
                    requireContext().contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    Log.d(TAG, "Persisted permission granted for cover URI: $uri")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to take persistent permission for cover: ${e.message}")
                }
                
                coverImageUri = uri
                binding.ivCoverPreview.setImageURI(uri)
                updateSaveButtonState()
            }
        }
    }
    
    // Google Sign-In launcher
    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let {
                handleGoogleSignInResult(it)
            }
        } else {
            Toast.makeText(requireContext(), R.string.google_signin_failed, Toast.LENGTH_SHORT).show()
            binding.rbLocal.isChecked = true
        }
    }

    // Add chapter launcher
    private val selectChapterLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d(TAG, "Chapter file selection result received: resultCode=${result.resultCode}")
        
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            Log.d(TAG, "Chapter file URI returned: $uri")
            
            if (uri != null) {
                try {
                    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    requireContext().contentResolver.takePersistableUriPermission(uri, flags)
                    Log.d(TAG, "Persisted permission granted for chapter URI: $uri")
                    
                    // Show chapter title dialog
                    showChapterTitleDialog(uri)
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing selected chapter file", e)
                    Toast.makeText(requireContext(), "Error accessing selected file: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.e(TAG, "No URI returned from file picker")
                Toast.makeText(requireContext(), "Failed to select audio file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddAudiobookBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Get the application-scoped ViewModel
        viewModel = ViewModelProvider(requireActivity())[AudioBookViewModel::class.java]
        
        // Check if we should be in multi-chapter mode from arguments
        arguments?.let {
            isMultiChapterMode = it.getBoolean("isMultiChapter", false)
            Log.d(TAG, "Multi-chapter mode: $isMultiChapterMode")
        }
        
        // Set up UI based on mode
        setupUIForMode()
        
        // Initially hide the audio file card until a file is selected
        binding.cardSelectedFile.visibility = View.GONE
        
        // Set up chapters RecyclerView
        setupChaptersRecyclerView()
        updateChaptersVisibility()
        
        setupGoogleSignIn()
        setupListeners()
        
        // Set up OneDrive result listeners - moved here so they're always active
        setupOneDriveResultListeners()
        
        // Set up fragment result listener for OneDrive file selection
        setFragmentResultListener("onedrive_file_result") { _, bundle ->
            val uriString = bundle.getString("uri")
            val fileName = bundle.getString("fileName")
            
            if (uriString != null && fileName != null) {
                val uri = Uri.parse(uriString)
                handleOneDriveFileSelected(uri, fileName)
            }
        }
    }
    
    private fun handleOneDriveFileSelected(uri: Uri, fileName: String) {
        Log.d(TAG, "OneDrive file selected: $fileName, URI: $uri")
        
        if (isMultiChapterMode) {
            // In multi-chapter mode, show chapter title dialog
            showChapterTitleDialog(uri)
        } else {
            // Single file mode
            audioFileUri = uri
            binding.tvSelectedFile.text = fileName
            
            // Show the file card with animation
            binding.cardSelectedFile.visibility = View.VISIBLE
            binding.cardSelectedFile.alpha = 0f
            binding.cardSelectedFile.animate().alpha(1f).setDuration(300).start()
        }
        
        updateSaveButtonState()
        
        // Show a success message
        Toast.makeText(
            requireContext(), 
            getString(R.string.onedrive_file_selected, fileName), 
            Toast.LENGTH_SHORT
        ).show()
    }
    
    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_READONLY))
            .build()
        
        googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso)
    }
    
    private fun setupChaptersRecyclerView() {
        chapterAdapter = ChapterAdapter { chapter ->
            // Handle removal of chapter
            chapters.remove(chapter)
            chapterAdapter.submitList(chapters.toList())
            updateChaptersVisibility()
            updateButtonText()
            updateSaveButtonState()
        }
        
        binding.rvChapters.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@AddAudiobookFragment.chapterAdapter
        }
    }
    
    private fun updateChaptersVisibility() {
        if (chapters.isEmpty()) {
            binding.tvNoChapters.visibility = View.VISIBLE
            binding.rvChapters.visibility = View.GONE
        } else {
            binding.tvNoChapters.visibility = View.GONE
            binding.rvChapters.visibility = View.VISIBLE
        }
        
        // Update card visibility based on whether we have a single file or chapters
        if (chapters.isEmpty()) {
            // Show card if we have a selected file
            binding.cardSelectedFile.visibility = if (audioFileUri != null) View.VISIBLE else View.GONE
        } else {
            // Hide card when we have chapters
            binding.cardSelectedFile.visibility = View.GONE
        }
    }
    
    private fun setupListeners() {
        binding.btnAddAudio.setOnClickListener {
            when (selectedSource) {
                AudioSource.LOCAL -> selectAudioFile()
                AudioSource.GOOGLE_DRIVE -> connectToGoogleDrive()
                AudioSource.ONEDRIVE -> connectToOneDrive()
            }
        }
        
        binding.btnSelectCover.setOnClickListener {
            selectCoverImage()
        }
        
        binding.btnSaveBook.setOnClickListener {
            saveAudiobook()
        }
        
        // Radio button listeners
        binding.rgAudioSource.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbLocal -> selectedSource = AudioSource.LOCAL
                R.id.rbGoogleDrive -> selectedSource = AudioSource.GOOGLE_DRIVE
                R.id.rbOneDrive -> selectedSource = AudioSource.ONEDRIVE
            }
            
            // Update button text based on selected source
            updateButtonText()
        }
        
        // Add text change listeners to update save button state
        binding.etBookTitle.setOnFocusChangeListener { _, _ -> updateSaveButtonState() }
        binding.etAuthor.setOnFocusChangeListener { _, _ -> updateSaveButtonState() }
        
        // If in multi-chapter mode, always show the chapters section as visible
        if (isMultiChapterMode) {
            binding.tvChaptersTitle.visibility = View.VISIBLE
            updateChaptersVisibility() // This will handle whether to show the recycler view or the "no chapters" text
        }
    }
    
    private fun updateButtonText() {
        if (isMultiChapterMode) {
            // In multi-chapter mode, the button is always for adding chapters
            binding.btnAddAudio.text = getString(R.string.add_chapter)
        } else {
            // In single file mode, the button is for selecting the main audio file
            binding.btnAddAudio.text = when (selectedSource) {
                AudioSource.LOCAL -> getString(R.string.select_audio_file)
                AudioSource.GOOGLE_DRIVE -> getString(R.string.select_from_google_drive)
                AudioSource.ONEDRIVE -> getString(R.string.select_from_one_drive)
            }
        }
    }
    
    private fun selectAudioFile() {
        Log.d(TAG, "selectAudioFile called")
        
        // Check and request permissions if needed
        if (checkAndRequestPermissions()) {
            if (isMultiChapterMode) {
                // In multi-chapter mode, always open the file picker to add a chapter
                openAudioFilePicker()
            } else {
                // In single file mode, open the file picker for the main file
                openAudioFilePicker()
            }
        }
    }
    
    private fun checkAndRequestPermissions(): Boolean {
        Log.d(TAG, "Checking storage permissions")
        val requiredPermissions = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ specific permissions
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_MEDIA_AUDIO) 
                != PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_MEDIA_IMAGES) 
                != PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            // For Android 12 and below
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        
        if (requiredPermissions.isNotEmpty()) {
            Log.d(TAG, "Requesting permissions: $requiredPermissions")
            requestPermissionLauncher.launch(requiredPermissions.toTypedArray())
            return false
        }
        
        Log.d(TAG, "All required permissions already granted")
        return true
    }
    
    private fun showPermissionDeniedDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Storage Permission Required")
            .setMessage("AudioBook Manager needs access to storage to select audio files. Please grant this permission in settings.")
            .setPositiveButton("Open Settings") { dialog, _ ->
                // Open app settings
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", requireActivity().packageName, null)
                intent.data = uri
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun openAudioFilePicker() {
        try {
            Log.d(TAG, "Starting audio file selection")
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                // Be more specific with mime types to ensure MP3 files are properly selected
                type = "audio/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                    "audio/mpeg", // MP3
                    "audio/mp4",  // M4A
                    "audio/ogg",  // OGG
                    "audio/x-wav" // WAV
                ))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
            Log.d(TAG, "Launching audio file picker with intent: $intent")
            selectAudioLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error launching audio file picker", e)
            Toast.makeText(requireContext(), "Error launching file picker: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun connectToGoogleDrive() {
        val account = GoogleSignIn.getLastSignedInAccount(requireContext())
        
        if (account != null && GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_READONLY))) {
            // Already signed in, set up Drive service
            setupDriveService(account.account!!)
            selectGoogleDriveFile()
        } else {
            // Need to sign in
            googleSignInLauncher.launch(googleSignInClient.signInIntent)
        }
    }
    
    private fun handleGoogleSignInResult(data: Intent) {
        GoogleSignIn.getSignedInAccountFromIntent(data)
            .addOnSuccessListener { account ->
                setupDriveService(account.account!!)
                selectGoogleDriveFile()
            }
            .addOnFailureListener { exception ->
                Log.e("GoogleDrive", "Failed to sign in", exception)
                Toast.makeText(requireContext(), getString(R.string.google_signin_error, exception.message), Toast.LENGTH_SHORT).show()
            }
    }
    
    private fun setupDriveService(account: android.accounts.Account) {
        val credential = GoogleAccountCredential.usingOAuth2(
            requireContext(),
            Collections.singleton(DriveScopes.DRIVE_READONLY)
        )
        credential.selectedAccount = account
        
        driveServiceHelper = Drive.Builder(
            NetHttpTransport(),
            GsonFactory(),
            credential
        )
            .setApplicationName(getString(R.string.app_name))
            .build()
        
        Toast.makeText(requireContext(), R.string.connected_to_drive, Toast.LENGTH_SHORT).show()
    }
    
    private fun selectGoogleDriveFile() {
        // In a real implementation, you would show a file picker for Google Drive here
        // For demo purposes, we'll just show a toast
        Toast.makeText(requireContext(), 
            R.string.google_drive_integration_message, 
            Toast.LENGTH_LONG).show()
        
        // For demo purposes, let's simulate that a file was selected
        audioFileUri = "content://com.google.android.apps.docs.storage/document/acc=1;doc=123".toUri()
        binding.tvSelectedFile.text = getString(R.string.sample_google_drive_file)
        updateSaveButtonState()
    }
    
    private fun connectToOneDrive() {
        Log.d(TAG, "Navigating to OneDrive browser")
        // Navigate to the OneDrive browser, specifying if this is for cover selection or audio file
        val isForCover = false // For now, we're not handling cover selection from OneDrive in this context
        val action = AddAudiobookFragmentDirections.actionNavigationAddAudiobookToOneDriveBrowser(isForCover)
        findNavController().navigate(action)
    }
    
    private fun setupOneDriveResultListeners() {
        Log.d(TAG, "Setting up OneDrive result listeners")
        
        // Get the current navigation entry for observing savedStateHandle
        val navBackStackEntry = findNavController().currentBackStackEntry
        if (navBackStackEntry == null) {
            Log.w(TAG, "Current back stack entry is null, cannot set up listeners")
            return
        }
        
        // Single audio file selection
        navBackStackEntry.savedStateHandle.getLiveData<String>("selected_audio_path").observe(
            viewLifecycleOwner
        ) { filePath ->
            if (filePath != null) {
                Log.d(TAG, "Received selected_audio_path: $filePath")
                val file = File(filePath)
                if (file.exists()) {
                    if (isMultiChapterMode) {
                        // Show dialog to name the chapter
                        showChapterTitleDialog(file.toUri())
                    } else {
                        // Set as main audio file
                        audioFileUri = file.toUri()
                        binding.tvSelectedFile.text = file.name
                        
                        // Show the file card with animation
                        binding.cardSelectedFile.visibility = View.VISIBLE
                        binding.cardSelectedFile.alpha = 0f
                        binding.cardSelectedFile.animate().alpha(1f).setDuration(300).start()
                        
                        Log.d(TAG, "Set main audio file: ${file.name}")
                        Toast.makeText(requireContext(), "Audio file selected: ${file.name}", Toast.LENGTH_SHORT).show()
                    }
                    updateSaveButtonState()
                } else {
                    Log.w(TAG, "Downloaded file does not exist: $filePath")
                    Toast.makeText(requireContext(), "Error: Downloaded file not found", Toast.LENGTH_SHORT).show()
                }
                // Clear the saved state to avoid duplicate observations
                navBackStackEntry.savedStateHandle.remove<String>("selected_audio_path")
            }
        }
        
        // Cover image selection
        navBackStackEntry.savedStateHandle.getLiveData<String>("selected_cover_path").observe(
            viewLifecycleOwner
        ) { filePath ->
            if (filePath != null) {
                Log.d(TAG, "Received selected_cover_path: $filePath")
                val file = File(filePath)
                if (file.exists()) {
                    coverImageUri = file.toUri()
                    binding.ivCoverPreview.setImageURI(coverImageUri)
                    updateSaveButtonState()
                    Toast.makeText(requireContext(), "Cover image selected", Toast.LENGTH_SHORT).show()
                } else {
                    Log.w(TAG, "Downloaded cover file does not exist: $filePath")
                }
                // Clear the saved state
                navBackStackEntry.savedStateHandle.remove<String>("selected_cover_path")
            }
        }
        
        // Multiple audio files import (batch)
        navBackStackEntry.savedStateHandle.getLiveData<Array<String>>("imported_audio_files").observe(
            viewLifecycleOwner
        ) { filePaths ->
            if (filePaths != null && filePaths.isNotEmpty()) {
                Log.d(TAG, "Received ${filePaths.size} imported audio files from OneDrive")
                
                if (filePaths.size == 1) {
                    // Single file imported from folder - treat as single file
                    val file = File(filePaths.first())
                    if (file.exists()) {
                        if (isMultiChapterMode) {
                            // Show dialog to name the chapter
                            showChapterTitleDialog(file.toUri())
                        } else {
                            // Set as main audio file
                            audioFileUri = file.toUri()
                            binding.tvSelectedFile.text = file.name
                            
                            // Show the file card with animation
                            binding.cardSelectedFile.visibility = View.VISIBLE
                            binding.cardSelectedFile.alpha = 0f
                            binding.cardSelectedFile.animate().alpha(1f).setDuration(300).start()
                            
                            Log.d(TAG, "Set main audio file: ${file.name}")
                            Toast.makeText(requireContext(), "Audio file selected. Please enter the book title and author to save.", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Log.w(TAG, "Downloaded file does not exist: ${filePaths.first()}")
                        Toast.makeText(requireContext(), "Error: Downloaded file not found", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // Multiple files imported - automatically enable multi-chapter mode and add all as chapters
                    Log.d(TAG, "Multiple files detected, enabling multi-chapter mode automatically")
                    
                    if (!isMultiChapterMode) {
                        isMultiChapterMode = true
                        setupUIForMode()
                        updateButtonText()
                    }
                    
                    // Import all as chapters with auto-naming
                    var successCount = 0
                    filePaths.forEachIndexed { index, path ->
                        val file = File(path)
                        if (file.exists()) {
                            val chapterName = "Chapter ${index + 1}"
                            addChapter(chapterName, file.toUri())
                            successCount++
                            Log.d(TAG, "Added chapter: $chapterName from file: ${file.name}")
                        } else {
                            Log.w(TAG, "File does not exist: $path")
                        }
                    }
                    updateChaptersVisibility()
                    Toast.makeText(requireContext(), "Added $successCount chapters from folder. Please enter the book title and author to save.", Toast.LENGTH_LONG).show()
                }
                updateSaveButtonState()
                
                // Clear the saved state
                navBackStackEntry.savedStateHandle.remove<Array<String>>("imported_audio_files")
            }
        }
    }
    
    private fun selectCoverImage() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        selectCoverLauncher.launch(intent)
    }
    
    private fun selectChapterFile() {
        try {
            Log.d(TAG, "Starting chapter file selection")
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "audio/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                    "audio/mpeg", // MP3
                    "audio/mp4",  // M4A
                    "audio/ogg",  // OGG
                    "audio/x-wav" // WAV
                ))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
            Log.d(TAG, "Launching chapter file picker")
            selectChapterLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error launching chapter file picker", e)
            Toast.makeText(requireContext(), "Error launching file picker: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showChapterTitleDialog(fileUri: Uri) {
        val fileName = getFileName(fileUri)
        val suggestedTitle = "Chapter ${chapters.size + 1}"
        
        val editText = EditText(requireContext()).apply {
            setText(suggestedTitle)
            hint = "Chapter title"
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT
            
            // Select all text for easy editing
            selectAll()
        }
        
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Chapter Title")
            .setMessage("Enter a title for this chapter: $fileName")
            .setView(editText)
            .setPositiveButton("Add") { dialog, _ ->
                val title = editText.text.toString().trim().ifEmpty { suggestedTitle }
                addChapter(title, fileUri)
            }
            .setNegativeButton("Cancel", null)
            .create()
        
        dialog.show()
    }
    
    private fun addChapter(title: String, fileUri: Uri) {
        val chapter = Chapter(
            bookId = "", // Will be set when saving the book
            title = title,
            filePath = fileUri.toString(),
            sequence = chapters.size, // Will be re-indexed when saving
            duration = 0 // Will be updated when playing
        )
        
        chapters.add(chapter)
        chapterAdapter.submitList(chapters.toList())
        updateChaptersVisibility()
        updateButtonText()
        updateSaveButtonState()
        
        Toast.makeText(requireContext(), "Added chapter: $title", Toast.LENGTH_SHORT).show()
    }
    
    private fun saveAudiobook() {
        val title = binding.etBookTitle.text.toString().trim()
        val author = binding.etAuthor.text.toString().trim()
        val description = binding.etDescription.text.toString().trim().ifEmpty { null }
        
        if (title.isEmpty() || author.isEmpty() || (audioFileUri == null && chapters.isEmpty())) {
            Toast.makeText(requireContext(), R.string.fill_required_fields, Toast.LENGTH_SHORT).show()
            return
        }
        
        Log.d(TAG, "Saving audiobook: $title by $author")
        Log.d(TAG, "Audio URI: $audioFileUri")
        Log.d(TAG, "Chapters: ${chapters.size}")
        
        // Show a loading state on the save button
        binding.btnSaveBook.isEnabled = false
        binding.btnSaveBook.text = getString(R.string.adding_to_library)
        
        // Create the audiobook
        val audioBook = AudioBook(
            title = title,
            author = author,
            description = description,
            filePath = audioFileUri?.toString() ?: "", // Empty if using chapters only
            coverImagePath = coverImageUri?.toString()
        )
        
        Log.d(TAG, "Created AudioBook object with ID: ${audioBook.id}")
        
        if (chapters.isEmpty()) {
            // Simple audiobook with single file
            Log.d(TAG, "Inserting single-file audiobook into database")
            viewModel.insert(audioBook)
            Log.d(TAG, "Audiobook inserted into database")
        } else {
            // Audiobook with chapters
            Log.d(TAG, "Inserting audiobook with ${chapters.size} chapters into database")
            viewModel.addBookWithChapters(audioBook, chapters)
            Log.d(TAG, "Audiobook with ${chapters.size} chapters inserted into database")
        }
        
        // Show success message and navigate back
        Toast.makeText(requireContext(), "Successfully added \"$title\" to your library!", Toast.LENGTH_LONG).show()
        
        // Navigate back to home with a delay to ensure the database operation completes
        viewLifecycleOwner.lifecycleScope.launch {
            kotlinx.coroutines.delay(500) // Small delay for database operation
            Log.d(TAG, "Navigating back to home fragment")
            try {
                findNavController().navigate(R.id.navigation_home)
            } catch (e: Exception) {
                Log.e(TAG, "Error navigating to home", e)
                // Try to pop back stack as fallback
                findNavController().popBackStack()
            }
        }
    }
    
    private fun saveFileToInternalStorage(uri: Uri, fileName: String): String? {
        return try {
            Log.d(TAG, "Attempting to save file from URI: $uri to $fileName")
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Log.e(TAG, "Failed to open input stream for URI: $uri")
                return null
            }
            
            val file = File(requireContext().filesDir, fileName)
            
            inputStream.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            
            Log.d(TAG, "Successfully saved file to: ${file.absolutePath}")
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error saving file: ${e.message}", e)
            null
        }
    }
    
    private fun getFileName(uri: Uri): String {
        Log.d(TAG, "Getting filename for URI: $uri")
        val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
        
        return cursor?.use {
            val nameIndex = it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            Log.d(TAG, "Name column index: $nameIndex")
            
            it.moveToFirst()
            if (nameIndex >= 0 && !it.isNull(nameIndex)) {
                val filename = it.getString(nameIndex)
                Log.d(TAG, "Found filename: $filename")
                filename
            } else {
                Log.w(TAG, "Could not get filename from cursor, returning unknown")
                getString(R.string.unknown_file)
            }
        } ?: run {
            Log.w(TAG, "Cursor is null, returning unknown file")
            getString(R.string.unknown_file)
        }
    }
    
    private fun updateSaveButtonState() {
        val title = binding.etBookTitle.text.toString().trim()
        val author = binding.etAuthor.text.toString().trim()
        
        val shouldEnable = if (isMultiChapterMode) {
            // In multi-chapter mode, we need at least one chapter
            title.isNotEmpty() && author.isNotEmpty() && chapters.isNotEmpty()
        } else {
            // In single file mode, we need an audio file
            title.isNotEmpty() && author.isNotEmpty() && audioFileUri != null
        }
        
        Log.d(TAG, "Updating save button state: enabled=$shouldEnable (title=$title, author=$author, uri=$audioFileUri, chapters=${chapters.size}, multiChapter=$isMultiChapterMode)")
        binding.btnSaveBook.isEnabled = shouldEnable
    }
    
    private fun setupUIForMode() {
        if (isMultiChapterMode) {
            binding.tvAddBookTitle.text = getString(R.string.multi_chapter_title)
            binding.btnAddAudio.text = getString(R.string.add_chapter)
            binding.tvChaptersTitle.visibility = View.VISIBLE
        } else {
            binding.tvAddBookTitle.text = getString(R.string.single_file_title)
            binding.btnAddAudio.text = getString(R.string.select_audio_file)
            binding.tvChaptersTitle.visibility = View.GONE
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 