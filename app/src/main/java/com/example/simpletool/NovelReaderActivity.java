package com.example.simpletool;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Dialog;
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
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
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

    // ==================== 常量定义 ====================
    // SharedPreferences 键名
    private static final String PREFS_NAME = "NovelReaderPrefs";
    private static final String KEY_LAST_POSITION = "last_position_";
    private static final String KEY_TEXT_SIZE = "text_size";
    private static final String KEY_TEXT_COLOR = "text_color";
    private static final String KEY_BG_COLOR = "bg_color";
    private static final String KEY_LINE_SPACING = "line_spacing";

    // 字号设置范围 (sp)
    private static final int MIN_TEXT_SIZE = 14;
    private static final int MAX_TEXT_SIZE = 28;
    private static final int DEFAULT_TEXT_SIZE = 18;

    // 行间距设置范围 (倍数)
    private static final float MIN_LINE_SPACING = 1.0f;
    private static final float MAX_LINE_SPACING = 2.5f;
    private static final float DEFAULT_LINE_SPACING = 1.4f;

    // 边距设置 (dp)
    private static final int PADDING_HORIZONTAL_DP = 24;  // 左右各24dp
    private static final int PADDING_VERTICAL_DP = 12;    // 上下各12dp
    private static final int LINE_SPACING_EXTRA_DP = 8;   // 额外的行间距

    // 触摸相关
    private static final int CLICK_THRESHOLD_DP = 4;

    // 动画时长 (毫秒)
    private static final int CONTROLS_ANIMATION_DURATION = 200;

    // 颜色选项
    private static final int COLOR_TEXT_DEFAULT = 0xFF333333;
    private static final int COLOR_BG_DEFAULT = 0xFFF5E6CA;

    // 背景颜色选项数组
    private static final int[] BG_COLOR_OPTIONS = {
            0xFFFFFFFF,    // 白色
            0xFFF5E6CA,   // 护眼黄
            0xFFE6F5EA,   // 护眼绿
            0xFFE0E0E0    // 浅灰
    };

    // 文本颜色选项数组
    private static final int[] TEXT_COLOR_OPTIONS = {
            0xFF333333,   // 深灰
            0xFF1A1A1A,   // 接近黑色
            0xFF8B0000,   // 深红
            0xFF000080    // 深蓝
    };

    // ==================== 成员变量 ====================
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

    // 阅读设置
    private TextToSpeech tts;
    private boolean isSpeaking = false;
    private int textSizeSp = DEFAULT_TEXT_SIZE;
    private int textColor = COLOR_TEXT_DEFAULT;
    private int bgColor = COLOR_BG_DEFAULT;
    private float lineSpacing = DEFAULT_LINE_SPACING;

    private boolean isControlsVisible = true;
    private ValueAnimator controlsAnimator;
    private String originalContent;

    // ==================== 常量定义结束 ====================

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
            private final int clickThreshold = dpToPx(CLICK_THRESHOLD_DP);

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
                Rect toolbarRect = getViewRect(findViewById(R.id.toolbar));
                Rect bottomRect = getViewRect(findViewById(R.id.bottom_controls));

                if (toolbarRect.contains((int) x, (int) y) ||
                        bottomRect.contains((int) x, (int) y)) {
                    return;
                }

                if (Math.abs(x - startX) < clickThreshold &&
                        Math.abs(y - startY) < clickThreshold) {
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
        viewPager.setCurrentItem(target, false);
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
        controlsAnimator.setDuration(CONTROLS_ANIMATION_DURATION);
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

    private void updateButtonColor(int color) {
        ImageButton btnChapter = findViewById(R.id.btnChapter);
        btnChapter.setColorFilter(color, PorterDuff.Mode.SRC_IN);
    }

    private void initTextPaint() {
        textPaint = new TextPaint();
        textPaint.setColor(textColor);
        textPaint.setTextSize(spToPx(textSizeSp));
        textPaint.setAntiAlias(true);
    }

    private void loadFile() {
        filePath = getIntent().getStringExtra("file_path");
        new Thread(() -> {
            try {
                String encoding = detectEncoding(new File(filePath));
                String content = readFileWithEncoding(filePath, encoding);
                originalContent = content;
                Log.e("@@@", originalContent.length() + "字数");
                parseChapters(content);
                runOnUiThread(() -> {
                    viewPager.post(() -> {
                        splitPages(content);
                        viewPager.setAdapter(new PagerAdapter());
                        restoreLastPosition();
                    });
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
                "(?m)^\\s*" +
                        "(?:" +
                        "(?:第\\s*[\\d\\u4e00-\\u9fa5]{1,10}\\s*[章回卷节篇集部])" +
                        "|(?:[卷篇集部]\\s*[\\d\\u4e00-\\u9fa5]{1,10})" +
                        "|(?:[序楔终][卷章]?\\s*)" +
                        ")" +
                        "\\s*[：:—-]?\\s*" +
                        ".+" +
                        "$"
        );

        Matcher matcher = pattern.matcher(content);
        List<Chapter> titleMatches = new ArrayList<>();

        while (matcher.find()) {
            String fullTitle = matcher.group().trim();
            titleMatches.add(new Chapter(
                    fullTitle,
                    matcher.start(),
                    matcher.end()
            ));
        }

        if (!titleMatches.isEmpty()) {
            List<Chapter> validChapters = new ArrayList<>();
            Chapter prev = null;
            for (Chapter curr : titleMatches) {
                if (prev == null) {
                    prev = curr;
                    continue;
                }
                if (curr.startPos - prev.endPos < 100) {
                    if (curr.title.length() > prev.title.length()) {
                        prev = curr;
                    }
                } else {
                    validChapters.add(prev);
                    prev = curr;
                }
            }
            validChapters.add(prev);

            Chapter first = validChapters.get(0);
            if (first.startPos > 0) {
                String preface = content.substring(0, first.startPos).trim();
                if (!preface.isEmpty()) {
                    chapters.add(new Chapter("前言", 0, first.startPos));
                }
            }

            for (int i = 0; i < validChapters.size(); i++) {
                Chapter current = validChapters.get(i);
                int endPos = (i < validChapters.size() - 1)
                        ? validChapters.get(i + 1).startPos
                        : content.length();

                while (endPos > current.startPos &&
                        Character.isWhitespace(content.charAt(endPos - 1))) {
                    endPos--;
                }

                chapters.add(new Chapter(
                        current.title,
                        current.startPos,
                        endPos
                ));
            }

            Chapter last = chapters.get(chapters.size() - 1);
            if (last.endPos < content.length()) {
                String epilogue = content.substring(last.endPos).trim();
                if (epilogue.length() > 50) {
                    chapters.add(new Chapter(
                            "尾声",
                            last.endPos,
                            content.length()
                    ));
                }
            }
        } else {
            chapters.add(new Chapter("全文", 0, content.length()));
        }

        Log.e("@@@", "切分章节共：" + chapters.size());
    }

    private void splitPages(String content) {
        pages.clear();

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        int toolbarHeight = findViewById(R.id.toolbar).getHeight();
        int bottomControlsHeight = findViewById(R.id.bottom_controls).getHeight();

        // 使用常量计算边距
        int verticalPadding = dpToPx(PADDING_VERTICAL_DP * 2);
        int horizontalPadding = dpToPx(PADDING_HORIZONTAL_DP * 2);

        pageHeight = metrics.heightPixels - toolbarHeight - bottomControlsHeight - verticalPadding;
        pageWidth = metrics.widthPixels - horizontalPadding;

        // 使用设置中的行间距倍数
        Layout layout = new StaticLayout(
                content,
                textPaint,
                pageWidth,
                Layout.Alignment.ALIGN_NORMAL,
                lineSpacing,
                dpToPx(LINE_SPACING_EXTRA_DP),
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
        float accumulatedHeight = 0;
        for (int i = startLine; i < layout.getLineCount(); i++) {
            float lineHeight = layout.getLineBottom(i) - layout.getLineTop(i);
            if (accumulatedHeight + lineHeight > pageHeight) {
                return i - 1;
            }
            accumulatedHeight += lineHeight;
        }
        return layout.getLineCount() - 1;
    }

    private void refreshTextDisplay() {
        textPaint.setTextSize(spToPx(textSizeSp));
        textPaint.setColor(textColor);

        findViewById(R.id.toolbar).post(() -> {
            findViewById(R.id.bottom_controls).post(() -> {
                splitPages(originalContent);
                if (currentPage >= pages.size()) {
                    currentPage = pages.size() - 1;
                }
                viewPager.getAdapter().notifyDataSetChanged();
                viewPager.setCurrentItem(currentPage, false);
            });
        });
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

    // 美化版设置对话框 - 包含行间距调节
    private void showFontSettings() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_font_settings);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.white);
        dialog.getWindow().setLayout(
                (int) (getResources().getDisplayMetrics().widthPixels * 0.9),
                ViewGroup.LayoutParams.WRAP_CONTENT
        );

        // 初始化视图
        SeekBar sbTextSize = dialog.findViewById(R.id.sbTextSize);
        TextView tvTextSize = dialog.findViewById(R.id.tvTextSize);
        SeekBar sbLineSpacing = dialog.findViewById(R.id.sbLineSpacing);
        TextView tvLineSpacing = dialog.findViewById(R.id.tvLineSpacing);
        RadioGroup rgColor = dialog.findViewById(R.id.rgColor);
        RadioGroup rgBgColor = dialog.findViewById(R.id.rgBgColor);
        TextView previewText = dialog.findViewById(R.id.previewText);
        Button btnConfirm = dialog.findViewById(R.id.btnConfirm);
        Button btnCancel = dialog.findViewById(R.id.btnCancel);

        // 初始化字号设置
        sbTextSize.setMax(MAX_TEXT_SIZE - MIN_TEXT_SIZE);
        sbTextSize.setProgress(textSizeSp - MIN_TEXT_SIZE);
        tvTextSize.setText(textSizeSp + " sp");

        // 初始化行间距设置 (范围1.0-2.5，步长0.1)
        int lineSpacingProgress = (int) ((lineSpacing - MIN_LINE_SPACING) * 10);
        sbLineSpacing.setMax((int) ((MAX_LINE_SPACING - MIN_LINE_SPACING) * 10));
        sbLineSpacing.setProgress(lineSpacingProgress);
        tvLineSpacing.setText(String.format("%.1f", lineSpacing));

        // 初始化预览
        previewText.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
        previewText.setTextColor(textColor);
        previewText.setBackgroundColor(bgColor);
        previewText.setLineSpacing(dpToPx(LINE_SPACING_EXTRA_DP), lineSpacing);

        // 字号滑动监听
        sbTextSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int newSize = progress + MIN_TEXT_SIZE;
                tvTextSize.setText(newSize + " sp");
                previewText.setTextSize(TypedValue.COMPLEX_UNIT_SP, newSize);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // 行间距滑动监听
        sbLineSpacing.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float newSpacing = MIN_LINE_SPACING + (progress / 10f);
                tvLineSpacing.setText(String.format("%.1f", newSpacing));
                previewText.setLineSpacing(dpToPx(LINE_SPACING_EXTRA_DP), newSpacing);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // 初始化文本颜色选择
        initTextColorRadioGroup(rgColor, TEXT_COLOR_OPTIONS, textColor, previewText);

        // 初始化背景颜色选择
        initBgRadioGroup(rgBgColor, BG_COLOR_OPTIONS, bgColor, previewText);

        // 按钮点击事件
        btnConfirm.setOnClickListener(v -> {
            textSizeSp = sbTextSize.getProgress() + MIN_TEXT_SIZE;
            lineSpacing = MIN_LINE_SPACING + (sbLineSpacing.getProgress() / 10f);
            textColor = getSelectedColor(rgColor);
            bgColor = getSelectedColor(rgBgColor);
            saveFontSettings();
            refreshTextDisplay();
            updateButtonColor(textColor);
            dialog.dismiss();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private int getSelectedColor(RadioGroup group) {
        int checkedId = group.getCheckedRadioButtonId();
        if (checkedId == -1) return Color.BLACK;
        RadioButton rb = group.findViewById(checkedId);
        return (int) rb.getTag();
    }

    private void initTextColorRadioGroup(RadioGroup group, int[] colors, int selectedColor, TextView preview) {
        for (int i = 0; i < group.getChildCount(); i++) {
            RadioButton rb = (RadioButton) group.getChildAt(i);
            rb.setTag(colors[i]);

            // 设置圆形颜色预览
            GradientDrawable bgDrawable = new GradientDrawable();
            bgDrawable.setShape(GradientDrawable.OVAL);
            bgDrawable.setSize(dpToPx(32), dpToPx(32));
            bgDrawable.setColor(colors[i]);
            bgDrawable.setStroke(dpToPx(2), Color.LTGRAY);
            rb.setBackground(bgDrawable);

            if (colors[i] == selectedColor) {
                group.check(rb.getId());
            }
        }

        group.setOnCheckedChangeListener((group1, checkedId) -> {
            if (checkedId != -1) {
                RadioButton rb = group1.findViewById(checkedId);
                preview.setTextColor((int) rb.getTag());
            }
        });
    }

    private void initBgRadioGroup(RadioGroup group, int[] colors, int selectedColor, TextView preview) {
        for (int i = 0; i < group.getChildCount(); i++) {
            RadioButton rb = (RadioButton) group.getChildAt(i);
            if (i < colors.length) {
                GradientDrawable bgDrawable = new GradientDrawable();
                bgDrawable.setShape(GradientDrawable.RECTANGLE);
                bgDrawable.setCornerRadius(dpToPx(8));
                bgDrawable.setColor(colors[i]);
                bgDrawable.setStroke(dpToPx(2), Color.LTGRAY);

                rb.setBackground(bgDrawable);
                rb.setTag(colors[i]);
                rb.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12));

                if (colors[i] == selectedColor) {
                    group.check(rb.getId());
                }
            }
        }

        group.setOnCheckedChangeListener((group1, checkedId) -> {
            if (checkedId != -1) {
                RadioButton rb = group1.findViewById(checkedId);
                preview.setBackgroundColor((int) rb.getTag());
            }
        });
    }

    private void saveFontSettings() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putInt(KEY_TEXT_SIZE, textSizeSp)
                .putInt(KEY_TEXT_COLOR, textColor)
                .putInt(KEY_BG_COLOR, bgColor)
                .putFloat(KEY_LINE_SPACING, lineSpacing)
                .apply();
    }

    private void loadFontSettings() {
        textSizeSp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getInt(KEY_TEXT_SIZE, DEFAULT_TEXT_SIZE);
        textColor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getInt(KEY_TEXT_COLOR, COLOR_TEXT_DEFAULT);
        bgColor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getInt(KEY_BG_COLOR, COLOR_BG_DEFAULT);
        lineSpacing = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getFloat(KEY_LINE_SPACING, DEFAULT_LINE_SPACING);
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

            // 使用常量设置边距
            textView.setPadding(
                    dpToPx(PADDING_HORIZONTAL_DP),
                    dpToPx(PADDING_VERTICAL_DP),
                    dpToPx(PADDING_HORIZONTAL_DP),
                    dpToPx(PADDING_VERTICAL_DP)
            );
            textView.setLineSpacing(dpToPx(LINE_SPACING_EXTRA_DP), lineSpacing);

            return new PageHolder(textView);
        }

        @Override
        public void onBindViewHolder(@NonNull PageHolder holder, int position) {
            holder.textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
            holder.textView.setTextColor(textColor);
            holder.textView.setBackgroundColor(bgColor);
            holder.textView.setText(pages.get(position).text);
            holder.textView.setLineSpacing(dpToPx(LINE_SPACING_EXTRA_DP), lineSpacing);
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
