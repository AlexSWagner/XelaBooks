package com.xelabooks.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.xelabooks.app.R
import com.xelabooks.app.model.Chapter
import java.io.File

class ChapterAdapter(
    private val onRemoveClick: (Chapter) -> Unit
) : ListAdapter<Chapter, ChapterAdapter.ChapterViewHolder>(ChapterDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChapterViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chapter, parent, false)
        return ChapterViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChapterViewHolder, position: Int) {
        val chapter = getItem(position)
        holder.bind(chapter, position + 1) // Chapter numbers are 1-based
    }

    inner class ChapterViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvChapterNumber: TextView = itemView.findViewById(R.id.tvChapterNumber)
        private val tvChapterTitle: TextView = itemView.findViewById(R.id.tvChapterTitle)
        private val tvChapterFile: TextView = itemView.findViewById(R.id.tvChapterFile)
        private val btnRemoveChapter: ImageButton = itemView.findViewById(R.id.btnRemoveChapter)

        fun bind(chapter: Chapter, number: Int) {
            tvChapterNumber.text = number.toString()
            tvChapterTitle.text = chapter.title
            
            // Display filename
            val filename = if (chapter.filePath.startsWith("content://")) {
                // Extract filename from URI
                chapter.filePath.substringAfterLast('/')
            } else {
                // Extract filename from file path
                File(chapter.filePath).name
            }
            tvChapterFile.text = filename
            
            btnRemoveChapter.setOnClickListener {
                onRemoveClick(chapter)
            }
        }
    }
}

class ChapterDiffCallback : DiffUtil.ItemCallback<Chapter>() {
    override fun areItemsTheSame(oldItem: Chapter, newItem: Chapter): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Chapter, newItem: Chapter): Boolean {
        return oldItem == newItem
    }
} 