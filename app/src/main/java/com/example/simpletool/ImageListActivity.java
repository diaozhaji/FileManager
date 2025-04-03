package com.example.simpletool;

import android.content.Intent;
import android.media.ExifInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import com.example.simpletool.utils.RotateTransformation;

import java.io.File;
import java.io.IOException;
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

    private void setRecyclerView() {
        recyclerView = findViewById(R.id.recycler_view);

        GridLayoutManager layoutManager = new GridLayoutManager(this, 3);
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return 1;
            }
        });
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setHasFixedSize(true);

        ImageListAdapter adapter = new ImageListAdapter();
        recyclerView.setAdapter(adapter);

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

            // 加载缩略图
            Glide.with(holder.itemView)
                    .load(file)
                    .override(200, 200) // 固定缩略图尺寸
                    .apply(new RequestOptions()
                            .transform(new RotateTransformation(file))) // 传递File对象
                    .fitCenter()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(holder.imageView);

            holder.itemView.setOnClickListener(v -> {
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

        private int getExifOrientation(File file) {
            try {
                ExifInterface exif = new ExifInterface(file.getAbsolutePath());
                int orientation = exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                );
                switch (orientation) {
                    case ExifInterface.ORIENTATION_ROTATE_90:
                        return 90;
                    case ExifInterface.ORIENTATION_ROTATE_180:
                        return 180;
                    case ExifInterface.ORIENTATION_ROTATE_270:
                        return 270;
                    default:
                        return 0;
                }
            } catch (IOException e) {
                return 0;
            }
        }
    }

    static class ImageViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;

        public ImageViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.image_view);
        }
    }
}