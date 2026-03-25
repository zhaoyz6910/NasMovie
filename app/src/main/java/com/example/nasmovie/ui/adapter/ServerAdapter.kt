package com.example.nasmovie.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.nasmovie.R
import com.example.nasmovie.data.model.SmbConfig

/**
 * 服务器列表适配器 - iOS 风格
 */
class ServerAdapter : RecyclerView.Adapter<ServerAdapter.ViewHolder>() {

    private var servers: List<SmbConfig> = emptyList()
    private var onItemClickListener: OnItemClickListener? = null
    private var onItemLongClickListener: OnItemLongClickListener? = null

    // 扫描状态相关
    private var activeScanServerId: Long = -1
    private var currentProgress: Int = 0
    private var progressText: String = ""
    private var progressDetail: String = ""

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_server, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val config = servers[position]
        holder.bind(config)
    }

    override fun getItemCount(): Int = servers.size

    fun setServers(servers: List<SmbConfig>?) {
        this.servers = servers ?: emptyList()
        notifyDataSetChanged()
    }

    /**
     * 更新扫描进度
     */
    fun updateScanProgress(serverId: Long, progress: Int, status: String, detail: String) {
        val oldId = this.activeScanServerId
        this.activeScanServerId = serverId
        this.currentProgress = progress
        this.progressText = status
        this.progressDetail = detail

        // 刷新新旧两个服务器的卡片
        servers.forEachIndexed { index, smbConfig ->
            val id = smbConfig.id
            if (id == serverId || id == oldId) {
                notifyItemChanged(index)
            }
        }
    }

    /**
     * 清除扫描状态
     */
    fun clearScanStatus() {
        val oldId = this.activeScanServerId
        this.activeScanServerId = -1
        if (oldId != -1L) {
            servers.forEachIndexed { index, smbConfig ->
                if (smbConfig.id == oldId) {
                    notifyItemChanged(index)
                    return@forEachIndexed
                }
            }
        }
    }

    fun setOnItemClickListener(listener: OnItemClickListener?) {
        this.onItemClickListener = listener
    }

    fun setOnItemLongClickListener(listener: OnItemLongClickListener?) {
        this.onItemLongClickListener = listener
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val tvName: TextView = itemView.findViewById(R.id.tv_server_name)
        private val tvHost: TextView = itemView.findViewById(R.id.tv_server_host)
        private val divider: View = itemView.findViewById(R.id.divider)
        private val progressContainer: View = itemView.findViewById(R.id.progress_container)
        private val progressBar: ProgressBar = itemView.findViewById(R.id.progress_bar)
        private val tvProgressStatus: TextView = itemView.findViewById(R.id.tv_progress_status)
        private val tvProgressDetail: TextView = itemView.findViewById(R.id.tv_progress_detail)

        init {
            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClickListener?.onItemClick(servers[position])
                }
            }

            itemView.setOnLongClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemLongClickListener?.onItemLongClick(servers[position])
                    true
                } else {
                    false
                }
            }
        }

        fun bind(config: SmbConfig) {
            tvName.text = config.name
            tvHost.text = config.host

            // 扫描进度逻辑
            if (config.id == activeScanServerId) {
                divider.visibility = View.VISIBLE
                progressContainer.visibility = View.VISIBLE
                progressBar.progress = currentProgress
                tvProgressStatus.text = progressText
                tvProgressDetail.text = progressDetail
            } else {
                divider.visibility = View.GONE
                progressContainer.visibility = View.GONE
            }
        }
    }

    interface OnItemClickListener {
        fun onItemClick(config: SmbConfig)
    }

    interface OnItemLongClickListener {
        fun onItemLongClick(config: SmbConfig)
    }
}