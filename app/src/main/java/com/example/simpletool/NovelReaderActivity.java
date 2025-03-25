package com.example.simpletool;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
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
    private int bgColor = 0xFFF5E6CA; // 默认护眼黄
    private final int[] colorOptions = {
            0xFFFFFFFF,    // 白色
            0xFFF5E6CA,    // 护眼黄
            0xFFE6F5EA,    // 护眼绿
            0xFFE0E0E0     // 浅灰
    };

    private boolean isControlsVisible = true;
    private ValueAnimator controlsAnimator;

    // 新增成员变量保存原始文本
    private String originalContent;

    // 字号范围调整为12sp-24sp
    private static final int MIN_TEXT_SIZE = 12;
    private static final int MAX_TEXT_SIZE = 24;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_novel_reader);
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

        // 修改触摸监听
        View touchLayer = findViewById(R.id.touch_layer);
        touchLayer.setOnTouchListener(new View.OnTouchListener() {
            private float startX, startY;
            private final int CLICK_THRESHOLD = dpToPx(4); // 4dp移动视为点击

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = event.getX();
                        startY = event.getY();
                        return true;
                    case MotionEvent.ACTION_UP:
                        handleTouchEvent(event.getX(), event.getY());
                        return true;
                }
                return false;
            }

            private void handleTouchEvent(float x, float y) {
                // 计算控件可见区域
                Rect toolbarRect = getViewRect(findViewById(R.id.toolbar));
                Rect bottomRect = getViewRect(findViewById(R.id.bottom_controls));

                // 排除工具栏和底部控制栏区域
                if (toolbarRect.contains((int) x, (int) y) ||
                        bottomRect.contains((int) x, (int) y)) {
                    return; // 不处理这些区域的点击
                }

                // 判断是否为有效点击（移动距离阈值）
                if (Math.abs(x - startX) < CLICK_THRESHOLD &&
                        Math.abs(y - startY) < CLICK_THRESHOLD) {
                    handlePageClick(x);
                }
            }

            private Rect getViewRect(View view) {
                int[] location = new int[2];
                view.getLocationOnScreen(location);
                return new Rect(
                        location[0],
                        location[1],
                        location[0] + view.getWidth(),
                        location[1] + view.getHeight()
                );
            }

            private void handlePageClick(float x) {
                int screenWidth = getResources().getDisplayMetrics().widthPixels;
                float thirdWidth = screenWidth / 3f;

                if (x < thirdWidth) {
                    flipPage(-1);
                } else if (x > 2 * thirdWidth) {
                    flipPage(1);
                } else {
                    toggleControls();
                }
            }
        });
    }

    private void flipPage(int direction) {
        int current = viewPager.getCurrentItem();
        int target = current + direction;

        // 使用平滑滚动关闭的切换方式
        viewPager.setCurrentItem(target, false);

        // 更新进度显示
        updateProgress();
    }

    private void toggleControls() {
        View toolbar = findViewById(R.id.toolbar);
        View bottomControls = findViewById(R.id.bottom_controls);

        if (controlsAnimator != null && controlsAnimator.isRunning()) {
            controlsAnimator.cancel();
        }

        float startAlpha = isControlsVisible ? 1f : 0f;
        float endAlpha = isControlsVisible ? 0f : 1f;

        controlsAnimator = ValueAnimator.ofFloat(startAlpha, endAlpha);
        controlsAnimator.setDuration(200);
        controlsAnimator.addUpdateListener(animation -> {
            float alpha = (float) animation.getAnimatedValue();
            toolbar.setAlpha(alpha);
            bottomControls.setAlpha(alpha);
        });

        controlsAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                toolbar.setVisibility(isControlsVisible ? View.GONE : View.VISIBLE);
                bottomControls.setVisibility(isControlsVisible ? View.GONE : View.VISIBLE);
                isControlsVisible = !isControlsVisible;
            }
        });

        controlsAnimator.start();
    }

    private void updateButtonColor(int textColor) {
        ImageButton btnChapter = findViewById(R.id.btnChapter);
        btnChapter.setColorFilter(textColor, PorterDuff.Mode.SRC_IN);
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
                originalContent = content; // 保存原始内容
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
        Layout layout = new StaticLayout(
                content,
                textPaint,
                pageWidth,  // 使用最新计算的宽度
                Layout.Alignment.ALIGN_NORMAL,
                1.2f,
                0f,
                false
        );

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
            tts.setOnUtteranceCompletedListener(new TextToSpeech.OnUtteranceCompletedListener() {
                @Override
                public void onUtteranceCompleted(String utteranceId) {
                    runOnUiThread(() -> {
                        if (position < pages.size() - 1) {
                            viewPager.setCurrentItem(position + 1, true);
                        } else {
                            isSpeaking = false;
                        }
                    });
                }
            });
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
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
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

            tts.setOnUtteranceCompletedListener(new TextToSpeech.OnUtteranceCompletedListener() {
                @Override
                public void onUtteranceCompleted(String utteranceId) {
                    runOnUiThread(() -> {
                        if (currentPage < pages.size() - 1) {
                            viewPager.setCurrentItem(currentPage + 1, true);
                        } else {
                            isSpeaking = false;
                        }
                    });
                }
            });
            isSpeaking = true;
        }
    }

    // 添加成员变量保存对话框视图引用
    private View dialogView;
    private SeekBar sbTextSize;
    private RadioGroup rgColor;
    private RadioGroup rgBgColor;
    private TextView tvTextSize;
    private TextView previewText;


    private void showFontSettings() {
        dialogView = getLayoutInflater().inflate(R.layout.dialog_font_settings, null);
        sbTextSize = dialogView.findViewById(R.id.sbTextSize);
        tvTextSize = dialogView.findViewById(R.id.tvTextSize);
        rgColor = dialogView.findViewById(R.id.rgColor);
        rgBgColor = dialogView.findViewById(R.id.rgBgColor);
        previewText = dialogView.findViewById(R.id.previewText);

        // 初始化字号设置（12-24sp）
        sbTextSize.setMax(MAX_TEXT_SIZE - MIN_TEXT_SIZE);
        sbTextSize.setProgress(textSizeSp - MIN_TEXT_SIZE);
        tvTextSize.setText(textSizeSp + " sp");

        // 初始化预览文本
        previewText.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
        previewText.setTextColor(textColor);
        previewText.setBackgroundColor(bgColor);

        // 实时更新监听
        sbTextSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int newSize = progress + MIN_TEXT_SIZE;
                tvTextSize.setText(newSize + " sp");
                previewText.setTextSize(TypedValue.COMPLEX_UNIT_SP, newSize);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        // 初始化颜色选择
        initColorRadioGroup(rgColor, new int[]{Color.BLACK, Color.RED, Color.BLUE}, textColor);
        initBgRadioGroup(rgBgColor, colorOptions, bgColor);

        // 颜色选择监听
        rgColor.setOnCheckedChangeListener((group, checkedId) -> {
            previewText.setTextColor(getSelectedColor(group));
        });
        rgBgColor.setOnCheckedChangeListener((group, checkedId) -> {
            previewText.setBackgroundColor(getSelectedColor(group));
        });

//        new AlertDialog.Builder(this)
//                .setView(dialogView)
//                .setPositiveButton("确定", (dialog, which) -> applySettings())
//                .show();
        new AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton("确定", (dialog, which) -> {
                    applySettings(); // 使用Lambda确保监听器绑定正确
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private int getSelectedColor(RadioGroup group) {
        int checkedId = group.getCheckedRadioButtonId();
        RadioButton rb = group.findViewById(checkedId);
        return (int) rb.getTag(); // 通过setTag存储颜色值
    }

    private void initColorRadioGroup(RadioGroup group, int[] colors, int selectedColor) {
        for (int i = 0; i < group.getChildCount(); i++) {
            RadioButton rb = (RadioButton) group.getChildAt(i);
            rb.setTag(colors[i]); // 存储颜色值
            if (colors[i] == selectedColor) {
                group.check(rb.getId());
            }
        }
    }

    // 初始化背景颜色单选组
    private void initBgRadioGroup(RadioGroup group, int[] colors, int selectedColor) {
        for (int i = 0; i < group.getChildCount(); i++) {
            RadioButton rb = (RadioButton) group.getChildAt(i);
            if (i < colors.length) {
                // 设置背景色预览
                GradientDrawable bgDrawable = new GradientDrawable();
                bgDrawable.setShape(GradientDrawable.RECTANGLE);
                bgDrawable.setCornerRadius(dpToPx(4));
                bgDrawable.setColor(colors[i]);
                bgDrawable.setStroke(dpToPx(2), Color.LTGRAY);

                rb.setBackground(bgDrawable);
                rb.setTag(colors[i]); // 存储颜色值
                rb.setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8));

                // 添加选中状态标记
                if (colors[i] == selectedColor) {
                    group.check(rb.getId());
//                    rb.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_check, 0);
                }
            }
        }
    }

    // 应用设置的方法
    private void applySettings() {
        Log.d("@@@", "applySettings");
        // 获取最新设置
        textSizeSp = sbTextSize.getProgress() + MIN_TEXT_SIZE;
        textColor = getSelectedColor(rgColor);
        bgColor = getSelectedColor(rgBgColor);

        // 保存设置
        saveFontSettings();
        // 刷新显示
        refreshTextDisplay();
        // 更新按钮颜色
        updateButtonColor(textColor);

    }


    private void saveFontSettings() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putInt("text_size", textSizeSp)
                .putInt("text_color", textColor)
                .putInt("bg_color", bgColor)
                .apply();
    }

    private void loadFontSettings() {
        textSizeSp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getInt("text_size", 16);
        textColor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getInt("text_color", Color.BLACK);
        bgColor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getInt("bg_color", 0xFFF5E6CA);
    }

    private void refreshTextDisplay() {
        // 更新文本绘制参数
        textPaint.setTextSize(spToPx(textSizeSp));
        textPaint.setColor(textColor);

        // 重新计算页面尺寸
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        pageWidth = metrics.widthPixels - dpToPx(32);
        pageHeight = metrics.heightPixels - dpToPx(96);

        // 使用原始内容重新分页
        splitPages(originalContent);

        // 刷新视图
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