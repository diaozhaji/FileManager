package com.example.simpletool;

import android.content.Intent;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.github.chrisbanes.photoview.PhotoView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ImagePreviewActivity extends AppCompatActivity {
    private ViewPager2 viewPager;
    private List<File> imageFiles;
    private int initialPosition;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 隐藏状态栏和导航栏
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        setContentView(R.layout.activity_image_preview);

        initData();
        setupViewPager();
        setupGestureDetector();
    }

    private void initData() {
        Intent intent = getIntent();
        String[] paths = intent.getStringArrayExtra("image_paths");
        initialPosition = intent.getIntExtra("position", 0);

        imageFiles = new ArrayList<>();
        if (paths != null) {
            for (String path : paths) {
                File file = new File(path);
                if (file.exists() && isImageFile(file)) {
                    imageFiles.add(file);
                }
            }
        }
    }

    private void setupViewPager() {
        viewPager = findViewById(R.id.view_pager);
        viewPager.setOrientation(ViewPager2.ORIENTATION_HORIZONTAL); // 可改为VERTICAL实现垂直滑动
        viewPager.setAdapter(new ImagePagerAdapter(this));
        viewPager.setCurrentItem(initialPosition, false);
    }

    private boolean isImageFile(File file) {
        String[] supportedExt = {".jpg", ".jpeg", ".png"};
        String name = file.getName().toLowerCase();
        for (String ext : supportedExt) {
            if (name.endsWith(ext)) return true;
        }
        return false;
    }

    // 手势检测（可选）
    private GestureDetector gestureDetector;

    private void setupGestureDetector() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                finish(); // 单击退出
                return true;
            }
        });
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event);
    }

    private class ImagePagerAdapter extends FragmentStateAdapter {
        public ImagePagerAdapter(FragmentActivity fa) {
            super(fa);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return ImageFragment.newInstance(imageFiles.get(position));
        }

        @Override
        public int getItemCount() {
            return imageFiles.size();
        }
    }

    public static class ImageFragment extends Fragment {
        private File imageFile;

        static ImageFragment newInstance(File file) {
            ImageFragment fragment = new ImageFragment();
            Bundle args = new Bundle();
            args.putString("path", file.getAbsolutePath());
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            if (getArguments() != null) {
                imageFile = new File(getArguments().getString("path"));
            }
        }

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater,
                                 @Nullable ViewGroup container,
                                 @Nullable Bundle savedInstanceState) {
            PhotoView photoView = new PhotoView(requireContext());

            Glide.with(this)
                    .load(imageFile)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
//                    .placeholder(R.drawable.ic_loading)
//                    .error(R.drawable.ic_error)
                    .into(photoView);

            return photoView;
        }
    }
}