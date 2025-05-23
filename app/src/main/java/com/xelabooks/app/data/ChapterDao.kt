package com.xelabooks.app.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.xelabooks.app.model.Chapter

@Dao
interface ChapterDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapter(chapter: Chapter)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapters: List<Chapter>)
    
    @Update
    suspend fun updateChapter(chapter: Chapter)
    
    @Delete
    suspend fun deleteChapter(chapter: Chapter)
    
    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY sequence ASC")
    fun getChaptersForBook(bookId: String): LiveData<List<Chapter>>
    
    @Query("SELECT * FROM chapters WHERE id = :chapterId")
    suspend fun getChapterById(chapterId: String): Chapter?
    
    @Query("UPDATE chapters SET lastPlayedPosition = :position WHERE id = :chapterId")
    suspend fun updatePlayPosition(chapterId: String, position: Long)
    
    @Query("SELECT COUNT(*) FROM chapters WHERE bookId = :bookId")
    suspend fun getChapterCount(bookId: String): Int
    
    @Query("SELECT * FROM chapters WHERE bookId = :bookId AND sequence = :nextSequence LIMIT 1")
    suspend fun getNextChapter(bookId: String, nextSequence: Int): Chapter?
    
    @Query("SELECT SUM(duration) FROM chapters WHERE bookId = :bookId")
    suspend fun getTotalDuration(bookId: String): Long
} 