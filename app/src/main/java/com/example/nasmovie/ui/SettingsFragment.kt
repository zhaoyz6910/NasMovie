package com.example.nasmovie.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.nasmovie.NASMovieApp
import com.example.nasmovie.R
import com.example.nasmovie.databinding.FragmentSettingsBinding
import com.example.nasmovie.util.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 设置 Fragment - iOS 风格复刻版
 */
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var preferenceManager: PreferenceManager

    // Activity Result API
    private lateinit var setPasswordLauncher: ActivityResultLauncher<Intent>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        initViews()
        initData()
        initActivityResultLauncher()
    }

    private fun initActivityResultLauncher() {
        setPasswordLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            updateLockDisplay()
        }
    }

    private fun initViews() {
        // 设置 Toolbar
        binding.toolbar.setTitle(R.string.settings)
        binding.toolbar.setShowBack(false)

        binding.itemServerManage.setOnClickListener {
            (activity as? MainActivity)?.openServerManage()
        }

        binding.itemClearCache.setOnClickListener { clearCache() }

        binding.itemAbout.setOnClickListener { showAboutDialog() }

        binding.itemLockSettings.setOnClickListener { toggleLock() }
        binding.itemLockPassword.setOnClickListener { changePassword() }
    }

    private fun initData() {
        preferenceManager = PreferenceManager(requireContext())
        updateLockDisplay()
    }

    private fun clearCache() {
        showCustomDialog("清除缓存", "确定要清除所有图片缓存吗？", "确定", "取消") {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    // 执行清理逻辑
                    NASMovieApp.getInstance().imageCache.clearCache()
                }
                if (isAdded && context != null) {
                    Toast.makeText(context, "缓存已清除", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showAboutDialog() {
        var versionName = "1.0"
        try {
            versionName = requireContext().packageManager
                .getPackageInfo(requireContext().packageName, 0).versionName ?: "1.0"
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        val message = getString(R.string.about_message, versionName)
        showCustomDialog("关于", message, "确定", null, null)
    }

    /**
     * 显示通用自定义样式的对话框
     */
    private fun showCustomDialog(
        title: String,
        message: String,
        positiveText: String?,
        negativeText: String?,
        onPositive: Runnable?
    ) {
        if (context == null) return

        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_confirm_exit, null)
        val dialog = android.app.Dialog(requireContext())
        dialog.setContentView(dialogView)

        dialog.window?.let { window ->
            window.setBackgroundDrawableResource(android.R.color.transparent)

            // 固定弹窗宽度 (320dp)
            val width = (320 * resources.displayMetrics.density).toInt()
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)

            val lp = window.attributes
            lp.dimAmount = 0.4f
            window.attributes = lp
        }

        val tvTitle = dialogView.findViewById<TextView>(R.id.tv_dialog_title)
        val tvMessage = dialogView.findViewById<TextView>(R.id.tv_dialog_message)
        val btnPositive = dialogView.findViewById<TextView>(R.id.btn_positive)
        val btnNegative = dialogView.findViewById<TextView>(R.id.btn_negative)

        tvTitle.text = title
        tvMessage.text = message

        if (positiveText != null) {
            btnPositive.text = positiveText
            btnPositive.setOnClickListener {
                dialog.dismiss()
                onPositive?.run()
            }
        } else {
            btnPositive.visibility = View.GONE
        }

        if (negativeText != null) {
            btnNegative.text = negativeText
            btnNegative.setOnClickListener { dialog.dismiss() }
        } else {
            btnNegative.visibility = View.GONE
        }

        dialog.show()
    }

    private fun updateLockDisplay() {
        val isLockEnabled = preferenceManager.isLockEnabled
        binding.switchLock.isChecked = isLockEnabled

        if (isLockEnabled) {
            binding.cardLockPassword.visibility = View.VISIBLE
        } else {
            binding.cardLockPassword.visibility = View.GONE
        }
    }

    private fun toggleLock() {
        val isLockEnabled = preferenceManager.isLockEnabled

        if (isLockEnabled) {
            showCustomDialog("关闭应用锁", "确定要关闭应用锁吗？", "确定", "取消") {
                preferenceManager.isLockEnabled = false
                preferenceManager.lockPassword = ""
                updateLockDisplay()
                Toast.makeText(context, "应用锁已关闭", Toast.LENGTH_SHORT).show()
            }
            // 确保在弹窗出现时 Switch 状态保持开启
            binding.switchLock.isChecked = true
        } else {
            val intent = Intent(context, LockActivity::class.java).apply {
                putExtra("setting_password", true)
            }
            setPasswordLauncher.launch(intent)
        }
    }

    private fun changePassword() {
        val intent = Intent(context, LockActivity::class.java).apply {
            putExtra("setting_password", true)
        }
        setPasswordLauncher.launch(intent)
    }

    override fun onResume() {
        super.onResume()
        updateLockDisplay()
    }
}