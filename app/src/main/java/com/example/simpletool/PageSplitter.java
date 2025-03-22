package com.example.simpletool;

import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import java.util.ArrayList;
import java.util.List;

class PageSplitter {
    private final TextPaint textPaint;
    private final int pageWidth;
    private final int pageHeight;

    public PageSplitter(TextPaint paint, int width, int height) {
        this.textPaint = paint;
        this.pageWidth = width;
        this.pageHeight = height;
    }

    public List<String> split(String text) {
        List<String> pages = new ArrayList<>();
        Layout layout = new StaticLayout(text, textPaint, pageWidth,
                Layout.Alignment.ALIGN_NORMAL, 1.2f, 0f, false);

        int start = 0;
        while (start < layout.getLineCount()) {
            int end = findEndLine(layout, start);
            pages.add(text.substring(layout.getLineStart(start),
                    layout.getLineEnd(end)));
            start = end + 1;
        }
        return pages;
    }

    private int findEndLine(Layout layout, int startLine) {
        int height = 0;
        for (int i = startLine; i < layout.getLineCount(); i++) {
            height += layout.getLineBottom(i) - layout.getLineTop(i);
            if (height > pageHeight) return i - 1;
        }
        return layout.getLineCount() - 1;
    }
}