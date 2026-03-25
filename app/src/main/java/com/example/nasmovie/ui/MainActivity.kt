package com.example.nasmovie.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import com.example.nasmovie.R
import com.example.nasmovie.util.PreferenceManager
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * 主 Activity - 包含三个平级 Fragment
 */
class MainActivity : AppCompatActivity() {

    private lateinit var bottomNavigation: BottomNavigationView
    private var rootView: View? = null

    private var homeFragment: HomeFragment? = null
    private var favoritesFragment: FavoritesFragment? = null
    private var settingsFragment: SettingsFragment? = null
    private var searchFragment: SearchFragment? = null

    private var currentFragment: Fragment? = null
    private val backStack = mutableListOf<Fragment>()

    // 双击退出标志
    private var lastBackPressTime = 0L
    // 双击退出间隔时间（毫秒）
    // 设为 2 秒是 Android 应用的标准交互体验
    private val backPressInterval = 2000L

    private lateinit var preferenceManager: PreferenceManager

    companion object {
        private const val KEY_CURRENT_FRAGMENT_TAG = "current_fragment_tag"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        preferenceManager = PreferenceManager(this)
        rootView = findViewById(android.R.id.content)

        if (preferenceManager.isLockEnabled) {
            rootView?.visibility = View.INVISIBLE
        }

        initViews()
        setupBackPressedHandler()

        // 恢复 Fragment 状态
        if (savedInstanceState != null) {
            val currentTag = savedInstanceState.getString(KEY_CURRENT_FRAGMENT_TAG)
            if (currentTag != null) {
                val fragment = supportFragmentManager.findFragmentByTag(currentTag)
                if (fragment != null) {
                    currentFragment = fragment
                    // 恢复 backStack
                    for (i in 1..savedInstanceState.getInt("back_stack_size", 0)) {
                        savedInstanceState.getString("back_stack_$i")?.let { tag ->
                            supportFragmentManager.findFragmentByTag(tag)?.let { stackFragment ->
                                backStack.add(stackFragment)
                            }
                        }
                    }
                    // 如果当前不是主 Fragment，隐藏底部导航栏
                    if (currentTag !in listOf("home", "favorites", "settings")) {
                        bottomNavigation.visibility = View.GONE
                    }
                } else {
                    loadHomeFragment()
                }
            } else {
                loadHomeFragment()
            }
        } else {
            loadHomeFragment()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        // 保存当前 Fragment
        currentFragment?.let { fragment ->
            getFragmentTag(fragment)?.let { tag ->
                outState.putString(KEY_CURRENT_FRAGMENT_TAG, tag)
            }
        }

        // 保存 backStack
        outState.putInt("back_stack_size", backStack.size)
        backStack.forEachIndexed { index, fragment ->
            getFragmentTag(fragment)?.let { tag ->
                outState.putString("back_stack_${index + 1}", tag)
            }
        }
    }

    private fun getFragmentTag(fragment: Fragment?): String? {
        if (fragment == null) return null

        // 先尝试获取 Fragment 的实际 tag
        fragment.tag?.let { return it }

        // 如果没有 tag，根据 Fragment 类型返回默认 tag
        return when (fragment) {
            is HomeFragment -> "home"
            is FavoritesFragment -> "favorites"
            is SettingsFragment -> "settings"
            is SearchFragment -> "search"
            is DetailFragment -> "detail"
            is ServerManageFragment -> "server_manage"
            is ServerEditFragment -> "server_edit"
            else -> null
        }
    }

    override fun onPause() {
        super.onPause()
        if (preferenceManager.isLockEnabled) {
            rootView?.visibility = View.INVISIBLE
        }
    }

    override fun onResume() {
        super.onResume()
        if (preferenceManager.isLockEnabled) {
            rootView?.postDelayed({ rootView?.visibility = View.VISIBLE }, 200)
        }
    }

    private fun initViews() {
        bottomNavigation = findViewById(R.id.bottom_navigation)

        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    loadHomeFragment()
                    true
                }
                R.id.nav_favorites -> {
                    loadFavoritesFragment()
                    true
                }
                R.id.nav_settings -> {
                    loadSettingsFragment()
                    true
                }
                else -> false
            }
        }
    }

    private fun loadHomeFragment() {
        if (homeFragment == null) {
            homeFragment = HomeFragment()
        }
        switchTabFragment(homeFragment!!, "home")
    }

    private fun loadFavoritesFragment() {
        if (favoritesFragment == null) {
            favoritesFragment = FavoritesFragment()
        }
        switchTabFragment(favoritesFragment!!, "favorites")
    }

    private fun loadSettingsFragment() {
        if (settingsFragment == null) {
            settingsFragment = SettingsFragment()
        }
        switchTabFragment(settingsFragment!!, "settings")
    }

    private fun switchTabFragment(fragment: Fragment, tag: String) {
        if (currentFragment == fragment) {
            return
        }

        backStack.clear()
        bottomNavigation.visibility = View.VISIBLE

        val transaction = supportFragmentManager.beginTransaction()
        transaction.setCustomAnimations(R.anim.fade_in, R.anim.fade_out)

        currentFragment?.let {
            transaction.hide(it)
        }

        if (!fragment.isAdded) {
            transaction.add(R.id.fragment_container, fragment, tag)
        }
        transaction.show(fragment)

        transaction.commit()
        currentFragment = fragment
    }

    fun switchToSettings() {
        bottomNavigation.selectedItemId = R.id.nav_settings
    }

    fun openDetail(movieId: String) {
        val detailFragment = DetailFragment.newInstance(movieId)
        loadSubFragment(detailFragment, "detail")
    }

    fun openSearch() {
        if (searchFragment == null) {
            searchFragment = SearchFragment()
        }
        loadSubFragment(searchFragment!!, "search")
    }

    fun openServerManage() {
        val serverManageFragment = ServerManageFragment()
        loadSubFragment(serverManageFragment, "server_manage")
    }

    fun openServerEdit(serverId: String?) {
        val serverEditFragment = ServerEditFragment.newInstance(serverId)
        loadSubFragment(serverEditFragment, "server_edit")
    }

    private fun loadSubFragment(fragment: Fragment, tag: String) {
        if (currentFragment == fragment) {
            return
        }

        currentFragment?.let { backStack.add(it) }
        bottomNavigation.visibility = View.GONE

        val transaction = supportFragmentManager.beginTransaction()
        transaction.setCustomAnimations(
            R.anim.slide_in_right,
            R.anim.slide_out_left,
            R.anim.slide_in_left,
            R.anim.slide_out_right
        )

        currentFragment?.let {
            transaction.hide(it)
        }

        if (!fragment.isAdded) {
            transaction.add(R.id.fragment_container, fragment, tag)
        }
        transaction.show(fragment)

        transaction.commit()
        currentFragment = fragment
    }

    private fun setupBackPressedHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentFragment is IBackInterceptor) {
                    if ((currentFragment as IBackInterceptor).onBackPressed()) {
                        return
                    }
                }
                performBack()
            }
        })
    }

    fun performBack() {
        if (backStack.isNotEmpty()) {
            val targetFragment = backStack.removeAt(backStack.size - 1)

            val transaction = supportFragmentManager.beginTransaction()
            transaction.setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right)

            currentFragment?.let {
                transaction.hide(it)
            }
            targetFragment?.let {
                transaction.show(it)
            }
            transaction.commit()
            currentFragment = targetFragment

            // 使用 tag 判断而不是实例比较，因为横竖屏切换后 fragment 实例会重建
            val targetTag = getFragmentTag(targetFragment)
            if (targetTag in listOf("home", "favorites", "settings")) {
                bottomNavigation.visibility = View.VISIBLE
            }
        } else {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastBackPressTime < backPressInterval) {
                finish()
            } else {
                Toast.makeText(this, "再按一次退出应用", Toast.LENGTH_SHORT).show()
                lastBackPressTime = currentTime
            }
        }
    }

    // 兼容旧方法名
    fun performRealBack() {
        performBack()
    }

    /**
     * 隐藏底部导航栏（用于子页面横竖屏切换）
     */
    fun hideBottomNavigation() {
        bottomNavigation.visibility = View.GONE
    }
}