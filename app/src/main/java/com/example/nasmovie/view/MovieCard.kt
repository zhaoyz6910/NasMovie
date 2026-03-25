package com.example.nasmovie.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.example.nasmovie.R
import com.example.nasmovie.data.model.Movie
import com.example.nasmovie.util.SmbImageLoader
import java.util.Locale

/**
 * 通用电影卡片组件
 * 支持多种显示模式：网格、列表、横向、收藏、搜索
 */
class MovieCard @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    // 卡片模式
    enum class CardMode {
        GRID,       // 网格模式（带评分徽章、年份时长、进度条）
        LIST,       // 列表模式（简洁）
        HORIZONTAL, // 横向滚动（带选中状态）
        FAVORITE,   // 收藏模式
        SEARCH      // 搜索结果（完整信息）
    }

    private val ivPoster: ImageView
    private val tvTitle: TextView
    private val tvRating: TextView
    private val tvYear: TextView
    private val tvDuration: TextView
    private val progressWatch: ProgressBar
    private val ratingBadge: View?
    private val selectionOverlay: View?
    private val divider: View?
    private val infoContainer: LinearLayout?

    private var currentMode = CardMode.GRID

    init {
        LayoutInflater.from(context).inflate(R.layout.view_movie_card, this, true)

        ivPoster = findViewById(R.id.iv_poster)
        tvTitle = findViewById(R.id.tv_title)
        tvRating = findViewById(R.id.tv_rating)
        tvYear = findViewById(R.id.tv_year)
        tvDuration = findViewById(R.id.tv_duration)
        progressWatch = findViewById(R.id.progress_watch)
        ratingBadge = findViewById(R.id.rating_badge)
        selectionOverlay = findViewById(R.id.selection_overlay)
        divider = findViewById(R.id.divider)
        infoContainer = findViewById(R.id.info_container)
    }

    /**
     * 加载电影数据（不带进度）
     */
    fun loadMovie(movie: Movie?) {
        loadMovie(movie, -1)
    }

    /**
     * 加载电影数据（带进度百分比，0-100，-1 表示不显示进度）
     */
    fun loadMovie(movie: Movie?, progress: Int) {
        if (movie == null) return

        // 使用 SmbImageLoader 加载海报
        SmbImageLoader.loadPoster(context, movie, ivPoster)

        // 设置标题
        tvTitle.text = movie.title

        // 处理评分 (使用 movie.rating 字段)
        val hasRating = movie.rating > 0
        if (hasRating) {
            tvRating.text = String.format(Locale.US, "%.1f", movie.rating)
            ratingBadge?.visibility = View.VISIBLE
        } else {
            ratingBadge?.visibility = View.GONE
        }

        // 设置年份
        if (movie.year > 0) {
            tvYear.text = movie.year.toString()
            tvYear.visibility = View.VISIBLE
        } else {
            tvYear.visibility = View.GONE
        }

        // 设置时长
        if (movie.duration > 0) {
            tvDuration.text = getDurationText(movie.duration)
            tvDuration.visibility = View.VISIBLE
            divider?.visibility = View.VISIBLE
        } else {
            tvDuration.visibility = View.GONE
            divider?.visibility = View.GONE
        }

        // 应用布局模式
        applyMode(hasRating)

        // 设置观看进度
        if (progress in 0..99) {
            progressWatch.progress = progress
            progressWatch.visibility = View.VISIBLE
        } else {
            progressWatch.visibility = View.GONE
        }
    }

    /**
     * 设置观看进度
     */
    fun setWatchProgress(progress: Int) {
        if (progress in 0..99) {
            progressWatch.progress = progress
            progressWatch.visibility = View.VISIBLE
        } else {
            progressWatch.visibility = View.GONE
        }
    }

    /**
     * 设置卡片模式
     */
    fun setMode(mode: CardMode) {
        this.currentMode = mode
        // 注意：这里由于没有实时 movie 数据，我们保守地尝试刷新
        // 实际上大部分情况是通过 loadMovie 触发的
    }

    private fun applyMode(hasRating: Boolean) {
        when (currentMode) {
            CardMode.GRID -> {
                // 网格模式：显示评分徽章、年份、时长
                ratingBadge?.visibility = if (hasRating) View.VISIBLE else View.GONE
                tvYear?.visibility = if (tvYear.text.isNullOrEmpty()) View.GONE else View.VISIBLE
                tvDuration?.visibility = if (tvDuration.text.isNullOrEmpty()) View.GONE else View.VISIBLE
                divider?.visibility = View.VISIBLE
                selectionOverlay?.visibility = View.GONE
            }

            CardMode.LIST -> {
                // 列表模式：简洁显示
                ratingBadge?.visibility = View.GONE
                tvDuration?.visibility = View.GONE
                divider?.visibility = View.GONE
                selectionOverlay?.visibility = View.GONE
            }

            CardMode.HORIZONTAL -> {
                // 横向模式：显示评分徽章、标题
                ratingBadge?.visibility = if (hasRating) View.VISIBLE else View.GONE
                tvYear?.visibility = View.GONE
                tvDuration?.visibility = View.GONE
                divider?.visibility = View.GONE
                selectionOverlay?.visibility = View.GONE
            }

            CardMode.FAVORITE -> {
                // 收藏模式：显示评分徽章、标题、选中状态
                ratingBadge?.visibility = if (hasRating) View.VISIBLE else View.GONE
                tvYear?.visibility = View.GONE
                tvDuration?.visibility = View.GONE
                divider?.visibility = View.GONE
                selectionOverlay?.visibility = View.GONE
            }

            CardMode.SEARCH -> {
                // 搜索模式：完整信息
                ratingBadge?.visibility = if (hasRating) View.VISIBLE else View.GONE
                tvYear?.visibility = if (tvYear.text.isNullOrEmpty()) View.GONE else View.VISIBLE
                tvDuration?.visibility = if (tvDuration.text.isNullOrEmpty()) View.GONE else View.VISIBLE
                divider?.visibility = View.VISIBLE
                selectionOverlay?.visibility = View.GONE
            }
        }
    }

    /**
     * 设置选中状态（用于横向滚动多选）
     */
    override fun setSelected(selected: Boolean) {
        selectionOverlay?.visibility = if (selected) View.VISIBLE else View.GONE
    }

    /**
     * 获取标题 TextView（可用于自定义）
     */
    fun getTitleTextView(): TextView = tvTitle

    /**
     * 获取海报 ImageView（可用于自定义）
     */
    fun getPosterImageView(): ImageView = ivPoster

    /**
     * 格式化时长
     */
    private fun getDurationText(minutes: Int): String {
        return "$minutes 分钟"
    }
}