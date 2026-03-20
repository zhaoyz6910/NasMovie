package com.example.nasmovie.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.example.nasmovie.R;
import com.example.nasmovie.util.PreferenceManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.List;

/**
 * 主 Activity - 包含三个平级 Fragment
 */
public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNavigation;
    private View rootView;

    private HomeFragment homeFragment;
    private FavoritesFragment favoritesFragment;
    private SettingsFragment settingsFragment;
    private SearchFragment searchFragment;

    private Fragment currentFragment;
    private final List<Fragment> backStack = new ArrayList<>();

    // 双击退出标志
    private long lastBackPressTime = 0;
    private static final long BACK_PRESS_INTERVAL = 2000;

    private PreferenceManager preferenceManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        preferenceManager = new PreferenceManager(this);
        rootView = findViewById(android.R.id.content);

        if (preferenceManager.isLockEnabled()) {
            rootView.setVisibility(View.INVISIBLE);
        }

        initViews();
        setupBackPressedHandler();

        if (savedInstanceState == null) {
            loadHomeFragment();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (preferenceManager.isLockEnabled()) {
            rootView.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (preferenceManager.isLockEnabled()) {
            rootView.postDelayed(() -> rootView.setVisibility(View.VISIBLE), 200);
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
        switchTabFragment(homeFragment, "home");
    }

    private void loadFavoritesFragment() {
        if (favoritesFragment == null) {
            favoritesFragment = new FavoritesFragment();
        }
        switchTabFragment(favoritesFragment, "favorites");
    }

    private void loadSettingsFragment() {
        if (settingsFragment == null) {
            settingsFragment = new SettingsFragment();
        }
        switchTabFragment(settingsFragment, "settings");
    }

    private void switchTabFragment(Fragment fragment, String tag) {
        if (currentFragment == fragment) {
            return;
        }

        backStack.clear();
        bottomNavigation.setVisibility(View.VISIBLE);

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.setCustomAnimations(R.anim.fade_in, R.anim.fade_out);

        if (currentFragment != null) {
            transaction.hide(currentFragment);
        }

        if (!fragment.isAdded()) {
            transaction.add(R.id.fragment_container, fragment, tag);
        }
        transaction.show(fragment);

        transaction.commit();
        currentFragment = fragment;
    }

    public void switchToSettings() {
        bottomNavigation.setSelectedItemId(R.id.nav_settings);
    }

    public void openDetail(String movieId) {
        DetailFragment detailFragment = DetailFragment.newInstance(movieId);
        loadSubFragment(detailFragment, "detail");
    }

    public void openSearch() {
        if (searchFragment == null) {
            searchFragment = new SearchFragment();
        }
        loadSubFragment(searchFragment, "search");
    }

    public void openServerManage() {
        ServerManageFragment serverManageFragment = new ServerManageFragment();
        loadSubFragment(serverManageFragment, "server_manage");
    }

    public void openServerEdit(String serverId) {
        ServerEditFragment serverEditFragment = ServerEditFragment.newInstance(serverId);
        loadSubFragment(serverEditFragment, "server_edit");
    }

    private void loadSubFragment(Fragment fragment, String tag) {
        if (currentFragment == fragment) {
            return;
        }

        backStack.add(currentFragment);
        bottomNavigation.setVisibility(View.GONE);

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.setCustomAnimations(
            R.anim.slide_in_right,
            R.anim.slide_out_left,
            R.anim.slide_in_left,
            R.anim.slide_out_right
        );

        if (currentFragment != null) {
            transaction.hide(currentFragment);
        }

        if (!fragment.isAdded()) {
            transaction.add(R.id.fragment_container, fragment, tag);
        }
        transaction.show(fragment);

        transaction.commit();
        currentFragment = fragment;
    }

    private void setupBackPressedHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (currentFragment instanceof IBackInterceptor) {
                    if (((IBackInterceptor) currentFragment).onBackPressed()) {
                        return;
                    }
                }
                performBack();
            }
        });
    }

    public void performBack() {
        if (!backStack.isEmpty()) {
            Fragment targetFragment = backStack.remove(backStack.size() - 1);

            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right);

            if (currentFragment != null) {
                transaction.hide(currentFragment);
            }
            if (targetFragment != null) {
                transaction.show(targetFragment);
            }
            transaction.commit();
            currentFragment = targetFragment;

            if (targetFragment == homeFragment || targetFragment == favoritesFragment || targetFragment == settingsFragment) {
                bottomNavigation.setVisibility(View.VISIBLE);
            }
        } else {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastBackPressTime < BACK_PRESS_INTERVAL) {
                finish();
            } else {
                Toast.makeText(this, "再按一次退出应用", Toast.LENGTH_SHORT).show();
                lastBackPressTime = currentTime;
            }
        }
    }
    
    // 兼容旧方法名
    public void performRealBack() {
        performBack();
    }

    /**
     * 隐藏底部导航栏（用于子页面横竖屏切换）
     */
    public void hideBottomNavigation() {
        if (bottomNavigation != null) {
            bottomNavigation.setVisibility(View.GONE);
        }
    }

    /**
     * 通知 Fragment 的 view 已被重新 inflate
     * 确保 Fragment 的显示状态正确
     */
    public void onFragmentViewReplaced(Fragment fragment) {
        if (currentFragment == fragment) {
            // 使用 detach/attach 重新初始化 Fragment 的状态
            getSupportFragmentManager().beginTransaction()
                .detach(fragment)
                .attach(fragment)
                .commit();
        }
    }
}