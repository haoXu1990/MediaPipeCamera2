package com.example.mediapipecamera2;

import android.content.Context;
import android.graphics.SurfaceTexture;


import android.media.ImageReader;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public abstract class Camera2Helper {

    public interface OnCameraStartedListener {
        public void onCameraStarted(@Nullable SurfaceTexture surfaceTexture);
    }

    public interface OnImageAvailableListener {
        public void onImageAvailable(@Nullable ImageReader imageReader);
    }

    public interface OnCameraErrorListener {
        public void onCameraError(@Nullable Exception e);
    }

    protected static final String TAG = "CameraHelper";


    protected OnCameraStartedListener onCameraStartedListener;

    protected OnImageAvailableListener onImageAvailableListener;

    protected OnCameraErrorListener onCameraErrorListener;


    /**
     *  初始化 Camera 并设置 frames 到自定义的 SurfaceTexture
     * */
    public abstract void startCamera(@NonNull Context ctx, @NonNull String cameraId);


    public void setOnCameraStartedListener(@Nullable OnCameraStartedListener listener) {
        onCameraStartedListener = listener;
    }

    public void setOnImageAvailableListener(@Nullable OnImageAvailableListener listener) {
        onImageAvailableListener = listener;
    }

    public void setOnCameraErrorListener(@Nullable OnCameraErrorListener listener) {
        onCameraErrorListener = listener;
    }
}
