package com.example.simpletool.reader.utils;

import android.content.Context;

public class ScreenUtils {

    private int dpToPx(Context context, int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }
}
