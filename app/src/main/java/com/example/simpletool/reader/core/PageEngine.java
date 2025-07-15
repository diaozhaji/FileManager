package com.example.simpletool.reader.core;

import android.content.Context;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.LruCache;

import com.example.simpletool.reader.model.Page;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PageEngine {
    // 缓存最近3种文本配置的分页结果（根据实际内存调整）
    private static final int MAX_CACHE_SIZE = 3;
    private final LruCache<String, List<Page>> pageCache = new LruCache<>(MAX_CACHE_SIZE);

    // 分页结果回调接口
    public interface PageListener {
        void onPageComplete(List<Page> pages);

        void onPageProgress(int percent);

        void onPageError(String message);
    }

    // 完整的Page类定义


    /**
     * 异步分页方法（线程安全实现）
     *
     * @param context    上下文
     * @param content    完整文本内容
     * @param textPaint  当前文本绘制参数
     * @param pageWidth  页面有效宽度（像素）
     * @param pageHeight 页面有效高度（像素）
     * @param listener   分页监听器
     */
    public void calculatePagesAsync(Context context, String content,
                                    TextPaint textPaint, int pageWidth, int pageHeight,
                                    PageListener listener) {
        new Thread(() -> {
            try {
                // 生成唯一缓存键（包含所有影响分页的参数）
                String cacheKey = generateCacheKey(textPaint, pageWidth, pageHeight);

                // 检查缓存
                List<Page> cached = pageCache.get(cacheKey);
                if (cached != null) {
                    notifySuccess(cached, listener);
                    return;
                }

                // 执行分页计算
                List<Page> pages = new ArrayList<>();
                StaticLayout layout = new StaticLayout(
                        content, textPaint, pageWidth,
                        Layout.Alignment.ALIGN_NORMAL, 1.2f, 0f, false
                );

                int lineCount = layout.getLineCount();
                int startLine = 0;
                int totalLines = layout.getLineCount();

                while (startLine < lineCount) {
                    int endLine = findPageEnd(layout, startLine, pageHeight);
                    int start = layout.getLineStart(startLine);
                    int end = layout.getLineEnd(endLine);
                    pages.add(new Page(content.substring(start, end), start, end));

                    // 基于行数计算更准确的进度
                    int progress = (int) ((endLine * 100f) / totalLines);
                    notifyProgress(progress, listener);

                    startLine = endLine + 1;
                }

                // 更新缓存
                pageCache.put(cacheKey, pages);
                notifySuccess(pages, listener);
            } catch (Exception e) {
                notifyError("分页失败: " + e.getMessage(), listener);
            }
        }).start();
    }

    /**
     * 智能分页核心算法
     */
    private int findPageEnd(Layout layout, int startLine, int pageHeight) {
        float accumulatedHeight = 0;
        final float maxHeight = pageHeight - 48; // 保留底部留白

        for (int i = startLine; i < layout.getLineCount(); i++) {
            float lineHeight = layout.getLineBottom(i) - layout.getLineTop(i);
            if (accumulatedHeight + lineHeight > maxHeight) {
                // 避免孤行现象：如果剩余行数小于3行，则合并到前一页
                int remainingLines = layout.getLineCount() - i;
                return (remainingLines < 3) ? layout.getLineCount() - 1 : i - 1;
            }
            accumulatedHeight += lineHeight;
        }
        return layout.getLineCount() - 1;
    }

    // 生成包含所有影响因素的缓存键
    private String generateCacheKey(TextPaint paint, int width, int height) {
        return String.format(Locale.US, "%.1f_%d_%d_%d_%d",
                paint.getTextSize(),
                paint.getColor(),
                width,
                height,
                paint.getFontMetrics().leading);
    }

    // 线程安全的结果通知方法
    private void notifySuccess(List<Page> pages, PageListener listener) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (listener != null) listener.onPageComplete(pages);
        });
    }

    private void notifyProgress(int percent, PageListener listener) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (listener != null) listener.onPageProgress(percent);
        });
    }

    private void notifyError(String message, PageListener listener) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (listener != null) listener.onPageError(message);
        });
    }
}