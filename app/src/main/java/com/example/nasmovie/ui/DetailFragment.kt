package com.example.nasmovie.ui

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.example.nasmovie.R
import com.example.nasmovie.data.model.Movie
import com.example.nasmovie.data.model.WatchProgress
import com.example.nasmovie.databinding.FragmentDetailBinding
import com.example.nasmovie.util.FileUtils
import com.example.nasmovie.util.StringUtils
import com.example.nasmovie.util.SmbImageLoader
import java.io.File
import java.util.Locale

/**
 * 电影详情 Fragment
 */
class DetailFragment : Fragment() {

    companion object {
        const val ARG_MOVIE_ID = "movie_id"

        fun newInstance(movieId: String): DetailFragment {
            return DetailFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_MOVIE_ID, movieId)
                }
            }
        }
    }

    private var _binding: FragmentDetailBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: DetailViewModel
    private var movieId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        arguments?.let {
            movieId = it.getString(ARG_MOVIE_ID)
        }

        viewModel = ViewModelProvider(this)[DetailViewModel::class.java]
        initViews()
        observeViewModel()

        movieId?.let {
            viewModel.init(it)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(ARG_MOVIE_ID, movieId)
    }

    private fun initViews() {
        // 设置 Toolbar
        val activity = activity as? AppCompatActivity
        activity?.let {
            it.setSupportActionBar(binding.toolbar.toolbar)
            it.supportActionBar?.setDisplayShowTitleEnabled(false)
        }

        // 设置标题和返回按钮
        binding.toolbar.setTitle("详情")
        binding.toolbar.setShowBack(true)
        binding.toolbar.setOnBackClickListener {
            (activity as? MainActivity)?.performRealBack()
        }

        binding.btnPlay.setOnClickListener { playMovie() }
        binding.btnFavorite.setOnClickListener { viewModel.toggleFavorite() }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading) {
                binding.progressLoading.visibility = View.VISIBLE
                binding.contentContainer.visibility = View.GONE
            } else {
                binding.progressLoading.visibility = View.GONE

                // 只有在第一次加载完成且有数据时才执行动画
                if (binding.contentContainer.visibility == View.GONE && viewModel.movie.value != null) {
                    binding.contentContainer.alpha = 0f
                    binding.contentContainer.visibility = View.VISIBLE
                    binding.contentContainer.animate()
                        .alpha(1f)
                        .setDuration(200)
                        .start()
                }
            }
        }

        viewModel.movie.observe(viewLifecycleOwner) { movie ->
            if (movie != null) {
                displayMovie(movie)
            } else if (viewModel.isLoading.value == false) {
                // 加载完成但电影为空，说明获取失败，返回上一页
                (activity as? MainActivity)?.performRealBack()
            }
        }

        viewModel.isFavorite.observe(viewLifecycleOwner) { isFav ->
            updateFavoriteButton(isFav)
        }

        viewModel.watchProgress.observe(viewLifecycleOwner) { progress ->
            displayProgress(progress)
        }
    }

    private fun displayMovie(movie: Movie) {
        binding.tvTitle.text = movie.title

        if (StringUtils.isNotEmpty(movie.originalTitle)) {
            binding.tvOriginalTitle.visibility = View.VISIBLE
            binding.tvOriginalTitle.text = movie.originalTitle
        } else {
            binding.tvOriginalTitle.visibility = View.GONE
        }

        if (movie.year > 0) {
            binding.tvYear.text = movie.year.toString()
        } else {
            binding.tvYear.text = "未知年份"
        }

        if (movie.duration > 0) {
            binding.tvDuration.text = FileUtils.formatDurationMinutes(movie.duration)
        } else {
            binding.tvDuration.text = "未知时长"
        }

        if (movie.rating > 0) {
            binding.tvRating.text = StringUtils.formatRating(movie.rating)
        } else {
            binding.tvRating.text = "暂无评分"
        }

        if (StringUtils.isNotEmpty(movie.director)) {
            binding.directorContainer?.visibility = View.VISIBLE
            binding.tvDirector.text = movie.director
        } else {
            binding.directorContainer?.visibility = View.GONE
        }

        val actors = movie.actorList
        if (actors.isNotEmpty()) {
            binding.actorsContainer?.visibility = View.VISIBLE
            binding.tvActors.text = StringUtils.join(actors, ", ")
        } else {
            binding.actorsContainer?.visibility = View.GONE
        }

        if (StringUtils.isNotEmpty(movie.plot)) {
            binding.tvPlot.text = movie.plot
        } else {
            binding.tvPlot.setText(R.string.movie_no_plot)
        }

        // 加载海报
        val localThumb = movie.localThumbPath
        val localPoster = movie.localPosterPath
        val thumbExists = !localThumb.isNullOrEmpty() && File(localThumb).exists()
        val posterExists = !localPoster.isNullOrEmpty() && File(localPoster).exists()

        // 横屏模式使用 poster，竖屏模式使用 thumb/detailPoster
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        if (isLandscape) {
            // 横屏：优先使用 poster
            when {
                posterExists -> {
                    Glide.with(requireContext())
                        .load(File(localPoster))
                        .placeholder(R.drawable.bg_poster_placeholder)
                        .error(R.drawable.bg_poster_placeholder)
                        .transition(DrawableTransitionOptions.withCrossFade(300))
                        .into(binding.ivPoster)
                }
                !movie.posterPath.isNullOrEmpty() -> {
                    SmbImageLoader.loadPoster(requireContext(), movie, binding.ivPoster)
                }
                thumbExists -> {
                    Glide.with(requireContext())
                        .load(File(localThumb))
                        .placeholder(R.drawable.bg_poster_placeholder)
                        .error(R.drawable.bg_poster_placeholder)
                        .transition(DrawableTransitionOptions.withCrossFade(300))
                        .into(binding.ivPoster)
                }
                !movie.thumbPath.isNullOrEmpty() -> {
                    SmbImageLoader.loadDetailPoster(requireContext(), movie, binding.ivPoster)
                }
                else -> {
                    Glide.with(requireContext())
                        .load(R.drawable.bg_poster_placeholder)
                        .transition(DrawableTransitionOptions.withCrossFade(300))
                        .into(binding.ivPoster)
                }
            }
        } else {
            // 竖屏：优先使用 thumb/detailPoster
            when {
                thumbExists -> {
                    Glide.with(requireContext())
                        .load(File(localThumb))
                        .placeholder(R.drawable.bg_poster_placeholder)
                        .error(R.drawable.bg_poster_placeholder)
                        .transition(DrawableTransitionOptions.withCrossFade(300))
                        .into(binding.ivPoster)
                }
                !movie.thumbPath.isNullOrEmpty() -> {
                    SmbImageLoader.loadDetailPoster(requireContext(), movie, binding.ivPoster)
                }
                posterExists -> {
                    Glide.with(requireContext())
                        .load(File(localPoster))
                        .placeholder(R.drawable.bg_poster_placeholder)
                        .error(R.drawable.bg_poster_placeholder)
                        .transition(DrawableTransitionOptions.withCrossFade(300))
                        .into(binding.ivPoster)
                }
                !movie.posterPath.isNullOrEmpty() -> {
                    SmbImageLoader.loadPoster(requireContext(), movie, binding.ivPoster)
                }
                else -> {
                    Glide.with(requireContext())
                        .load(R.drawable.bg_poster_placeholder)
                        .transition(DrawableTransitionOptions.withCrossFade(300))
                        .into(binding.ivPoster)
                }
            }
        }
    }

    private fun displayProgress(progress: WatchProgress?) {
        if (progress != null && progress.percentage > 0 && !progress.isCompleted) {
            binding.cardProgress.visibility = View.VISIBLE
            binding.tvProgress.text = String.format(Locale.US, "已观看 %d%%", progress.percentage)
            binding.progressBar.progress = progress.percentage
            binding.btnPlay.setText(R.string.resume_play)
        } else {
            binding.cardProgress.visibility = View.GONE
            binding.btnPlay.setText(R.string.play)
        }
    }

    private fun updateFavoriteButton(isFav: Boolean?) {
        val isFavSafe = isFav ?: false
        binding.btnFavorite?.let { btn ->
            btn.setIconResource(R.drawable.ic_favorite)
            if (isFavSafe) {
                btn.setIconTintResource(R.color.iosBlue)
            } else {
                btn.setIconTintResource(R.color.iosGray)
            }
        }
    }

    private fun playMovie() {
        val currentMovie = viewModel.movie.value ?: return
        val intent = Intent(context, ExoPlayerActivity::class.java).apply {
            putExtra(ExoPlayerActivity.EXTRA_MOVIE_ID, currentMovie.id)
        }
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshProgress()
    }
}