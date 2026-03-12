package com.example.nasmovie.ui;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.example.nasmovie.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.List;

/**
 * 主 Activity - 包含三个平级 Fragment
 */
public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNavigation;

    private HomeFragment homeFragment;
    private FavoritesFragment favoritesFragment;
    private SettingsFragment settingsFragment;

    private Fragment currentFragment;
    private final List<Fragment> backStack = new ArrayList<>();

    // 双击退出标志
    private long lastBackPressTime = 0;
    private static final long BACK_PRESS_INTERVAL = 2000; // 2 秒内再次按返回键退出

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();

        // 如果是第一次启动，加载首页 Fragment
        if (savedInstanceState == null) {
            loadHomeFragment();
        }
    }

    private void initViews() {
        bottomNavigation = findViewById(R.id.bottom_navigation);

        bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                loadHomeFragment();
                return true;
            } else if (itemId == R.id.nav_favorites) {
                loadFavoritesFragment();
                return true;
            } else if (itemId == R.id.nav_settings) {
                loadSettingsFragment();
                return true;
            }
            return false;
        });
    }

    private void loadHomeFragment() {
        if (homeFragment == null) {
            homeFragment = new HomeFragment();
        }
        loadFragment(homeFragment, "home", false);
    }

    private void loadFavoritesFragment() {
        if (favoritesFragment == null) {
            favoritesFragment = new FavoritesFragment();
        }
        loadFragment(favoritesFragment, "favorites", false);
    }

    private void loadSettingsFragment() {
        if (settingsFragment == null) {
            settingsFragment = new SettingsFragment();
        }
        loadFragment(settingsFragment, "settings", false);
    }

    public void switchToSettings() {
        bottomNavigation.setSelectedItemId(R.id.nav_settings);
    }

    public void openDetail(String movieId) {
        DetailFragment detailFragment = DetailFragment.newInstance(movieId);
        loadFragment(detailFragment, "detail", true);
    }

    public void openSearch() {
        SearchFragment searchFragment = new SearchFragment();
        loadFragment(searchFragment, "search", true);
    }

    public void openServerManage() {
        ServerManageFragment serverManageFragment = new ServerManageFragment();
        loadFragment(serverManageFragment, "server_manage", true);
    }

    public void openServerEdit(String serverId) {
        ServerEditFragment serverEditFragment = ServerEditFragment.newInstance(serverId);
        loadFragment(serverEditFragment, "server_edit", true);
    }

    public void scanMedia() {
        if (homeFragment == null) {
            homeFragment = new HomeFragment();
        }
        loadFragment(homeFragment, "home", false);
    }

    private void loadFragment(Fragment fragment, String tag, boolean addToBack) {
        if (currentFragment == fragment) {
            return;
        }

        if (addToBack) {
            // 保存当前 Fragment 作为回退目标
            backStack.add(currentFragment);
            // 隐藏底部导航
            bottomNavigation.setVisibility(android.view.View.GONE);
        } else {
            backStack.clear();
            // 显示底部导航
            bottomNavigation.setVisibility(android.view.View.VISIBLE);
        }

        getSupportFragmentManager()
            .beginTransaction()
            .replace(R.id.fragment_container, fragment, tag)
            .commit();

        currentFragment = fragment;
    }

    @Override
    public void onBackPressed() {
        // 首先尝试让当前 Fragment 拦截返回事件
        if (currentFragment instanceof IBackInterceptor) {
            if (((IBackInterceptor) currentFragment).onBackPressed()) {
                return; // 被拦截了
            }
        }
        performRealBack();
    }

    /**
     * 执行真正的返回逻辑（跳过拦截器，供 Fragment 在确认后调用）
     */
    public void performRealBack() {
        if (!backStack.isEmpty()) {
            // 先获取要回退到的 Fragment（栈顶元素）
            Fragment targetFragment = backStack.get(backStack.size() - 1);

            // 移除栈顶元素
            backStack.remove(backStack.size() - 1);

            // 回退到目标 Fragment
            getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, targetFragment)
                .commit();
            currentFragment = targetFragment;

            // 如果回退到的是三个主 Tab 之一，显示底部导航
            if (targetFragment == homeFragment || targetFragment == favoritesFragment || targetFragment == settingsFragment) {
                bottomNavigation.setVisibility(android.view.View.VISIBLE);
            }
        } else {
            // 回退栈为空，说明在三个主 Tab 页面（首页、收藏、设置）
            // 使用双击退出逻辑
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastBackPressTime < BACK_PRESS_INTERVAL) {
                // 2 秒内再次按返回键，退出应用
                super.onBackPressed();
            } else {
                // 第一次按返回键，显示提示
                Toast.makeText(this, "再按一次退出应用", Toast.LENGTH_SHORT).show();
                lastBackPressTime = currentTime;
            }
        }
    }
}
