package com.example.nasmovie.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.nasmovie.data.model.Movie
import com.example.nasmovie.databinding.ItemFavoriteMovieBinding
import com.example.nasmovie.view.MovieCard

/**
 * 收藏电影适配器 (Kotlin + ListAdapter)
 */
class FavoriteAdapter : ListAdapter<Movie, FavoriteAdapter.ViewHolder>(MovieDiffCallback()) {

    val selectedIds = mutableSetOf<String>()
    var isSelectionMode = false
        private set

    private var clickListener: OnMovieClickListener? = null
    private var longClickListener: OnMovieLongClickListener? = null

    interface OnMovieClickListener {
        fun onMovieClick(movie: Movie, position: Int)
    }

    interface OnMovieLongClickListener {
        fun onMovieLongClick(movie: Movie, position: Int): Boolean
    }

    fun setOnMovieClickListener(listener: OnMovieClickListener) {
        this.clickListener = listener
    }

    fun setOnMovieLongClickListener(listener: OnMovieLongClickListener) {
        this.longClickListener = listener
    }

    // 为了兼容旧的 Java 代码
    fun setMovies(movies: List<Movie>) {
        submitList(movies)
    }

    fun setSelectionMode(enabled: Boolean) {
        this.isSelectionMode = enabled
        if (!enabled) {
            selectedIds.clear()
        }
        notifyDataSetChanged()
    }

    fun toggleSelection(movieId: String) {
        if (selectedIds.contains(movieId)) {
            selectedIds.remove(movieId)
        } else {
            selectedIds.add(movieId)
        }
        notifyDataSetChanged()
    }

    fun getSelectedCount(): Int {
        return selectedIds.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFavoriteMovieBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val movie = getItem(position)
        holder.bind(movie, selectedIds.contains(movie.id), isSelectionMode)
    }

    inner class ViewHolder(private val binding: ItemFavoriteMovieBinding) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val movie = getItem(position)
                    if (isSelectionMode) {
                        toggleSelection(movie.id)
                    } else {
                        clickListener?.onMovieClick(movie, position)
                    }
                }
            }

            binding.root.setOnLongClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val movie = getItem(position)
                    longClickListener?.onMovieLongClick(movie, position) ?: false
                } else {
                    false
                }
            }
        }

        fun bind(movie: Movie, isSelected: Boolean, selectionMode: Boolean) {
            binding.movieCard.setMode(MovieCard.CardMode.FAVORITE)
            binding.movieCard.loadMovie(movie)
            binding.movieCard.setSelected(isSelected && selectionMode)
        }
    }

    private class MovieDiffCallback : DiffUtil.ItemCallback<Movie>() {
        override fun areItemsTheSame(oldItem: Movie, newItem: Movie): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Movie, newItem: Movie): Boolean {
            return oldItem == newItem
        }
    }
}
