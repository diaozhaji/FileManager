package com.example.simpletool;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.media.ExifInterface;

import androidx.annotation.NonNull;

import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;

/**
 * 图片旋转变换 - 用于处理EXIF旋转信息
 */
public class RotateTransformation extends BitmapTransformation {

    private final File file;

    public RotateTransformation(File file) {
        this.file = file;
    }

    @Override
    protected Bitmap transform(@NonNull BitmapPool pool, @NonNull Bitmap toTransform,
                              int outWidth, int outHeight) {
        int rotation = getRotation();
        if (rotation == 0) {
            return toTransform;
        }

        Matrix matrix = new Matrix();
        matrix.postRotate(rotation);

        return Bitmap.createBitmap(toTransform, 0, 0,
                toTransform.getWidth(), toTransform.getHeight(), matrix, true);
    }

    @Override
    public void updateDiskCacheKey(MessageDigest messageDigest) {
        messageDigest.update(("RotateTransformation_" + file.getAbsolutePath()).getBytes());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RotateTransformation that = (RotateTransformation) o;
        return file.equals(that.file);
    }

    @Override
    public int hashCode() {
        return file.hashCode();
    }

    private int getRotation() {
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
