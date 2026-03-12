package com.example.nasmovie.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;

import com.example.nasmovie.R;

/**
 * 自定义 Toolbar 组件
 * 支持居中标题和可选返回按钮
 */
public class NasToolbar extends FrameLayout {

    private ImageView btnBack;
    private TextView tvTitle;
    private Toolbar toolbar;
    private View divider;

    private OnBackClickListener backClickListener;

    public interface OnBackClickListener {
        void onBackClick();
    }

    public NasToolbar(Context context) {
        super(context);
        init(context, null);
    }

    public NasToolbar(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public NasToolbar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, @Nullable AttributeSet attrs) {
        LayoutInflater.from(context).inflate(R.layout.view_nas_toolbar, this, true);

        View root = getChildAt(0);
        toolbar = root.findViewById(R.id.inner_toolbar);
        btnBack = root.findViewById(R.id.btn_back);
        tvTitle = root.findViewById(R.id.tv_toolbar_title);
        divider = root.findViewById(R.id.divider);

        // 读取自定义属性
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.NasToolbar);
            String title = a.getString(R.styleable.NasToolbar_titleText);
            boolean showBack = a.getBoolean(R.styleable.NasToolbar_showBack, false);

            if (title != null) {
                tvTitle.setText(title);
            }
            btnBack.setVisibility(showBack ? VISIBLE : GONE);

            a.recycle();
        }

        btnBack.setOnClickListener(v -> {
            if (backClickListener != null) {
                backClickListener.onBackClick();
            }
        });
    }

    /**
     * 设置标题
     */
    public void setTitle(String title) {
        tvTitle.setText(title);
    }

    /**
     * 设置标题
     */
    public void setTitle(int resId) {
        tvTitle.setText(resId);
    }

    /**
     * 显示返回按钮
     */
    public void setShowBack(boolean show) {
        btnBack.setVisibility(show ? VISIBLE : GONE);
    }

    /**
     * 设置返回按钮点击监听
     */
    public void setOnBackClickListener(OnBackClickListener listener) {
        this.backClickListener = listener;
    }

    /**
     * 获取标题 TextView
     */
    public TextView getTitleTextView() {
        return tvTitle;
    }

    /**
     * 获取返回按钮 ImageView
     */
    public ImageView getBackButton() {
        return btnBack;
    }

    /**
     * 获取内部 Toolbar
     */
    public Toolbar getToolbar() {
        return toolbar;
    }

    /**
     * 设置分割线可见性
     */
    public void showDivider(boolean show) {
        divider.setVisibility(show ? VISIBLE : GONE);
    }
}
