package com.example.nasmovie.view

import android.animation.ValueAnimator
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.example.nasmovie.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * 通用底部抽屉组件
 * 支持展开状态下继续向上拖动时的拉伸缩放效果
 */
class BottomSheetDrawer : BottomSheetDialogFragment() {

    companion object {
        private const val TAG = "BottomSheetDrawer"

        fun newInstance(): BottomSheetDrawer = BottomSheetDrawer()
    }

    private val items = mutableListOf<MenuItem>()
    private var contentView: View? = null
    private var bottomSheetView: View? = null

    // 拉伸效果相关
    private val scaleRatio = 0.4f       // 拉伸系数
    private val maxScale = 1.15f        // 最大缩放倍数
    private val replyRatio = 0.3f       // 回弹时间系数

    private var lastTouchY = 0f
    private var stretchStartY = 0f      // 开始拉伸时的 Y 坐标
    private var isStretching = false
    private var currentScale = 1.0f
    private var originalHeight = 0f
    private var originalBottomSheetHeight = 0f   // bottomSheet 原始高度
    private var initialTopPosition = -1          // bottomSheet 初始顶部位置

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.Theme_NASMovie_BottomSheetDialog)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // 创建自定义 BottomSheetDialog 以拦截触摸事件
        return StretchBottomSheetDialog(requireContext(), theme)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        contentView = inflater.inflate(R.layout.sheet_bottom_drawer, container, false)
        return contentView
    }

    /**
     * 自定义 BottomSheetDialog，重写 dispatchTouchEvent 来拦截触摸事件
     */
    private inner class StretchBottomSheetDialog(
        context: Context,
        theme: Int
    ) : BottomSheetDialog(context, theme) {

        override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
            // 先让拉伸处理逻辑处理
            if (handleStretchTouch(ev)) {
                return true  // 事件被消费
            }
            return super.dispatchTouchEvent(ev)
        }
    }

    override fun onStart() {
        super.onStart()

        // 获取 BottomSheet 的根视图
        val parent = contentView?.parent as? View
        if (parent != null) {
            bottomSheetView = parent

            // 设置不裁剪子View，让拉伸内容可以超出边界显示
            (bottomSheetView as? ViewGroup)?.clipChildren = false
            (contentView as? ViewGroup)?.clipChildren = false
            (bottomSheetView?.parent as? ViewGroup)?.let { grandParent ->
                grandParent.clipChildren = false
                grandParent.clipToPadding = false
            }

            // 记录原始尺寸
            bottomSheetView?.post {
                bottomSheetView?.let {
                    originalHeight = it.height.toFloat()
                    originalBottomSheetHeight = it.height.toFloat()
                }
            }
        }

        // 设置 skipCollapsed 并强制展开
        (dialog as? BottomSheetDialog)?.let { dialog ->
            dialog.behavior.skipCollapsed = true
            dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED

            // 在平板设备上设置宽度为屏幕宽度
            bottomSheetView?.let { sheet ->
                sheet.layoutParams.width = getScreenWidth()
            }
        }
    }

    /**
     * 获取屏幕宽度，兼容不同 Android 版本
     */
    @Suppress("DEPRECATION")
    private fun getScreenWidth(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 使用 WindowMetrics
            val windowMetrics = requireContext()
                .getSystemService(android.view.WindowManager::class.java)
                .currentWindowMetrics
            windowMetrics.bounds.width()
        } else {
            // Android 10 及以下使用旧 API
            val metrics = android.util.DisplayMetrics()
            val windowManager = requireContext()
                .getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager
            windowManager?.defaultDisplay?.getMetrics(metrics)
            metrics.widthPixels
        }
    }

    private fun handleStretchTouch(event: MotionEvent): Boolean {
        // 获取 bottomSheet 当前位置
        val location = IntArray(2)
        bottomSheetView?.getLocationOnScreen(location)
        val topPosition = location[1]

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchY = event.rawY
                initialTopPosition = topPosition
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                val deltaY = lastTouchY - event.rawY  // 向上为正

                if (isStretching) {
                    // 已经在拉伸状态，继续处理拉伸
                    val totalDeltaY = stretchStartY - event.rawY  // 向上为正
                    if (totalDeltaY > 0) {
                        val stretchDistance = totalDeltaY * scaleRatio
                        applyScale(stretchDistance)
                    } else if (totalDeltaY < -10) {
                        // 向下拖动超过阈值，取消拉伸状态
                        contentView?.scaleY = 1.0f
                        currentScale = 1.0f
                        isStretching = false
                    } else {
                        contentView?.scaleY = 1.0f
                        currentScale = 1.0f
                    }
                    lastTouchY = event.rawY
                    return true
                }

                // 检测是否可以触发拉伸：
                // 用户向上拖动，但 topPosition 没有变小（说明抽屉已经无法继续向上移动）
                if (deltaY > 10 && topPosition >= initialTopPosition) {
                    isStretching = true
                    stretchStartY = lastTouchY
                    lastTouchY = event.rawY
                    return true
                }

                // 正常拖动：更新位置
                initialTopPosition = topPosition
                lastTouchY = event.rawY
                return false
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isStretching) {
                    isStretching = false
                    animateRebound()
                    return true
                }
            }
        }
        return false
    }

    /**
     * 应用缩放效果
     */
    private fun applyScale(stretchDistance: Float) {
        var newScale = 1.0f + (stretchDistance / originalHeight)

        // 限制最大缩放倍数
        newScale = newScale.coerceAtMost(maxScale)
        newScale = newScale.coerceAtLeast(1.0f)

        // 设置缩放锚点为底部，使缩放时内容向上延伸
        contentView?.let { view ->
            view.pivotY = view.height.toFloat()
            currentScale = newScale
            view.scaleY = newScale
        }
    }

    /**
     * 回弹动画
     */
    private fun animateRebound() {
        val startScale = currentScale
        val animator = ValueAnimator.ofFloat(startScale, 1.0f)
        animator.duration = ((startScale - 1.0f) * 500 / replyRatio).toLong()
        animator.addUpdateListener { animation ->
            val scale = animation.animatedValue as Float
            contentView?.scaleY = scale
            currentScale = scale
        }
        animator.start()
        currentScale = 1.0f
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val container = view.findViewById<LinearLayout>(R.id.itemsContainer)

        for (item in items) {
            val itemView = LayoutInflater.from(context)
                .inflate(R.layout.item_bottom_drawer, container, false)

            val textView = itemView.findViewById<TextView>(R.id.itemText)
            textView.text = item.title

            if (item.isDestructive) {
                textView.setTextColor(Color.parseColor("#ff716c"))
            }

            itemView.setOnClickListener {
                dismiss()
                item.clickListener?.invoke()
            }

            container.addView(itemView)
        }
    }

    fun addItem(title: String, listener: (() -> Unit)?): BottomSheetDrawer {
        items.add(MenuItem(title, false, listener))
        return this
    }

    fun addDestructiveItem(title: String, listener: (() -> Unit)?): BottomSheetDrawer {
        items.add(MenuItem(title, true, listener))
        return this
    }

    fun interface OnItemClickListener {
        fun onClick()
    }

    private data class MenuItem(
        val title: String,
        val isDestructive: Boolean,
        val clickListener: (() -> Unit)?
    )

    class Builder {
        private val items = mutableListOf<MenuItem>()

        fun addItem(title: String, listener: (() -> Unit)?): Builder {
            items.add(MenuItem(title, false, listener))
            return this
        }

        fun addDestructiveItem(title: String, listener: (() -> Unit)?): Builder {
            items.add(MenuItem(title, true, listener))
            return this
        }

        fun build(): BottomSheetDrawer {
            return BottomSheetDrawer().apply {
                this.items.addAll(this@Builder.items)
            }
        }
    }
}