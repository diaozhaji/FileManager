package com.example.simpletool;

import android.content.Intent;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.os.Bundle;
import android.util.Log;
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
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.example.simpletool.utils.RotateTransformation;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ImageListActivity extends AppCompatActivity {
    private static final String TAG = "ImageListActivity";

    // 缩略图配置
    private static final int THUMBNAIL_SIZE = 400;  // 基础缩略图尺寸
    private static final float EXTREME_RATIO = 3.0f;  // 极端宽高比阈值
    private static final int MAX_THUMBNAIL_HEIGHT = 600;  // 长图最大高度

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

    /**
     * 获取图片原始尺寸
     */
    private int[] getImageSize(File file) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;  // 只获取尺寸，不加载图片
        BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        return new int[]{options.outWidth, options.outHeight};
    }

    /**
     * 计算缩略图尺寸
     * 对于极端比例的图片，按短边等比例缩放
     */
    private int[] calculateThumbnailSize(int originalWidth, int originalHeight) {
        float ratio = (float) originalWidth / originalHeight;

        // 计算基础缩略图尺寸
        int targetWidth, targetHeight;

        if (ratio > EXTREME_RATIO) {
            // 图片特别宽（横向长图）
            // 按高度等比例缩放，保持清晰
            targetHeight = THUMBNAIL_SIZE;
            targetWidth = (int) (targetHeight * ratio);
            Log.d(TAG, "极端宽图: " + originalWidth + "x" + originalHeight +
                    " -> " + targetWidth + "x" + targetHeight + ", ratio=" + ratio);
        } else if (ratio < 1.0f / EXTREME_RATIO) {
            // 图片特别长（纵向长图/竖图）
            // 按宽度等比例缩放，但限制最大高度
            targetWidth = THUMBNAIL_SIZE;
            targetHeight = (int) (targetWidth / ratio);
            // 限制最大高度
            if (targetHeight > MAX_THUMBNAIL_HEIGHT) {
                targetHeight = MAX_THUMBNAIL_HEIGHT;
                targetWidth = (int) (targetHeight * ratio);
            }
            Log.d(TAG, "极端长图: " + originalWidth + "x" + originalHeight +
                    " -> " + targetWidth + "x" + targetHeight + ", ratio=" + ratio);
        } else {
            // 正常比例图片，使用正方形
            targetWidth = THUMBNAIL_SIZE;
            targetHeight = THUMBNAIL_SIZE;
        }

        return new int[]{targetWidth, targetHeight};
    }

    private class ImageListAdapter extends RecyclerView.Adapter<ImageViewHolder> {

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

            // 获取原始图片尺寸
            int[] originalSize = getImageSize(file);
            int originalWidth = originalSize[0];
            int originalHeight = originalSize[1];

            // 计算合适的缩略图尺寸
            int[] thumbnailSize = calculateThumbnailSize(originalWidth, originalHeight);
            int targetWidth = thumbnailSize[0];
            int targetHeight = thumbnailSize[1];

            // 获取旋转角度
            int rotation = getExifRotation(file);

            // 使用Glide加载优化后的缩略图
            RequestOptions requestOptions = new RequestOptions()
                    .override(targetWidth, targetHeight)
                    .format(DecodeFormat.PREFER_RGB_565)  // 使用RGB_565更清晰
                    .diskCacheStrategy(DiskCacheStrategy.ALL);

            // 根据旋转角度应用变换
            if (rotation != 0) {
                requestOptions.transform(new RotateTransformation(file));
            }

            // 根据图片比例选择合适的缩放方式
            float ratio = (float) originalWidth / originalHeight;
            if (ratio > EXTREME_RATIO || ratio < 1.0f / EXTREME_RATIO) {
                // 极端比例图片使用CenterCrop，从中间裁剪
                requestOptions.centerCrop();
            } else {
                // 正常比例使用fitCenter
                requestOptions.fitCenter();
            }

            Glide.with(holder.itemView)
                    .load(file)
                    .apply(requestOptions)
                    .into(holder.imageView);

            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(ImageListActivity.this, FlowActivity.class);
                intent.putExtra("image_paths", getFilePaths().toArray(new String[0]));
                intent.putExtra("current_position", position);
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

        private int getExifRotation(File file) {
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
