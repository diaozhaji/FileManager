package com.example.simpletool.utils;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.media.ExifInterface;

import androidx.annotation.NonNull;

import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;

// 修改后的RotateTransformation类：
public class RotateTransformation extends BitmapTransformation {
    private final File file;

    int rotationDegrees = 0;

    public RotateTransformation(File file) {
        this.file = file;
    }

    @Override
    protected Bitmap transform(@NonNull BitmapPool pool, @NonNull Bitmap toTransform, int outWidth, int outHeight) {
        try {
            ExifInterface exif = new ExifInterface(file.getAbsolutePath());
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    rotationDegrees = 90;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    rotationDegrees = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    rotationDegrees = 270;
                    break;
            }
            return rotateImage(pool, toTransform, rotationDegrees);
        } catch (IOException e) {
            e.printStackTrace();
            return toTransform;
        }
    }

    private Bitmap rotateImage(BitmapPool pool, Bitmap source, int degrees) {
        if (degrees == 0 || source == null) return source;
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    @Override
    public void updateDiskCacheKey(@NonNull MessageDigest messageDigest) {
        messageDigest.update((file.getAbsolutePath() + rotationDegrees).getBytes());
    }
}