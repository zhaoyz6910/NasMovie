package com.example.nasmovie.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.nasmovie.data.model.Movie
import com.example.nasmovie.data.model.WatchProgress
import com.example.nasmovie.data.repository.MovieRepository
import kotlinx.coroutines.launch

class DetailViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MovieRepository()
    private var currentMovieId: String? = null

    private val _movie = MutableLiveData<Movie?>()
    val movie: LiveData<Movie?> = _movie

    private val _isFavorite = MutableLiveData<Boolean>()
    val isFavorite: LiveData<Boolean> = _isFavorite

    private val _watchProgress = MutableLiveData<WatchProgress?>()
    val watchProgress: LiveData<WatchProgress?> = _watchProgress

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    fun init(movieId: String) {
        if (currentMovieId != movieId) {
            currentMovieId = movieId
            loadData()
        }
    }

    private fun loadData() {
        val id = currentMovieId ?: return
        
        _isLoading.value = true
        viewModelScope.launch {
            val m = repository.getMovieById(id)
            val fav = repository.isFavorite(id)
            val progress = repository.getWatchProgress(id)

            _movie.value = m
            _isFavorite.value = fav
            _watchProgress.value = progress
            _isLoading.value = false
        }
    }

    fun toggleFavorite() {
        val id = currentMovieId ?: return
        
        val currentFav = _isFavorite.value ?: false
        val newFav = !currentFav
        
        // 乐观更新 UI
        _isFavorite.value = newFav

        viewModelScope.launch {
            if (newFav) {
                repository.addFavorite(id)
            } else {
                repository.removeFavorite(id)
            }
        }
    }

    fun refreshProgress() {
        val id = currentMovieId ?: return
        
        viewModelScope.launch {
            val progress = repository.getWatchProgress(id)
            _watchProgress.value = progress
        }
    }

    override fun onCleared() {
        super.onCleared()
        repository.close()
    }
}
