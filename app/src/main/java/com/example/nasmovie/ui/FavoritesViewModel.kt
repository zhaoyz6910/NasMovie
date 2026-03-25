package com.example.nasmovie.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.nasmovie.data.model.Movie
import com.example.nasmovie.data.repository.MovieRepository
import kotlinx.coroutines.launch
import java.util.Locale

class FavoritesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MovieRepository(application)

    private val _allFavorites = MutableLiveData<List<Movie>>()
    
    private val _displayedFavorites = MutableLiveData<List<Movie>>()
    val displayedFavorites: LiveData<List<Movie>> = _displayedFavorites

    init {
        loadFavorites()
    }

    fun loadFavorites() {
        viewModelScope.launch {
            val favorites = repository.getFavoriteMovies()
            _allFavorites.value = favorites
            _displayedFavorites.value = favorites
        }
    }

    fun filterFavorites(query: String?) {
        val all = _allFavorites.value ?: return

        if (query.isNullOrBlank()) {
            _displayedFavorites.value = all
        } else {
            val lowerQuery = query.lowercase(Locale.ROOT)
            val filtered = all.filter {
                it.title?.lowercase(Locale.ROOT)?.contains(lowerQuery) == true
            }
            _displayedFavorites.value = filtered
        }
    }

    fun deleteSelected(selectedIds: Set<String>) {
        viewModelScope.launch {
            for (movieId in selectedIds) {
                repository.removeFavorite(movieId)
            }
            // 重新加载数据以确保一致性
            loadFavorites()
        }
    }

    override fun onCleared() {
        super.onCleared()
        repository.close()
    }
}
