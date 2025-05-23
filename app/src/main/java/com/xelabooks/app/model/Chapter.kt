package com.xelabooks.app.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.io.Serializable
import java.util.UUID

@Entity(
    tableName = "chapters",
    foreignKeys = [
        ForeignKey(
            entity = AudioBook::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("bookId")]
)
data class Chapter(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val bookId: String,
    val title: String,
    val filePath: String,
    val duration: Long = 0,
    val sequence: Int,  // Chapter order in book
    val lastPlayedPosition: Long = 0,
    val dateAdded: Long = System.currentTimeMillis()
) : Serializable 