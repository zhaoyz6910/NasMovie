package com.example.nasmovie.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibratorManager
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import com.example.nasmovie.R
import com.example.nasmovie.util.PreferenceManager
import java.lang.ref.WeakReference

/**
 * 锁屏验证页面
 */
class LockActivity : AppCompatActivity(), View.OnClickListener {

    companion object {
        // 密码长度
        // 设为 4 位是平衡安全性和易用性的常见选择
        private const val PASSWORD_LENGTH = 4
    }

    private lateinit var tvTitle: TextView
    private lateinit var tvSubtitle: TextView
    private lateinit var tvError: TextView
    private lateinit var ivLockIcon: ImageView
    private lateinit var dotViews: Array<View>
    private val passwordDigits = mutableListOf<String>()

    private lateinit var preferenceManager: PreferenceManager
    private var isSettingPassword = false
    private var isConfirmingPassword = false
    private var tempPassword = ""
    private var isUnlocking = false

    // 延迟倒计时 - 使用静态内部类避免内存泄漏
    private val delayHandler = Handler(Looper.getMainLooper())
    private var delayRunnable: DelayRunnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lock)


        preferenceManager = PreferenceManager(this)

        // 获取模式：验证模式 / 设置密码模式
        isSettingPassword = intent.getBooleanExtra("setting_password", false)

        initViews()
        resetUIState()
        updateUI()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // 防止重复启动时状态异常，重置所有状态
        setIntent(intent)
        isUnlocking = false
        isSettingPassword = intent.getBooleanExtra("setting_password", false)
        isConfirmingPassword = false
        tempPassword = ""
        passwordDigits.clear()
        resetUIState()
        updateUI()
        updateDots()
    }

    /**
     * 重置 UI 状态，确保所有元素可见
     */
    private fun resetUIState() {
        // 重置所有 UI 元素的 alpha 值
        tvTitle.alpha = 1f
        tvSubtitle.alpha = 1f
        tvError.alpha = 1f
        ivLockIcon.alpha = 1f

        for (dot in dotViews) {
            dot.alpha = 1f
        }

        // 重置数字键盘区域的 alpha
        val parent = findViewById<View>(R.id.btn_0).parent
        if (parent is View) {
            parent.alpha = 1f
        }

        // 重置锁图标
        ivLockIcon.setImageResource(R.drawable.ic_lock)
        ivLockIcon.colorFilter = null
        ivLockIcon.clearAnimation()

        // 重置标题颜色
        tvTitle.setTextColor(resources.getColor(R.color.textPrimary, null))

        // 重新启用键盘
        setKeypadEnabled(true)
    }

    private fun initViews() {
        tvTitle = findViewById(R.id.tv_lock_title)
        tvSubtitle = findViewById(R.id.tv_lock_subtitle)
        tvError = findViewById(R.id.tv_lock_error)
        ivLockIcon = findViewById(R.id.iv_lock_icon)

        // 密码圆点指示器
        dotViews = arrayOf(
            findViewById(R.id.dot_1),
            findViewById(R.id.dot_2),
            findViewById(R.id.dot_3),
            findViewById(R.id.dot_4)
        )

        // 数字键盘
        val numberIds = intArrayOf(
            R.id.btn_0, R.id.btn_1, R.id.btn_2, R.id.btn_3, R.id.btn_4,
            R.id.btn_5, R.id.btn_6, R.id.btn_7, R.id.btn_8, R.id.btn_9
        )
        for (id in numberIds) {
            findViewById<View>(id).setOnClickListener(this)
        }

        // 删除按钮
        findViewById<View>(R.id.btn_delete).setOnClickListener { onDeleteClick() }

        // 长按删除清空
        findViewById<View>(R.id.btn_delete).setOnLongClickListener {
            if (isUnlocking) return@setOnLongClickListener true
            clearPassword()
            true
        }
    }

    private fun updateUI() {
        if (isSettingPassword) {
            if (isConfirmingPassword) {
                tvTitle.text = "确认密码"
                tvSubtitle.text = "请再次输入4位数字密码"
            } else {
                tvTitle.text = "设置密码"
                tvSubtitle.text = "请输入4位数字密码"
            }
        } else {
            tvTitle.text = "应用锁"
            tvSubtitle.text = "请输入密码解锁"
        }
        tvError.visibility = View.INVISIBLE
    }

    override fun onClick(v: View) {
        // 数字按钮
        when (v.id) {
            R.id.btn_0 -> addDigit("0")
            R.id.btn_1 -> addDigit("1")
            R.id.btn_2 -> addDigit("2")
            R.id.btn_3 -> addDigit("3")
            R.id.btn_4 -> addDigit("4")
            R.id.btn_5 -> addDigit("5")
            R.id.btn_6 -> addDigit("6")
            R.id.btn_7 -> addDigit("7")
            R.id.btn_8 -> addDigit("8")
            R.id.btn_9 -> addDigit("9")
        }
    }

    private fun addDigit(digit: String) {
        // 检查是否正在开锁动画中
        if (isUnlocking) return

        // 检查是否需要延迟
        if (!isSettingPassword && preferenceManager.isLockDelayRequired) {
            startDelayCountdown()
            return
        }

        if (passwordDigits.size < PASSWORD_LENGTH) {
            passwordDigits.add(digit)
            updateDots()

            // 输入完成，自动验证
            if (passwordDigits.size == PASSWORD_LENGTH) {
                Handler(Looper.getMainLooper()).postDelayed({ onPasswordComplete() }, 100)
            }
        }
    }

    /**
     * 启动延迟倒计时
     */
    private fun startDelayCountdown() {
        stopDelayCountdown()

        delayRunnable = DelayRunnable(WeakReference(this))

        // 立即显示第一次
        val remaining = preferenceManager.lockRemainingDelayTime / 1000
        showError("请等待 $remaining 秒后重试")
        delayHandler.postDelayed(delayRunnable!!, 1000)
    }

    /**
     * 停止延迟倒计时
     */
    private fun stopDelayCountdown() {
        delayRunnable?.let {
            delayHandler.removeCallbacks(it)
            delayRunnable = null
        }
    }

    /**
     * 静态内部类 Runnable，避免持有外部类引用导致内存泄漏
     */
    private class DelayRunnable(private val activityRef: WeakReference<LockActivity>) : Runnable {
        override fun run() {
            val activity = activityRef.get() ?: return
            if (activity.isFinishing) return

            if (activity.preferenceManager.isLockDelayRequired) {
                val remaining = activity.preferenceManager.lockRemainingDelayTime / 1000
                if (remaining > 0) {
                    activity.showError("请等待 $remaining 秒后重试")
                    activity.delayHandler.postDelayed(this, 1000)
                } else {
                    activity.tvError.visibility = View.INVISIBLE
                }
            } else {
                activity.tvError.visibility = View.INVISIBLE
            }
        }
    }

    private fun onDeleteClick() {
        if (isUnlocking) return
        if (passwordDigits.isNotEmpty()) {
            passwordDigits.removeAt(passwordDigits.size - 1)
            updateDots()
            tvError.visibility = View.INVISIBLE
        }
    }

    private fun clearPassword() {
        if (isUnlocking) return
        passwordDigits.clear()
        updateDots()
        tvError.visibility = View.INVISIBLE
    }

    private fun updateDots() {
        for (i in 0 until PASSWORD_LENGTH) {
            if (i < passwordDigits.size) {
                dotViews[i].setBackgroundResource(R.drawable.bg_dot_filled)
            } else {
                dotViews[i].setBackgroundResource(R.drawable.bg_dot_empty)
            }
        }
    }

    private fun onPasswordComplete() {
        val password = passwordDigits.joinToString("")

        if (isSettingPassword) {
            handleSetPassword(password)
        } else {
            handleVerifyPassword(password)
        }
    }

    private fun handleSetPassword(password: String) {
        if (!isConfirmingPassword) {
            // 第一次输入，保存临时密码
            tempPassword = password
            isConfirmingPassword = true
            passwordDigits.clear()
            updateDots()
            updateUI()
        } else {
            // 确认密码
            if (password == tempPassword) {
                // 密码设置成功，播放开锁动画
                playUnlockAnimationForSetup()
            } else {
                // 两次输入不一致
                showError("两次输入的密码不一致，请重新设置")
                isConfirmingPassword = false
                tempPassword = ""
                passwordDigits.clear()
                updateDots()
                updateUI()
            }
        }
    }

    private fun handleVerifyPassword(password: String) {
        if (preferenceManager.verifyPassword(password)) {
            // 验证成功，播放开锁动画
            playUnlockAnimation()
        } else {
            // 验证失败
            preferenceManager.incrementLockErrorCount()
            val errorCount = preferenceManager.lockErrorCount

            if (errorCount >= 5) {
                showError("密码错误次数过多，请30秒后重试")
            } else {
                showError("密码错误，还剩 ${5 - errorCount} 次机会")
            }

            // 震动反馈
            vibrateError()

            passwordDigits.clear()
            updateDots()
        }
    }

    private fun playUnlockAnimation() {
        if (isUnlocking) return
        isUnlocking = true

        // 禁用数字键盘
        setKeypadEnabled(false)

        // 更新锁图标为开锁状态
        ivLockIcon.setImageResource(R.drawable.ic_lock_open)
        ivLockIcon.setColorFilter(android.graphics.Color.parseColor("#4CAF50"))

        // 更新标题文字和颜色（验证成功）
        tvTitle.text = "解锁成功"
        tvTitle.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
        tvSubtitle.text = "正在进入..."

        // 播放开锁动画
        val animation = AnimationUtils.loadAnimation(this, R.anim.anim_lock_open)
        animation.setAnimationListener(object : android.view.animation.Animation.AnimationListener {
            override fun onAnimationStart(animation: android.view.animation.Animation?) {}
            override fun onAnimationEnd(animation: android.view.animation.Animation?) {
                // 动画结束后进入APP
                enterApp()
            }
            override fun onAnimationRepeat(animation: android.view.animation.Animation?) {}
        })
        ivLockIcon.startAnimation(animation)

        // 同时淡出其他UI元素（隐藏数字键盘区域）
        (findViewById<View>(R.id.btn_0).parent as View).animate().alpha(0f).setDuration(300).start()
        tvTitle.animate().alpha(0f).setDuration(300).start()
        tvSubtitle.animate().alpha(0f).setDuration(300).start()
        tvError.animate().alpha(0f).setDuration(300).start()
        for (dot in dotViews) {
            dot.animate().alpha(0f).setDuration(300).start()
        }
    }

    private fun enterApp() {
        // 验证成功
        preferenceManager.clearLockErrorCount()
        preferenceManager.shouldShowLock = false
        finish()
    }

    private fun playUnlockAnimationForSetup() {
        if (isUnlocking) return
        isUnlocking = true

        // 禁用数字键盘
        setKeypadEnabled(false)

        // 更新标题和副标题
        tvTitle.text = "密码设置成功"
        tvSubtitle.text = "正在进入..."
        tvTitle.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
        tvError.visibility = View.INVISIBLE

        // 更新锁图标为开锁状态
        ivLockIcon.setImageResource(R.drawable.ic_lock_open)
        ivLockIcon.setColorFilter(android.graphics.Color.parseColor("#4CAF50"))

        // 播放开锁动画
        val animation = AnimationUtils.loadAnimation(this, R.anim.anim_lock_open)
        animation.setAnimationListener(object : android.view.animation.Animation.AnimationListener {
            override fun onAnimationStart(animation: android.view.animation.Animation?) {}
            override fun onAnimationEnd(animation: android.view.animation.Animation?) {
                // 动画结束后保存密码并返回
                preferenceManager.setEncryptedLockPassword(tempPassword)
                preferenceManager.isLockEnabled = true
                preferenceManager.clearLockErrorCount()
                setResult(RESULT_OK)
                finish()
            }
            override fun onAnimationRepeat(animation: android.view.animation.Animation?) {}
        })
        ivLockIcon.startAnimation(animation)

        // 同时淡出其他UI元素（隐藏数字键盘区域）
        (findViewById<View>(R.id.btn_0).parent as View).animate().alpha(0f).setDuration(300).start()
        tvTitle.animate().alpha(0f).setDuration(300).start()
        tvSubtitle.animate().alpha(0f).setDuration(300).start()
        for (dot in dotViews) {
            dot.animate().alpha(0f).setDuration(300).start()
        }
    }

    private fun setKeypadEnabled(enabled: Boolean) {
        val numberIds = intArrayOf(
            R.id.btn_0, R.id.btn_1, R.id.btn_2, R.id.btn_3, R.id.btn_4,
            R.id.btn_5, R.id.btn_6, R.id.btn_7, R.id.btn_8, R.id.btn_9, R.id.btn_delete
        )
        for (id in numberIds) {
            findViewById<View>(id).isEnabled = enabled
        }
    }

    private fun showError(message: String) {
        tvError.text = message
        tvError.visibility = View.VISIBLE
    }

    @Suppress("DEPRECATION")
    private fun vibrateError() {
        val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService<VibratorManager>()
            vibratorManager?.defaultVibrator
        } else {
            getSystemService(android.os.Vibrator::class.java)
        }

        vibrator?.let {
            if (it.hasVibrator()) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    it.vibrate(android.os.VibrationEffect.createOneShot(200, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    it.vibrate(200)
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // 动画播放期间禁用返回键
            if (isUnlocking) return true

            if (isSettingPassword) {
                // 设置密码模式，返回即取消
                finish()
            } else {
                // 验证模式，返回则退出应用
                val intent = Intent(Intent.ACTION_MAIN)
                intent.addCategory(Intent.CATEGORY_HOME)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                finishAffinity()
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        // 再次检查是否需要延迟
        if (!isSettingPassword && preferenceManager.isLockDelayRequired) {
            startDelayCountdown()
        }
    }

    override fun onPause() {
        super.onPause()
        // 停止倒计时
        stopDelayCountdown()
    }

    override fun onDestroy() {
        super.onDestroy()
        // 停止倒计时
        stopDelayCountdown()
    }
}