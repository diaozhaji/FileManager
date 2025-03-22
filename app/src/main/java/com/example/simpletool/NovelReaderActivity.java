package com.example.simpletool;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import org.mozilla.universalchardet.UniversalDetector;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// NovelReaderActivity.java
public class NovelReaderActivity extends AppCompatActivity {
    private static final String PREFS_NAME = "NovelReaderPrefs";
    private static final String KEY_LAST_POSITION = "last_position_";

    private ViewPager2 viewPager;
    private TextView tvProgress;
    private SeekBar sbProgress;
    private List<String> pages = new ArrayList<>();
    private List<Chapter> chapters = new ArrayList<>();
    private String filePath;
    private int currentPage = 0;
    private int currentChapter = 0;
    private TextPaint textPaint;
    private int pageWidth;
    private int pageHeight;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_novel_reader);

        initViews();
        initTextPaint();
        loadFile();
        setupViewPager();
        setupProgressBar();
    }

    private void initViews() {
        viewPager = findViewById(R.id.viewPager);
        tvProgress = findViewById(R.id.tvProgress);
        sbProgress = findViewById(R.id.sbProgress);

        // 添加章节跳转按钮
        findViewById(R.id.btnChapter).setOnClickListener(v -> showChapterDialog());
    }

    private void initTextPaint() {
        textPaint = new TextPaint();
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(spToPx(16)); // 默认16sp
        textPaint.setAntiAlias(true);

        // 获取页面尺寸
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        pageWidth = metrics.widthPixels - dpToPx(32); // 左右留白
        pageHeight = metrics.heightPixels - dpToPx(96); // 上下留白
    }

    private void loadFile() {
        filePath = getIntent().getStringExtra("file_path");
        new Thread(() -> {
            try {
                // 检测文件编码
                String encoding = detectEncoding(new File(filePath));
                String content = readFileWithEncoding(filePath, encoding);

                // 解析章节
                parseChapters(content);

                // 分页处理
                splitPages(content);

                runOnUiThread(() -> {
                    viewPager.getAdapter().notifyDataSetChanged();
                    restoreLastPosition();
                });
            } catch (IOException e) {
                runOnUiThread(() -> showErrorDialog());
            }
        }).start();
    }

    // 新增错误处理方法
    private void showErrorDialog() {
        new AlertDialog.Builder(this)
                .setTitle("文件读取失败")
                .setMessage("无法打开文件，请检查文件是否有效")
                .setPositiveButton("确定", (dialog, which) -> finish())
                .show();
    }

    private void parseChapters(String content) {
        chapters.clear();
        if (content == null || content.isEmpty()) return;

        Pattern pattern = Pattern.compile("第[\\u4e00-\\u9fa5]+章\\s*.*");
        Matcher matcher = pattern.matcher(content);

        int lastEnd = 0;
        while (matcher.find()) {
            int start = matcher.start();
            if (start > lastEnd) {
                chapters.add(new Chapter("前言", lastEnd, start));
            }
            chapters.add(new Chapter(matcher.group(), start, matcher.end()));
            lastEnd = matcher.end();
        }
        if (lastEnd < content.length()) {
            chapters.add(new Chapter("尾声", lastEnd, content.length()));
        }

        if (chapters.isEmpty()) {
            chapters.add(new Chapter("全文", 0, content.length()));
        }
    }

    private void splitPages(String content) {
        pages.clear();
        Layout layout = new StaticLayout(content, textPaint, pageWidth,
                Layout.Alignment.ALIGN_NORMAL, 1.2f, 0f, false);

        int lineCount = layout.getLineCount();
        int startLine = 0;
        while (startLine < lineCount) {
            int endLine = findPageEndLine(layout, startLine);
            int start = layout.getLineStart(startLine);
            int end = layout.getLineEnd(endLine);
            pages.add(content.substring(start, end));
            startLine = endLine + 1;
        }
    }

    private int findPageEndLine(Layout layout, int startLine) {
        int height = 0;
        for (int i = startLine; i < layout.getLineCount(); i++) {
            height += layout.getLineBottom(i) - layout.getLineTop(i);
            if (height > pageHeight) return i - 1;
        }
        return layout.getLineCount() - 1;
    }

    private void setupViewPager() {
        viewPager.setAdapter(new PagerAdapter());
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                currentPage = position;
                updateProgress();
                saveProgress();
            }
        });
    }

    private void setupProgressBar() {
        sbProgress.setMax(pages.size());
        sbProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    viewPager.setCurrentItem(progress, true);
                }
            }
            // 其他方法实现...

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
    }

    private void updateProgress() {
        sbProgress.setProgress(currentPage);
        tvProgress.setText(String.format(Locale.getDefault(),
                "%d/%d (%.1f%%)", currentPage + 1, pages.size(),
                (currentPage + 1) * 100f / pages.size()));
    }

    private void showChapterDialog() {
        new AlertDialog.Builder(this)
                .setTitle("章节列表")
                .setItems(getChapterTitles(), (dialog, which) -> {
                    jumpToChapter(which);
                })
                .show();
    }

    private void jumpToChapter(int chapterIndex) {
        if (chapterIndex < 0 || chapterIndex >= chapters.size()) return;

        currentChapter = chapterIndex; // 更新当前章节
        int targetPage = findPageForPosition(chapters.get(chapterIndex).startPos);
        if (targetPage >= 0 && targetPage < pages.size()) {
            viewPager.setCurrentItem(targetPage, true);
        }
    }

    private int findPageForPosition(int charPosition) {
        for (int i = 0; i < pages.size(); i++) {
            int pageStart = 0;
            for (int j = 0; j < i; j++) {
                pageStart += pages.get(j).length();
            }
            int pageEnd = pageStart + pages.get(i).length();
            if (charPosition >= pageStart && charPosition < pageEnd) {
                return i;
            }
        }
        return 0;
    }

    private String[] getChapterTitles() {
        return chapters.stream()
                .map(c -> c.title)
                .toArray(String[]::new);
    }

    // 编码检测
    private String detectEncoding(File file) throws IOException {
        UniversalDetector detector = new UniversalDetector(null);
        byte[] buf = new byte[4096];
        try (FileInputStream fis = new FileInputStream(file)) {
            int nread;
            while ((nread = fis.read(buf)) > 0 && !detector.isDone()) {
                detector.handleData(buf, 0, nread);
            }
        }
        detector.dataEnd();
        String encoding = detector.getDetectedCharset();
        return encoding != null ? encoding : "GBK";
    }

    private String readFileWithEncoding(String path, String encoding) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(path), encoding))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    private void saveProgress() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putInt(KEY_LAST_POSITION + filePath.hashCode(), currentPage)
                .apply();
    }

    private void restoreLastPosition() {
        currentPage = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getInt(KEY_LAST_POSITION + filePath.hashCode(), 0);
        viewPager.setCurrentItem(currentPage, false);
        updateProgress();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveProgress();
    }

    // 单位转换方法
    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private int spToPx(int sp) {
        return (int) (sp * getResources().getDisplayMetrics().scaledDensity);
    }

    // Adapter实现
    private class PagerAdapter extends RecyclerView.Adapter<PagerAdapter.PageHolder> {
        @NonNull
        @Override
        public PageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView textView = new TextView(parent.getContext());
            textView.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, textPaint.getTextSize());
            textView.setLineSpacing(0, 1.2f);
            textView.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));
            return new PageHolder(textView);
        }

        @Override
        public void onBindViewHolder(@NonNull PageHolder holder, int position) {
            holder.textView.setText(pages.get(position));
        }

        @Override
        public int getItemCount() {
            return pages.size();
        }

        class PageHolder extends RecyclerView.ViewHolder {
            TextView textView;

            PageHolder(View view) {
                super(view);
                textView = (TextView) view;
            }
        }
    }

    // 章节数据类
    private static class Chapter {
        String title;
        int startPos;
        int endPos;

        Chapter(String title, int start, int end) {
            this.title = title;
            this.startPos = start;
            this.endPos = end;
        }
    }
}