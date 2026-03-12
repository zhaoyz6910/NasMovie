package com.example.nasmovie.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * 搜索历史管理器
 * 使用 SharedPreferences 存储搜索历史，最多保存10条
 */
public class SearchHistoryManager {

    private static final String PREF_NAME = "search_history_pref";
    private static final String KEY_HISTORY = "search_history";
    private static final int MAX_HISTORY_SIZE = 10;

    private final SharedPreferences preferences;
    private final Gson gson;

    public SearchHistoryManager(Context context) {
        preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
    }

    /**
     * 获取搜索历史列表（按时间倒序，最新的在前）
     */
    public List<String> getSearchHistory() {
        String json = preferences.getString(KEY_HISTORY, null);
        if (json == null) {
            return new ArrayList<>();
        }
        Type type = new TypeToken<List<String>>() {}.getType();
        List<String> history = gson.fromJson(json, type);
        return history != null ? history : new ArrayList<>();
    }

    /**
     * 添加搜索记录
     * 如果已存在，先移除再添加到最前面（去重+置顶）
     * 如果超过10条，移除最旧的
     */
    public void addSearchRecord(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return;
        }

        String trimmedKeyword = keyword.trim();
        List<String> history = getSearchHistory();

        // 如果已存在，先移除
        history.remove(trimmedKeyword);

        // 添加到列表开头（最新的）
        history.add(0, trimmedKeyword);

        // 限制最多10条
        if (history.size() > MAX_HISTORY_SIZE) {
            history = history.subList(0, MAX_HISTORY_SIZE);
        }

        // 保存
        saveHistory(history);
    }

    /**
     * 清空搜索历史
     */
    public void clearHistory() {
        preferences.edit().remove(KEY_HISTORY).apply();
    }

    /**
     * 删除单条搜索记录
     */
    public void removeSearchRecord(String keyword) {
        List<String> history = getSearchHistory();
        history.remove(keyword);
        saveHistory(history);
    }

    /**
     * 检查搜索历史是否为空
     */
    public boolean isEmpty() {
        return getSearchHistory().isEmpty();
    }

    /**
     * 保存历史记录到 SharedPreferences
     */
    private void saveHistory(List<String> history) {
        String json = gson.toJson(history);
        preferences.edit().putString(KEY_HISTORY, json).apply();
    }
}
