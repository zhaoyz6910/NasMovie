package com.example.nasmovie.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.nasmovie.data.model.Movie
import com.example.nasmovie.data.repository.MovieRepository
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MovieRepository()

    private val _allMovies = MutableLiveData<List<Movie>>()
    val allMovies: LiveData<List<Movie>> = _allMovies

    private val _featuredMovies = MutableLiveData<List<Movie>>()
    val featuredMovies: LiveData<List<Movie>> = _featuredMovies

    private val _recentMovies = MutableLiveData<List<Movie>>()
    val recentMovies: LiveData<List<Movie>> = _recentMovies

    private val _highRatedMovies = MutableLiveData<List<Movie>>()
    val highRatedMovies: LiveData<List<Movie>> = _highRatedMovies

    private val _newestMovies = MutableLiveData<List<Movie>>()
    val newestMovies: LiveData<List<Movie>> = _newestMovies

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    var currentSortType = MovieRepository.SortType.ADD_TIME_DESC
        private set

    init {
        loadMovies()
    }

    fun loadMovies() {
        _isLoading.value = true
        
        viewModelScope.launch {
            val movies = repository.getAllMovies(currentSortType)
            val featured = getRandomMovies(movies, 5)
            val recent = getRecentWatchedMovies(10)
            val highRated = getHighRatedMovies(movies, 10)
            val newest = getNewestMovies(movies, 10)

            _allMovies.value = movies
            _featuredMovies.value = featured
            _recentMovies.value = recent
            _highRatedMovies.value = highRated
            _newestMovies.value = newest
            _isLoading.value = false
        }
    }

    fun changeSort(sortType: MovieRepository.SortType) {
        if (currentSortType != sortType) {
            currentSortType = sortType
            loadMovies()
        }
    }

    private fun getRandomMovies(movies: List<Movie>?, limit: Int): List<Movie> {
        if (movies.isNullOrEmpty()) return emptyList()
        val shuffled = movies.shuffled()
        return if (shuffled.size > limit) shuffled.subList(0, limit) else shuffled
    }

    private suspend fun getRecentWatchedMovies(limit: Int): List<Movie> {
        val recentProgress = repository.getRecentWatchProgress(limit)
        val result = mutableListOf<Movie>()
        for (progress in recentProgress) {
            val movie = repository.getMovieById(progress.movieId)
            if (movie != null) {
                movie.progress = progress.percentage
                result.add(movie)
            }
        }
        return result
    }

    private fun getHighRatedMovies(movies: List<Movie>?, limit: Int): List<Movie> {
        if (movies.isNullOrEmpty()) return emptyList()
        val highRated = movies.filter { it.rating >= 8.0f }.sortedByDescending { it.rating }
        return if (highRated.size > limit) highRated.subList(0, limit) else highRated
    }

    private fun getNewestMovies(movies: List<Movie>?, limit: Int): List<Movie> {
        if (movies.isNullOrEmpty()) return emptyList()
        val newest = movies.sortedByDescending { it.addTime }
        return if (newest.size > limit) newest.subList(0, limit) else newest
    }

    override fun onCleared() {
        super.onCleared()
        repository.close()
    }
}
