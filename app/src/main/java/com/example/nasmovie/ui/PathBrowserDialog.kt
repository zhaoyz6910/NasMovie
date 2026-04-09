package com.example.nasmovie.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.nasmovie.R
import com.example.nasmovie.data.model.SmbConfig
import com.example.nasmovie.data.smb.SmbClient
import com.example.nasmovie.databinding.DialogPathBrowserBinding
import com.example.nasmovie.databinding.ItemBrowseFolderBinding
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/**
 * 路径浏览对话框
 * 用于选择 SMB 服务器的共享目录
 */
class PathBrowserDialog(
    context: Context,
    private val host: String,
    private val port: Int,
    private val username: String?,
    private val password: String?,
    private val onPathSelected: (shareName: String, moviePath: String) -> Unit
) : Dialog(context), CoroutineScope {

    companion object {
        private const val TAG = "PathBrowserDialog"
    }

    private lateinit var binding: DialogPathBrowserBinding
    private val job = Job()
    override val coroutineContext: CoroutineContext = job + Dispatchers.Main

    private val adapter = FolderAdapter { folder -> onFolderClicked(folder) }
    private val pathStack = mutableListOf<PathItem>()
    private var currentShareName: String? = null
    private var initialShareName: String? = null

    fun setInitialShare(shareName: String) {
        initialShareName = shareName
    }

    data class PathItem(
        val type: Type,
        val name: String,
        val path: String
    ) {
        enum class Type {
            SHARE,      // 共享根目录
            DIRECTORY   // 子目录
        }
    }

    data class Folder(
        val name: String,
        val path: String,
        val isShare: Boolean
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        binding = DialogPathBrowserBinding.inflate(LayoutInflater.from(context))
        setContentView(binding.root)

        window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        initViews()

        Log.i(TAG, "PathBrowserDialog 创建: host=$host, port=$port, username=$username, initialShare=$initialShareName")

        // 如果有初始共享名，直接进入该共享
        if (initialShareName != null) {
            currentShareName = initialShareName
            pathStack.add(PathItem(PathItem.Type.SHARE, initialShareName!!, ""))
            updateBreadcrumb()
            Log.i(TAG, "直接进入共享: $initialShareName")
            loadDirectory(initialShareName!!, "")
        } else {
            // 加载真实的共享名列表
            Log.i(TAG, "开始加载真实共享名列表")
            loadRealShares()
        }
    }

    private fun initViews() {
        binding.btnClose.setOnClickListener { dismiss() }
        binding.btnSelect.setOnClickListener { onSelectCurrentPath() }

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@PathBrowserDialog.adapter
        }
    }

    private fun loadRealShares() {
        showLoading(true)
        launch {
            try {
                Log.i(TAG, "开始枚举真实共享名...")
                val shares = withContext(Dispatchers.IO) {
                    SmbClient().listShares(host, port, username, password)
                }

                if (shares.isEmpty()) {
                    Log.w(TAG, "未发现共享")
                    showContent()
                    // 显示空列表提示
                    binding.emptyView.visibility = View.VISIBLE
                    binding.recyclerView.visibility = View.GONE
                    updateSelectButton(false)
                    Toast.makeText(context, "未发现共享，请检查服务器设置和权限", Toast.LENGTH_LONG).show()
                } else {
                    Log.i(TAG, "成功枚举到 ${shares.size} 个共享: $shares")
                    showContent()
                    displayShares(shares)
                }
            } catch (e: Exception) {
                Log.e(TAG, "枚举共享失败: ${e.message}", e)
                showError("枚举共享失败: ${e.message}")
                updateSelectButton(false)
                Toast.makeText(context, "枚举共享失败: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                showLoading(false)
            }
        }
    }

    private fun displayShares(shares: List<String>) {
        Log.i(TAG, "显示共享列表: $shares")
        val folders = shares.map { Folder(it, it, true) }
        adapter.setFolders(folders)
        updateSelectButton(false)  // 共享列表阶段，不能选择
    }

    private fun loadDirectory(shareName: String, path: String) {
        Log.i(TAG, "加载目录: shareName=$shareName, path=$path")
        showLoading(true)
        launch {
            try {
                val config = SmbConfig().apply {
                    this.host = this@PathBrowserDialog.host
                    this.port = this@PathBrowserDialog.port
                    this.username = this@PathBrowserDialog.username
                    this.password = this@PathBrowserDialog.password
                    this.shareName = shareName
                    this.moviePath = path
                }

                val folders = withContext(Dispatchers.IO) {
                    SmbClient().use { client ->
                        if (client.connect(config)) {
                            Log.i(TAG, "连接成功，开始列出目录")
                            client.listFiles(path).filter { it.isDirectory }
                        } else {
                            Log.e(TAG, "连接失败")
                            emptyList()
                        }
                    }
                }

                Log.i(TAG, "找到 ${folders.size} 个子目录")
                showContent()
                displayFolders(folders)
            } catch (e: Exception) {
                Log.e(TAG, "加载目录失败: ${e.message}", e)
                showError("加载目录失败: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun displayFolders(folders: List<com.example.nasmovie.data.smb.SmbFileInfo>) {
        val folderList = folders.mapNotNull { info ->
            val name = info.name
            val path = info.path
            if (name != null && path != null) {
                Folder(name, path, false)
            } else {
                null
            }
        }
        adapter.setFolders(folderList)

        // 允许选择当前目录（子目录列表非空或空都可以选择）
        updateSelectButton(true)
    }

    private fun onFolderClicked(folder: Folder) {
        Log.i(TAG, "点击文件夹: ${folder.name}, isShare=${folder.isShare}")
        if (folder.isShare) {
            // 进入共享
            currentShareName = folder.name
            pathStack.add(PathItem(PathItem.Type.SHARE, folder.name, ""))
            updateBreadcrumb()
            loadDirectory(folder.name, "")
        } else {
            // 进入子目录
            pathStack.add(PathItem(PathItem.Type.DIRECTORY, folder.name, folder.path))
            updateBreadcrumb()
            loadDirectory(currentShareName!!, folder.path)
        }
    }

    private fun onBreadcrumbClicked(index: Int) {
        Log.i(TAG, "点击面包屑: index=$index, pathStack.size=${pathStack.size}")

        if (index == -1) {
            // 点击根节点"共享"，回到共享列表
            pathStack.clear()
            currentShareName = null
            updateBreadcrumb()
            loadRealShares()
            return
        }

        if (index < pathStack.size) {
            val item = pathStack[index]
            if (item.type == PathItem.Type.SHARE) {
                // 回到共享列表
                pathStack.subList(index, pathStack.size).clear()
                currentShareName = null
                updateBreadcrumb()
                loadRealShares()  // 重新加载真实共享列表
            } else {
                // 回到某个子目录
                pathStack.subList(index + 1, pathStack.size).clear()
                updateBreadcrumb()
                loadDirectory(currentShareName!!, item.path)
            }
        }
    }

    private fun updateBreadcrumb() {
        binding.breadcrumbContainer.removeAllViews()

        // 添加"共享"作为根节点
        val rootText = createBreadcrumbItem("共享", 0)
        binding.breadcrumbContainer.addView(rootText)

        // 添加分隔符
        if (pathStack.isNotEmpty()) {
            binding.breadcrumbContainer.addView(createSeparator())
        }

        pathStack.forEachIndexed { index, item ->
            val text = createBreadcrumbItem(item.name, index + 1)
            binding.breadcrumbContainer.addView(text)

            if (index < pathStack.size - 1) {
                binding.breadcrumbContainer.addView(createSeparator())
            }
        }
    }

    private fun createBreadcrumbItem(text: String, index: Int): TextView {
        return TextView(context).apply {
            this.text = text
            setTextColor(
                if (index == pathStack.size) context.getColor(R.color.iosBlue)
                else context.getColor(android.R.color.white)
            )
            textSize = 14f
            setOnClickListener { onBreadcrumbClicked(index - 1) }
            setPadding(4, 8, 4, 8)
        }
    }

    private fun createSeparator(): TextView {
        return TextView(context).apply {
            text = ">"
            setTextColor(context.getColor(R.color.iosGray))
            textSize = 14f
            setPadding(4, 8, 4, 8)
        }
    }

    private fun onSelectCurrentPath() {
        val shareName = currentShareName
        val moviePath = pathStack
            .filter { it.type == PathItem.Type.DIRECTORY }
            .joinToString("/") { it.name }

        Log.i(TAG, "选择当前路径: shareName=$shareName, moviePath=$moviePath")

        if (shareName != null) {
            onPathSelected(shareName, moviePath)
            dismiss()
        } else {
            Log.w(TAG, "未选择共享名，不能选择路径")
        }
    }

    private fun showLoading(show: Boolean) {
        binding.loadingView.visibility = if (show) View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun showContent() {
        binding.emptyView.visibility = View.GONE
        binding.errorView.visibility = View.GONE
    }

    private fun showError(message: String) {
        binding.recyclerView.visibility = View.GONE
        binding.emptyView.visibility = View.GONE
        binding.errorView.visibility = View.VISIBLE
        binding.errorText.text = message
    }

    private fun updateSelectButton(enabled: Boolean) {
        binding.btnSelect.isEnabled = enabled && currentShareName != null
    }

    override fun dismiss() {
        job.cancel()
        super.dismiss()
    }

    // Adapter
    private class FolderAdapter(
        private val onFolderClicked: (Folder) -> Unit
    ) : RecyclerView.Adapter<FolderAdapter.ViewHolder>() {

        private val folders = mutableListOf<Folder>()

        fun setFolders(newFolders: List<Folder>) {
            folders.clear()
            folders.addAll(newFolders)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemBrowseFolderBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(folders[position])
        }

        override fun getItemCount() = folders.size

        inner class ViewHolder(
            private val binding: ItemBrowseFolderBinding
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(folder: Folder) {
                binding.tvName.text = folder.name

                if (folder.isShare) {
                    binding.tvInfo.text = "共享文件夹"
                    binding.tvInfo.visibility = View.VISIBLE
                } else {
                    binding.tvInfo.visibility = View.GONE
                }

                binding.root.setOnClickListener { onFolderClicked(folder) }
            }
        }
    }
}