package com.example.nasmovie.ui;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.nasmovie.NASMovieApp;
import com.example.nasmovie.R;
import com.example.nasmovie.data.db.SmbConfigDao;
import com.example.nasmovie.data.model.SmbConfig;
import com.example.nasmovie.data.smb.SmbClient;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.concurrent.Executors;

/**
 * 服务器编辑页面
 */
public class ServerEditActivity extends AppCompatActivity {

    public static final String EXTRA_SERVER_ID = "server_id";

    private TextInputEditText etName;
    private TextInputEditText etHost;
    private TextInputEditText etPort;
    private TextInputEditText etShare;
    private TextInputEditText etPath;
    private TextInputEditText etUsername;
    private TextInputEditText etPassword;
    private MaterialButton btnTest;
    private MaterialButton btnSave;

    private SmbConfigDao smbConfigDao;
    private long serverId = -1;
    private SmbConfig currentConfig;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server_edit);

        serverId = getIntent().getLongExtra(EXTRA_SERVER_ID, -1);

        initViews();
        initData();

        if (serverId > 0) {
            loadServer();
        }
    }

    private void initViews() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(serverId > 0 ? R.string.edit_server : R.string.add_server);

        etName = findViewById(R.id.et_name);
        etHost = findViewById(R.id.et_host);
        etPort = findViewById(R.id.et_port);
        etShare = findViewById(R.id.et_share);
        etPath = findViewById(R.id.et_path);
        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        btnTest = findViewById(R.id.btn_test);
        btnSave = findViewById(R.id.btn_save);

        btnTest.setOnClickListener(v -> testConnection());
        btnSave.setOnClickListener(v -> saveServer());
    }

    private void initData() {
        smbConfigDao = NASMovieApp.getInstance().getDatabase().smbConfigDao();
    }

    private void loadServer() {
        new Thread(() -> {
            currentConfig = smbConfigDao.getById(serverId);
            runOnUiThread(() -> {
                if (currentConfig != null) {
                    displayServer();
                }
            });
        }).start();
    }

    private void displayServer() {
        etName.setText(currentConfig.getName());
        etHost.setText(currentConfig.getHost());
        etPort.setText(String.valueOf(currentConfig.getPort()));
        etShare.setText(currentConfig.getShareName());
        etPath.setText(currentConfig.getMoviePath());
        etUsername.setText(currentConfig.getUsername());
        etPassword.setText(currentConfig.getPassword());
    }

    private void testConnection() {
        SmbConfig config = getConfigFromInput();
        if (config == null) return;

        btnTest.setEnabled(false);
        btnTest.setText("测试中...");

        Executors.newSingleThreadExecutor().execute(() -> {
            SmbClient client = new SmbClient();
            boolean success = client.testConnection(config);
            client.disconnect();

            runOnUiThread(() -> {
                btnTest.setEnabled(true);
                btnTest.setText(R.string.test_connection);

                if (success) {
                    Toast.makeText(this, R.string.connection_success, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, R.string.connection_failed, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void saveServer() {
        SmbConfig config = getConfigFromInput();
        if (config == null) return;

        new Thread(() -> {
            long savedId;
            if (serverId > 0) {
                config.setId(serverId);
                smbConfigDao.update(config);
                savedId = serverId;
            } else {
                savedId = smbConfigDao.insert(config);
            }

            runOnUiThread(() -> {
                Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show();
                finish();
            });
        }).start();
    }

    private SmbConfig getConfigFromInput() {
        String name = etName.getText() != null ? etName.getText().toString().trim() : "";
        String host = etHost.getText() != null ? etHost.getText().toString().trim() : "";
        String portStr = etPort.getText() != null ? etPort.getText().toString().trim() : "445";
        String share = etShare.getText() != null ? etShare.getText().toString().trim() : "";
        String path = etPath.getText() != null ? etPath.getText().toString().trim() : "";
        String username = etUsername.getText() != null ? etUsername.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString() : "";

        // 验证必填字段
        if (name.isEmpty()) {
            etName.setError("请输入服务器名称");
            return null;
        }
        if (host.isEmpty()) {
            etHost.setError("请输入IP地址");
            return null;
        }
        if (share.isEmpty()) {
            etShare.setError("请输入共享文件夹名");
            return null;
        }

        SmbConfig config = new SmbConfig();
        config.setName(name);
        config.setHost(host);
        config.setPort(Integer.parseInt(portStr));
        config.setShareName(share);
        config.setMoviePath(path);
        config.setUsername(username);
        config.setPassword(password);

        return config;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}