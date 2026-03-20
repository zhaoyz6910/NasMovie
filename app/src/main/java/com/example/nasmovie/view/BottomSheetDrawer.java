package com.example.nasmovie.view;

import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.nasmovie.R;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;

/**
 * 通用底部抽屉组件
 * 支持展开状态下继续向上拖动时的拉伸缩放效果
 */
public class BottomSheetDrawer extends BottomSheetDialogFragment {

    private static final String TAG = "BottomSheetDrawer";

    private final List<MenuItem> items = new ArrayList<>();
    private View contentView;
    private View bottomSheetView;

    // 拉伸效果相关
    private static final float SCALE_RATIO = 0.4f;      // 拉伸系数
    private static final float MAX_SCALE = 1.15f;       // 最大缩放倍数
    private static final float REPLY_RATIO = 0.3f;      // 回弹时间系数

    private float lastTouchY;
    private float stretchStartY;  // 开始拉伸时的 Y 坐标
    private boolean isStretching = false;
    private float currentScale = 1.0f;
    private float originalHeight;
    private float originalBottomSheetHeight;  // bottomSheet 原始高度
    private int initialTopPosition = -1;  // bottomSheet 初始顶部位置

    public static BottomSheetDrawer newInstance() {
        return new BottomSheetDrawer();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NORMAL, R.style.Theme_NASMovie_BottomSheetDialog);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        // 创建自定义 BottomSheetDialog 以拦截触摸事件
        return new StretchBottomSheetDialog(requireContext(), getTheme());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        contentView = inflater.inflate(R.layout.sheet_bottom_drawer, container, false);
        return contentView;
    }
    
    /**
     * 自定义 BottomSheetDialog，重写 dispatchTouchEvent 来拦截触摸事件
     */
    private class StretchBottomSheetDialog extends BottomSheetDialog {
        
        public StretchBottomSheetDialog(@NonNull Context context, int theme) {
            super(context, theme);
        }
        
        @Override
        public boolean dispatchTouchEvent(@NonNull MotionEvent ev) {
            // 先让拉伸处理逻辑处理
            if (handleStretchTouch(ev)) {
                return true;  // 事件被消费
            }
            return super.dispatchTouchEvent(ev);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        
        // 获取 BottomSheet 的根视图
        View parent = (View) contentView.getParent();
        if (parent != null) {
            bottomSheetView = parent;
            
            // 设置不裁剪子View，让拉伸内容可以超出边界显示
            if (bottomSheetView instanceof ViewGroup) {
                ((ViewGroup) bottomSheetView).setClipChildren(false);
            }
            // LinearLayout 也是 ViewGroup
            if (contentView instanceof ViewGroup) {
                ((ViewGroup) contentView).setClipChildren(false);
            }
            if (bottomSheetView.getParent() instanceof ViewGroup) {
                ViewGroup grandParent = (ViewGroup) bottomSheetView.getParent();
                grandParent.setClipChildren(false);
                grandParent.setClipToPadding(false);
            }
            
            // 记录原始尺寸
            bottomSheetView.post(() -> {
                originalHeight = bottomSheetView.getHeight();
                originalBottomSheetHeight = bottomSheetView.getHeight();
            });
        }
        
        // 设置 skipCollapsed 并强制展开
        BottomSheetDialog dialog = (BottomSheetDialog) getDialog();
        if (dialog != null) {
            dialog.getBehavior().setSkipCollapsed(true);
            dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
            
            // 在平板设备上设置宽度为屏幕宽度
            if (bottomSheetView != null) {
                android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
                dialog.getWindow().getWindowManager().getDefaultDisplay().getMetrics(metrics);
                int screenWidth = metrics.widthPixels;
                bottomSheetView.getLayoutParams().width = screenWidth;
            }
        }
    }

    private boolean handleStretchTouch(MotionEvent event) {
        // 获取 bottomSheet 当前位置
        int[] location = new int[2];
        if (bottomSheetView != null) {
            bottomSheetView.getLocationOnScreen(location);
        }
        int topPosition = location[1];
        
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchY = event.getRawY();
                initialTopPosition = topPosition;
                return false;

            case MotionEvent.ACTION_MOVE:
                float deltaY = lastTouchY - event.getRawY();  // 向上为正
                
                if (isStretching) {
                    // 已经在拉伸状态，继续处理拉伸
                    float totalDeltaY = stretchStartY - event.getRawY();  // 向上为正
                    if (totalDeltaY > 0) {
                        float stretchDistance = totalDeltaY * SCALE_RATIO;
                        applyScale(stretchDistance);
                    } else if (totalDeltaY < -10) {
                        // 向下拖动超过阈值，取消拉伸状态
                        contentView.setScaleY(1.0f);
                        currentScale = 1.0f;
                        isStretching = false;
                    } else {
                        contentView.setScaleY(1.0f);
                        currentScale = 1.0f;
                    }
                    lastTouchY = event.getRawY();
                    return true;
                }
                
                // 检测是否可以触发拉伸：
                // 用户向上拖动，但 topPosition 没有变小（说明抽屉已经无法继续向上移动）
                if (deltaY > 10 && topPosition >= initialTopPosition) {
                    isStretching = true;
                    stretchStartY = lastTouchY;
                    lastTouchY = event.getRawY();
                    return true;
                }
                
                // 正常拖动：更新位置
                initialTopPosition = topPosition;
                lastTouchY = event.getRawY();
                return false;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isStretching) {
                    isStretching = false;
                    animateRebound();
                    return true;
                }
                break;
        }
        return false;
    }

    /**
     * 重置 bottomSheet 位置
     */
    private void resetBottomSheetHeight() {
        // 不需要重置，因为不再移动 bottomSheet
    }

    /**
     * 应用缩放效果
     */
    private void applyScale(float stretchDistance) {
        float newScale = 1.0f + (stretchDistance / originalHeight);

        // 限制最大缩放倍数
        newScale = Math.min(newScale, MAX_SCALE);
        newScale = Math.max(newScale, 1.0f);

        // 设置缩放锚点为底部，使缩放时内容向上延伸
        contentView.setPivotY(contentView.getHeight());
        
        currentScale = newScale;
        contentView.setScaleY(newScale);
        
        // 不再移动 bottomSheet，依靠 clipChildren=false 让内容超出边界显示
    }

    /**
     * 回弹动画
     */
    private void animateRebound() {
        float startScale = currentScale;
        ValueAnimator animator = ValueAnimator.ofFloat(startScale, 1.0f);
        animator.setDuration((long) ((startScale - 1.0f) * 500 / REPLY_RATIO));
        animator.addUpdateListener(animation -> {
            float scale = (float) animation.getAnimatedValue();
            contentView.setScaleY(scale);
            currentScale = scale;
        });
        animator.start();
        currentScale = 1.0f;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        LinearLayout container = view.findViewById(R.id.itemsContainer);

        for (int i = 0; i < items.size(); i++) {
            MenuItem item = items.get(i);
            View itemView = LayoutInflater.from(getContext())
                    .inflate(R.layout.item_bottom_drawer, container, false);

            TextView textView = itemView.findViewById(R.id.itemText);
            textView.setText(item.title);

            if (item.isDestructive) {
                textView.setTextColor(Color.parseColor("#ff716c"));
            }

            itemView.setOnClickListener(v -> {
                dismiss();
                if (item.clickListener != null) {
                    item.clickListener.onClick();
                }
            });

            container.addView(itemView);
        }
    }

    public BottomSheetDrawer addItem(String title, OnItemClickListener listener) {
        items.add(new MenuItem(title, false, listener));
        return this;
    }

    public BottomSheetDrawer addDestructiveItem(String title, OnItemClickListener listener) {
        items.add(new MenuItem(title, true, listener));
        return this;
    }

    public interface OnItemClickListener {
        void onClick();
    }

    private static class MenuItem {
        final String title;
        final boolean isDestructive;
        final OnItemClickListener clickListener;

        MenuItem(String title, boolean isDestructive, OnItemClickListener clickListener) {
            this.title = title;
            this.isDestructive = isDestructive;
            this.clickListener = clickListener;
        }
    }

    public static class Builder {
        private final List<MenuItem> items = new ArrayList<>();

        public Builder addItem(String title, OnItemClickListener listener) {
            items.add(new MenuItem(title, false, listener));
            return this;
        }

        public Builder addDestructiveItem(String title, OnItemClickListener listener) {
            items.add(new MenuItem(title, true, listener));
            return this;
        }

        public BottomSheetDrawer build() {
            BottomSheetDrawer drawer = new BottomSheetDrawer();
            drawer.items.addAll(this.items);
            return drawer;
        }
    }
}