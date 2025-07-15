package com.example.simpletool.reader.activity;

import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.simpletool.R;
import com.example.simpletool.reader.core.FileParser;
import com.example.simpletool.reader.db.AppDatabase;
import com.example.simpletool.reader.model.Book;
import com.example.simpletool.reader.model.Bookmark;
import com.example.simpletool.reader.model.Chapter;
import com.example.simpletool.reader.view.ReaderView;

public class ReaderActivity extends AppCompatActivity
        implements ReaderView.ReaderListener {

    private ReaderView readerView;
    private TextView tvChapter, tvProgress;
    private Book currentBook;
    private Chapter currentChapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_novel_reader_2);

        // 初始化视图
        initViews();
        // 加载书籍数据
        loadBookData();
        // 初始化阅读配置
        setupReaderConfig();
    }

    private void initViews() {
        readerView = findViewById(R.id.reader_view);
        tvChapter = findViewById(R.id.tv_chapter);
        tvProgress = findViewById(R.id.tv_progress);

        // 设置阅读器监听
        readerView.setListener(this);

        // 底部按钮事件
        findViewById(R.id.btn_chapter_list).setOnClickListener(v -> showChapterDialog());
        findViewById(R.id.btn_tts).setOnClickListener(v -> startTTS());
        findViewById(R.id.btn_settings).setOnClickListener(v -> showSettings());
    }

    private void loadBookData() {
        // 从Intent获取书籍信息
        currentBook = getIntent().getParcelableExtra("book");
        currentChapter = getIntent().getParcelableExtra("chapter");

        // 从数据库加载阅读进度[4,5](@ref)
        AppDatabase db = Room.databaseBuilder(this, AppDatabase.class, "books")
                .allowMainThreadQueries().build();
        Bookmark lastBookmark = db.bookDao().getLastBookmark(currentBook.getId());

        // 加载章节内容
        String content = FileParser.parseChapter(
                currentChapter.getFilePath(),
                currentChapter.getEncoding()
        );
        readerView.setContent(content);

        // 恢复阅读位置
        if (lastBookmark != null) {
            readerView.jumpToPage(lastBookmark.getPage());
        }
    }

    private void setupReaderConfig() {
        // 应用用户配置
        SharedPreferences prefs = getSharedPreferences("reader_config", MODE_PRIVATE);
        readerView.setTextSize(prefs.getFloat("text_size", 16));
        readerView.setTextColor(prefs.getInt("text_color", Color.BLACK));
        readerView.setBgColor(prefs.getInt("bg_color", 0xFFF5E6CA));
    }

    // 实现ReaderListener接口
    @Override
    public void onPageChanged(int page, int total) {
        runOnUiThread(() -> {
            tvChapter.setText(currentChapter.getTitle());
            tvProgress.setText(String.format("%d/%d", page + 1, total));
            saveReadingProgress(page);
        });
    }

    @Override
    public void onTapCenterArea() {
        toggleControlBars();
    }

    private void saveReadingProgress(int page) {
        new Thread(() -> {
            Bookmark bookmark = new Bookmark(
                    currentBook.getId(),
                    currentChapter.getId(),
                    page,
                    System.currentTimeMillis()
            );
            AppDatabase db = Room.databaseBuilder(this, AppDatabase.class, "books")
                    .build();
            db.bookDao().insertBookmark(bookmark);
        }).start();
    }

    private void toggleControlBars() {
        boolean visible = findViewById(R.id.top_bar).getVisibility() == View.VISIBLE;
        findViewById(R.id.top_bar).setVisibility(visible ? View.GONE : View.VISIBLE);
        findViewById(R.id.bottom_control).setVisibility(visible ? View.GONE : View.VISIBLE);
    }

    // 横竖屏切换处理
    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        readerView.requestLayout(); // 重新计算分页
    }
}