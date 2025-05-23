package com.xelabooks.app.data

import androidx.lifecycle.LiveData
import com.xelabooks.app.model.AudioBook
import com.xelabooks.app.model.Chapter

class AudioBookRepository(
    private val audioBookDao: AudioBookDao,
    private val chapterDao: ChapterDao
) {
    
    val allBooks: LiveData<List<AudioBook>> = audioBookDao.getAllBooks()
    
    suspend fun insert(audioBook: AudioBook) {
        audioBookDao.insertBook(audioBook)
    }
    
    suspend fun delete(audioBook: AudioBook) {
        audioBookDao.deleteBook(audioBook)
    }
    
    suspend fun getBookById(id: String): AudioBook? {
        return audioBookDao.getBookById(id)
    }
    
    suspend fun updatePlayPosition(bookId: String, position: Long) {
        audioBookDao.updatePlayPosition(bookId, position)
    }
    
    suspend fun updateCurrentChapter(bookId: String, chapterId: String?) {
        audioBookDao.updateCurrentChapter(bookId, chapterId)
    }
    
    suspend fun updatePlaybackState(bookId: String, position: Long, chapterId: String?) {
        audioBookDao.updatePlaybackState(bookId, position, chapterId)
    }
    
    fun searchBooks(query: String): LiveData<List<AudioBook>> {
        return audioBookDao.searchBooks(query)
    }
    
    // Chapter operations
    suspend fun insertChapter(chapter: Chapter) {
        chapterDao.insertChapter(chapter)
    }
    
    suspend fun insertChapters(chapters: List<Chapter>) {
        chapterDao.insertChapters(chapters)
    }
    
    suspend fun updateChapter(chapter: Chapter) {
        chapterDao.updateChapter(chapter)
    }
    
    suspend fun deleteChapter(chapter: Chapter) {
        chapterDao.deleteChapter(chapter)
    }
    
    fun getChaptersForBook(bookId: String): LiveData<List<Chapter>> {
        return chapterDao.getChaptersForBook(bookId)
    }
    
    suspend fun getChapterById(chapterId: String): Chapter? {
        return chapterDao.getChapterById(chapterId)
    }
    
    suspend fun updateChapterPlayPosition(chapterId: String, position: Long) {
        chapterDao.updatePlayPosition(chapterId, position)
    }
    
    suspend fun getNextChapter(bookId: String, currentSequence: Int): Chapter? {
        return chapterDao.getNextChapter(bookId, currentSequence + 1)
    }
    
    suspend fun getTotalBookDuration(bookId: String): Long {
        return chapterDao.getTotalDuration(bookId)
    }
} 