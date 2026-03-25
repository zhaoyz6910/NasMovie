package com.example.nasmovie.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.nasmovie.R
import com.example.nasmovie.data.model.Movie
import com.example.nasmovie.util.SmbImageLoader
import java.util.Locale

/**
 * 特色电影轮播适配器
 */
class FeaturedMovieAdapter : RecyclerView.Adapter<FeaturedMovieAdapter.ViewHolder>() {

    private var movies: List<Movie> = emptyList()
    private var listener: OnFeaturedClickListener? = null

    // ViewPager 虚拟数量，用于实现无限轮播效果
    // 设为 10000 是一个足够大的数，用户几乎不可能滑到边界
    private val virtualCount = 10000

    interface OnFeaturedClickListener {
        fun onFeaturedClick(movie: Movie)
    }

    fun setOnFeaturedClickListener(listener: OnFeaturedClickListener?) {
        this.listener = listener
    }

    fun setMovies(movies: List<Movie>?) {
        this.movies = movies ?: emptyList()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_featured_movie, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        // 使用取模运算实现循环
        val actualPosition = if (movies.isEmpty()) 0 else position % movies.size
        val movie = movies[actualPosition]
        holder.bind(movie, actualPosition)
    }

    override fun getItemCount(): Int {
        // 返回虚拟数量来实现无限循环效果
        return if (movies.isEmpty()) 0 else virtualCount
    }

    /**
     * 获取实际的电影数量
     */
    fun getActualItemCount(): Int = movies.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imagePoster: ImageView = itemView.findViewById(R.id.image_poster)
        private val textRating: TextView = itemView.findViewById(R.id.text_rating)
        private val textTitle: TextView = itemView.findViewById(R.id.text_title)
        private val textYearGenres: TextView = itemView.findViewById(R.id.text_year_genres)
        private var currentMovie: Movie? = null

        init {
            itemView.setOnClickListener {
                currentMovie?.let { movie ->
                    listener?.onFeaturedClick(movie)
                }
            }
        }

        fun bind(movie: Movie, actualPosition: Int) {
            this.currentMovie = movie
            // 加载详情海报（使用localThumbPath）
            SmbImageLoader.loadDetailPoster(itemView.context, movie, imagePoster)

            // 设置评分
            if (movie.rating > 0) {
                textRating.text = String.format(Locale.US, "%.1f", movie.rating)
                textRating.visibility = View.VISIBLE
            } else {
                textRating.visibility = View.GONE
            }

            // 设置标题
            textTitle.text = movie.title

            // 设置年份和类型
            val info = StringBuilder()
            if (movie.year > 0) {
                info.append(movie.year)
            }
            val genres = movie.genreList
            if (genres.isNotEmpty()) {
                if (info.isNotEmpty()) info.append(" • ")
                info.append(genres.subList(0, minOf(2, genres.size)).joinToString(" / "))
            }
            textYearGenres.text = info.toString()
        }
    }
}