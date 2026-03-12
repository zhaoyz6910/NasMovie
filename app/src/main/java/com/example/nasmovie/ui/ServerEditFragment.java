package com.example.nasmovie.ui;

import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.nasmovie.NASMovieApp;
import com.example.nasmovie.R;
import com.example.nasmovie.data.db.AppDatabase;
import com.example.nasmovie.data.db.SmbConfigDao;
import com.example.nasmovie.data.model.SmbConfig;
import com.example.nasmovie.data.smb.SmbClient;
import com.example.nasmovie.view.NasToolbar;

/**
 * 服务器编辑/添加 Fragment - iOS 风格复刻版
 */
public class ServerEditFragment extends Fragment {

    public static ServerEditFragment newInstance(String serverId) {
        ServerEditFragment fragment = new ServerEditFragment();
        Bundle args = new Bundle();
        args.putString("server_id", serverId);
        fragment.setArguments(args);
        return fragment;
    }

    private EditText etName, etHost, etPort, etShare, etPath, etUsername, etPassword;
    private ImageButton btnTogglePassword;
    private View btnTest, btnSave;
    private NasToolbar toolbar;

    private AppDatabase database;
    private SmbConfigDao smbConfigDao;
    private long serverId = -1;
    private boolean isPasswordVisible = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_server_edit, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        initViews(view);
        initData();
    }

    private void initViews(View view) {
        toolbar = view.findViewById(R.id.toolbar);
        etName = view.findViewById(R.id.et_server_name);
        etHost = view.findViewById(R.id.et_server_host);
        etPort = view.findViewById(R.id.et_server_port);
        etShare = view.findViewById(R.id.et_server_share);
        etPath = view.findViewById(R.id.et_movie_path);
        etUsername = view.findViewById(R.id.et_username);
        etPassword = view.findViewById(R.id.et_password);
        btnTogglePassword = view.findViewById(R.id.btn_toggle_password);
        btnTest = view.findViewById(R.id.btn_test);
        btnSave = view.findViewById(R.id.btn_save);

        toolbar.setShowBack(true);
        if (getActivity() instanceof androidx.appcompat.app.AppCompatActivity) {
            androidx.appcompat.app.AppCompatActivity activity = (androidx.appcompat.app.AppCompatActivity) getActivity();
            activity.setSupportActionBar(toolbar.getToolbar());
            if (activity.getSupportActionBar() != null) {
                activity.getSupportActionBar().setDisplayShowTitleEnabled(false);
            }
        }
        toolbar.setOnBackClickListener(() -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).onBackPressed();
            }
        });

        btnTogglePassword.setOnClickListener(v -> togglePasswordVisibility());
        btnTest.setOnClickListener(v -> testConnection());
        btnSave.setOnClickListener(v -> saveServer());
    }

    private void togglePasswordVisibility() {
        isPasswordVisible = !isPasswordVisible;
        if (isPasswordVisible) {
            etPassword.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
            btnTogglePassword.setImageResource(R.drawable.ic_visibility_off);
        } else {
            etPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
            btnTogglePassword.setImageResource(R.drawable.ic_visibility);
        }
        etPassword.setSelection(etPassword.getText().length());
    }

    private void initData() {
        database = NASMovieApp.getInstance().getDatabase();
        smbConfigDao = database.smbConfigDao();

        Bundle args = getArguments();
        if (args != null && args.containsKey("server_id")) {
            String idStr = args.getString("server_id");
            if (idStr != null) {
                serverId = Long.parseLong(idStr);
                loadServerData();
                toolbar.setTitle("编辑服务器");
            }
        } else {
            toolbar.setTitle("添加服务器");
        }
    }

    private void loadServerData() {
        new Thread(() -> {
            SmbConfig config = smbConfigDao.getById(serverId);
            if (config != null && isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    etName.setText(config.getName());
                    etHost.setText(config.getHost());
                    etPort.setText(String.valueOf(config.getPort()));
                    etShare.setText(config.getShareName());
                    etPath.setText(config.getMoviePath());
                    etUsername.setText(config.getUsername());
                    etPassword.setText(config.getPassword());
                });
            }
        }).start();
    }

    private void testConnection() {
        final SmbConfig config = getConfigFromInput();
        if (config == null) return;

        Toast.makeText(getContext(), "正在测试连接...", Toast.LENGTH_SHORT).show();
        
        new Thread(() -> {
            SmbClient client = new SmbClient();
            boolean success = client.testConnection(config);
            
            View view = getView();
            if (view != null) {
                view.post(() -> {
                    if (isAdded()) {
                        if (success) {
                            Toast.makeText(getContext(), "连接成功", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(getContext(), "连接失败，请检查配置", Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        }).start();
    }

    private void saveServer() {
        final SmbConfig config = getConfigFromInput();
        if (config == null) return;

        new Thread(() -> {
            try {
                if (serverId > 0) {
                    config.setId(serverId);
                    smbConfigDao.update(config);
                } else {
                    smbConfigDao.insert(config);
                }

                View view = getView();
                if (view != null) {
                    view.post(() -> {
                        if (isAdded() && getContext() != null) {
                            Toast.makeText(getContext(), "保存成功", Toast.LENGTH_SHORT).show();
                            if (getActivity() instanceof MainActivity) {
                                ((MainActivity) getActivity()).onBackPressed();
                            }
                        }
                    });
                }
            } catch (Exception e) {
                View view = getView();
                if (view != null) {
                    view.post(() -> {
                        if (isAdded()) {
                            Toast.makeText(getContext(), "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    private SmbConfig getConfigFromInput() {
        String name = etName.getText().toString().trim();
        String host = etHost.getText().toString().trim();
        String portStr = etPort.getText().toString().trim();
        String share = etShare.getText().toString().trim();
        String path = etPath.getText().toString().trim();
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString();

        if (name.isEmpty()) {
            etName.setError("请输入名称");
            return null;
        }
        if (host.isEmpty()) {
            etHost.setError("请输入 IP");
            return null;
        }
        if (share.isEmpty()) {
            etShare.setError("请输入共享名");
            return null;
        }

        int port;
        try {
            port = portStr.isEmpty() ? 445 : Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            etPort.setError("无效端口");
            return null;
        }

        SmbConfig config = new SmbConfig();
        config.setName(name);
        config.setHost(host);
        config.setPort(port);
        config.setShareName(share);
        config.setMoviePath(path);
        config.setUsername(username);
        config.setPassword(password);

        return config;
    }
}