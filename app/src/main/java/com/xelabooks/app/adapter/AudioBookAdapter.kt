package com.xelabooks.app.adapter

import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.xelabooks.app.R
import com.xelabooks.app.model.AudioBook
import com.google.android.material.chip.Chip
import java.io.File
import java.util.concurrent.TimeUnit

private const val TAG = "AudioBookAdapter"

class AudioBookAdapter(
    private val onItemClick: (AudioBook) -> Unit,
    private val onDeleteClick: (AudioBook) -> Unit
) : 
    ListAdapter<AudioBook, AudioBookAdapter.AudioBookViewHolder>(AudioBookDiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AudioBookViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_audiobook, parent, false)
        return AudioBookViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: AudioBookViewHolder, position: Int) {
        val audioBook = getItem(position)
        holder.bind(audioBook)
    }
    
    inner class AudioBookViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleTextView: TextView = itemView.findViewById(R.id.tvBookTitle)
        private val authorTextView: TextView = itemView.findViewById(R.id.tvBookAuthor)
        private val durationChip: Chip = itemView.findViewById(R.id.tvDuration)
        private val coverImageView: ImageView = itemView.findViewById(R.id.ivBookCover)
        private val playButton: ImageButton = itemView.findViewById(R.id.btnPlayBook)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.btnDeleteBook)
        
        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
            
            // Play button click listener
            playButton.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    try {
                        onItemClick(getItem(position))
                    } catch (e: Exception) {
                        Log.e(TAG, "Error navigating from play button: ${e.message}")
                        Toast.makeText(itemView.context, "Could not play this book", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            
            // Delete button click listener
            deleteButton.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onDeleteClick(getItem(position))
                }
            }
        }
        
        fun bind(audioBook: AudioBook) {
            Log.d(TAG, "Binding audiobook: ${audioBook.title} by ${audioBook.author}")
            titleTextView.text = audioBook.title
            authorTextView.text = audioBook.author
            
            // Format duration
            val hours = TimeUnit.MILLISECONDS.toHours(audioBook.duration)
            val minutes = TimeUnit.MILLISECONDS.toMinutes(audioBook.duration) % 60
            val seconds = TimeUnit.MILLISECONDS.toSeconds(audioBook.duration) % 60
            
            val formattedDuration = if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
            
            durationChip.text = formattedDuration
            
            // Load cover image
            if (audioBook.coverImagePath != null) {
                try {
                    // First try to parse as a URI
                    val imageUri = Uri.parse(audioBook.coverImagePath)
                    Log.d(TAG, "Loading cover image from URI: $imageUri")
                    
                    Glide.with(itemView.context)
                        .load(imageUri)
                        .placeholder(R.drawable.placeholder_book_cover)
                        .error(R.drawable.placeholder_book_cover)
                        .into(coverImageView)
                } catch (e: Exception) {
                    // Fallback to treating it as a file path
                    Log.w(TAG, "Failed to load cover as URI, trying as File: ${e.message}")
                    Glide.with(itemView.context)
                        .load(File(audioBook.coverImagePath))
                        .placeholder(R.drawable.placeholder_book_cover)
                        .error(R.drawable.placeholder_book_cover)
                        .into(coverImageView)
                }
            } else {
                // Set default cover image
                coverImageView.setImageResource(R.drawable.placeholder_book_cover)
            }
        }
    }
}

class AudioBookDiffCallback : DiffUtil.ItemCallback<AudioBook>() {
    override fun areItemsTheSame(oldItem: AudioBook, newItem: AudioBook): Boolean {
        return oldItem.id == newItem.id
    }
    
    override fun areContentsTheSame(oldItem: AudioBook, newItem: AudioBook): Boolean {
        return oldItem == newItem
    }
} 