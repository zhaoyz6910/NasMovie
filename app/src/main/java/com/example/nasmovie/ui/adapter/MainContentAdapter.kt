package com.example.nasmovie.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.nasmovie.R
import com.example.nasmovie.data.model.Movie
import com.example.nasmovie.data.repository.MovieRepository
import com.example.nasmovie.databinding.ItemMainSectionBinding
import com.example.nasmovie.databinding.ItemMovieGridBinding
import com.example.nasmovie.databinding.ItemMovieHorizontalScrollBinding
import com.example.nasmovie.databinding.ItemSectionHeaderBinding
import com.example.nasmovie.view.MovieCard
import java.lang.ref.WeakReference

/**
 * 主内容适配器 - 合并横向分类和网格列表 (Kotlin 重构版)
 */
class MainContentAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_SECTION = 0
        const val TYPE_GRID_HEADER = 1
        const val TYPE_GRID_ITEM = 2
        const val TYPE_HEADER = 3

        private const val TYPE_SECTION_PRIVATE = 0
        private const val TYPE_GRID_HEADER_PRIVATE = 1
        private const val TYPE_GRID_ITEM_PRIVATE = 2
        private const val TYPE_HEADER_PRIVATE = 3
    }

    private var sections = listOf<SectionData>()
    private var gridMovies = listOf<Movie>()
    
    private var listener: OnMovieClickListener? = null
    private var sortClickListener: (() -> Unit)? = null
    
    private var headerViewRef: WeakReference<View>? = null
    private var currentSortText: String? = null
    
    // 保留以兼容旧代码
    private var repository: MovieRepository? = null

    data class SectionData(val title: String, val movies: List<Movie>)

    interface OnMovieClickListener {
        fun onMovieClick(movie: Movie)
    }

    fun setOnMovieClickListener(listener: OnMovieClickListener) {
        this.listener = listener
    }

    fun setOnSortClickListener(listener: () -> Unit) {
        this.sortClickListener = listener
    }

    fun setRepository(repository: MovieRepository) {
        this.repository = repository
    }

    var headerView: View?
        get() = headerViewRef?.get()
        set(value) {
            headerViewRef = if (value != null) WeakReference(value) else null
            notifyDataSetChanged()
        }

    // 兼容方法
    fun getHeaderViewCompat(): View? = headerView

    fun setData(sections: List<SectionData>?, gridMovies: List<Movie>?) {
        this.sections = sections ?: emptyList()
        this.gridMovies = gridMovies ?: emptyList()
        notifyDataSetChanged()
    }

    fun setSortText(sortText: String) {
        this.currentSortText = sortText
        val offset = if (headerView != null) 1 else 0
        val headerPosition = offset + sections.size
        notifyItemChanged(headerPosition)
    }

    override fun getItemViewType(position: Int): Int {
        val hasHeader = headerView != null
        val offset = if (hasHeader) 1 else 0
        val sectionCount = sections.size
        val adjustedPosition = position - offset

        return when {
            position == 0 && hasHeader -> TYPE_HEADER_PRIVATE
            adjustedPosition < sectionCount -> TYPE_SECTION_PRIVATE
            adjustedPosition == sectionCount -> TYPE_GRID_HEADER_PRIVATE
            else -> TYPE_GRID_ITEM_PRIVATE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER_PRIVATE -> HeaderViewHolder(headerView!!)
            TYPE_SECTION_PRIVATE -> SectionViewHolder(
                ItemMainSectionBinding.inflate(inflater, parent, false)
            )
            TYPE_GRID_HEADER_PRIVATE -> GridHeaderViewHolder(
                ItemSectionHeaderBinding.inflate(inflater, parent, false)
            )
            else -> GridItemViewHolder(
                ItemMovieGridBinding.inflate(inflater, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is HeaderViewHolder) return

        val offset = if (headerView != null) 1 else 0
        val adjustedPosition = position - offset
        val sectionCount = sections.size

        when (holder) {
            is SectionViewHolder -> holder.bind(sections[adjustedPosition])
            is GridHeaderViewHolder -> holder.bind(R.string.section_all_movies, currentSortText)
            is GridItemViewHolder -> {
                val gridIndex = adjustedPosition - sectionCount - 1
                holder.bind(gridMovies[gridIndex])
            }
        }
    }

    override fun getItemCount(): Int {
        val offset = if (headerView != null) 1 else 0
        return offset + sections.size + 1 + gridMovies.size
    }

    // ==================== ViewHolders ====================

    inner class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    inner class SectionViewHolder(private val binding: ItemMainSectionBinding) : RecyclerView.ViewHolder(binding.root) {
        private val horizontalAdapter = HorizontalMovieAdapter()

        init {
            binding.recyclerViewHorizontal.apply {
                layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
                adapter = horizontalAdapter
            }
            horizontalAdapter.setOnMovieClickListener(object : HorizontalMovieAdapter.OnMovieClickListener {
                override fun onMovieClick(movie: Movie) {
                    listener?.onMovieClick(movie)
                }
            })
        }

        fun bind(section: SectionData) {
            binding.textSectionTitle.text = section.title
            horizontalAdapter.submitList(section.movies)
        }
    }

    inner class GridHeaderViewHolder(private val binding: ItemSectionHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.textViewMore.visibility = View.GONE
        }

        fun bind(titleRes: Int, sortText: String?) {
            binding.textSectionTitle.setText(titleRes)
            if (sortText != null) {
                binding.textSort.visibility = View.VISIBLE
                binding.textSort.text = sortText
                binding.textSort.setOnClickListener {
                    sortClickListener?.invoke()
                }
            } else {
                binding.textSort.visibility = View.GONE
            }
        }
    }

    inner class GridItemViewHolder(private val binding: ItemMovieGridBinding) : RecyclerView.ViewHolder(binding.root) {
        private var currentMovie: Movie? = null

        init {
            binding.root.setOnClickListener {
                currentMovie?.let { listener?.onMovieClick(it) }
            }
        }

        fun bind(movie: Movie) {
            currentMovie = movie
            binding.movieCard.setMode(MovieCard.CardMode.GRID)
            binding.movieCard.loadMovie(movie, movie.progress)
        }
    }

    // ==================== 横向电影适配器 ====================

    class HorizontalMovieAdapter : ListAdapter<Movie, HorizontalMovieAdapter.ViewHolder>(MovieDiffCallback()) {
        private var listener: OnMovieClickListener? = null

        interface OnMovieClickListener {
            fun onMovieClick(movie: Movie)
        }

        fun setOnMovieClickListener(listener: OnMovieClickListener) {
            this.listener = listener
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemMovieHorizontalScrollBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )

            // 根据屏幕大小计算卡片尺寸
            val metrics = parent.context.resources.displayMetrics
            val screenWidth = metrics.widthPixels
            val maxWidth = (600 * metrics.density).toInt()
            val density = metrics.density

            val cardWidth = if (screenWidth > maxWidth) {
                (240 * density).toInt()
            } else {
                (120 * density).toInt()
            }
            val cardHeight = (cardWidth * 1.5f).toInt()

            binding.movieCard.layoutParams = binding.movieCard.layoutParams.apply {
                width = cardWidth
                height = cardHeight
            }

            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(getItem(position))
        }

        inner class ViewHolder(private val binding: ItemMovieHorizontalScrollBinding) : RecyclerView.ViewHolder(binding.root) {
            private var currentMovie: Movie? = null

            init {
                binding.root.setOnClickListener {
                    currentMovie?.let { listener?.onMovieClick(it) }
                }
            }

            fun bind(movie: Movie) {
                currentMovie = movie
                binding.movieCard.setMode(MovieCard.CardMode.HORIZONTAL)
                binding.movieCard.loadMovie(movie, movie.progress)
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
}
