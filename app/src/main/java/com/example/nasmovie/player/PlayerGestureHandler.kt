package com.example.nasmovie.player

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.example.nasmovie.util.AppConstants

/**
 * 播放器手势处理类
 * 处理亮度、音量、快进快退手势
 */
class PlayerGestureHandler(
    private val context: Context,
    private val playerView: View,
    private val callback: GestureCallback?
) {

    companion object {
        private const val GESTURE_NONE = 0
        private const val GESTURE_BRIGHTNESS = 1
        private const val GESTURE_VOLUME = 2
        private const val GESTURE_SEEK = 3
    }

    private val audioManager: AudioManager? = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val windowManager: WindowManager? = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    private val maxVolume: Int = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15

    private var currentGesture = GESTURE_NONE
    private var startX = 0f
    private var startY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var currentBrightness = 0
    private var currentVolume = 0
    private var seekPosition = 0L
    private var isGestureMoving = false

    /**
     * 处理触摸事件
     * @return true 如果处理了手势滑动，false 如果只是点击
     */
    fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                lastX = event.x
                lastY = event.y
                currentGesture = GESTURE_NONE
                isGestureMoving = false
            }
            MotionEvent.ACTION_MOVE -> handleMove(event)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> handleEnd()
        }
        return isGestureMoving
    }

    private fun handleMove(event: MotionEvent) {
        val deltaX = event.x - lastX
        val deltaY = event.y - lastY

        // 判断手势类型
        if (currentGesture == GESTURE_NONE) {
            if (kotlin.math.abs(deltaY) > kotlin.math.abs(deltaX) && kotlin.math.abs(deltaY) > 20) {
                // 垂直滑动
                isGestureMoving = true
                if (startX < playerView.width / 2) {
                    // 左侧：亮度
                    currentGesture = GESTURE_BRIGHTNESS
                    currentBrightness = getCurrentWindowBrightness()
                } else {
                    // 右侧：音量
                    currentGesture = GESTURE_VOLUME
                    currentVolume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
                }
            } else if (kotlin.math.abs(deltaX) > kotlin.math.abs(deltaY) && kotlin.math.abs(deltaX) > 20) {
                // 水平滑动：快进快退
                isGestureMoving = true
                currentGesture = GESTURE_SEEK
                seekPosition = callback?.getCurrentPosition() ?: 0
            }
        }

        // 处理手势
        when (currentGesture) {
            GESTURE_BRIGHTNESS -> handleBrightness(deltaY)
            GESTURE_VOLUME -> handleVolume(deltaY)
            GESTURE_SEEK -> handleSeek(event.x)
        }

        lastX = event.x
        lastY = event.y
    }

    /**
     * 处理亮度调整
     */
    private fun handleBrightness(deltaY: Float) {
        // 计算亮度变化
        val delta = -deltaY / playerView.height
        var newBrightness = (currentBrightness + delta * 255).toInt()
        newBrightness = newBrightness.coerceIn(0, 255)

        // 设置亮度
        setBrightness(newBrightness)

        // 更新当前亮度值
        currentBrightness = newBrightness

        // 显示亮度提示
        val percent = newBrightness * 100 / 255
        callback?.onBrightnessChanged(percent)
    }

    /**
     * 处理音量调整
     */
    private fun handleVolume(deltaY: Float) {
        // 计算音量变化
        val delta = -deltaY / playerView.height
        val steps = (delta * maxVolume).toInt()
        var newVolume = currentVolume + steps
        newVolume = newVolume.coerceIn(0, maxVolume)

        // 设置音量
        audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)

        // 更新当前音量值
        currentVolume = newVolume

        // 显示音量提示
        val percent = newVolume * 100 / maxVolume
        callback?.onVolumeChanged(percent)
    }

    /**
     * 处理快进快退
     */
    private fun handleSeek(currentX: Float) {
        // 计算从起点开始的总偏移量
        val totalDeltaX = currentX - startX
        val percent = totalDeltaX / playerView.width
        val seekDelta = (percent * 120000).toLong() // 最大2分钟

        var newPosition = seekPosition + seekDelta
        val duration = callback?.getDuration() ?: 0

        newPosition = newPosition.coerceIn(0, duration)

        // 显示进度提示
        callback?.onSeekPreview(newPosition, seekDelta)
    }

    private fun handleEnd() {
        if (currentGesture == GESTURE_SEEK) {
            // 执行跳转
            val duration = callback?.getDuration() ?: 0
            val percent = (lastX - startX) / playerView.width
            val seekDelta = (percent * 120000).toLong()
            var newPosition = seekPosition + seekDelta
            newPosition = newPosition.coerceIn(0, duration)
            callback?.onSeek(newPosition)
        }

        // 隐藏提示
        callback?.onGestureEnd()

        currentGesture = GESTURE_NONE
    }

    /**
     * 获取当前窗口亮度（应用设置的亮度）
     */
    private fun getCurrentWindowBrightness(): Int {
        return try {
            val brightness = (context as? Activity)?.window?.attributes?.screenBrightness ?: -1f
            if (brightness >= 0) {
                (brightness * 255).toInt()
            } else {
                getCurrentBrightness()
            }
        } catch (e: Exception) {
            getCurrentBrightness()
        }
    }

    /**
     * 获取当前亮度
     */
    private fun getCurrentBrightness(): Int {
        return try {
            Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS
            )
        } catch (e: Settings.SettingNotFoundException) {
            128
        }
    }

    /**
     * 设置亮度
     */
    private fun setBrightness(brightness: Int) {
        try {
            val params = (context as? Activity)?.window?.attributes
            params?.screenBrightness = brightness / 255f
            (context as? Activity)?.window?.attributes = params
        } catch (e: Exception) {
            // 忽略
        }
    }

    /**
     * 手势回调接口
     */
    interface GestureCallback {
        fun getCurrentPosition(): Long
        fun getDuration(): Long
        fun onBrightnessChanged(percent: Int)
        fun onVolumeChanged(percent: Int)
        fun onSeekPreview(position: Long, delta: Long)
        fun onSeek(position: Long)
        fun onGestureEnd()
    }
}