package com.limelight.utils;

import android.graphics.Bitmap;

public abstract class BitmapRunnable implements Runnable {

    @Override
    public void run() {
    }

    public abstract void runOnBmp(Bitmap bmp);
}