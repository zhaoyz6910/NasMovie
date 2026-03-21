package com.example.nasmovie.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
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
    // 双击退出间隔时间（毫秒）
    // 设为 2 秒是 Android 应用的标准交互体验
    private static final long BACK_PRESS_INTERVAL = 2000;

    private PreferenceManager preferenceManager;
    
    private static final String KEY_CURRENT_FRAGMENT_TAG = "current_fragment_tag";

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

        // 恢复 Fragment 状态
        if (savedInstanceState != null) {
            String currentTag = savedInstanceState.getString(KEY_CURRENT_FRAGMENT_TAG);
            if (currentTag != null) {
                Fragment fragment = getSupportFragmentManager().findFragmentByTag(currentTag);
                if (fragment != null) {
                    currentFragment = fragment;
                    // 恢复 backStack
                    for (int i = 1; i <= savedInstanceState.getInt("back_stack_size", 0); i++) {
                        String tag = savedInstanceState.getString("back_stack_" + i);
                        if (tag != null) {
                            Fragment stackFragment = getSupportFragmentManager().findFragmentByTag(tag);
                            if (stackFragment != null) {
                                backStack.add(stackFragment);
                            }
                        }
                    }
                    // 如果当前不是主 Fragment，隐藏底部导航栏
                    if (currentTag != null && !currentTag.equals("home") && !currentTag.equals("favorites") && !currentTag.equals("settings")) {
                        bottomNavigation.setVisibility(View.GONE);
                    }
                } else {
                    loadHomeFragment();
                }
            } else {
                loadHomeFragment();
            }
        } else {
            loadHomeFragment();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        
        // 保存当前 Fragment
        if (currentFragment != null) {
            String tag = getFragmentTag(currentFragment);
            if (tag != null) {
                outState.putString(KEY_CURRENT_FRAGMENT_TAG, tag);
            }
        }
        
        // 保存 backStack
        outState.putInt("back_stack_size", backStack.size());
        for (int i = 0; i < backStack.size(); i++) {
            String tag = getFragmentTag(backStack.get(i));
            if (tag != null) {
                outState.putString("back_stack_" + (i + 1), tag);
            }
        }
    }

    private String getFragmentTag(Fragment fragment) {
    if (fragment == null) return null;
    
    // 先尝试获取 Fragment 的实际 tag
    String tag = fragment.getTag();
    if (tag != null) return tag;
    
    // 如果没有 tag，根据 Fragment 类型返回默认 tag
    if (fragment instanceof HomeFragment) return "home";
    if (fragment instanceof FavoritesFragment) return "favorites";
    if (fragment instanceof SettingsFragment) return "settings";
    if (fragment instanceof SearchFragment) return "search";
    if (fragment instanceof DetailFragment) return "detail";
    if (fragment instanceof ServerManageFragment) return "server_manage";
    if (fragment instanceof ServerEditFragment) return "server_edit";
    
    return null;
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

            // 使用 tag 判断而不是实例比较，因为横竖屏切换后 fragment 实例会重建
            String targetTag = getFragmentTag(targetFragment);
            if ("home".equals(targetTag) || "favorites".equals(targetTag) || "settings".equals(targetTag)) {
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
}