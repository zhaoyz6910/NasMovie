package com.example.nasmovie.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.nasmovie.data.local.SearchHistoryManager
import com.example.nasmovie.data.model.Movie
import com.example.nasmovie.data.repository.MovieRepository
import kotlinx.coroutines.launch

class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MovieRepository()
    private val historyManager = SearchHistoryManager(application)

    private val _searchResults = MutableLiveData<List<Movie>?>()
    val searchResults: LiveData<List<Movie>?> = _searchResults

    private val _searchHistory = MutableLiveData<List<String>>()
    val searchHistory: LiveData<List<String>> = _searchHistory

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    init {
        loadSearchHistory()
    }

    fun loadSearchHistory() {
        _searchHistory.value = historyManager.getSearchHistory()
    }

    fun clearHistory() {
        historyManager.clearHistory()
        loadSearchHistory()
    }

    fun performSearch(query: String?) {
        if (query.isNullOrBlank()) {
            _searchResults.value = null
            return
        }

        _isLoading.value = true
        
        viewModelScope.launch {
            historyManager.addSearchRecord(query)
            val results = repository.searchMovies(query)
            
            _searchResults.value = results
            _isLoading.value = false
            loadSearchHistory() // 刷新搜索历史
        }
    }

    fun clearSearchResults() {
        _searchResults.value = null
    }

    override fun onCleared() {
        super.onCleared()
        repository.close()
    }
}
