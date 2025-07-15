package com.example.simpletool.reader.activity;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.example.simpletool.reader.model.Book;

public abstract class BaseReaderActivity extends AppCompatActivity {
    protected ViewPager2 mViewPager;
    protected ReaderConfig mConfig;
    protected Book mCurrentBook;

    // 初始化阅读配置
    protected void initReaderConfig() {
        mConfig = new ReaderConfig.Builder()
                .textSize(16)
                .lineSpacing(1.2f)
                .pageTurnStyle(PageTurnStyle.SLIDE)
                .nightMode(false)
                .build();
    }

    // 页面切换监听
    private void setupViewPager() {
        mViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                saveReadingProgress(position);
            }
        });
    }

    protected abstract void saveReadingProgress(int position);
}