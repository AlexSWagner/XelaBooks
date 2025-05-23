package com.xelabooks.app.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable
import java.util.UUID

@Entity(tableName = "audiobooks")
data class AudioBook(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val author: String,
    val duration: Long = 0, // Duration in milliseconds
    val filePath: String,
    val coverImagePath: String? = null,
    val description: String? = null,
    val lastPlayedPosition: Long = 0,
    val currentChapterId: String? = null, // Track which chapter was last played
    val dateAdded: Long = System.currentTimeMillis()
) : Serializable 