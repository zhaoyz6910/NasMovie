package com.example.nasmovie.ui

import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.util.Log
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
        private const val TAG = "ServerEditFragment"

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
        binding.btnBrowsePath.setOnClickListener { showPathBrowser() }
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

                    // 合并共享名和路径显示
                    val displayPath = if (config.shareName != null) {
                        if (config.moviePath.isNullOrEmpty()) {
                            config.shareName
                        } else {
                            "${config.shareName}/${config.moviePath}"
                        }
                    } else {
                        ""
                    }
                    binding.etMoviePath.setText(displayPath)

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
        val fullPath = binding.etMoviePath.text.toString().trim()
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
        if (fullPath.isEmpty()) {
            binding.etMoviePath.error = "请输入路径"
            return null
        }

        val port = try {
            if (portStr.isEmpty()) 445 else portStr.toInt()
        } catch (e: NumberFormatException) {
            binding.etServerPort.error = "无效端口"
            return null
        }

        // 解析路径为 shareName 和 moviePath
        val (shareName, moviePath) = parsePath(fullPath)

        val config = SmbConfig()
        config.name = name
        config.host = host
        config.port = port
        config.shareName = shareName
        config.moviePath = moviePath
        config.username = username
        config.password = password

        return config
    }

    private fun parsePath(fullPath: String): Pair<String?, String?> {
        val parts = fullPath.split("/", limit = 2)
        val result = when (parts.size) {
            1 -> Pair(parts[0], null)
            else -> Pair(parts[0], parts[1])
        }
        Log.i(TAG, "解析路径: fullPath=$fullPath, shareName=${result.first}, moviePath=${result.second}")
        return result
    }

    private fun showPathBrowser() {
        val host = binding.etServerHost.text.toString().trim()
        val portStr = binding.etServerPort.text.toString().trim()
        val username = binding.etUsername.text.toString().trim()
        val password = binding.etPassword.text.toString()

        Log.i(TAG, "打开路径浏览器: host=$host, port=$portStr, username=$username")

        // 验证 IP 地址
        if (host.isEmpty()) {
            binding.etServerHost.error = "请先输入 IP 地址"
            Toast.makeText(context, "请先输入 IP 地址", Toast.LENGTH_SHORT).show()
            return
        }

        // 验证端口
        val port = try {
            if (portStr.isEmpty()) 445 else portStr.toInt()
        } catch (e: NumberFormatException) {
            binding.etServerPort.error = "无效端口"
            Toast.makeText(context, "无效端口", Toast.LENGTH_SHORT).show()
            return
        }

        // 验证账号密码
        if (username.isEmpty() && password.isEmpty()) {
            // 两者都为空，询问是否使用匿名登录
            Toast.makeText(context, "请输入账号密码（或使用匿名登录）", Toast.LENGTH_SHORT).show()
            binding.etUsername.error = "请输入账号"
            binding.etPassword.error = "请输入密码"
            binding.etUsername.requestFocus()
            return
        }

        if (username.isNotEmpty() && password.isEmpty()) {
            // 只输入了账号，没有密码
            Toast.makeText(context, "请输入密码", Toast.LENGTH_SHORT).show()
            binding.etPassword.error = "请输入密码"
            binding.etPassword.requestFocus()
            return
        }

        if (username.isEmpty() && password.isNotEmpty()) {
            // 只输入了密码，没有账号
            Toast.makeText(context, "请输入账号", Toast.LENGTH_SHORT).show()
            binding.etUsername.error = "请输入账号"
            binding.etUsername.requestFocus()
            return
        }

        // 所有验证通过，弹出路径浏览器
        val dialog = PathBrowserDialog(
            requireContext(),
            host,
            port,
            username.ifEmpty { null },
            password.ifEmpty { null }
        ) { shareName, moviePath ->
            // 用户选择路径后的回调
            val displayPath = if (moviePath.isNullOrEmpty()) {
                shareName
            } else {
                "$shareName/$moviePath"
            }
            Log.i(TAG, "用户选择路径: shareName=$shareName, moviePath=$moviePath, displayPath=$displayPath")
            binding.etMoviePath.setText(displayPath)
        }
        dialog.show()
    }
}
