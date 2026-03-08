package com.example.nasmovie.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.nasmovie.R;
import com.example.nasmovie.util.PreferenceManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 锁屏验证页面
 */
public class LockActivity extends AppCompatActivity implements View.OnClickListener {

    private static final int PASSWORD_LENGTH = 4;

    private TextView tvTitle;
    private TextView tvSubtitle;
    private TextView tvError;
    private View[] dotViews;
    private List<String> passwordDigits = new ArrayList<>();

    private PreferenceManager preferenceManager;
    private boolean isSettingPassword = false;
    private boolean isConfirmingPassword = false;
    private String tempPassword = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lock);

        // 禁止截图（增强安全性）
        getWindow().setFlags(
                android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE
        );

        preferenceManager = new PreferenceManager(this);

        // 获取模式：验证模式 / 设置密码模式
        isSettingPassword = getIntent().getBooleanExtra("setting_password", false);

        initViews();
        updateUI();
    }

    private void initViews() {
        tvTitle = findViewById(R.id.tv_lock_title);
        tvSubtitle = findViewById(R.id.tv_lock_subtitle);
        tvError = findViewById(R.id.tv_lock_error);

        // 密码圆点指示器
        dotViews = new View[PASSWORD_LENGTH];
        dotViews[0] = findViewById(R.id.dot_1);
        dotViews[1] = findViewById(R.id.dot_2);
        dotViews[2] = findViewById(R.id.dot_3);
        dotViews[3] = findViewById(R.id.dot_4);

        // 数字键盘
        int[] numberIds = {
                R.id.btn_0, R.id.btn_1, R.id.btn_2, R.id.btn_3, R.id.btn_4,
                R.id.btn_5, R.id.btn_6, R.id.btn_7, R.id.btn_8, R.id.btn_9
        };
        for (int id : numberIds) {
            findViewById(id).setOnClickListener(this);
        }

        // 删除按钮
        findViewById(R.id.btn_delete).setOnClickListener(v -> onDeleteClick());

        // 长按删除清空
        findViewById(R.id.btn_delete).setOnLongClickListener(v -> {
            clearPassword();
            return true;
        });
    }

    private void updateUI() {
        if (isSettingPassword) {
            if (isConfirmingPassword) {
                tvTitle.setText("确认密码");
                tvSubtitle.setText("请再次输入4位数字密码");
            } else {
                tvTitle.setText("设置密码");
                tvSubtitle.setText("请输入4位数字密码");
            }
        } else {
            tvTitle.setText("应用锁");
            tvSubtitle.setText("请输入密码解锁");
        }
        tvError.setVisibility(View.GONE);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();

        // 数字按钮
        if (id == R.id.btn_0) addDigit("0");
        else if (id == R.id.btn_1) addDigit("1");
        else if (id == R.id.btn_2) addDigit("2");
        else if (id == R.id.btn_3) addDigit("3");
        else if (id == R.id.btn_4) addDigit("4");
        else if (id == R.id.btn_5) addDigit("5");
        else if (id == R.id.btn_6) addDigit("6");
        else if (id == R.id.btn_7) addDigit("7");
        else if (id == R.id.btn_8) addDigit("8");
        else if (id == R.id.btn_9) addDigit("9");
    }

    private void addDigit(String digit) {
        // 检查是否需要延迟
        if (!isSettingPassword && preferenceManager.isLockDelayRequired()) {
            long remaining = preferenceManager.getLockRemainingDelayTime() / 1000;
            showError("请等待 " + remaining + " 秒后重试");
            return;
        }

        if (passwordDigits.size() < PASSWORD_LENGTH) {
            passwordDigits.add(digit);
            updateDots();

            // 输入完成，自动验证
            if (passwordDigits.size() == PASSWORD_LENGTH) {
                new Handler(Looper.getMainLooper()).postDelayed(this::onPasswordComplete, 100);
            }
        }
    }

    private void onDeleteClick() {
        if (!passwordDigits.isEmpty()) {
            passwordDigits.remove(passwordDigits.size() - 1);
            updateDots();
            tvError.setVisibility(View.GONE);
        }
    }

    private void clearPassword() {
        passwordDigits.clear();
        updateDots();
        tvError.setVisibility(View.GONE);
    }

    private void updateDots() {
        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            if (i < passwordDigits.size()) {
                dotViews[i].setBackgroundResource(R.drawable.bg_dot_filled);
            } else {
                dotViews[i].setBackgroundResource(R.drawable.bg_dot_empty);
            }
        }
    }

    private void onPasswordComplete() {
        String password = String.join("", passwordDigits);

        if (isSettingPassword) {
            handleSetPassword(password);
        } else {
            handleVerifyPassword(password);
        }
    }

    private void handleSetPassword(String password) {
        if (!isConfirmingPassword) {
            // 第一次输入，保存临时密码
            tempPassword = password;
            isConfirmingPassword = true;
            passwordDigits.clear();
            updateDots();
            updateUI();
        } else {
            // 确认密码
            if (password.equals(tempPassword)) {
                // 密码设置成功
                preferenceManager.setLockPassword(password);
                preferenceManager.setLockEnabled(true);
                preferenceManager.clearLockErrorCount();
                setResult(RESULT_OK); // 设置成功结果
                Toast.makeText(this, "密码设置成功", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                // 两次输入不一致
                showError("两次输入的密码不一致，请重新设置");
                isConfirmingPassword = false;
                tempPassword = "";
                passwordDigits.clear();
                updateDots();
                updateUI();
            }
        }
    }

    private void handleVerifyPassword(String password) {
        if (preferenceManager.verifyPassword(password)) {
            // 验证成功
            preferenceManager.clearLockErrorCount();
            preferenceManager.setShouldShowLock(false);
            finish();
        } else {
            // 验证失败
            preferenceManager.incrementLockErrorCount();
            int errorCount = preferenceManager.getLockErrorCount();

            if (errorCount >= 5) {
                showError("密码错误次数过多，请30秒后重试");
            } else {
                showError("密码错误，还剩 " + (5 - errorCount) + " 次机会");
            }

            // 震动反馈
            vibreateError();

            passwordDigits.clear();
            updateDots();
        }
    }

    private void showError(String message) {
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
    }

    private void vibreateError() {
        android.os.Vibrator vibrator = (android.os.Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(200);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (isSettingPassword) {
                // 设置密码模式，返回即取消
                finish();
            } else {
                // 验证模式，返回则退出应用
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_HOME);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finishAffinity();
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 再次检查是否需要延迟
        if (!isSettingPassword && preferenceManager.isLockDelayRequired()) {
            long remaining = preferenceManager.getLockRemainingDelayTime() / 1000;
            showError("请等待 " + remaining + " 秒后重试");
        }
    }
}
