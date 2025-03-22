package com.example.simpletool;

import android.graphics.Color;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
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
import java.util.stream.Collectors;

public class NovelReaderActivity extends AppCompatActivity {
    private static final String PREFS_NAME = "NovelReaderPrefs";
    private static final String KEY_LAST_POSITION = "last_position_";
    private ViewPager2 viewPager;
    private TextView tvProgress;
    private SeekBar sbProgress;
    private List<Page> pages = new ArrayList<>();
    private List<Chapter> chapters = new ArrayList<>();
    private String filePath;
    private int currentPage = 0;
    private TextPaint textPaint;
    private int pageWidth;
    private int pageHeight;

    // 新增功能成员变量
    private TextToSpeech tts;
    private boolean isSpeaking = false;
    private int textSizeSp = 16;
    private int textColor = Color.BLACK;
    private int bgColor = Color.WHITE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_novel_reader);

        // 初始化视图和功能
        initViews();
        initTextPaint();
        initTTS();
        loadFontSettings();
        loadFile();
        setupViewPager();
        setupProgressBar();
    }

    private void initViews() {
        viewPager = findViewById(R.id.viewPager);
        tvProgress = findViewById(R.id.tvProgress);
        sbProgress = findViewById(R.id.sbProgress);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        findViewById(R.id.btnChapter).setOnClickListener(v -> showChapterDialog());
    }

    private void initTextPaint() {
        textPaint = new TextPaint();
        textPaint.setColor(textColor);
        textPaint.setTextSize(spToPx(textSizeSp));
        textPaint.setAntiAlias(true);

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        pageWidth = metrics.widthPixels - dpToPx(32);
        pageHeight = metrics.heightPixels - dpToPx(96);
    }

    private void loadFile() {
        filePath = getIntent().getStringExtra("file_path");
        new Thread(() -> {
            try {
                String encoding = detectEncoding(new File(filePath));
                String content = readFileWithEncoding(filePath, encoding);
                parseChapters(content);
                splitPages(content);
                runOnUiThread(() -> {
                    viewPager.setAdapter(new PagerAdapter());
                    restoreLastPosition();
                });
            } catch (IOException e) {
                runOnUiThread(this::showErrorDialog);
            }
        }).start();
    }

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

        Pattern pattern = Pattern.compile(
                "^第[\\d\\u4e00-\\u9fa5]{1,10}[章回卷节]\\s+.+$",
                Pattern.MULTILINE
        );
        Matcher matcher = pattern.matcher(content);
        List<Chapter> tempChapters = new ArrayList<>();
        boolean hasRealChapters = false;

        while (matcher.find()) {
            hasRealChapters = true;
            String chapterTitle = matcher.group().trim();
            tempChapters.add(new Chapter(chapterTitle, matcher.start(), matcher.end()));
        }

        if (hasRealChapters) {
            int firstChapterStart = tempChapters.get(0).startPos;
            if (firstChapterStart > 100) {
                chapters.add(new Chapter("前言", 0, firstChapterStart));
            }
            for (int i = 0; i < tempChapters.size(); i++) {
                Chapter current = tempChapters.get(i);
                chapters.add(current);
                if (i == tempChapters.size() - 1 &&
                        content.length() - current.endPos > 100) {
                    chapters.add(new Chapter("尾声", current.endPos, content.length()));
                }
            }
        } else {
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
            pages.add(new Page(content.substring(start, end), start, end));
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
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                currentPage = position;
                updateProgress();
                saveProgress();
                handleTtsOnPageChange(position);
            }
        });
    }

    private void handleTtsOnPageChange(int position) {
        if (isSpeaking) {
            tts.stop();
            String text = pages.get(position).text;
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "read_aloud");
        }
    }

    private void setupProgressBar() {
        sbProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    viewPager.setCurrentItem(progress, true);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void updateProgress() {
        sbProgress.setMax(pages.size() - 1);
        sbProgress.setProgress(currentPage);
        tvProgress.setText(String.format(Locale.getDefault(),
                "%d/%d (%.1f%%)", currentPage + 1, pages.size(),
                (currentPage + 1) * 100f / pages.size()));
    }

    private void showChapterDialog() {
        List<Chapter> filteredChapters = chapters.stream()
                .filter(c -> !c.title.equals("前言") && !c.title.equals("尾声"))
                .collect(Collectors.toList());

        new AlertDialog.Builder(this)
                .setTitle("章节列表")
                .setItems(
                        filteredChapters.stream()
                                .map(c -> c.title)
                                .toArray(String[]::new),
                        (dialog, which) -> jumpToChapter(filteredChapters.get(which))
                )
                .show();
    }

    private void jumpToChapter(Chapter chapter) {
        int targetPage = findPageForPosition(chapter.startPos);
        viewPager.setCurrentItem(targetPage, true);
    }

    private int findPageForPosition(int charPosition) {
        int totalLength = 0;
        for (Page page : pages) {
            if (charPosition >= totalLength && charPosition < totalLength + page.text.length()) {
                return pages.indexOf(page);
            }
            totalLength += page.text.length();
        }
        return 0;
    }

    private void initTTS() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.CHINA);
            }
        });
    }

    private void toggleReadAloud() {
        if (isSpeaking) {
            tts.stop();
            isSpeaking = false;
        } else {
            String text = pages.get(currentPage).text;
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "read_aloud");
            isSpeaking = true;
        }
    }

    private void showFontSettings() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_font_settings, null);
        SeekBar sbTextSize = dialogView.findViewById(R.id.sbTextSize);
        RadioGroup rgColor = dialogView.findViewById(R.id.rgColor);

        sbTextSize.setProgress(textSizeSp - 8);
        switch (textColor) {
            case Color.RED: rgColor.check(R.id.rbRed); break;
            case Color.BLUE: rgColor.check(R.id.rbBlue); break;
            default: rgColor.check(R.id.rbBlack);
        }

        new AlertDialog.Builder(this)
                .setTitle("字体设置")
                .setView(dialogView)
                .setPositiveButton("确定", (dialog, which) -> {
                    textSizeSp = sbTextSize.getProgress() + 8;
                    int colorId = rgColor.getCheckedRadioButtonId();
                    textColor = colorId == R.id.rbRed ? Color.RED
                            : colorId == R.id.rbBlue ? Color.BLUE : Color.BLACK;
                    saveFontSettings();
                    refreshTextDisplay();
                })
                .show();
    }

    private void saveFontSettings() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putInt("text_size", textSizeSp)
                .putInt("text_color", textColor)
                .apply();
    }

    private void loadFontSettings() {
        textSizeSp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getInt("text_size", 16);
        textColor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getInt("text_color", Color.BLACK);
    }

    private void refreshTextDisplay() {
        textPaint.setTextSize(spToPx(textSizeSp));
        textPaint.setColor(textColor);
        splitPages(pages.stream().map(p -> p.text).collect(Collectors.joining()));
        viewPager.getAdapter().notifyDataSetChanged();
        viewPager.setCurrentItem(currentPage, false);
    }

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
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.reader_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_font) {
            showFontSettings();
            return true;
        } else if (id == R.id.menu_read_aloud) {
            toggleReadAloud();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveProgress();
        if (isSpeaking) tts.stop();
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private int spToPx(int sp) {
        return (int) (sp * getResources().getDisplayMetrics().scaledDensity);
    }

    private static class Page {
        String text;
        int start;
        int end;
        Page(String text, int start, int end) {
            this.text = text;
            this.start = start;
            this.end = end;
        }
    }

    private static class Chapter {
        String title;
        int startPos;
        int endPos;
        Chapter(String title, int startPos, int endPos) {
            this.title = title;
            this.startPos = startPos;
            this.endPos = endPos;
        }
    }

    private class PagerAdapter extends RecyclerView.Adapter<PagerAdapter.PageHolder> {
        @NonNull
        @Override
        public PageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView textView = new TextView(parent.getContext());
            textView.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, spToPx(textSizeSp));
            textView.setTextColor(textColor);
            textView.setBackgroundColor(bgColor);
            textView.setLineSpacing(0, 1.2f);
            textView.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));
            return new PageHolder(textView);
        }

        @Override
        public void onBindViewHolder(@NonNull PageHolder holder, int position) {
            holder.textView.setText(pages.get(position).text);
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
}