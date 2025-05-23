package com.xelabooks.app.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.xelabooks.app.data.AudioBookDatabase
import com.xelabooks.app.data.AudioBookRepository
import com.xelabooks.app.model.AudioBook
import com.xelabooks.app.model.Chapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AudioBookViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository: AudioBookRepository
    val allBooks: LiveData<List<AudioBook>>
    
    init {
        val database = AudioBookDatabase.getDatabase(application)
        val dao = database.audioBookDao()
        val chapterDao = database.chapterDao()
        repository = AudioBookRepository(dao, chapterDao)
        allBooks = repository.allBooks
    }
    
    fun insert(audioBook: AudioBook) = viewModelScope.launch(Dispatchers.IO) {
        repository.insert(audioBook)
    }
    
    fun delete(audioBook: AudioBook) = viewModelScope.launch(Dispatchers.IO) {
        repository.delete(audioBook)
    }
    
    fun getBookById(id: String): LiveData<AudioBook?> = liveData(Dispatchers.IO) {
        emit(repository.getBookById(id))
    }
    
    fun updatePlayPosition(bookId: String, position: Long) = viewModelScope.launch(Dispatchers.IO) {
        repository.updatePlayPosition(bookId, position)
    }
    
    fun updateCurrentChapter(bookId: String, chapterId: String?) = viewModelScope.launch(Dispatchers.IO) {
        repository.updateCurrentChapter(bookId, chapterId)
    }
    
    fun updatePlaybackState(bookId: String, position: Long, chapterId: String?) = viewModelScope.launch(Dispatchers.IO) {
        repository.updatePlaybackState(bookId, position, chapterId)
    }
    
    fun searchBooks(query: String): LiveData<List<AudioBook>> {
        return repository.searchBooks(query)
    }
    
    // Chapter operations
    fun insertChapter(chapter: Chapter) = viewModelScope.launch(Dispatchers.IO) {
        repository.insertChapter(chapter)
    }
    
    fun insertChapters(chapters: List<Chapter>) = viewModelScope.launch(Dispatchers.IO) {
        repository.insertChapters(chapters)
    }
    
    fun updateChapter(chapter: Chapter) = viewModelScope.launch(Dispatchers.IO) {
        repository.updateChapter(chapter)
    }
    
    fun deleteChapter(chapter: Chapter) = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteChapter(chapter)
    }
    
    fun getChaptersForBook(bookId: String): LiveData<List<Chapter>> {
        return repository.getChaptersForBook(bookId)
    }
    
    fun getChapterById(chapterId: String): LiveData<Chapter?> = liveData(Dispatchers.IO) {
        emit(repository.getChapterById(chapterId))
    }
    
    fun updateChapterPlayPosition(chapterId: String, position: Long) = viewModelScope.launch(Dispatchers.IO) {
        repository.updateChapterPlayPosition(chapterId, position)
    }
    
    suspend fun getNextChapter(bookId: String, currentSequence: Int): Chapter? {
        return withContext(Dispatchers.IO) {
            repository.getNextChapter(bookId, currentSequence)
        }
    }
    
    suspend fun getTotalBookDuration(bookId: String): Long {
        return withContext(Dispatchers.IO) {
            repository.getTotalBookDuration(bookId)
        }
    }
    
    fun addBookWithChapters(book: AudioBook, chapters: List<Chapter>) = viewModelScope.launch(Dispatchers.IO) {
        // Insert book first to get ID
        repository.insert(book)
        
        // Set book ID for each chapter and insert chapters
        val chaptersWithBookId = chapters.mapIndexed { index, chapter ->
            chapter.copy(bookId = book.id, sequence = index)
        }
        repository.insertChapters(chaptersWithBookId)
    }
} 