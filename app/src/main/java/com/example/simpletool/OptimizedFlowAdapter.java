package com.example.simpletool;

import android.content.Context;
import android.graphics.Point;
import android.media.ExifInterface;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.MemoryCategory;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.CenterInside;
import com.bumptech.glide.request.RequestOptions;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 图片流式浏览适配器 - 支持长图优化显示
 */
public class OptimizedFlowAdapter extends RecyclerView.Adapter<OptimizedFlowAdapter.ImageViewHolder> {

    private static final String TAG = "OptimizedFlowAdapter";

    // 长图配置
    private static final float EXTREME_RATIO = 3.0f;  // 极端宽高比阈值
    // 不限制最大高度，让长图完整展示

    private final Context context;
    private final List<String> imagePaths;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(int position);
    }

    public OptimizedFlowAdapter(Context context, List<String> imagePaths) {
        this.context = context;
        this.imagePaths = imagePaths != null ? imagePaths : new ArrayList<>();

        // 提高Glide内存使用级别
        if (context != null) {
            Glide.get(context).setMemoryCategory(MemoryCategory.HIGH);
        }
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_image_flow, parent, false);
        return new ImageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
        String imagePath = imagePaths.get(position);
        File imageFile = new File(imagePath);

        if (!imageFile.exists()) {
            holder.imageView.setImageResource(android.R.color.darker_gray);
            return;
        }

        // 获取屏幕尺寸用于计算合适的图片尺寸
        Point screenSize = getScreenSize();

        // 计算显示尺寸
        int[] displaySize = calculateDisplaySize(imageFile, screenSize.x, screenSize.y);

        // 获取旋转角度
        int rotation = getExifRotation(imageFile);

        // 构建RequestOptions
        RequestOptions requestOptions = new RequestOptions()
                .override(displaySize[0], displaySize[1])
                .format(DecodeFormat.PREFER_RGB_565)  // 使用RGB_565格式，更清晰
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .skipMemoryCache(false);

        // 根据旋转角度应用变换
        if (rotation != 0) {
            requestOptions.transform(new CenterInside(), new RotateTransformation(imageFile));
        } else {
            requestOptions.transform(new CenterInside());
        }

        // 加载图片
        Glide.with(context)
                .load(imageFile)
                .apply(requestOptions)
                .into(holder.imageView);

        // 设置点击事件
        if (listener != null) {
            holder.itemView.setOnClickListener(v -> listener.onItemClick(position));
        }
    }

    /**
     * 计算图片显示尺寸
     * 对于极端比例的长图，按短边等比例缩放
     */
    private int[] calculateDisplaySize(File imageFile, int screenWidth, int screenHeight) {
        int[] originalSize = getImageSize(imageFile);
        int originalWidth = originalSize[0];
        int originalHeight = originalSize[1];

        float ratio = (float) originalWidth / originalHeight;

        int targetWidth;
        int targetHeight;

        if (ratio > EXTREME_RATIO) {
            // 极端宽图 - 按高度等比例缩放
            targetHeight = screenHeight;
            targetWidth = (int) (targetHeight * ratio);
            Log.d(TAG, "极端宽图: " + originalWidth + "x" + originalHeight +
                    " -> " + targetWidth + "x" + targetHeight);
        } else if (ratio < 1.0f / EXTREME_RATIO) {
            // 极端长图 - 按宽度等比例缩放，完整展示不裁剪
            targetWidth = screenWidth;
            targetHeight = (int) (targetWidth / ratio);
            // 不限制高度，让长图完整展示
            Log.d(TAG, "极端长图: " + originalWidth + "x" + originalHeight +
                    " -> " + targetWidth + "x" + targetHeight);
        } else {
            // 正常比例图片 - 适应屏幕宽度，保持比例
            if (originalWidth > originalHeight) {
                // 横向图片
                targetWidth = screenWidth;
                targetHeight = (int) (screenWidth / ratio);
            } else {
                // 纵向图片
                targetHeight = screenHeight;
                targetWidth = (int) (screenHeight * ratio);
            }
        }

        return new int[]{targetWidth, targetHeight};
    }

    /**
     * 获取图片原始尺寸
     */
    private int[] getImageSize(File file) {
        android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        return new int[]{options.outWidth, options.outHeight};
    }

    /**
     * 获取屏幕尺寸
     */
    private Point getScreenSize() {
        Point point = new Point();
        if (context != null) {
            point.x = context.getResources().getDisplayMetrics().widthPixels;
            point.y = context.getResources().getDisplayMetrics().heightPixels;
        }
        return point;
    }

    /**
     * 获取EXIF旋转角度
     */
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

    @Override
    public int getItemCount() {
        return imagePaths.size();
    }

    /**
     * 清理缓存
     */
    public void clearCache() {
        if (context != null) {
            Glide.get(context).clearMemory();
        }
    }

    static class ImageViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;

        ImageViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.image_view);
        }
    }
}
