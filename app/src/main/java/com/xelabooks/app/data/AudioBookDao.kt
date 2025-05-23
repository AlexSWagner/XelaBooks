package com.xelabooks.app.data

import androidx.lifecycle.LiveData
import androidx.room.*
import com.xelabooks.app.model.AudioBook

@Dao
interface AudioBookDao {
    @Query("SELECT * FROM audiobooks ORDER BY dateAdded DESC")
    fun getAllBooks(): LiveData<List<AudioBook>>
    
    @Query("SELECT * FROM audiobooks WHERE id = :bookId")
    suspend fun getBookById(bookId: String): AudioBook?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: AudioBook)
    
    @Delete
    suspend fun deleteBook(book: AudioBook)
    
    @Query("UPDATE audiobooks SET lastPlayedPosition = :position WHERE id = :bookId")
    suspend fun updatePlayPosition(bookId: String, position: Long)
    
    @Query("UPDATE audiobooks SET currentChapterId = :chapterId WHERE id = :bookId")
    suspend fun updateCurrentChapter(bookId: String, chapterId: String?)
    
    @Query("UPDATE audiobooks SET lastPlayedPosition = :position, currentChapterId = :chapterId WHERE id = :bookId")
    suspend fun updatePlaybackState(bookId: String, position: Long, chapterId: String?)
    
    @Query("SELECT * FROM audiobooks WHERE title LIKE '%' || :query || '%' OR author LIKE '%' || :query || '%'")
    fun searchBooks(query: String): LiveData<List<AudioBook>>
} 