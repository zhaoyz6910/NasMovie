package com.example.nasmovie.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.nasmovie.util.AppConstants
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 搜索历史管理器
 * 使用 SharedPreferences 存储搜索历史，最多保存10条
 */
class SearchHistoryManager(context: Context) {

    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    /**
     * 获取搜索历史列表（按时间倒序，最新的在前）
     */
    fun getSearchHistory(): List<String> {
        val json = preferences.getString(KEY_HISTORY, null) ?: return emptyList()
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    /**
     * 添加搜索记录
     * 如果已存在，先移除再添加到最前面（去重+置顶）
     * 如果超过10条，移除最旧的
     */
    fun addSearchRecord(keyword: String?) {
        if (keyword.isNullOrBlank()) return

        val trimmedKeyword = keyword.trim()
        val history = getSearchHistory().toMutableList()

        // 如果已存在，先移除
        history.remove(trimmedKeyword)

        // 添加到列表开头（最新的）
        history.add(0, trimmedKeyword)

        // 限制最多10条
        val trimmedHistory = if (history.size > AppConstants.MAX_SEARCH_HISTORY_SIZE) {
            history.take(AppConstants.MAX_SEARCH_HISTORY_SIZE)
        } else {
            history
        }

        // 保存
        saveHistory(trimmedHistory)
    }

    /**
     * 清空搜索历史
     */
    fun clearHistory() {
        preferences.edit().remove(KEY_HISTORY).apply()
    }

    /**
     * 删除单条搜索记录
     */
    fun removeSearchRecord(keyword: String) {
        val history = getSearchHistory().toMutableList()
        history.remove(keyword)
        saveHistory(history)
    }

    /**
     * 检查搜索历史是否为空
     */
    fun isEmpty(): Boolean = getSearchHistory().isEmpty()

    /**
     * 保存历史记录到 SharedPreferences
     */
    private fun saveHistory(history: List<String>) {
        val json = gson.toJson(history)
        preferences.edit().putString(KEY_HISTORY, json).apply()
    }

    companion object {
        private const val PREF_NAME = "search_history_pref"
        private const val KEY_HISTORY = "search_history"
    }
}