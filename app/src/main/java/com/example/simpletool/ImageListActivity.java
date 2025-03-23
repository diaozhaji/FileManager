package com.example.simpletool;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.MemoryCategory;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.Transition;
import com.example.simpletool.utils.RotateTransformation;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ImageListActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private List<File> imageFiles = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_list);

        // 获取传递的图片路径
        ArrayList<String> paths = getIntent().getStringArrayListExtra("image_paths");
        if (paths != null) {
            for (String path : paths) {
                File file = new File(path);
                if (file.exists() && isImageFile(file)) {
                    imageFiles.add(file);
                }
            }
        }

        setRecyclerView();


    }

    public void setRecyclerView() {
        recyclerView = findViewById(R.id.recycler_view);

        GridLayoutManager layoutManager = new GridLayoutManager(this, 1);
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return 1; // 保持所有item等宽
            }
        });
        recyclerView.setLayoutManager(layoutManager);

        ImageListAdapter adapter = new ImageListAdapter();
        recyclerView.setAdapter(adapter);
        recyclerView.setHasFixedSize(true);

        Glide.get(this).setMemoryCategory(MemoryCategory.HIGH);


    }

    private boolean isImageFile(File file) {
        String[] supportedExt = {".jpg", ".jpeg", ".png", ".webp"};
        String name = file.getName().toLowerCase();
        for (String ext : supportedExt) {
            if (name.endsWith(ext)) return true;
        }
        return false;
    }

    private class ImageListAdapter extends RecyclerView.Adapter<ImageViewHolder> {

        private final Map<String, Integer> sizeCache = new HashMap<>();

        @NonNull
        @Override
        public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_image, parent, false);
            return new ImageViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
            File file = imageFiles.get(position);
            ImageView imageView = holder.imageView;

            // 先重置高度防止复用错乱
            imageView.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
            ));

            Glide.with(holder.itemView)
                    .load(file)
                    .override(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL)
                    .into(new CustomTarget<Drawable>() {
                        @Override
                        public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                            String key = file.getAbsolutePath();
                            Integer cachedHeight = sizeCache.get(key);

                            if(cachedHeight != null) {
                                imageView.getLayoutParams().height = cachedHeight;
                                imageView.setImageDrawable(resource);
                                return;
                            }

                            int screenWidth = holder.itemView.getContext().getResources().getDisplayMetrics().widthPixels;
                            int imageWidth = resource.getIntrinsicWidth();
                            int imageHeight = resource.getIntrinsicHeight();

                            // 计算目标高度
                            int targetHeight = (int) ((screenWidth * 1f / imageWidth) * imageHeight);

                            // 设置最终尺寸
                            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) imageView.getLayoutParams();
                            params.height = targetHeight;
                            imageView.setLayoutParams(params);

                            // 最后设置图片
                            imageView.setImageDrawable(resource);

                            // 存入缓存
                            sizeCache.put(key, targetHeight);
                        }

                        @Override
                        public void onLoadCleared(@Nullable Drawable placeholder) {
                        }
                    });

//            // 加载缩略图
//            Glide.with(holder.itemView)
//                    .load(file)
//                    .apply(new RequestOptions().transform(new RotateTransformation(file)))
////                    .override(200, 200) // 固定缩略图尺寸
//                    .thumbnail(0.25f) // 先加载1/4大小的缩略图
//                    .centerCrop()
//                    .diskCacheStrategy(DiskCacheStrategy.ALL)
//                    .into(holder.imageView);

            holder.itemView.setOnClickListener(v -> {
                // 跳转到预览界面
                Intent intent = new Intent(ImageListActivity.this, ImagePreviewActivity.class);
                intent.putStringArrayListExtra("image_paths", new ArrayList<>(getFilePaths()));
                intent.putExtra("position", position);
                startActivity(intent);
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
            });
        }

        @Override
        public int getItemCount() {
            return imageFiles.size();
        }

        private List<String> getFilePaths() {
            List<String> paths = new ArrayList<>();
            for (File file : imageFiles) {
                paths.add(file.getAbsolutePath());
            }
            return paths;
        }
    }

    // 自定义EXIF方向转换器


    static class ImageViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;

        public ImageViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.image_view);
        }
    }
}
