package com.example.mediapipecamera2;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.mediapipe.framework.MediaPipeException;

public class Camera2Input {

    private static final String TAG = "Camera2Input";


    private final Camera2PreviewHelper camera2Helper = new Camera2PreviewHelper();


    private Camera2Helper.OnCameraErrorListener customOnCameraErrorListener;

    private Camera2Helper.OnImageAvailableListener onImageAvailableListener;

    public Camera2Input() {}



    public void setOnImageAvailableListener(Camera2Helper.OnImageAvailableListener listener) {
        onImageAvailableListener = listener;
    }

    public void setOnCameraErrorListener(Camera2Helper.OnCameraErrorListener listener) {
        customOnCameraErrorListener = listener;
    }


    public void start(@NonNull Context context, @NonNull String cameraId) {

        if (null == onImageAvailableListener) {
            throw new MediaPipeException(
                    MediaPipeException.StatusCode.FAILED_PRECONDITION.ordinal(),
                    "newFrameListener is not set.");
        }

        camera2Helper.setOnImageAvailableListener(onImageAvailableListener);

        if (customOnCameraErrorListener != null) {
            camera2Helper.setOnCameraErrorListener(customOnCameraErrorListener);
        }

        camera2Helper.startCamera(context, cameraId);

    }

}
