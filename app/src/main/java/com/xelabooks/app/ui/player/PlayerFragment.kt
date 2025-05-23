package com.xelabooks.app.ui.player

import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.xelabooks.app.R
import com.xelabooks.app.databinding.FragmentPlayerBinding
import com.xelabooks.app.model.AudioBook
import com.xelabooks.app.model.Chapter
import com.xelabooks.app.viewmodel.AudioBookViewModel
import java.io.File
import java.util.concurrent.TimeUnit

private const val TAG = "PlayerFragment"

class PlayerFragment : Fragment() {

    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var viewModel: AudioBookViewModel
    
    private var mediaPlayer: MediaPlayer? = null
    private var currentAudioBook: AudioBook? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isPlaying = false
    
    // Chapter related fields
    private var currentChapters = mutableListOf<Chapter>()
    private var currentChapterIndex = 0
    private var isInitialChapterLoad = true // Track if this is the first time loading chapters

    private var lastSavedPosition = -1L // Track last saved position to avoid unnecessary DB writes
    
    private val updateSeekBarTask = object : Runnable {
        override fun run() {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) {
                    binding.seekBar.progress = mp.currentPosition
                    updateCurrentTimeText(mp.currentPosition)
                    
                    // Save position every 10 seconds to reduce DB load, and only if position actually changed significantly
                    val currentPosition = mp.currentPosition.toLong()
                    val positionDiff = kotlin.math.abs(currentPosition - lastSavedPosition)
                    if (currentPosition % 10000 < 1000 && positionDiff > 5000) {
                        lastSavedPosition = currentPosition
                        currentAudioBook?.let { book ->
                            // For chapters, save chapter position
                            if (currentChapters.isNotEmpty() && currentChapterIndex < currentChapters.size) {
                                val chapter = currentChapters[currentChapterIndex]
                                viewModel.updateChapterPlayPosition(chapter.id, currentPosition)
                                // Also update which chapter we're on (but less frequently)
                                if (currentPosition % 30000 < 1000) {
                                    viewModel.updateCurrentChapter(book.id, chapter.id)
                                }
                                Unit
                            } else {
                                // For single-file books
                                viewModel.updatePlayPosition(book.id, currentPosition)
                            }
                        }
                    }
                }
                // Only schedule next update if media player is still playing
                if (mp.isPlaying) {
                    handler.postDelayed(this, 1000)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewModel = ViewModelProvider(requireActivity())[AudioBookViewModel::class.java]
        
        setupUI()
        setupListeners()
        
        // Get book ID from arguments
        arguments?.getString("bookId")?.let { bookId ->
            
            // Set up book observer
            viewModel.getBookById(bookId).observe(viewLifecycleOwner) { book ->
                if (book != null) {
                    loadAudioBook(book)
                } else {
                    Log.e(TAG, "Book not found with ID: $bookId")
                    Toast.makeText(requireContext(), "Book not found", Toast.LENGTH_SHORT).show()
                    
                    // Fallback to first book if available
                    viewModel.allBooks.observe(viewLifecycleOwner) { books ->
                        if (books.isNotEmpty() && currentAudioBook == null) {
                            loadAudioBook(books[0])
                        }
                    }
                }
            }
            
            // Set up chapters observer SEPARATELY to avoid multiple observers
            viewModel.getChaptersForBook(bookId).observe(viewLifecycleOwner) { chapters ->
                if (chapters.isNotEmpty()) {
                    loadChapters(chapters)
                    // Double-check that chapter controls are visible
                    binding.layoutChapters.post {
                        binding.layoutChapters.visibility = View.VISIBLE
                    }
                } else {
                    // Hide chapter controls if no chapters
                    binding.layoutChapters.visibility = View.GONE
                    // Initialize media player for single-file audiobook if book is loaded
                    currentAudioBook?.let { book ->
                        if (book.filePath.isNotEmpty()) {
                            initializeMediaPlayer(book.filePath, book.lastPlayedPosition.toInt())
                        } else {
                            Log.e(TAG, "Book has no chapters and no file path - cannot play")
                            Toast.makeText(requireContext(), "This audiobook cannot be played - no audio files found", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        } ?: run {
            // Fallback if no book ID was passed
            viewModel.allBooks.observe(viewLifecycleOwner) { books ->
                if (books.isNotEmpty() && currentAudioBook == null) {
                    loadAudioBook(books[0])
                }
            }
        }
    }
    
    private fun setupUI() {
        // Set default state
        binding.tvCurrentTime.text = "00:00"
        binding.tvTotalTime.text = "00:00"
        binding.seekBar.progress = 0
        binding.btn1x.isSelected = true
        
        // Initially hide chapter controls
        binding.layoutChapters.visibility = View.GONE
    }
    
    private fun setupListeners() {
        binding.btnPlay.setOnClickListener {
            togglePlayback()
        }
        
        binding.btnRewind.setOnClickListener {
            rewind()
        }
        
        binding.btnForward.setOnClickListener {
            forward()
        }
        
        // Chapter navigation
        binding.btnPrevChapter.setOnClickListener {
            navigateToPreviousChapter()
        }
        
        binding.btnNextChapter.setOnClickListener {
            navigateToNextChapter()
        }
        
        binding.btnChapterList.setOnClickListener {
            showChapterList()
        }
        
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mediaPlayer?.seekTo(progress)
                    updateCurrentTimeText(progress)
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // Pause updates while user is seeking
                handler.removeCallbacks(updateSeekBarTask)
            }
            
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Resume updates
                handler.postDelayed(updateSeekBarTask, 1000)
            }
        })
        
        // Speed buttons
        binding.btn05x.setOnClickListener { setPlaybackSpeed(0.5f) }
        binding.btn075x.setOnClickListener { setPlaybackSpeed(0.75f) }
        binding.btn1x.setOnClickListener { setPlaybackSpeed(1.0f) }
        binding.btn125x.setOnClickListener { setPlaybackSpeed(1.25f) }
        binding.btn15x.setOnClickListener { setPlaybackSpeed(1.5f) }
        binding.btn2x.setOnClickListener { setPlaybackSpeed(2.0f) }
    }
    
    private fun loadAudioBook(audioBook: AudioBook) {
        currentAudioBook = audioBook
        
        // Update UI
        binding.tvPlayerTitle.text = audioBook.title
        binding.tvPlayerAuthor.text = audioBook.author
        
        // Load cover image
        if (audioBook.coverImagePath != null) {
            try {
                // Try loading as URI first
                val coverUri = Uri.parse(audioBook.coverImagePath)
                Glide.with(this)
                    .load(coverUri)
                    .placeholder(R.drawable.placeholder_book_cover)
                    .error(R.drawable.placeholder_book_cover)
                    .into(binding.ivPlayerCover)
            } catch (e: Exception) {
                // Fall back to file path
                Log.w(TAG, "Error loading cover as URI: ${e.message}")
                Glide.with(this)
                    .load(File(audioBook.coverImagePath))
                    .placeholder(R.drawable.placeholder_book_cover)
                    .error(R.drawable.placeholder_book_cover)
                    .into(binding.ivPlayerCover)
            }
        } else {
            binding.ivPlayerCover.setImageResource(R.drawable.placeholder_book_cover)
        }
        
        // Don't initialize media player here - wait for either chapters to be loaded or confirm it's a single-file book
        // The observer in onViewCreated will handle loading chapters if they exist
        // If no chapters exist, initializeMediaPlayer will be called from there with the single file
    }
    
    private fun initializeMediaPlayer(filePath: String, initialPosition: Int) {
        try {
            releaseMediaPlayer()
            
            mediaPlayer = MediaPlayer().apply {
                // Handle both URI string and file path
                try {
                    // First try as URI
                    val uri = Uri.parse(filePath)
                    setDataSource(requireContext(), uri)
                } catch (e: Exception) {
                    // Fall back to file path
                    Log.w(TAG, "Error setting URI data source: ${e.message}")
                    setDataSource(filePath)
                }
                
                prepareAsync()
                
                setOnPreparedListener { mp ->
                    mp.seekTo(initialPosition)
                    
                    // Set up seek bar
                    binding.seekBar.max = duration
                    binding.tvTotalTime.text = formatTime(duration.toLong())
                    updateCurrentTimeText(currentPosition)
                    
                    // Enable play button
                    binding.btnPlay.isEnabled = true
                }
                
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    Toast.makeText(requireContext(), "Error playing audio file", Toast.LENGTH_SHORT).show()
                    true
                }
                
                setOnCompletionListener {
                    binding.btnPlay.setImageResource(android.R.drawable.ic_media_play)
                    this@PlayerFragment.isPlaying = false
                    binding.seekBar.progress = 0
                    updateCurrentTimeText(0)
                    
                    // Save position as 0 to start from beginning next time
                    currentAudioBook?.let { book ->
                        viewModel.updatePlaybackState(book.id, 0, null)
                    }
                }
            }
            
            // Start media player updates
            handler.postDelayed(updateSeekBarTask, 1000)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing MediaPlayer", e)
            Toast.makeText(requireContext(), "Error playing audio file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun togglePlayback() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                binding.btnPlay.setImageResource(R.drawable.ic_play)
                // Remove any existing handler callbacks when pausing
                handler.removeCallbacks(updateSeekBarTask)
            } else {
                it.start()
                binding.btnPlay.setImageResource(R.drawable.ic_pause)
                // Only start handler if not already running
                handler.removeCallbacks(updateSeekBarTask)
                handler.postDelayed(updateSeekBarTask, 1000)
            }
            this@PlayerFragment.isPlaying = it.isPlaying
        }
    }
    
    private fun rewind() {
        mediaPlayer?.let {
            val newPosition = (it.currentPosition - 10000).coerceAtLeast(0)
            it.seekTo(newPosition)
            binding.seekBar.progress = newPosition
            updateCurrentTimeText(newPosition)
        }
    }
    
    private fun forward() {
        mediaPlayer?.let {
            val newPosition = (it.currentPosition + 30000).coerceAtMost(it.duration)
            it.seekTo(newPosition)
            binding.seekBar.progress = newPosition
            updateCurrentTimeText(newPosition)
        }
    }
    
    private fun updateCurrentTimeText(position: Int) {
        binding.tvCurrentTime.text = formatTime(position.toLong())
    }
    
    private fun formatTime(timeMs: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(timeMs)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(timeMs) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(timeMs) % 60
        
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }
    
    private fun setPlaybackSpeed(speed: Float) {
        mediaPlayer?.let {
            it.playbackParams = it.playbackParams.setSpeed(speed)
        }
        
        // Update UI
        binding.btn05x.isChecked = speed == 0.5f
        binding.btn075x.isChecked = speed == 0.75f
        binding.btn1x.isChecked = speed == 1.0f
        binding.btn125x.isChecked = speed == 1.25f
        binding.btn15x.isChecked = speed == 1.5f
        binding.btn2x.isChecked = speed == 2.0f
    }
    
    private fun releaseMediaPlayer() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release()
        }
        mediaPlayer = null
        handler.removeCallbacks(updateSeekBarTask)
    }
    
    override fun onPause() {
        super.onPause()
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                binding.btnPlay.setImageResource(R.drawable.ic_play)
            }
            
            // Save position
            currentAudioBook?.let { book ->
                // For chapters, save chapter position
                if (currentChapters.isNotEmpty() && currentChapterIndex < currentChapters.size) {
                    val chapter = currentChapters[currentChapterIndex]
                    viewModel.updateChapterPlayPosition(chapter.id, it.currentPosition.toLong())
                    // Save the current chapter ID with the book
                    viewModel.updateCurrentChapter(book.id, chapter.id)
                } else {
                    // For single-file books
                    viewModel.updatePlayPosition(book.id, it.currentPosition.toLong())
                }
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        releaseMediaPlayer()
        _binding = null
    }
    
    private fun loadChapters(chapters: List<Chapter>) {
        currentChapters.clear()
        currentChapters.addAll(chapters.sortedBy { it.sequence })
        
        // Show chapter controls - Force visibility
        binding.layoutChapters.visibility = View.VISIBLE
        
        // Only restore saved chapter on initial load, not on subsequent observer triggers
        if (isInitialChapterLoad) {
            // Check if we should restore to a specific chapter
            val savedChapterId = currentAudioBook?.currentChapterId
            if (savedChapterId != null) {
                // Find the chapter in our list
                val chapterIndex = currentChapters.indexOfFirst { it.id == savedChapterId }
                if (chapterIndex != -1) {
                    currentChapterIndex = chapterIndex
                    loadChapter(currentChapters[currentChapterIndex])
                    isInitialChapterLoad = false // Mark as no longer initial load
                    return
                } else {
                    Log.w(TAG, "Saved chapter ID not found in chapter list")
                }
            }
            
            // If no saved chapter, load the first chapter
            if (currentChapters.isNotEmpty()) {
                currentChapterIndex = 0
                loadChapter(currentChapters[currentChapterIndex])
            }
            
            isInitialChapterLoad = false // Mark as no longer initial load
        } else {
            // On subsequent loads, just update the chapter list but don't change current selection
        }
        
        updateChapterButtonStates()
    }
    
    private fun loadChapter(chapter: Chapter) {
        // Save current playback state
        val wasPlaying = mediaPlayer?.isPlaying ?: false
        
        // Save this as the current chapter for the book
        currentAudioBook?.let { book ->
            viewModel.updateCurrentChapter(book.id, chapter.id)
        }
        
        // Release current media player
        releaseMediaPlayer()
        
        // Create new media player
        mediaPlayer = MediaPlayer().apply {
            try {
                // First try as URI
                val uri = Uri.parse(chapter.filePath)
                setDataSource(requireContext(), uri)
            } catch (e: Exception) {
                // Fall back to file path
                Log.w(TAG, "Error setting URI data source: ${e.message}")
                setDataSource(chapter.filePath)
            }
            
            prepareAsync()
            
            setOnPreparedListener { mp ->
                mp.seekTo(chapter.lastPlayedPosition.toInt())
                
                // Set up seek bar
                binding.seekBar.max = duration
                binding.tvTotalTime.text = formatTime(duration.toLong())
                updateCurrentTimeText(currentPosition)
                
                // Enable play button
                binding.btnPlay.isEnabled = true
                
                // Resume playback if it was playing
                if (wasPlaying) {
                    start()
                    binding.btnPlay.setImageResource(R.drawable.ic_pause)
                    this@PlayerFragment.isPlaying = true
                }
            }
            
            setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                Toast.makeText(requireContext(), "Error playing audio file", Toast.LENGTH_SHORT).show()
                true
            }
            
            setOnCompletionListener {
                if (currentChapterIndex < currentChapters.size - 1) {
                    // Move to next chapter
                    currentChapterIndex++
                    loadChapter(currentChapters[currentChapterIndex])
                } else {
                    // End of book
                    binding.btnPlay.setImageResource(R.drawable.ic_play)
                    this@PlayerFragment.isPlaying = false
                    binding.seekBar.progress = 0
                    updateCurrentTimeText(0)
                    
                    // Save position as 0 to start from beginning next time
                    currentAudioBook?.let { book ->
                        viewModel.updatePlaybackState(book.id, 0, null)
                    }
                }
            }
        }
        
        // Update the book title with chapter info
        updateTitleWithChapterInfo()
        
        // Start media player updates
        handler.postDelayed(updateSeekBarTask, 1000)
        
        // Update navigation buttons
        updateChapterButtonStates()
    }
    
    private fun updateTitleWithChapterInfo() {
        if (currentChapters.isNotEmpty() && currentChapterIndex < currentChapters.size) {
            val chapter = currentChapters[currentChapterIndex]
            val title = currentAudioBook?.title ?: ""
            binding.tvPlayerTitle.text = "$title\n${chapter.title} (${currentChapterIndex + 1}/${currentChapters.size})"
        }
    }
    
    private fun updateChapterButtonStates() {
        binding.btnPrevChapter.isEnabled = currentChapterIndex > 0
        binding.btnNextChapter.isEnabled = currentChapterIndex < currentChapters.size - 1
    }
    
    private fun navigateToPreviousChapter() {
        if (currentChapterIndex > 0) {
            currentChapterIndex--
            loadChapter(currentChapters[currentChapterIndex])
        }
    }
    
    private fun navigateToNextChapter() {
        if (currentChapterIndex < currentChapters.size - 1) {
            currentChapterIndex++
            loadChapter(currentChapters[currentChapterIndex])
        }
    }
    
    private fun showChapterList() {
        if (currentChapters.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.no_chapters_available), Toast.LENGTH_SHORT).show()
            return
        }
        
        val chapterTitles = currentChapters.map { 
            "${it.sequence + 1}. ${it.title}" 
        }.toTypedArray()
        
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.chapters)
            .setSingleChoiceItems(chapterTitles, currentChapterIndex) { dialog, index ->
                if (index != currentChapterIndex) {
                    currentChapterIndex = index
                    loadChapter(currentChapters[currentChapterIndex])
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
} 