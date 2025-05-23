package com.xelabooks.app.ui.onedrive

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.xelabooks.app.R
import com.xelabooks.app.data.OneDriveManager
import com.xelabooks.app.databinding.ItemOnedriveBinding
import java.text.DecimalFormat

/**
 * Adapter for OneDrive items in the browser
 */
class OneDriveItemAdapter(
    private val onItemClick: (OneDriveManager.DriveItem) -> Unit,
    private val onItemLongClick: (OneDriveManager.DriveItem) -> Boolean
) : ListAdapter<OneDriveManager.DriveItem, OneDriveItemAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemOnedriveBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemOnedriveBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
            
            binding.root.setOnLongClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    return@setOnLongClickListener onItemLongClick(getItem(position))
                }
                false
            }
        }
        
        fun bind(item: OneDriveManager.DriveItem) {
            binding.textName.text = item.name
            
            // Set icon based on item type
            if (item.isFolder) {
                binding.imageIcon.setImageResource(R.drawable.ic_folder)
                binding.textDetails.text = "Folder"
            } else {
                // File icon based on file type
                if (item.isAudioFile) {
                    binding.imageIcon.setImageResource(R.drawable.ic_audio_file)
                    binding.textDetails.text = formatFileSize(item.size)
                } else if (item.isImageFile) {
                    binding.imageIcon.setImageResource(R.drawable.ic_image_file)
                    binding.textDetails.text = formatFileSize(item.size)
                } else {
                    binding.imageIcon.setImageResource(R.drawable.ic_file)
                    binding.textDetails.text = formatFileSize(item.size)
                }
            }
        }
        
        private fun formatFileSize(size: Long): String {
            if (size <= 0) return "0 B"
            
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
            
            return DecimalFormat("#,##0.#").format(
                size / Math.pow(1024.0, digitGroups.toDouble())
            ) + " " + units[digitGroups]
        }
    }
    
    private class DiffCallback : DiffUtil.ItemCallback<OneDriveManager.DriveItem>() {
        override fun areItemsTheSame(
            oldItem: OneDriveManager.DriveItem,
            newItem: OneDriveManager.DriveItem
        ): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(
            oldItem: OneDriveManager.DriveItem,
            newItem: OneDriveManager.DriveItem
        ): Boolean {
            return oldItem == newItem
        }
    }
} 