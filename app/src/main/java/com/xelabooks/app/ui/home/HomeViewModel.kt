package com.xelabooks.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.xelabooks.app.data.AudioBookDatabase
import com.xelabooks.app.data.AudioBookRepository
import com.xelabooks.app.model.AudioBook
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: AudioBookRepository
    val allBooks: LiveData<List<AudioBook>>
    
    init {
        val database = AudioBookDatabase.getDatabase(application)
        val dao = database.audioBookDao()
        val chapterDao = database.chapterDao()
        repository = AudioBookRepository(dao, chapterDao)
        allBooks = repository.allBooks
    }
    
    fun deleteBook(audioBook: AudioBook) {
        viewModelScope.launch {
            repository.delete(audioBook)
        }
    }
}