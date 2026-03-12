package com.example.nasmovie.ui;

/**
 * Fragment 返回拦截接口
 */
public interface IBackInterceptor {
    /**
     * 是否拦截返回事件
     * @return true 拦截, false 不拦截
     */
    boolean onBackPressed();
}