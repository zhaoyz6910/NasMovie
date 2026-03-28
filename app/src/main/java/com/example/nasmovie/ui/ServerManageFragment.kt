package com.example.nasmovie.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.lifecycle.lifecycleScope
import com.example.nasmovie.NASMovieApp
import com.example.nasmovie.R
import com.example.nasmovie.data.model.SmbConfig
import com.example.nasmovie.databinding.ActivityServerManageBinding
import com.example.nasmovie.service.ScanService
import com.example.nasmovie.ui.adapter.ServerAdapter
import com.example.nasmovie.view.BottomSheetDrawer
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 服务器管理 Fragment - iOS 风格新版
 */
class ServerManageFragment : Fragment(),
    ServerAdapter.OnItemClickListener,
    ServerAdapter.OnItemLongClickListener,
    IBackInterceptor {

    private var _binding: ActivityServerManageBinding? = null
    private val binding get() = _binding!!

    private lateinit var smbConfigDao: com.example.nasmovie.data.db.SmbConfigDao
    private lateinit var adapter: ServerAdapter
    private lateinit var scanService: ScanService

    // 当前正在扫描的服务器 ID
    private var currentScanningServerId = -1L

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ActivityServerManageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        initViews()
        initData()
        loadServers()
    }

    private fun initViews() {
        // 设置 Toolbar
        activity?.let { activity ->
            (activity as? AppCompatActivity)?.setSupportActionBar(binding.toolbar.toolbar)
            (activity as? AppCompatActivity)?.supportActionBar?.setDisplayShowTitleEnabled(false)
        }

        // 设置标题和返回按钮
        binding.toolbar.setTitle(R.string.server_manage)
        binding.toolbar.setShowBack(true)
        binding.toolbar.setOnBackClickListener {
            (activity as? MainActivity)?.performRealBack()
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(context)
        // 禁用 item 动画，避免扫描时进度更新导致闪烁
        binding.recyclerView.itemAnimator = null
        adapter = ServerAdapter()
        adapter.setOnItemClickListener(this)
        adapter.setOnItemLongClickListener(this)
        binding.recyclerView.adapter = adapter

        binding.fabAdd.setOnClickListener {
            (activity as? MainActivity)?.openServerEdit(null)
        }

        binding.btnScan.setOnClickListener { scanMedia() }
    }

    private fun initData() {
        smbConfigDao = NASMovieApp.getInstance().database.smbConfigDao()
        scanService = ScanService()
    }

    private fun loadServers() {
        lifecycleScope.launch {
            val servers = withContext(Dispatchers.IO) {
                smbConfigDao.getAll()
            }
            if (isAdded) {
                adapter.setServers(servers)
                updateEmptyView()
            }
        }
    }

    private fun updateEmptyView() {
        if (adapter.itemCount == 0) {
            binding.emptyView.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
        } else {
            binding.emptyView.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
        }
    }

    override fun onItemClick(config: SmbConfig) {
        (activity as? MainActivity)?.openServerEdit(config.id.toString())
    }

    override fun onItemLongClick(config: SmbConfig) {
        val drawer = BottomSheetDrawer.Builder()
            .addItem("扫描媒体库") { scanSingleServer(config.id) }
            .addItem("编辑服务器") { onItemClick(config) }
            .addDestructiveItem("删除服务器") { deleteServer(config) }
            .build()

        drawer.show(parentFragmentManager, "ServerOptionsDrawer")
    }

    private fun setAsDefaultServer(config: SmbConfig) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                // 先清除所有默认标记
                val allServers = smbConfigDao.getAll()
                for (server in allServers) {
                    if (server.isDefault) {
                        server.isDefault = false
                        smbConfigDao.update(server)
                    }
                }
                // 设置当前服务器为默认
                config.isDefault = true
                smbConfigDao.update(config)
            }
            loadServers()
            if (isAdded) {
                Toast.makeText(context, "已设为默认服务器", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteServer(config: SmbConfig) {
        BottomSheetDrawer.Builder()
            .addItem("取消", null)
            .addDestructiveItem("删除") {
                lifecycleScope.launch {
                    val success = withContext(Dispatchers.IO) {
                        try {
                            val database = NASMovieApp.getInstance().database
                            val serverId = config.id  // 现在直接使用 Long 类型，无需转换

                            // 1. 使用批量删除：删除观看进度和收藏
                            database.watchProgressDao().deleteByServerId(serverId)
                            database.favoriteDao().deleteByServerId(serverId)

                            // 2. 删除该服务器的所有影视资源
                            database.movieDao().deleteByServerId(serverId)

                            // 3. 删除服务器配置
                            smbConfigDao.delete(config)
                            true
                        } catch (e: Exception) {
                            android.util.Log.e("ServerManageFragment", "删除服务器失败", e)
                            false
                        }
                    }

                    if (success && isAdded) {
                        loadServers()
                        Toast.makeText(context, "服务器已删除", Toast.LENGTH_SHORT).show()
                    } else if (isAdded) {
                        Toast.makeText(context, "删除失败，请稍后重试", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .build()
            .show(parentFragmentManager, "DeleteConfirmDrawer")
    }

    private fun scanSingleServer(id: Long) {
        if (scanService.isScanning) {
            Toast.makeText(context, R.string.scanning, Toast.LENGTH_SHORT).show()
            return
        }
        scanService.scanServer(id, createScanCallback())
    }

    private fun scanMedia() {
        if (scanService.isScanning) {
            Toast.makeText(context, "已停止当前扫描", Toast.LENGTH_SHORT).show()
            scanService.stopScan()
            adapter.clearScanStatus()
            (binding.btnScan as? MaterialButton)?.text = "扫描媒体库"
            return
        }
        scanService.scanAllServers(createScanCallback())
    }

    private fun createScanCallback(): ScanService.ScanCallback {
        return object : ScanService.ScanCallback {
            override fun onStart() {
                // 开始批量扫描
                (binding.btnScan as? MaterialButton)?.text = "取消扫描"
            }

            override fun onServerStart(config: SmbConfig) {
                // 已在主线程
                currentScanningServerId = config.id
                adapter.updateScanProgress(currentScanningServerId, 0, "准备扫描...", "")
            }

            override fun onProgress(current: Int, total: Int, currentPath: String) {
                // 已在主线程
                val percentage = if (total > 0) current * 100 / total else 0
                val status = "正在扫描... $percentage%"
                val detail = "$currentPath ($current/$total)"

                adapter.updateScanProgress(currentScanningServerId, percentage, status, detail)
            }

            override fun onComplete(addedCount: Int, totalServers: Int) {
                // 已在主线程
                currentScanningServerId = -1
                adapter.clearScanStatus()
                (binding.btnScan as? MaterialButton)?.text = "扫描媒体库"
                if (isAdded && context != null) {
                    Toast.makeText(
                        context,
                        getString(R.string.scan_complete, addedCount),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onError(error: String) {
                // 已在主线程
                currentScanningServerId = -1
                adapter.clearScanStatus()
                (binding.btnScan as? MaterialButton)?.text = "扫描媒体库"
                if (isAdded && context != null) {
                    Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onBackPressed(): Boolean {
        if (::scanService.isInitialized && scanService.isScanning) {
            showConfirmExitDialog()
            return true // 拦截事件，显示自定义对话框
        }
        return false // 不拦截，交给 Activity 处理
    }

    private fun showConfirmExitDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_confirm_exit, null)
        val dialog = android.app.Dialog(requireContext())
        dialog.setContentView(dialogView)

        // 设置对话框背景透明，以便显示自定义圆角背景
        dialog.window?.let { window ->
            window.setBackgroundDrawableResource(android.R.color.transparent)

            // 固定弹窗宽度 (320dp)
            val width = (320 * resources.displayMetrics.density).toInt()
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)

            // 设置背景遮罩 alpha
            val lp = window.attributes
            lp.dimAmount = 0.4f // 对应 bg-black/40
            window.attributes = lp
        }

        dialogView.findViewById<View>(R.id.btn_negative).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<View>(R.id.btn_positive).setOnClickListener {
            dialog.dismiss()
            if (::scanService.isInitialized) scanService.stopScan()
            (activity as? MainActivity)?.performRealBack()
        }

        dialog.show()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            // Fragment 从隐藏变为可见时刷新列表
            loadServers()
        }
    }

    override fun onResume() {
        super.onResume()
        loadServers()
    }

    override fun onDestroy() {
        super.onDestroy()
        // 只有在页面销毁时才确保停止，作为最后一道防线
        if (::scanService.isInitialized && scanService.isScanning) {
            scanService.stopScan()
        }
    }
}