package com.example.simpletool;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class FlowActivity extends AppCompatActivity {

    private static final String TAG = "FlowActivity";
    private static final String EXTRA_IMAGE_PATHS = "image_paths";
    private static final String EXTRA_CURRENT_POSITION = "current_position";

    private RecyclerView recyclerView;
    private OptimizedFlowAdapter imageAdapter;
    private TextView tvImageCounter;
    private List<String> imagePaths = new ArrayList<>();
    private int currentPosition = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flow_gallery);

        // 获取传递的数据
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            String[] paths = extras.getStringArray(EXTRA_IMAGE_PATHS);
            if (paths != null) {
                for (String path : paths) {
                    imagePaths.add(path);
                }
            }
            currentPosition = extras.getInt(EXTRA_CURRENT_POSITION, 0);
        }

        initViews();
        setupRecyclerView();
    }

    private void initViews() {
        recyclerView = findViewById(R.id.recycler_view);
        tvImageCounter = findViewById(R.id.tv_image_counter);

        // 返回按钮点击事件
        View btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                finish();
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            });
        }

        // 更新图片计数器
        updateImageCounter(currentPosition);
    }

    private void setupRecyclerView() {
        // 创建适配器
        imageAdapter = new OptimizedFlowAdapter(this, imagePaths);

        // 设置布局管理器 - 垂直滑动
        LinearLayoutManager layoutManager = new LinearLayoutManager(
                this,
                LinearLayoutManager.VERTICAL,
                false
        );
        recyclerView.setLayoutManager(layoutManager);

        // 设置适配器
        recyclerView.setAdapter(imageAdapter);

        // 去除滚动阴影
        recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        // 滚动到指定位置
        if (currentPosition >= 0 && currentPosition < imagePaths.size()) {
            recyclerView.scrollToPosition(currentPosition);
            // 延迟一下确保滚动到位
            recyclerView.post(() -> updateImageCounter(currentPosition));
        }

        // 监听滚动位置变化
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            private int lastVisibleItem = -1;

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                updateCurrentPosition();
            }

            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                // 滚动停止时更新位置，确保准确
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    updateCurrentPosition();
                }
            }

            private void updateCurrentPosition() {
                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager != null) {
                    // 获取第一个可见项的位置
                    int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();

                    if (firstVisibleItemPosition != RecyclerView.NO_POSITION &&
                            firstVisibleItemPosition >= 0 &&
                            firstVisibleItemPosition < imagePaths.size() &&
                            firstVisibleItemPosition != lastVisibleItem) {

                        lastVisibleItem = firstVisibleItemPosition;
                        updateImageCounter(firstVisibleItemPosition);
                    }
                }
            }
        });

        // 点击图片可以显示/隐藏工具栏
        imageAdapter.setOnItemClickListener(new OptimizedFlowAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(int position) {
                toggleToolbar();
            }
        });
    }

    private void updateImageCounter(int position) {
        if (tvImageCounter != null && imagePaths.size() > 0) {
            // 确保位置在有效范围内
            if (position < 0) position = 0;
            if (position >= imagePaths.size()) position = imagePaths.size() - 1;

            currentPosition = position;
            tvImageCounter.setText((position + 1) + " / " + imagePaths.size());
        }
    }

    private void toggleToolbar() {
        View toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            if (toolbar.getVisibility() == View.VISIBLE) {
                toolbar.setVisibility(View.GONE);
            } else {
                toolbar.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 清理资源
        if (imageAdapter != null) {
            imageAdapter.clearCache();
        }
    }
}
