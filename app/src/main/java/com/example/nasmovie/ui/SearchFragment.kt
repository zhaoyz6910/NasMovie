package com.example.nasmovie.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.example.nasmovie.R
import com.example.nasmovie.data.model.Movie
import com.example.nasmovie.databinding.FragmentSearchBinding
import com.example.nasmovie.ui.adapter.SearchResultAdapter
import com.google.android.material.chip.Chip

/**
 * 搜索 Fragment
 */
class SearchFragment : Fragment(), SearchResultAdapter.OnMovieClickListener {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private lateinit var resultAdapter: SearchResultAdapter
    private lateinit var viewModel: SearchViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel = ViewModelProvider(this)[SearchViewModel::class.java]
        initViews(view)
        observeViewModel()
    }

    private fun initViews(view: View) {
        // 设置 Toolbar
        val activity = activity as? AppCompatActivity
        activity?.let {
            it.setSupportActionBar(binding.toolbar.toolbar)
            it.supportActionBar?.setDisplayShowTitleEnabled(false)
        }

        // 设置标题和返回按钮
        binding.toolbar.setTitle("搜索")
        binding.toolbar.setShowBack(true)
        binding.toolbar.setOnBackClickListener {
            (activity as? MainActivity)?.performRealBack()
        }

        // 搜索结果网格 - 3 列
        resultAdapter = SearchResultAdapter()
        resultAdapter.setOnMovieClickListener(this)
        val gridLayoutManager = GridLayoutManager(context, 3)
        binding.recyclerViewResults.layoutManager = gridLayoutManager
        binding.recyclerViewResults.adapter = resultAdapter

        // 搜索输入监听
        binding.editSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.btnClear.visibility = if (s?.isNotEmpty() == true) View.VISIBLE else View.GONE
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // 搜索按钮监听
        binding.editSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                viewModel.performSearch(binding.editSearch.text.toString().trim())
                true
            } else {
                false
            }
        }

        // 清除按钮
        binding.btnClear.setOnClickListener {
            binding.editSearch.setText("")
            viewModel.clearSearchResults()
        }

        // 清空历史按钮
        view.findViewById<View>(R.id.btn_clear_history).setOnClickListener {
            viewModel.clearHistory()
        }

        // 下拉刷新
        binding.swipeRefreshLayout.setOnRefreshListener {
            val query = binding.editSearch.text.toString().trim()
            if (query.isNotEmpty()) {
                viewModel.performSearch(query)
            } else {
                binding.swipeRefreshLayout.isRefreshing = false
            }
        }
        binding.swipeRefreshLayout.setColorSchemeResources(R.color.colorPrimary)
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading) {
                binding.progressBar.visibility = View.VISIBLE
                binding.searchHistoryContainer.visibility = View.GONE
                binding.swipeRefreshLayout.isRefreshing = false
            } else {
                binding.progressBar.visibility = View.GONE
            }
        }

        viewModel.searchHistory.observe(viewLifecycleOwner) { history ->
            setupSearchHistory(history)
        }

        viewModel.searchResults.observe(viewLifecycleOwner) { results ->
            if (results == null) {
                showSearchHistory()
            } else {
                resultAdapter.setMovies(results)
                if (results.isEmpty()) {
                    showEmptyState(binding.editSearch.text.toString().trim())
                } else {
                    showResults()
                }
            }
        }
    }

    private fun setupSearchHistory(searchHistory: List<String>?) {
        binding.chipGroupHistory.removeAllViews()

        searchHistory?.forEach { history ->
            val chip = Chip(requireContext()).apply {
                text = history
                setChipBackgroundColorResource(R.color.colorSurface)
                chipStrokeWidth = 1f
                setChipStrokeColorResource(R.color.divider)
                setTextColor(requireContext().getColor(R.color.textPrimary))
                setOnClickListener {
                    binding.editSearch.setText(history)
                    viewModel.performSearch(history)
                }
            }
            binding.chipGroupHistory.addView(chip)
        }
    }

    private fun showSearchHistory() {
        binding.recyclerViewResults.visibility = View.GONE
        binding.emptyView.visibility = View.GONE
        binding.searchHistoryContainer.visibility = View.VISIBLE
    }

    private fun showResults() {
        binding.recyclerViewResults.visibility = View.VISIBLE
        binding.emptyView.visibility = View.GONE
        binding.searchHistoryContainer.visibility = View.GONE
    }

    private fun showEmptyState(query: String) {
        binding.recyclerViewResults.visibility = View.GONE
        binding.emptyView.visibility = View.VISIBLE
        binding.searchHistoryContainer.visibility = View.GONE

        val tvEmptyTitle = binding.emptyView.findViewById<android.widget.TextView>(R.id.tv_empty_title)
        val tvEmptyDesc = binding.emptyView.findViewById<android.widget.TextView>(R.id.tv_empty_desc)

        tvEmptyTitle.text = getString(R.string.search_no_result)
        tvEmptyDesc.text = "未找到包含 \"$query\" 的电影"
    }

    override fun onMovieClick(movie: Movie) {
        (activity as? MainActivity)?.openDetail(movie.id)
    }

    override fun onResume() {
        super.onResume()
        // 刷新搜索历史
        viewModel.loadSearchHistory()
    }
}