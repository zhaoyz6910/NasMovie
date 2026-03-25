package com.example.nasmovie.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.nasmovie.data.model.Movie
import com.example.nasmovie.databinding.ItemSearchResultBinding
import com.example.nasmovie.view.MovieCard

/**
 * 搜索结果适配器 - 3列网格布局 (Kotlin + ListAdapter)
 */
class SearchResultAdapter : ListAdapter<Movie, SearchResultAdapter.ViewHolder>(MovieDiffCallback()) {

    private var listener: OnMovieClickListener? = null

    interface OnMovieClickListener {
        fun onMovieClick(movie: Movie)
    }

    fun setOnMovieClickListener(listener: OnMovieClickListener) {
        this.listener = listener
    }

    // 兼容旧代码
    fun setMovies(movies: List<Movie>?) {
        submitList(movies ?: emptyList())
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSearchResultBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemSearchResultBinding) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    listener?.onMovieClick(getItem(position))
                }
            }
        }

        fun bind(movie: Movie) {
            binding.movieCard.setMode(MovieCard.CardMode.SEARCH)
            binding.movieCard.loadMovie(movie)
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
