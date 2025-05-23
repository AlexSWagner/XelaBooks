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
            Log.d(TAG, "Loading book with ID: $bookId")
            viewModel.getBookById(bookId).observe(viewLifecycleOwner) { book ->
                if (book != null) {
                    loadAudioBook(book)
                    
                    // Load chapters for this book
                    viewModel.getChaptersForBook(bookId).observe(viewLifecycleOwner) { chapters ->
                        if (chapters.isNotEmpty()) {
                            Log.d(TAG, "Loaded ${chapters.size} chapters for book")
                            loadChapters(chapters)
                            // Double-check that chapter controls are visible
                            binding.layoutChapters.post {
                                binding.layoutChapters.visibility = View.VISIBLE
                                Log.d(TAG, "Post-ensuring chapter controls visibility")
                            }
                        } else {
                            Log.d(TAG, "No chapters found for book")
                            // Hide chapter controls if no chapters
                            binding.layoutChapters.visibility = View.GONE
                        }
                    }
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
        } ?: run {
            // Fallback if no book ID was passed
            Log.d(TAG, "No book ID provided, loading first available book")
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
        Log.d(TAG, "Loading audiobook: ${audioBook.title} with path: ${audioBook.filePath}")
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
        
        // If book has chapters, load them first and let that handle media player setup
        if (audioBook.currentChapterId != null) {
            Log.d(TAG, "Book has a saved chapter ID: ${audioBook.currentChapterId}")
            // Continue with loading chapters - we'll select the correct one when chapters are loaded
        } else {
            // Initialize media player for the main audiobook file
            initializeMediaPlayer(audioBook.filePath, audioBook.lastPlayedPosition.toInt())
        }
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
                    Log.d(TAG, "Set data source as URI: $uri")
                } catch (e: Exception) {
                    // Fall back to file path
                    Log.w(TAG, "Error setting URI data source: ${e.message}")
                    setDataSource(filePath)
                    Log.d(TAG, "Set data source as file path")
                }
                
                prepareAsync()
                
                setOnPreparedListener { mp ->
                    Log.d(TAG, "MediaPlayer prepared, duration: ${mp.duration}ms")
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
                    Log.d(TAG, "Playback completed")
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
    
    private val updateSeekBarTask = object : Runnable {
        override fun run() {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    binding.seekBar.progress = it.currentPosition
                    updateCurrentTimeText(it.currentPosition)
                    
                    // Save position every 5 seconds
                    if (it.currentPosition % 5000 < 1000) {
                        currentAudioBook?.let { book ->
                            // For chapters, save chapter position
                            if (currentChapters.isNotEmpty() && currentChapterIndex < currentChapters.size) {
                                val chapter = currentChapters[currentChapterIndex]
                                viewModel.updateChapterPlayPosition(chapter.id, it.currentPosition.toLong())
                                // Also update which chapter we're on
                                viewModel.updateCurrentChapter(book.id, chapter.id)
                            } else {
                                // For single-file books
                                viewModel.updatePlayPosition(book.id, it.currentPosition.toLong())
                            }
                        }
                    }
                }
                handler.postDelayed(this, 1000)
            }
        }
    }
    
    private fun togglePlayback() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                binding.btnPlay.setImageResource(R.drawable.ic_play)
            } else {
                it.start()
                binding.btnPlay.setImageResource(R.drawable.ic_pause)
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
        Log.d(TAG, "Loading ${chapters.size} chapters")
        currentChapters.clear()
        currentChapters.addAll(chapters.sortedBy { it.sequence })
        
        // Show chapter controls - Force visibility
        binding.layoutChapters.visibility = View.VISIBLE
        Log.d(TAG, "Setting chapter controls to VISIBLE")
        
        // Check if we should restore to a specific chapter
        val savedChapterId = currentAudioBook?.currentChapterId
        if (savedChapterId != null) {
            Log.d(TAG, "Found saved chapter ID: $savedChapterId")
            // Find the chapter in our list
            val chapterIndex = currentChapters.indexOfFirst { it.id == savedChapterId }
            if (chapterIndex != -1) {
                Log.d(TAG, "Loading saved chapter at index $chapterIndex")
                currentChapterIndex = chapterIndex
                loadChapter(currentChapters[currentChapterIndex])
                return
            } else {
                Log.w(TAG, "Saved chapter ID not found in chapter list")
            }
        }
        
        // If we have single file and chapters, switch to the first chapter
        if (mediaPlayer != null && currentChapters.isNotEmpty()) {
            // Check if we're already playing a chapter
            val currentFilePath = currentAudioBook?.filePath ?: ""
            val isPlayingChapter = currentChapters.any { it.filePath == currentFilePath }
            
            if (!isPlayingChapter) {
                currentChapterIndex = 0
                loadChapter(currentChapters[currentChapterIndex])
            }
        }
        
        updateChapterButtonStates()
    }
    
    private fun loadChapter(chapter: Chapter) {
        Log.d(TAG, "Loading chapter: ${chapter.title}")
        
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
                Log.d(TAG, "Set data source as URI: $uri")
            } catch (e: Exception) {
                // Fall back to file path
                Log.w(TAG, "Error setting URI data source: ${e.message}")
                setDataSource(chapter.filePath)
                Log.d(TAG, "Set data source as file path")
            }
            
            prepareAsync()
            
            setOnPreparedListener { mp ->
                Log.d(TAG, "MediaPlayer prepared for chapter, duration: ${mp.duration}ms")
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
                Log.d(TAG, "Chapter playback completed")
                
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