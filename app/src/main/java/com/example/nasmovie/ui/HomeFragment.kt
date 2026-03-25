package com.example.nasmovie.ui

import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.viewpager2.widget.ViewPager2
import com.example.nasmovie.R
import com.example.nasmovie.data.model.Movie
import com.example.nasmovie.data.repository.MovieRepository
import com.example.nasmovie.databinding.DialogSortBinding
import com.example.nasmovie.databinding.FragmentHomeBinding
import com.example.nasmovie.ui.adapter.FeaturedMovieAdapter
import com.example.nasmovie.ui.adapter.MainContentAdapter
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.lang.ref.WeakReference

/**
 * 首页 Fragment
 */
class HomeFragment : Fragment(),
    FeaturedMovieAdapter.OnFeaturedClickListener,
    MainContentAdapter.OnMovieClickListener {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var featuredAdapter: FeaturedMovieAdapter
    private lateinit var mainContentAdapter: MainContentAdapter

    private lateinit var viewModel: HomeViewModel

    // 自动轮播相关
    private val autoSlideInterval = 4000L // 4秒轮播一次
    private val autoSlideHandler = Handler(Looper.getMainLooper())
    private var autoSlideRunnable: AutoSlideRunnable? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun startAutoSlide() {
        stopAutoSlide()
        autoSlideRunnable = AutoSlideRunnable(this)
        autoSlideHandler.postDelayed(autoSlideRunnable!!, autoSlideInterval)
    }

    private fun stopAutoSlide() {
        autoSlideRunnable?.let {
            autoSlideHandler.removeCallbacks(it)
            autoSlideRunnable = null
        }
    }

    /**
     * 静态内部类 Runnable，避免持有外部类引用导致内存泄漏
     */
    private class AutoSlideRunnable(fragment: HomeFragment) : Runnable {
        private val fragmentRef = WeakReference(fragment)

        override fun run() {
            val fragment = fragmentRef.get() ?: return
            if (!fragment.isAdded || fragment._binding == null) return

            val binding = fragment.binding
            if (binding.viewPagerFeatured != null && fragment.featuredAdapter.itemCount > 0) {
                val currentItem = binding.viewPagerFeatured.currentItem
                binding.viewPagerFeatured.setCurrentItem(currentItem + 1, true)
                fragment.autoSlideHandler.postDelayed(this, fragment.autoSlideInterval)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel = ViewModelProvider(this)[HomeViewModel::class.java]
        initViews(view)
        observeViewModel()
    }

    private fun initViews(view: View) {
        binding.toolbar?.let { toolbar ->
            toolbar.setTitle(R.string.app_name)
            toolbar.setShowBack(false)
            (activity as? AppCompatActivity)?.let { activity ->
                activity.setSupportActionBar(toolbar.toolbar)
                activity.supportActionBar?.setDisplayShowTitleEnabled(false)
            }
        }

        featuredAdapter = FeaturedMovieAdapter()
        featuredAdapter.setOnFeaturedClickListener(this)
        binding.viewPagerFeatured.adapter = featuredAdapter
        binding.viewPagerFeatured.offscreenPageLimit = 3

        // 处理触摸事件，用户触摸时停止轮播
        binding.viewPagerFeatured.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrollStateChanged(state: Int) {
                when (state) {
                    ViewPager2.SCROLL_STATE_DRAGGING -> stopAutoSlide()
                    ViewPager2.SCROLL_STATE_IDLE -> startAutoSlide()
                }
            }
        })

        binding.viewPagerFeatured.setPageTransformer { page, position ->
            if (position == 0f) {
                page.scaleX = 1f
                page.scaleY = 1f
                page.alpha = 1f
                return@setPageTransformer
            }

            val absPosition = kotlin.math.abs(position)
            if (absPosition <= 1f) {
                val scale = 1.0f - 0.05f * absPosition
                page.scaleX = scale
                page.scaleY = scale
                page.alpha = 1.0f - 0.2f * absPosition
            } else {
                page.scaleX = 0.95f
                page.scaleY = 0.95f
                page.alpha = 0.3f
            }
        }

        mainContentAdapter = MainContentAdapter()
        mainContentAdapter.setOnMovieClickListener(this)
        mainContentAdapter.setRepository(MovieRepository(requireContext()))

        // 根据屏幕宽度计算列数，平板设备卡片宽度 240dp
        val metrics = resources.displayMetrics
        val screenWidth = metrics.widthPixels
        val maxWidth = (600 * metrics.density).toInt()
        val desiredCardWidth = if (screenWidth > maxWidth) {
            // 平板设备：卡片宽度 240dp
            (240 * metrics.density).toInt()
        } else {
            // 手机设备：卡片宽度约 120dp
            (120 * metrics.density).toInt()
        }
        val spanCount = maxOf(2, screenWidth / desiredCardWidth)

        val gridLayoutManager = GridLayoutManager(context, spanCount)
        gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                val viewType = mainContentAdapter.getItemViewType(position)
                return if (viewType == MainContentAdapter.TYPE_HEADER ||
                    viewType == MainContentAdapter.TYPE_SECTION ||
                    viewType == MainContentAdapter.TYPE_GRID_HEADER) {
                    spanCount
                } else {
                    1
                }
            }
        }
        binding.recyclerViewMain.layoutManager = gridLayoutManager
        binding.recyclerViewMain.adapter = mainContentAdapter
        binding.recyclerViewMain.setHasFixedSize(true)

        binding.btnScan.setOnClickListener {
            // 跳转到服务器管理页面
            (activity as? MainActivity)?.openServerManage()
        }

        binding.cardSearch.setOnClickListener {
            (activity as? MainActivity)?.openSearch()
        }

        // 下拉刷新
        binding.swipeRefresh.setColorSchemeResources(
            R.color.colorPrimary,
            R.color.colorAccent
        )
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadMovies()
        }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading) {
                binding.progressBar.visibility = View.VISIBLE
                binding.emptyView.visibility = View.GONE
            } else {
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
            }
        }

        viewModel.allMovies.observe(viewLifecycleOwner) { allMovies ->
            if (allMovies.isNullOrEmpty()) {
                showEmptyView()
            } else {
                showContent()
                updateSortText()
            }
        }

        viewModel.featuredMovies.observe(viewLifecycleOwner) { featuredMovies ->
            if (featuredMovies != null) {
                featuredAdapter.setMovies(featuredMovies)
                if (featuredMovies.isNotEmpty()) {
                    val startPosition = (1000 / featuredMovies.size) * featuredMovies.size
                    binding.viewPagerFeatured.setCurrentItem(startPosition, false)
                    binding.viewPagerFeatured.visibility = View.VISIBLE
                    updateViewPagerSize()
                    setupHeaderView()
                } else {
                    mainContentAdapter.headerView = null
                }
            }
        }

        viewModel.recentMovies.observe(viewLifecycleOwner) {
            updateSections()
        }

        viewModel.highRatedMovies.observe(viewLifecycleOwner) {
            updateSections()
        }

        viewModel.newestMovies.observe(viewLifecycleOwner) {
            updateSections()
        }
    }

    private fun setupHeaderView() {
        if (mainContentAdapter.headerView == null) {
            val headerContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val searchParent = binding.cardSearch.parent as? ViewGroup
            searchParent?.removeView(binding.cardSearch)
            binding.cardSearch.visibility = View.VISIBLE
            val searchParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (48 * resources.displayMetrics.density).toInt()
            ).apply {
                val margin16 = (16 * resources.displayMetrics.density).toInt()
                val margin8 = (8 * resources.displayMetrics.density).toInt()
                setMargins(margin16, margin8, margin16, margin8)
            }
            binding.cardSearch.layoutParams = searchParams
            headerContainer.addView(binding.cardSearch)

            val pagerParent = binding.viewPagerFeatured.parent as? ViewGroup
            pagerParent?.removeView(binding.viewPagerFeatured)
            binding.viewPagerFeatured.visibility = View.VISIBLE
            val pagerParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (180 * resources.displayMetrics.density).toInt()
            ).apply {
                val margin8 = (8 * resources.displayMetrics.density).toInt()
                setMargins(0, margin8, 0, (16 * resources.displayMetrics.density).toInt())
            }
            binding.viewPagerFeatured.layoutParams = pagerParams
            headerContainer.addView(binding.viewPagerFeatured)

            mainContentAdapter.headerView = headerContainer
        }
    }

    private fun updateSections() {
        val recentMovies = viewModel.recentMovies.value
        val highRatedMovies = viewModel.highRatedMovies.value
        val newestMovies = viewModel.newestMovies.value
        val allMovies = viewModel.allMovies.value

        if (allMovies == null) return

        val sections = mutableListOf<MainContentAdapter.SectionData>()

        if (!recentMovies.isNullOrEmpty()) {
            sections.add(MainContentAdapter.SectionData(getString(R.string.section_recent), recentMovies))
        }
        if (!highRatedMovies.isNullOrEmpty()) {
            sections.add(MainContentAdapter.SectionData(getString(R.string.section_high_rated), highRatedMovies))
        }
        if (!newestMovies.isNullOrEmpty()) {
            sections.add(MainContentAdapter.SectionData(getString(R.string.section_newest), newestMovies))
        }

        mainContentAdapter.setData(sections, allMovies)
        mainContentAdapter.setOnSortClickListener { showSortDialog() }
    }

    private fun showEmptyView() {
        binding.emptyView.visibility = View.VISIBLE
        binding.recyclerViewMain.visibility = View.GONE
    }

    private fun showContent() {
        binding.emptyView.visibility = View.GONE
        binding.recyclerViewMain.visibility = View.VISIBLE
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            viewModel.loadMovies()
        }
    }

    override fun onResume() {
        super.onResume()
        if (viewModel.allMovies.value.isNullOrEmpty()) {
            viewModel.loadMovies()
        }
        startAutoSlide()
        updateViewPagerSize()
    }

    override fun onPause() {
        super.onPause()
        stopAutoSlide()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateViewPagerSize()
        updateGridSpanCount()
    }

    private fun updateViewPagerSize() {
        if (_binding == null || binding.viewPagerFeatured == null || !isAdded) return

        val screenWidth = resources.displayMetrics.widthPixels
        val maxWidth = (600 * resources.displayMetrics.density).toInt()
        val density = resources.displayMetrics.density

        if (screenWidth > maxWidth) {
            val pagerHeight = (maxWidth * 9f / 16f).toInt()
            val pagerParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                pagerHeight
            ).apply {
                setMargins(0, (8 * density).toInt(), 0, (16 * density).toInt())
            }
            binding.viewPagerFeatured.layoutParams = pagerParams

            val padding = (screenWidth - maxWidth) / 2
            binding.viewPagerFeatured.setPadding(padding, 0, padding, 0)
        } else {
            binding.viewPagerFeatured.setPadding((32 * density).toInt(), 0, (32 * density).toInt(), 0)
        }

        binding.viewPagerFeatured.post {
            if (isAdded && _binding != null && binding.viewPagerFeatured != null) {
                binding.viewPagerFeatured.requestLayout()
                binding.viewPagerFeatured.invalidate()
            }
        }
    }

    private fun updateGridSpanCount() {
        if (_binding == null || binding.recyclerViewMain == null || !::mainContentAdapter.isInitialized || !isAdded) return

        val metrics = resources.displayMetrics
        val screenWidth = metrics.widthPixels
        val maxWidth = (600 * metrics.density).toInt()
        val desiredCardWidth = if (screenWidth > maxWidth) {
            (240 * metrics.density).toInt()
        } else {
            (120 * metrics.density).toInt()
        }
        val spanCount = maxOf(2, screenWidth / desiredCardWidth)

        val layoutManager = binding.recyclerViewMain.layoutManager as? GridLayoutManager
        if (layoutManager != null && layoutManager.spanCount != spanCount) {
            // 重新创建 LayoutManager 并设置 spanSizeLookup
            val newLayoutManager = GridLayoutManager(context, spanCount)
            newLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    val viewType = mainContentAdapter.getItemViewType(position)
                    return if (viewType == MainContentAdapter.TYPE_HEADER ||
                        viewType == MainContentAdapter.TYPE_SECTION ||
                        viewType == MainContentAdapter.TYPE_GRID_HEADER) {
                        spanCount
                    } else {
                        1
                    }
                }
            }
            binding.recyclerViewMain.layoutManager = newLayoutManager
        }
    }

    override fun onFeaturedClick(movie: Movie) {
        openMovieDetail(movie)
    }

    override fun onMovieClick(movie: Movie) {
        openMovieDetail(movie)
    }

    private fun openMovieDetail(movie: Movie) {
        (activity as? MainActivity)?.openDetail(movie.id)
    }

    // ==================== 排序相关 ====================

    private fun showSortDialog() {
        val dialog = BottomSheetDialog(requireContext())
        val sortBinding = DialogSortBinding.inflate(LayoutInflater.from(requireContext()))

        sortBinding.sortTitleAsc.setOnClickListener {
            changeSort(MovieRepository.SortType.TITLE_ASC)
            dialog.dismiss()
        }
        sortBinding.sortAddTime.setOnClickListener {
            changeSort(MovieRepository.SortType.ADD_TIME_DESC)
            dialog.dismiss()
        }
        sortBinding.sortYear.setOnClickListener {
            changeSort(MovieRepository.SortType.YEAR_DESC)
            dialog.dismiss()
        }
        sortBinding.sortRating.setOnClickListener {
            changeSort(MovieRepository.SortType.RATING_DESC)
            dialog.dismiss()
        }
        sortBinding.sortDuration.setOnClickListener {
            changeSort(MovieRepository.SortType.DURATION_DESC)
            dialog.dismiss()
        }
        sortBinding.sortFileSize.setOnClickListener {
            changeSort(MovieRepository.SortType.FILE_SIZE_DESC)
            dialog.dismiss()
        }

        dialog.setContentView(sortBinding.root)
        dialog.show()
    }

    private fun changeSort(sortType: MovieRepository.SortType) {
        viewModel.changeSort(sortType)
    }

    private fun updateSortText() {
        val sortText = when (viewModel.currentSortType) {
            MovieRepository.SortType.TITLE_ASC -> getString(R.string.sort_title)
            MovieRepository.SortType.YEAR_DESC -> getString(R.string.sort_year)
            MovieRepository.SortType.RATING_DESC -> getString(R.string.sort_rating)
            MovieRepository.SortType.DURATION_DESC -> getString(R.string.sort_duration)
            MovieRepository.SortType.FILE_SIZE_DESC -> getString(R.string.sort_file_size)
            MovieRepository.SortType.ADD_TIME_DESC -> getString(R.string.sort_add_time)
        }
        mainContentAdapter.setSortText(sortText)
    }
}