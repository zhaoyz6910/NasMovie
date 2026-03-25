package com.example.nasmovie.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.example.nasmovie.R
import com.example.nasmovie.data.model.Movie
import com.example.nasmovie.databinding.FragmentFavoritesBinding
import com.example.nasmovie.ui.adapter.FavoriteAdapter

/**
 * 收藏 Fragment
 */
class FavoritesFragment : Fragment(),
    FavoriteAdapter.OnMovieClickListener,
    FavoriteAdapter.OnMovieLongClickListener {

    private var _binding: FragmentFavoritesBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: FavoriteAdapter
    private lateinit var viewModel: FavoritesViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFavoritesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel = ViewModelProvider(this)[FavoritesViewModel::class.java]
        initViews(view)
        observeViewModel()
    }

    private fun initViews(view: View) {
        // 设置 Toolbar
        binding.toolbar?.let { toolbar ->
            toolbar.setTitle("我的收藏")
            toolbar.setShowBack(false)
            (activity as? AppCompatActivity)?.let { activity ->
                activity.setSupportActionBar(toolbar.toolbar)
                activity.supportActionBar?.setDisplayShowTitleEnabled(false)
            }
        }

        // 根据屏幕宽度计算列数，卡片宽度约 120dp
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

        binding.recyclerViewFavorites.layoutManager = GridLayoutManager(context, spanCount)
        adapter = FavoriteAdapter()
        adapter.setOnMovieClickListener(this)
        adapter.setOnMovieLongClickListener(this)
        binding.recyclerViewFavorites.adapter = adapter

        binding.btnDelete.setOnClickListener { deleteSelected() }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.filterFavorites(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun observeViewModel() {
        viewModel.displayedFavorites.observe(viewLifecycleOwner) { favorites ->
            adapter.setMovies(favorites)
            updateEmptyView()
        }
    }

    private fun updateEmptyView() {
        if (adapter.itemCount == 0) {
            binding.emptyView.visibility = View.VISIBLE
            binding.recyclerViewFavorites.visibility = View.GONE
        } else {
            binding.emptyView.visibility = View.GONE
            binding.recyclerViewFavorites.visibility = View.VISIBLE
        }
    }

    private fun deleteSelected() {
        val selectedIds = adapter.selectedIds
        if (selectedIds.isEmpty()) {
            Toast.makeText(context, "请选择要删除的收藏", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(requireContext())
            .setTitle("删除收藏")
            .setMessage(getString(R.string.delete_favorites_confirm, selectedIds.size))
            .setPositiveButton("删除") { _, _ ->
                viewModel.deleteSelected(selectedIds)
                adapter.setSelectionMode(false)
                binding.bottomActions.visibility = View.GONE
                Toast.makeText(context, "已删除 ${selectedIds.size} 部收藏", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onMovieClick(movie: Movie, position: Int) {
        (activity as? MainActivity)?.openDetail(movie.id)
    }

    override fun onMovieLongClick(movie: Movie, position: Int): Boolean {
        if (!adapter.isSelectionMode) {
            adapter.setSelectionMode(true)
            adapter.toggleSelection(movie.id)
            binding.bottomActions.visibility = View.VISIBLE
        }
        return true
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadFavorites()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            viewModel.loadFavorites()
        }
    }
}