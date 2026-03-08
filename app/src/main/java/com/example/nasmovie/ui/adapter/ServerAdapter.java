package com.example.nasmovie.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nasmovie.R;
import com.example.nasmovie.data.model.SmbConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务器列表适配器
 */
public class ServerAdapter extends RecyclerView.Adapter<ServerAdapter.ViewHolder> {

    private List<SmbConfig> servers = new ArrayList<>();
    private OnItemClickListener onItemClickListener;
    private OnItemLongClickListener onItemLongClickListener;

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

    public List<SmbConfig> getServers() {
        return servers;
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
        private final TextView tvShare;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_server_name);
            tvHost = itemView.findViewById(R.id.tv_server_host);
            tvShare = itemView.findViewById(R.id.tv_server_share);

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
            tvShare.setText(config.getShareName());
        }
    }

    public interface OnItemClickListener {
        void onItemClick(SmbConfig config);
    }

    public interface OnItemLongClickListener {
        void onItemLongClick(SmbConfig config);
    }
}