package com.example.nasmovie.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nasmovie.R;
import com.example.nasmovie.data.model.SmbConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务器列表适配器 - iOS 风格
 */
public class ServerAdapter extends RecyclerView.Adapter<ServerAdapter.ViewHolder> {

    private List<SmbConfig> servers = new ArrayList<>();
    private OnItemClickListener onItemClickListener;
    private OnItemLongClickListener onItemLongClickListener;

    // 扫描状态相关
    private long activeScanServerId = -1;
    private int currentProgress = 0;
    private String progressText = "";
    private String progressDetail = "";

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_server, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SmbConfig config = servers.get(position);
        holder.bind(config);
    }

    @Override
    public int getItemCount() {
        return servers.size();
    }

    public void setServers(List<SmbConfig> servers) {
        this.servers = servers != null ? servers : new ArrayList<>();
        notifyDataSetChanged();
    }

    /**
     * 更新扫描进度
     */
    public void updateScanProgress(long serverId, int progress, String status, String detail) {
        long oldId = this.activeScanServerId;
        this.activeScanServerId = serverId;
        this.currentProgress = progress;
        this.progressText = status;
        this.progressDetail = detail;
        
        // 刷新新旧两个服务器的卡片
        for (int i = 0; i < servers.size(); i++) {
            long id = servers.get(i).getId();
            if (id == serverId || id == oldId) {
                notifyItemChanged(i);
            }
        }
    }

    /**
     * 清除扫描状态
     */
    public void clearScanStatus() {
        long oldId = this.activeScanServerId;
        this.activeScanServerId = -1;
        if (oldId != -1) {
            for (int i = 0; i < servers.size(); i++) {
                if (servers.get(i).getId() == oldId) {
                    notifyItemChanged(i);
                    break;
                }
            }
        }
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.onItemClickListener = listener;
    }

    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
        this.onItemLongClickListener = listener;
    }

    class ViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvName;
        private final TextView tvHost;
        private final View divider;
        private final View progressContainer;
        private final ProgressBar progressBar;
        private final TextView tvProgressStatus;
        private final TextView tvProgressDetail;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_server_name);
            tvHost = itemView.findViewById(R.id.tv_server_host);
            divider = itemView.findViewById(R.id.divider);
            progressContainer = itemView.findViewById(R.id.progress_container);
            progressBar = itemView.findViewById(R.id.progress_bar);
            tvProgressStatus = itemView.findViewById(R.id.tv_progress_status);
            tvProgressDetail = itemView.findViewById(R.id.tv_progress_detail);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && onItemClickListener != null) {
                    onItemClickListener.onItemClick(servers.get(position));
                }
            });

            itemView.setOnLongClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && onItemLongClickListener != null) {
                    onItemLongClickListener.onItemLongClick(servers.get(position));
                    return true;
                }
                return false;
            });
        }

        public void bind(SmbConfig config) {
            tvName.setText(config.getName());
            tvHost.setText(config.getHost());

            // 扫描进度逻辑
            if (config.getId() == activeScanServerId) {
                divider.setVisibility(View.VISIBLE);
                progressContainer.setVisibility(View.VISIBLE);
                progressBar.setProgress(currentProgress);
                tvProgressStatus.setText(progressText);
                tvProgressDetail.setText(progressDetail);
            } else {
                divider.setVisibility(View.GONE);
                progressContainer.setVisibility(View.GONE);
            }
        }
    }

    public interface OnItemClickListener {
        void onItemClick(SmbConfig config);
    }

    public interface OnItemLongClickListener {
        void onItemLongClick(SmbConfig config);
    }
}