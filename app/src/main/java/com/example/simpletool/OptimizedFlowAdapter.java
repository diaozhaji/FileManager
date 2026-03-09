package com.example.simpletool;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.media.ExifInterface;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.CenterInside;
import com.bumptech.glide.request.RequestOptions;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 图片流式浏览适配器 - 最佳实践版本
 * 综合两个版本的优点：
 * 1. 后台预加载图片尺寸和EXIF信息（来自com.jy.imagebrowser）
 * 2. 支持长图完整展示、EXIF旋转校正（来自com.example.simpletool）
 * 3. 合理的缓存策略，避免过度消耗内存
 */
public class OptimizedFlowAdapter extends RecyclerView.Adapter<OptimizedFlowAdapter.ImageViewHolder> {

    private static final String TAG = "OptimizedFlowAdapter";

    // 长图配置
    private static final float EXTREME_RATIO = 3.0f;

    private final Context context;
    private final List<String> imagePaths;
    private OnItemClickListener listener;

    // 预加载缓存（避免每次bindViewHolder都读文件）
    private final Map<String, ImageInfo> imageInfoCache = new HashMap<>();
    private final ExecutorService preloadExecutor = Executors.newSingleThreadExecutor();

    // 屏幕尺寸（只获取一次）
    private final int screenWidth;
    private final int screenHeight;

    public interface OnItemClickListener {
        void onItemClick(int position);
    }

    public OptimizedFlowAdapter(Context context, List<String> imagePaths) {
        this.context = context;
        this.imagePaths = imagePaths != null ? imagePaths : new ArrayList<>();

        // 获取屏幕尺寸（只获取一次）
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics displayMetrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(displayMetrics);
        screenWidth = displayMetrics.widthPixels;
        screenHeight = displayMetrics.heightPixels;

        // 后台预加载所有图片的尺寸和EXIF信息
        preloadImageInfo();
    }

    /**
     * 后台预加载图片信息（尺寸+旋转角度）
     * 只在构造函数中执行一次，避免滚动时重复IO
     */
    private void preloadImageInfo() {
        if (imagePaths == null || imagePaths.isEmpty()) {
            return;
        }

        preloadExecutor.execute(() -> {
            for (String path : imagePaths) {
                if (!imageInfoCache.containsKey(path)) {
                    ImageInfo info = loadImageInfo(path);
                    if (info != null) {
                        synchronized (imageInfoCache) {
                            imageInfoCache.put(path, info);
                        }
                    }
                }
            }
        });
    }

    /**
     * 加载单张图片的信息（尺寸+旋转角度）
     */
    private ImageInfo loadImageInfo(String path) {
        File file = new File(path);
        if (!file.exists()) {
            return null;
        }

        ImageInfo info = new ImageInfo();

        // 读取图片尺寸
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);

        info.originalWidth = options.outWidth;
        info.originalHeight = options.outHeight;

        // 读取EXIF旋转角度
        try {
            ExifInterface exif = new ExifInterface(path);
            int orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
            );
            info.rotation = getRotationFromExif(orientation);
        } catch (IOException e) {
            info.rotation = 0;
        }

        return info;
    }

    /**
     * 从EXIF方向获取旋转角度
     */
    private int getRotationFromExif(int orientation) {
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

        // 从缓存获取图片信息（不再每次读文件）
        ImageInfo info = imageInfoCache.get(imagePath);

        // 计算显示尺寸
        int targetWidth;
        int targetHeight;

        if (info != null && info.originalWidth > 0 && info.originalHeight > 0) {
            // 使用预加载的尺寸计算
            float ratio = (float) info.originalWidth / info.originalHeight;

            if (ratio > EXTREME_RATIO) {
                // 极端宽图
                targetHeight = screenHeight;
                targetWidth = (int) (targetHeight * ratio);
            } else if (ratio < 1.0f / EXTREME_RATIO) {
                // 极端长图 - 完整展示
                targetWidth = screenWidth;
                targetHeight = (int) (targetWidth / ratio);
            } else {
                // 正常比例
                if (info.originalWidth > info.originalHeight) {
                    targetWidth = screenWidth;
                    targetHeight = (int) (screenWidth / ratio);
                } else {
                    targetHeight = screenHeight;
                    targetWidth = (int) (screenHeight * ratio);
                }
            }
        } else {
            // 缓存中没有信息，使用默认值
            targetWidth = screenWidth;
            targetHeight = screenWidth;
        }

        // 构建RequestOptions
        RequestOptions requestOptions = new RequestOptions()
                .override(targetWidth, targetHeight)
                .format(DecodeFormat.PREFER_ARGB_8888)  // 使用ARGB_8888兼容性更好
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .skipMemoryCache(false);

        // 根据EXIF旋转应用变换（使用缓存的旋转角度）
        if (info != null && info.rotation != 0) {
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

    @Override
    public int getItemCount() {
        return imagePaths.size();
    }

    /**
     * 清理缓存，释放内存
     */
    public void clearCache() {
        if (context != null) {
            Glide.get(context).clearMemory();
        }
    }

    /**
     * 预加载信息的数据类
     */
    private static class ImageInfo {
        int originalWidth;
        int originalHeight;
        int rotation;
    }

    static class ImageViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;

        ImageViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.image_view);
        }
    }
}
