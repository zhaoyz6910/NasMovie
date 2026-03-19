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

import java.lang.ref.WeakReference;
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
    private ImageView ivLockIcon;
    private View[] dotViews;
    private List<String> passwordDigits = new ArrayList<>();

    private PreferenceManager preferenceManager;
    private boolean isSettingPassword = false;
    private boolean isConfirmingPassword = false;
    private String tempPassword = "";
    private boolean isUnlocking = false;

    // 延迟倒计时 - 使用静态内部类避免内存泄漏
    private final Handler delayHandler = new Handler(Looper.getMainLooper());
    private DelayRunnable delayRunnable;

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
        resetUIState();
        updateUI();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // 防止重复启动时状态异常，重置所有状态
        setIntent(intent);
        isUnlocking = false;
        isSettingPassword = intent.getBooleanExtra("setting_password", false);
        isConfirmingPassword = false;
        tempPassword = "";
        passwordDigits.clear();
        resetUIState();
        updateUI();
        updateDots();
    }

    /**
     * 重置 UI 状态，确保所有元素可见
     */
    private void resetUIState() {
        // 重置所有 UI 元素的 alpha 值
        tvTitle.setAlpha(1f);
        tvSubtitle.setAlpha(1f);
        tvError.setAlpha(1f);
        ivLockIcon.setAlpha(1f);
        
        for (View dot : dotViews) {
            dot.setAlpha(1f);
        }

        // 重置数字键盘区域的 alpha
        android.view.ViewParent parent = findViewById(R.id.btn_0).getParent();
        if (parent instanceof android.view.View) {
            ((android.view.View) parent).setAlpha(1f);
        }

        // 重置锁图标
        ivLockIcon.setImageResource(R.drawable.ic_lock);
        ivLockIcon.setColorFilter(null);
        ivLockIcon.clearAnimation();

        // 重置标题颜色
        tvTitle.setTextColor(getResources().getColor(R.color.textPrimary, null));

        // 重新启用键盘
        setKeypadEnabled(true);
    }

    private void initViews() {
        tvTitle = findViewById(R.id.tv_lock_title);
        tvSubtitle = findViewById(R.id.tv_lock_subtitle);
        tvError = findViewById(R.id.tv_lock_error);
        ivLockIcon = findViewById(R.id.iv_lock_icon);

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
            if (isUnlocking) return true;
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
        tvError.setVisibility(View.INVISIBLE);
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
        // 检查是否正在开锁动画中
        if (isUnlocking) return;

        // 检查是否需要延迟
        if (!isSettingPassword && preferenceManager.isLockDelayRequired()) {
            startDelayCountdown();
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
    
    /**
     * 启动延迟倒计时
     */
    private void startDelayCountdown() {
        stopDelayCountdown();
        
        delayRunnable = new DelayRunnable(this);
        
        // 立即显示第一次
        long remaining = preferenceManager.getLockRemainingDelayTime() / 1000;
        showError("请等待 " + remaining + " 秒后重试");
        delayHandler.postDelayed(delayRunnable, 1000);
    }
    
    /**
     * 停止延迟倒计时
     */
    private void stopDelayCountdown() {
        if (delayRunnable != null) {
            delayHandler.removeCallbacks(delayRunnable);
            delayRunnable = null;
        }
    }

    /**
     * 静态内部类 Runnable，避免持有外部类引用导致内存泄漏
     */
    private static class DelayRunnable implements Runnable {
        private final WeakReference<LockActivity> activityRef;

        DelayRunnable(LockActivity activity) {
            this.activityRef = new WeakReference<>(activity);
        }

        @Override
        public void run() {
            LockActivity activity = activityRef.get();
            if (activity == null || activity.isFinishing()) return;

            if (activity.preferenceManager.isLockDelayRequired()) {
                long remaining = activity.preferenceManager.getLockRemainingDelayTime() / 1000;
                if (remaining > 0) {
                    activity.showError("请等待 " + remaining + " 秒后重试");
                    activity.delayHandler.postDelayed(this, 1000);
                } else {
                    activity.tvError.setVisibility(View.INVISIBLE);
                }
            } else {
                activity.tvError.setVisibility(View.INVISIBLE);
            }
        }
    }

    private void onDeleteClick() {
        if (isUnlocking) return;
        if (!passwordDigits.isEmpty()) {
            passwordDigits.remove(passwordDigits.size() - 1);
            updateDots();
            tvError.setVisibility(View.INVISIBLE);
        }
    }

    private void clearPassword() {
        if (isUnlocking) return;
        passwordDigits.clear();
        updateDots();
        tvError.setVisibility(View.INVISIBLE);
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
                // 密码设置成功，播放开锁动画
                playUnlockAnimationForSetup();
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
            // 验证成功，播放开锁动画
            playUnlockAnimation();
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

    private void playUnlockAnimation() {
        if (isUnlocking) return;
        isUnlocking = true;

        // 禁用数字键盘
        setKeypadEnabled(false);

        // 更新锁图标为开锁状态
        ivLockIcon.setImageResource(R.drawable.ic_lock_open);
        ivLockIcon.setColorFilter(android.graphics.Color.parseColor("#4CAF50"));

        // 更新标题文字和颜色（验证成功）
        tvTitle.setText("解锁成功");
        tvTitle.setTextColor(android.graphics.Color.parseColor("#4CAF50"));
        tvSubtitle.setText("正在进入...");

        // 播放开锁动画
        android.view.animation.Animation animation = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.anim_lock_open);
        animation.setAnimationListener(new android.view.animation.Animation.AnimationListener() {
            @Override
            public void onAnimationStart(android.view.animation.Animation animation) {}

            @Override
            public void onAnimationEnd(android.view.animation.Animation animation) {
                // 动画结束后进入APP
                enterApp();
            }

            @Override
            public void onAnimationRepeat(android.view.animation.Animation animation) {}
        });
        ivLockIcon.startAnimation(animation);

        // 同时淡出其他UI元素（隐藏数字键盘区域）
        ((android.view.View) findViewById(R.id.btn_0).getParent()).animate().alpha(0f).setDuration(300).start();
        tvTitle.animate().alpha(0f).setDuration(300).start();
        tvSubtitle.animate().alpha(0f).setDuration(300).start();
        tvError.animate().alpha(0f).setDuration(300).start();
        for (View dot : dotViews) {
            dot.animate().alpha(0f).setDuration(300).start();
        }
    }

    private void enterApp() {
        // 验证成功
        preferenceManager.clearLockErrorCount();
        preferenceManager.setShouldShowLock(false);
        finish();
    }

    private void playUnlockAnimationForSetup() {
        if (isUnlocking) return;
        isUnlocking = true;

        // 禁用数字键盘
        setKeypadEnabled(false);

        // 更新标题和副标题
        tvTitle.setText("密码设置成功");
        tvSubtitle.setText("正在进入...");
        tvTitle.setTextColor(android.graphics.Color.parseColor("#4CAF50"));
        tvError.setVisibility(View.INVISIBLE);

        // 更新锁图标为开锁状态
        ivLockIcon.setImageResource(R.drawable.ic_lock_open);
        ivLockIcon.setColorFilter(android.graphics.Color.parseColor("#4CAF50"));

        // 播放开锁动画
        android.view.animation.Animation animation = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.anim_lock_open);
        animation.setAnimationListener(new android.view.animation.Animation.AnimationListener() {
            @Override
            public void onAnimationStart(android.view.animation.Animation animation) {}

            @Override
            public void onAnimationEnd(android.view.animation.Animation animation) {
                // 动画结束后保存密码并返回
                preferenceManager.setLockPassword(tempPassword);
                preferenceManager.setLockEnabled(true);
                preferenceManager.clearLockErrorCount();
                setResult(RESULT_OK);
                finish();
            }

            @Override
            public void onAnimationRepeat(android.view.animation.Animation animation) {}
        });
        ivLockIcon.startAnimation(animation);

        // 同时淡出其他UI元素（隐藏数字键盘区域）
        ((android.view.View) findViewById(R.id.btn_0).getParent()).animate().alpha(0f).setDuration(300).start();
        tvTitle.animate().alpha(0f).setDuration(300).start();
        tvSubtitle.animate().alpha(0f).setDuration(300).start();
        for (View dot : dotViews) {
            dot.animate().alpha(0f).setDuration(300).start();
        }
    }

    private void setKeypadEnabled(boolean enabled) {
        int[] numberIds = {
                R.id.btn_0, R.id.btn_1, R.id.btn_2, R.id.btn_3, R.id.btn_4,
                R.id.btn_5, R.id.btn_6, R.id.btn_7, R.id.btn_8, R.id.btn_9, R.id.btn_delete
        };
        for (int id : numberIds) {
            findViewById(id).setEnabled(enabled);
        }
    }

    private void showError(String message) {
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
    }

    @SuppressWarnings("deprecation")
    private void vibreateError() {
        android.os.Vibrator vibrator;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            android.os.VibratorManager vibratorManager = (android.os.VibratorManager) getSystemService(VIBRATOR_MANAGER_SERVICE);
            vibrator = vibratorManager != null ? vibratorManager.getDefaultVibrator() : null;
        } else {
            vibrator = (android.os.Vibrator) getSystemService(VIBRATOR_SERVICE);
        }
        
        if (vibrator != null && vibrator.hasVibrator()) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(200, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(200);
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // 动画播放期间禁用返回键
            if (isUnlocking) return true;

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
            startDelayCountdown();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 停止倒计时
        stopDelayCountdown();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 停止倒计时
        stopDelayCountdown();
    }
}
