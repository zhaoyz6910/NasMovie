package com.example.nasmovie.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import com.example.nasmovie.R

/**
 * 自定义 Toolbar 组件
 * 支持居中标题和可选返回按钮
 */
class NasToolbar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val btnBack: ImageView
    private val tvTitle: TextView
    val toolbar: Toolbar
    private val divider: View

    private var backClickListener: (() -> Unit)? = null

    fun interface OnBackClickListener {
        fun onBackClick()
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.view_nas_toolbar, this, true)

        val root = getChildAt(0)
        toolbar = root.findViewById(R.id.inner_toolbar)
        btnBack = root.findViewById(R.id.btn_back)
        tvTitle = root.findViewById(R.id.tv_toolbar_title)
        divider = root.findViewById(R.id.divider)

        // 读取自定义属性
        attrs?.let {
            val a = context.obtainStyledAttributes(attrs, R.styleable.NasToolbar)
            val title = a.getString(R.styleable.NasToolbar_titleText)
            val showBack = a.getBoolean(R.styleable.NasToolbar_showBack, false)

            title?.let { tvTitle.text = it }
            btnBack.visibility = if (showBack) VISIBLE else GONE

            a.recycle()
        }

        btnBack.setOnClickListener {
            backClickListener?.invoke()
        }
    }

    /**
     * 设置标题
     */
    fun setTitle(title: String?) {
        tvTitle.text = title
    }

    /**
     * 设置标题
     */
    fun setTitle(resId: Int) {
        tvTitle.setText(resId)
    }

    /**
     * 显示返回按钮
     */
    fun setShowBack(show: Boolean) {
        btnBack.visibility = if (show) VISIBLE else GONE
    }

    /**
     * 设置返回按钮点击监听
     */
    fun setOnBackClickListener(listener: (() -> Unit)?) {
        this.backClickListener = listener
    }

    /**
     * 设置返回按钮点击监听 (兼容接口方式)
     */
    fun setOnBackClickListener(listener: OnBackClickListener?) {
        this.backClickListener = listener?.let { { it.onBackClick() } }
    }

    /**
     * 获取标题 TextView
     */
    fun getTitleTextView(): TextView = tvTitle

    /**
     * 获取返回按钮 ImageView
     */
    fun getBackButton(): ImageView = btnBack

    /**
     * 设置分割线可见性
     */
    fun showDivider(show: Boolean) {
        divider.visibility = if (show) VISIBLE else GONE
    }
}