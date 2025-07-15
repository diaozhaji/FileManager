package com.example.simpletool.reader.view;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.ColorInt;

import java.util.ArrayList;
import java.util.List;


public class ReaderView extends View {
    // 配置参数
    private int textColor = Color.BLACK;
    private int bgColor = 0xFFF5E6CA;
    private float textSize = 16f;
    private float lineSpacing = 1.2f;
    private int pagePadding = 16;

    // 绘制工具
    private TextPaint textPaint;
    private StaticLayout currentPageLayout;
    private StaticLayout nextPageLayout;

    // 分页数据
    private List<CharSequence> pages = new ArrayList<>();
    private int currentPage = 0;

    // 动画控制
    private ValueAnimator pageAnimator;
    private float pageOffset = 0f;
    private boolean isAnimating = false;

    // 触摸处理
    private float touchStartX;
    private boolean isLeftSwipe;

    // 回调接口
    public interface ReaderListener {
        void onPageChanged(int page, int total);

        void onTapCenterArea();
    }

    private ReaderListener listener;

    public ReaderView(Context context) {
        super(context);
        init();
    }

    public ReaderView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        // 初始化画笔
        textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(textColor);
        textPaint.setTextSize(spToPx(textSize));

        // 启用硬件加速
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    // 核心方法：设置文本内容
    public void setContent(final String content) {
        new Thread(() -> {
            List<CharSequence> calculatedPages = paginateContent(content);
            post(() -> {
                pages.clear();
                pages.addAll(calculatedPages);
                currentPage = 0;
                preparePageLayouts();
                invalidate();
                if (listener != null) {
                    listener.onPageChanged(0, pages.size());
                }
            });
        }).start();
    }

    // 分页逻辑
    private List<CharSequence> paginateContent(String content) {
        List<CharSequence> result = new ArrayList<>();
        int availableWidth = getWidth() - 2 * dpToPx(pagePadding);
        int availableHeight = getHeight() - 2 * dpToPx(pagePadding);

        TextPaint paint = new TextPaint(textPaint);
        Layout layout = new StaticLayout(
                content, paint, availableWidth,
                Layout.Alignment.ALIGN_NORMAL,
                lineSpacing, 0, false
        );

        int startLine = 0;
        while (startLine < layout.getLineCount()) {
            int endLine = findPageEnd(layout, startLine, availableHeight);
            int start = layout.getLineStart(startLine);
            int end = layout.getLineEnd(endLine);
            result.add(content.subSequence(start, end));
            startLine = endLine + 1;
        }
        return result;
    }

    private int findPageEnd(Layout layout, int startLine, int maxHeight) {
        int heightUsed = 0;
        for (int i = startLine; i < layout.getLineCount(); i++) {
            heightUsed += layout.getLineBottom(i) - layout.getLineTop(i);
            if (heightUsed > maxHeight) {
                return i - 1;
            }
        }
        return layout.getLineCount() - 1;
    }

    // 触摸事件处理
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (isAnimating) return true;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touchStartX = event.getX();
                return true;

            case MotionEvent.ACTION_UP:
                handleTouchEnd(event.getX());
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void handleTouchEnd(float endX) {
        float delta = endX - touchStartX;

        // 点击判断（滑动距离小于阈值）
        if (Math.abs(delta) < dpToPx(8)) {
            if (listener != null) {
                listener.onTapCenterArea();
            }
            return;
        }

        // 翻页方向判断
        isLeftSwipe = delta < 0;
        if (isLeftSwipe && currentPage == pages.size() - 1) return;
        if (!isLeftSwipe && currentPage == 0) return;

        startPageAnimation();
    }

    // 翻页动画
    private void startPageAnimation() {
        if (pageAnimator != null) {
            pageAnimator.cancel();
        }

        preparePageLayouts();
        pageAnimator = ValueAnimator.ofFloat(0f, 1f);
        pageAnimator.setDuration(300);
        pageAnimator.addUpdateListener(anim -> {
            pageOffset = (float) anim.getAnimatedValue();
            invalidate();
        });
        pageAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                currentPage += isLeftSwipe ? 1 : -1;
                pageOffset = 0f;
                isAnimating = false;
                invalidate();
                if (listener != null) {
                    listener.onPageChanged(currentPage, pages.size());
                }
            }
        });
        isAnimating = true;
        pageAnimator.start();
    }

    // 预加载页面布局
    private void preparePageLayouts() {
        int width = getWidth() - 2 * dpToPx(pagePadding);

        currentPageLayout = new StaticLayout(
                pages.get(currentPage),
                textPaint,
                width,
                Layout.Alignment.ALIGN_NORMAL,
                lineSpacing,
                0,
                false
        );

        int nextPage = currentPage + (isLeftSwipe ? 1 : -1);
        if (nextPage >= 0 && nextPage < pages.size()) {
            nextPageLayout = new StaticLayout(
                    pages.get(nextPage),
                    textPaint,
                    width,
                    Layout.Alignment.ALIGN_NORMAL,
                    lineSpacing,
                    0,
                    false
            );
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // 绘制背景
        canvas.drawColor(bgColor);

        // 无内容时显示提示
        if (pages.isEmpty()) {
            drawPlaceholder(canvas);
            return;
        }

        // 计算绘制位置
        int saveCount = canvas.save();
        int x = dpToPx(pagePadding);
        int y = dpToPx(pagePadding);

        // 绘制翻页动画
        if (isAnimating) {
            drawPageAnimation(canvas, x, y);
        } else {
            canvas.translate(x, y);
            currentPageLayout.draw(canvas);
        }

        canvas.restoreToCount(saveCount);
    }

    private void drawPageAnimation(Canvas canvas, int x, int y) {
        float ratio = isLeftSwipe ? pageOffset : 1 - pageOffset;
        float translateX = getWidth() * ratio * (isLeftSwipe ? -1 : 1);

        // 绘制当前页
        canvas.save();
        canvas.translate(x + translateX, y);
        currentPageLayout.draw(canvas);
        canvas.restore();

        // 绘制下一页
        if (nextPageLayout != null) {
            canvas.save();
            canvas.translate(x + translateX + (isLeftSwipe ? getWidth() : -getWidth()), y);
            nextPageLayout.draw(canvas);
            canvas.restore();
        }
    }

    private void drawPlaceholder(Canvas canvas) {
        String text = "暂无内容";
        float textWidth = textPaint.measureText(text);
        canvas.drawText(
                text,
                (getWidth() - textWidth) / 2,
                getHeight() / 2,
                textPaint
        );
    }

    // 工具方法
    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private float spToPx(float sp) {
        return sp * getResources().getDisplayMetrics().scaledDensity;
    }

    // 配置设置方法
    public void setTextSize(float sp) {
        textSize = sp;
        textPaint.setTextSize(spToPx(sp));
        requestLayout();
        invalidate();
    }

    public void setTextColor(@ColorInt int color) {
        textColor = color;
        textPaint.setColor(color);
        invalidate();
    }

    public void setBgColor(@ColorInt int color) {
        bgColor = color;
        invalidate();
    }

    public void setListener(ReaderListener listener) {
        this.listener = listener;
    }
}