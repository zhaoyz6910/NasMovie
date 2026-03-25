package com.example.nasmovie.ui

import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.nasmovie.NASMovieApp
import com.example.nasmovie.R
import com.example.nasmovie.data.db.AppDatabase
import com.example.nasmovie.data.db.SmbConfigDao
import com.example.nasmovie.data.model.SmbConfig
import com.example.nasmovie.data.smb.SmbClient
import com.example.nasmovie.databinding.ActivityServerEditBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 服务器编辑/添加 Fragment - iOS 风格复刻版
 */
class ServerEditFragment : Fragment() {

    companion object {
        @JvmStatic
        fun newInstance(serverId: String?): ServerEditFragment {
            val fragment = ServerEditFragment()
            val args = Bundle()
            if (!serverId.isNullOrEmpty()) {
                args.putString("server_id", serverId)
            }
            fragment.arguments = args
            return fragment
        }
    }

    private var _binding: ActivityServerEditBinding? = null
    private val binding get() = _binding!!

    private lateinit var database: AppDatabase
    private lateinit var smbConfigDao: SmbConfigDao
    private var serverId: Long = -1
    private var isPasswordVisible = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ActivityServerEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        initViews()
        initData()
    }

    private fun initViews() {
        binding.toolbar.setShowBack(true)
        val activity = activity
        if (activity is AppCompatActivity) {
            activity.setSupportActionBar(binding.toolbar.toolbar)
            activity.supportActionBar?.setDisplayShowTitleEnabled(false)
        }
        binding.toolbar.setOnBackClickListener {
            if (activity is MainActivity) {
                activity.performRealBack()
            }
        }

        binding.btnTogglePassword.setOnClickListener { togglePasswordVisibility() }
        binding.btnTest.setOnClickListener { testConnection() }
        binding.btnSave.setOnClickListener { saveServer() }
    }

    private fun togglePasswordVisibility() {
        isPasswordVisible = !isPasswordVisible
        if (isPasswordVisible) {
            binding.etPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
            binding.btnTogglePassword.setImageResource(R.drawable.ic_visibility_off)
        } else {
            binding.etPassword.transformationMethod = PasswordTransformationMethod.getInstance()
            binding.btnTogglePassword.setImageResource(R.drawable.ic_visibility)
        }
        binding.etPassword.setSelection(binding.etPassword.text.length)
    }

    private fun initData() {
        database = NASMovieApp.getInstance().database
        smbConfigDao = database.smbConfigDao()

        val args = arguments
        if (args != null && args.containsKey("server_id")) {
            val idStr = args.getString("server_id")
            if (idStr != null) {
                serverId = idStr.toLong()
                loadServerData()
                binding.toolbar.setTitle("编辑服务器")
            }
        } else {
            binding.toolbar.setTitle("添加服务器")
        }
    }

    private fun loadServerData() {
        lifecycleScope.launch(Dispatchers.IO) {
            val config = smbConfigDao.getById(serverId)
            if (config != null && isAdded) {
                withContext(Dispatchers.Main) {
                    binding.etServerName.setText(config.name)
                    binding.etServerHost.setText(config.host)
                    binding.etServerPort.setText(config.port.toString())
                    binding.etServerShare.setText(config.shareName)
                    binding.etMoviePath.setText(config.moviePath)
                    binding.etUsername.setText(config.username)
                    binding.etPassword.setText(config.password)
                }
            }
        }
    }

    private fun testConnection() {
        val config = getConfigFromInput() ?: return

        Toast.makeText(context, "正在测试连接...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            val success = SmbClient().testConnection(config)
            
            withContext(Dispatchers.Main) {
                if (isAdded) {
                    if (success) {
                        Toast.makeText(context, "连接成功", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "连接失败，请检查配置", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun saveServer() {
        val config = getConfigFromInput() ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (serverId > 0) {
                    config.id = serverId
                    smbConfigDao.update(config)
                } else {
                    smbConfigDao.insert(config)
                }

                // 添加短暂延迟确保数据库事务完成
                delay(100)

                withContext(Dispatchers.Main) {
                    if (isAdded && context != null) {
                        Toast.makeText(context, "保存成功", Toast.LENGTH_SHORT).show()
                        val activity = activity
                        if (activity is MainActivity) {
                            activity.performRealBack()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        Toast.makeText(context, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun getConfigFromInput(): SmbConfig? {
        val name = binding.etServerName.text.toString().trim()
        val host = binding.etServerHost.text.toString().trim()
        val portStr = binding.etServerPort.text.toString().trim()
        val share = binding.etServerShare.text.toString().trim()
        val path = binding.etMoviePath.text.toString().trim()
        val username = binding.etUsername.text.toString().trim()
        val password = binding.etPassword.text.toString()

        if (name.isEmpty()) {
            binding.etServerName.error = "请输入名称"
            return null
        }
        if (host.isEmpty()) {
            binding.etServerHost.error = "请输入 IP"
            return null
        }
        if (share.isEmpty()) {
            binding.etServerShare.error = "请输入共享名"
            return null
        }

        val port = try {
            if (portStr.isEmpty()) 445 else portStr.toInt()
        } catch (e: NumberFormatException) {
            binding.etServerPort.error = "无效端口"
            return null
        }

        val config = SmbConfig()
        config.name = name
        config.host = host
        config.port = port
        config.shareName = share
        config.moviePath = path
        config.username = username
        config.password = password

        return config
    }
}
