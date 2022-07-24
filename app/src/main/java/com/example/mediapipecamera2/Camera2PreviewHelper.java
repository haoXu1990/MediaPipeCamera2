package com.example.mediapipecamera2;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

public class Camera2PreviewHelper extends Camera2Helper {

    private static final class SingleThreadHandlerExecutor implements Executor {
        private final HandlerThread handlerThread;
        private final Handler handler;

        SingleThreadHandlerExecutor(String threadName, int priority) {
            handlerThread = new HandlerThread(threadName, priority);
            handlerThread.start();
            handler = new Handler(handlerThread.getLooper());
        }

        @Override
        public void execute(Runnable runnable) {
            if (!handler.post(runnable)) {
                throw new RejectedExecutionException(handlerThread.getName() + "is shutting down");
            }
        }

        public Handler getHandler() {
            return handler;
        }

        boolean shutdown() {
            return handlerThread.quitSafely();
        }
    }

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }


    private static final String TAG = "Camera2Helper";
    private String cameraId;
    protected CameraDevice cameraDevice;
    protected CameraCaptureSession cameraCaptureSessions;
    protected CaptureRequest.Builder captureRequestBuilder;
    protected CameraCharacteristics characteristics;
    private Size imageDimension;
    private ImageReader imageReader;
    private static final int REQUEST_CAMERA_PERMISSION = 200;

    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;
    private Handler mainHandler;
    private Context context;

    private int mSensorOrientation;

    public Camera2PreviewHelper() {

    }

    @Override
    public void startCamera(@NonNull Context ctx,@NonNull String cameraId) {
        this.context = ctx;
        this.cameraId = cameraId;

        closeCamera();
        startBackgroundThread();
        openCamera(null);
    }

    private void closeCamera() {
        try {
            stopBackgroundThread();
            Log.d(TAG, "Closing camera");
            if (null != cameraDevice) {
                cameraDevice.close();
                cameraDevice = null;
            }
            if (null != imageReader) {
                imageReader.close();
                imageReader = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void openCamera(@Nullable Size targetSize) {
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (cameraId == null) {
                cameraId = manager.getCameraIdList()[1];
                Log.d(TAG, "openCamera: not input cameraId, set front camera");
            }
            Log.d(TAG, "Cameras count = " + manager.getCameraIdList().length);


            characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;

            mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

            int displayRotation = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
            boolean swappedDimensions = false;
            switch (displayRotation) {
                case Surface.ROTATION_0:
                case Surface.ROTATION_180:
                    if (mSensorOrientation == 90 || mSensorOrientation == 270) {
                        swappedDimensions = true;
                    }
                    break;
                case Surface.ROTATION_90:
                case Surface.ROTATION_270:
                    if (mSensorOrientation == 0 || mSensorOrientation == 180) {
                        swappedDimensions = true;
                    }
                    break;
                default:
                    Log.e(TAG, "Display rotation is invalid: " + displayRotation);
            }

            imageDimension = map.getOutputSizes(ImageReader.class)[0];

            if (onImageAvailableListener != null) {

                imageReader = ImageReader.newInstance(imageDimension.getWidth(),
                        imageDimension.getHeight(),
                        ImageFormat.JPEG,
                        2);

                imageReader.setOnImageAvailableListener(imageReader -> {
                    if (onImageAvailableListener != null) {
                        onImageAvailableListener.onImageAvailable(imageReader);
                    }
                }, mBackgroundHandler);

            } else {
                Log.e(TAG, "imageAvailableListener is not set");
                return;
            }

            // check Permission
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions((Activity) context,
                        new String[]{Manifest.permission.CAMERA,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        REQUEST_CAMERA_PERMISSION);

                Log.d(TAG,"Permission issue");
                return;
            }
            Log.d(TAG, "Opening camera from manager " + cameraId);
            manager.openCamera(cameraId, stateCallback, mainHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    protected final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            Log.d(TAG, "camera opened");
            cameraDevice = camera;
            createCameraPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            cameraDevice.close();
        }

        @Override
        public void onError(@NonNull CameraDevice device, int i) {
            try {
                Log.e(TAG, "Error on  CameraDevice");
                device.close();
                cameraDevice = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };



    protected void createCameraPreview() {
        try {
            Log.d(TAG, "Creating camera preview");
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON);
            WindowManager manager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            int rotation = manager.getDefaultDisplay().getRotation();

//            int rotation = getDisplayRotation(characteristics);
            captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation));
//            captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, frameRotation);
            captureRequestBuilder.addTarget(imageReader.getSurface());

            cameraDevice.createCaptureSession(Arrays.asList(imageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    if (null == cameraDevice) {
                        return;
                    }
                    cameraCaptureSessions = cameraCaptureSession;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Log.d(TAG, "onConfigureFailed: Configuration Change");
                }
            }, mainHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private int getOrientation(int rotation) {
        // Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
        // We have to take that into account and rotate JPEG properly.
        // For devices with orientation of 90, we simply return our mapping from ORIENTATIONS.
        // For devices with orientation of 270, we need to rotate the JPEG 180 degrees.
        return (ORIENTATIONS.get(rotation) + mSensorOrientation + 270) % 360;
    }


    protected void updatePreview() {
        if (null == cameraDevice) {
            Log.e(TAG, "updatePreview error , return");
            return;
        }
        WindowManager manager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        int rotation = manager.getDefaultDisplay().getRotation();

//            int rotation = getDisplayRotation(characteristics);
        captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation));
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        try {
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    protected int getDisplayRotation(CameraCharacteristics cameraCharacteristics) {
        WindowManager manager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        int rotation = manager.getDefaultDisplay().getRotation();
        int degress = 0;
        switch (rotation) {
            case Surface.ROTATION_90:
                degress = 90;
                break;
            case Surface.ROTATION_180:
                degress = 180;
                break;
            case Surface.ROTATION_270:
                degress = 270;
                break;
            default:
                degress = 0;
                break;
        }

        int sensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
            return (360 - (sensorOrientation + degress) % 360) % 360;
        } else  {
            return (sensorOrientation - degress + 360) % 360;
        }
    }

    protected void startBackgroundThread() {
        mainHandler = new Handler(Looper.getMainLooper());
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    protected void stopBackgroundThread() {
        if (mBackgroundThread == null) { return; }
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
