package com.example.nasmovie.player;

import android.content.Context;
import android.media.AudioManager;
import android.provider.Settings;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

/**
 * 播放器手势处理类
 * 处理亮度、音量、快进快退手势
 */
public class PlayerGestureHandler extends GestureDetector.SimpleOnGestureListener {

    private static final int GESTURE_NONE = 0;
    private static final int GESTURE_BRIGHTNESS = 1;
    private static final int GESTURE_VOLUME = 2;
    private static final int GESTURE_SEEK = 3;

    private static final int SEEK_STEP = 10000; // 10秒

    private final Context context;
    private final View playerView;
    private final AudioManager audioManager;
    private final WindowManager windowManager;
    private final GestureCallback callback;

    private int currentGesture = GESTURE_NONE;
    private float startX, startY;
    private float lastX, lastY;
    private int currentBrightness;
    private int currentVolume;
    private int maxVolume;
    private long seekPosition;
    private boolean isGestureMoving = false;

    public PlayerGestureHandler(Context context, View playerView, GestureCallback callback) {
        this.context = context;
        this.playerView = playerView;
        this.callback = callback;
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
    }

    /**
     * 处理触摸事件
     * @return true 如果处理了手势滑动，false 如果只是点击
     */
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                startX = event.getX();
                startY = event.getY();
                lastX = event.getX();
                lastY = event.getY();
                currentGesture = GESTURE_NONE;
                isGestureMoving = false;
                break;

            case MotionEvent.ACTION_MOVE:
                handleMove(event);
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                handleEnd();
                break;
        }
        return isGestureMoving;
    }

    private void handleMove(MotionEvent event) {
        float deltaX = event.getX() - lastX;
        float deltaY = event.getY() - lastY;

        // 判断手势类型
        if (currentGesture == GESTURE_NONE) {
            if (Math.abs(deltaY) > Math.abs(deltaX) && Math.abs(deltaY) > 20) {
                // 垂直滑动
                isGestureMoving = true;
                if (startX < playerView.getWidth() / 2) {
                    // 左侧：亮度
                    isGestureMoving = true;
                    currentGesture = GESTURE_BRIGHTNESS;
                    currentBrightness = getCurrentWindowBrightness();
                } else {
                    // 右侧：音量
                    currentGesture = GESTURE_VOLUME;
                    currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                }
            } else if (Math.abs(deltaX) > Math.abs(deltaY) && Math.abs(deltaX) > 20) {
                // 水平滑动：快进快退
                isGestureMoving = true;
                currentGesture = GESTURE_SEEK;
                if (callback != null) {
                    seekPosition = callback.getCurrentPosition();
                }
            }
        }

        // 处理手势
        switch (currentGesture) {
            case GESTURE_BRIGHTNESS:
                handleBrightness(deltaY);
                break;
            case GESTURE_VOLUME:
                handleVolume(deltaY);
                break;
            case GESTURE_SEEK:
                // 传入当前位置，计算从起点开始的总偏移
                handleSeek(event.getX());
                break;
        }

        lastX = event.getX();
        lastY = event.getY();
    }

    /**
     * 处理亮度调整
     */
    private void handleBrightness(float deltaY) {
        // 计算亮度变化
        float delta = -deltaY / playerView.getHeight();
        int newBrightness = (int) (currentBrightness + delta * 255);
        newBrightness = Math.max(0, Math.min(255, newBrightness));

        // 设置亮度
        setBrightness(newBrightness);

        // 更新当前亮度值，避免下次计算时基准值不变导致跳动
        currentBrightness = newBrightness;

        // 显示亮度提示
        if (callback != null) {
            int percent = newBrightness * 100 / 255;
            callback.onBrightnessChanged(percent);
        }
    }

    /**
     * 处理音量调整
     */
    private void handleVolume(float deltaY) {
        // 计算音量变化
        float delta = -deltaY / playerView.getHeight();
        int steps = (int) (delta * maxVolume);
        int newVolume = currentVolume + steps;
        newVolume = Math.max(0, Math.min(maxVolume, newVolume));

        // 设置音量
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0);

        // 更新当前音量值，避免下次计算时基准值不变导致跳动
        currentVolume = newVolume;

        // 显示音量提示
        if (callback != null) {
            int percent = newVolume * 100 / maxVolume;
            callback.onVolumeChanged(percent);
        }
    }

    /**
     * 处理快进快退
     */
    private void handleSeek(float currentX) {
        // 计算从起点开始的总偏移量
        float totalDeltaX = currentX - startX;
        float percent = totalDeltaX / playerView.getWidth();
        long seekDelta = (long) (percent * 120000); // 最大2分钟

        long newPosition = seekPosition + seekDelta;
        long duration = callback != null ? callback.getDuration() : 0;

        newPosition = Math.max(0, Math.min(duration, newPosition));

        // 显示进度提示
        if (callback != null) {
            callback.onSeekPreview(newPosition, seekDelta);
        }
    }

    private void handleEnd() {
        if (currentGesture == GESTURE_SEEK && callback != null) {
            // 执行跳转
            long duration = callback.getDuration();
            float percent = (lastX - startX) / playerView.getWidth();
            long seekDelta = (long) (percent * 120000); // 最大2分钟，与handleSeek一致
            long newPosition = seekPosition + seekDelta;
            newPosition = Math.max(0, Math.min(duration, newPosition));
            callback.onSeek(newPosition);
        }

        // 隐藏提示
        if (callback != null) {
            callback.onGestureEnd();
        }

        currentGesture = GESTURE_NONE;
    }

    /**
     * 获取当前窗口亮度（应用设置的亮度）
     */
    private int getCurrentWindowBrightness() {
        try {
            float brightness = ((android.app.Activity) context).getWindow().getAttributes().screenBrightness;
            if (brightness >= 0) {
                return (int) (brightness * 255);
            }
        } catch (Exception e) {
            // 忽略
        }
        // 如果窗口亮度未设置，返回系统亮度
        return getCurrentBrightness();
    }

    /**
     * 获取当前亮度
     */
    private int getCurrentBrightness() {
        try {
            int brightness = Settings.System.getInt(
                context.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS
            );
            return brightness;
        } catch (Settings.SettingNotFoundException e) {
            return 128;
        }
    }

    /**
     * 设置亮度
     */
    private void setBrightness(int brightness) {
        try {
            WindowManager.LayoutParams params = ((android.app.Activity) context).getWindow().getAttributes();
            params.screenBrightness = brightness / 255f;
            ((android.app.Activity) context).getWindow().setAttributes(params);
        } catch (Exception e) {
            // 忽略
        }
    }

    /**
     * 手势回调接口
     */
    public interface GestureCallback {
        long getCurrentPosition();
        long getDuration();
        void onBrightnessChanged(int percent);
        void onVolumeChanged(int percent);
        void onSeekPreview(long position, long delta);
        void onSeek(long position);
        void onGestureEnd();
    }
}