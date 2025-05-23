package com.xelabooks.app.ui.add

import android.content.Context
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.xelabooks.app.R
import com.xelabooks.app.data.OneDriveManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class OneDriveFileSelectorFragment : Fragment() {
    private lateinit var toolbar: Toolbar
    private lateinit var rvOneDriveFiles: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var progressDownload: ProgressBar
    private lateinit var tvMessage: TextView
    
    private lateinit var oneDriveManager: OneDriveManager
    private lateinit var fileAdapter: OneDriveRealFileAdapter
    
    companion object {
        private const val TAG = "OneDriveFileSelector"
    }
    
    override fun onAttach(context: Context) {
        super.onAttach(context)
        
        // Initialize OneDrive manager
        oneDriveManager = OneDriveManager(context)
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_onedrive_file_selector, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize views
        toolbar = view.findViewById(R.id.toolbar)
        rvOneDriveFiles = view.findViewById(R.id.rvOneDriveFiles)
        progressBar = view.findViewById(R.id.progressBar)
        progressDownload = view.findViewById(R.id.progressDownload)
        tvMessage = view.findViewById(R.id.tvMessage)
        
        // Set up the toolbar
        toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
        
        // Set up the RecyclerView
        setupRecyclerView()
        
        // Initialize OneDrive and authenticate
        initializeOneDrive()
    }
    
    private fun setupRecyclerView() {
        fileAdapter = OneDriveRealFileAdapter { selectedFile ->
            handleFileSelection(selectedFile)
        }
        
        rvOneDriveFiles.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = fileAdapter
            addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
        }
    }
    
    private fun initializeOneDrive() {
        // Show loading state
        showLoading(true)
        
        // Use coroutines for initialize method since it's a suspend function
        CoroutineScope(Dispatchers.IO).launch {
            val result = oneDriveManager.initialize()
            
            withContext(Dispatchers.Main) {
                result.fold(
                    onSuccess = { 
                        Log.d(TAG, "OneDrive manager initialized successfully")
                        if (oneDriveManager.isSignedIn()) {
                            // Already signed in, list files
                            listOneDriveFiles()
                        } else {
                            // Need to sign in
                            signInToOneDrive()
                        }
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Failed to initialize OneDrive manager", error)
                        showError("Failed to initialize OneDrive: ${error.message}")
                    }
                )
            }
        }
    }
    
    private fun signInToOneDrive() {
        oneDriveManager.signIn(requireActivity()) { success, error ->
            if (success) {
                Log.d(TAG, "Successfully signed in to OneDrive")
                listOneDriveFiles()
            } else {
                Log.e(TAG, "Failed to sign in to OneDrive: $error")
                requireActivity().runOnUiThread {
                    showError(error ?: getString(R.string.onedrive_signin_failed))
                    findNavController().navigateUp()
                }
            }
        }
    }
    
    private fun listOneDriveFiles() {
        CoroutineScope(Dispatchers.IO).launch {
            val result = oneDriveManager.searchAudioFiles()
            
            withContext(Dispatchers.Main) {
                showLoading(false)
                
                result.fold(
                    onSuccess = { files ->
                        if (files.isEmpty()) {
                            showMessage(getString(R.string.onedrive_no_files))
                        } else {
                            fileAdapter.submitList(files)
                            rvOneDriveFiles.visibility = View.VISIBLE
                        }
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Error listing OneDrive files", error)
                        showError(getString(R.string.onedrive_browsing_error, error.message))
                    }
                )
            }
        }
    }
    
    private fun handleFileSelection(file: OneDriveManager.DriveItem) {
        Log.d(TAG, "File selected: ${file.name}")
        
        // Show download progress
        progressDownload.visibility = View.VISIBLE
        progressDownload.progress = 0
        
        val fileName = file.name
        val downloadDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            ?: requireContext().filesDir
        
        // Create a unique file name to avoid collisions
        val uniqueFileName = "${UUID.randomUUID()}_$fileName"
        val destFile = File(downloadDir, uniqueFileName)
        
        CoroutineScope(Dispatchers.IO).launch {
            val result = oneDriveManager.downloadFile(file, destFile) { progress ->
                // Update progress on UI thread
                requireActivity().runOnUiThread {
                    progressDownload.progress = progress
                }
            }
            
            withContext(Dispatchers.Main) {
                progressDownload.visibility = View.GONE
                
                result.fold(
                    onSuccess = { downloadedFile ->
                        Log.d(TAG, "File downloaded successfully to: ${downloadedFile.absolutePath}")
                        
                        // Use the navigation controller to navigate back with a result
                        val bundle = Bundle().apply {
                            putString("uri", downloadedFile.toUri().toString())
                            putString("fileName", fileName)
                        }
                        
                        // Set a result and navigate back
                        parentFragmentManager.setFragmentResult("onedrive_file_result", bundle)
                        findNavController().navigateUp()
                        
                        // Show a success message
                        Toast.makeText(
                            requireContext(), 
                            getString(R.string.onedrive_file_selected, fileName), 
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Error downloading file", error)
                        showError(getString(R.string.onedrive_file_access_error, error.message))
                    }
                )
            }
        }
    }
    
    private fun showLoading(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        rvOneDriveFiles.visibility = View.GONE
        tvMessage.visibility = View.GONE
    }
    
    private fun showMessage(message: String) {
        progressBar.visibility = View.GONE
        rvOneDriveFiles.visibility = View.GONE
        tvMessage.visibility = View.VISIBLE
        tvMessage.text = message
    }
    
    private fun showError(message: String) {
        showMessage(message)
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }
    
    /**
     * Adapter for displaying OneDrive files
     */
    inner class OneDriveRealFileAdapter(
        private val onItemClick: (OneDriveManager.DriveItem) -> Unit
    ) : ListAdapter<OneDriveManager.DriveItem, OneDriveRealFileAdapter.FileViewHolder>(
        FileDiffCallback()
    ) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_onedrive_file, parent, false)
            return FileViewHolder(view)
        }

        override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
            val item = getItem(position)
            holder.bind(item)
        }

        inner class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvFileName: TextView = itemView.findViewById(R.id.tvFileName)
            private val tvFileInfo: TextView = itemView.findViewById(R.id.tvFileInfo)
            private val ivFileIcon: ImageView = itemView.findViewById(R.id.ivFileIcon)

            init {
                itemView.setOnClickListener {
                    val position = adapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        onItemClick(getItem(position))
                    }
                }
            }

            fun bind(item: OneDriveManager.DriveItem) {
                tvFileName.text = item.name

                // Display file size
                val size = item.size
                val sizeMB = if (size > 0L) {
                    String.format("%.2f MB", size.toDouble() / (1024 * 1024))
                } else {
                    "Unknown size"
                }

                tvFileInfo.text = sizeMB

                // Set appropriate icon based on file type
                val fileExtension = item.name.substringAfterLast('.', "").lowercase()
                when (fileExtension) {
                    "mp3" -> ivFileIcon.setImageResource(R.drawable.ic_audio_file)
                    "m4a" -> ivFileIcon.setImageResource(R.drawable.ic_audio_file)
                    "wav" -> ivFileIcon.setImageResource(R.drawable.ic_audio_file)
                    "ogg" -> ivFileIcon.setImageResource(R.drawable.ic_audio_file)
                    "flac" -> ivFileIcon.setImageResource(R.drawable.ic_audio_file)
                    else -> ivFileIcon.setImageResource(R.drawable.ic_file)
                }
            }
        }
    }
    
    class FileDiffCallback : DiffUtil.ItemCallback<OneDriveManager.DriveItem>() {
        override fun areItemsTheSame(oldItem: OneDriveManager.DriveItem, newItem: OneDriveManager.DriveItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: OneDriveManager.DriveItem, newItem: OneDriveManager.DriveItem): Boolean {
            return oldItem.name == newItem.name && oldItem.size == newItem.size
        }
    }
} 