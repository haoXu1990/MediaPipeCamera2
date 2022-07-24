package com.example.mediapipecamera2;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;

import com.google.mediapipe.solutioncore.CameraInput;
import com.google.mediapipe.solutioncore.SolutionGlSurfaceView;
import com.google.mediapipe.solutions.hands.Hands;
import com.google.mediapipe.solutions.hands.HandsOptions;
import com.google.mediapipe.solutions.hands.HandsResult;

import java.nio.ByteBuffer;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivityR";
    private Camera2Input camera2Input;

    private TextureView mTextureView;

    private Hands hands;
    // Run the pipeline and the model inference on GPU or CPU.
    private static final boolean RUN_ON_GPU = true;

    private int mImageWidth = 0;
    private int mImageHeight = 0;


    private CameraInput cameraInput;

    private SolutionGlSurfaceView<HandsResult> glSurfaceView;

    boolean useCameraX = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setupStreamingModePipeline();
    }

    @Override
    protected void onPause() {
        super.onPause();

        glSurfaceView.setVisibility(View.GONE);

        if (cameraInput != null) {
            cameraInput.close();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (useCameraX) {
            glSurfaceView.post(this::startCameraX);
        } else {
            glSurfaceView.post(this::startCamera2);
        }

        glSurfaceView.setVisibility(View.VISIBLE);

        if (cameraInput != null) {
            cameraInput = new CameraInput(this);
            cameraInput.setNewFrameListener(textureFrame -> hands.send(textureFrame));
        }
    }



    private void initGLSurfaceView() {
        glSurfaceView =
                new SolutionGlSurfaceView<>(this, hands.getGlContext(), hands.getGlMajorVersion());
        glSurfaceView.setSolutionResultRenderer(new HandsResultGlRenderer());
        glSurfaceView.setRenderInputImage(true);

        if (useCameraX) {
            glSurfaceView.post(this::startCameraX);
        } else {
            glSurfaceView.post(this::startCamera2);
        }

        FrameLayout frameLayout = findViewById(R.id.preview_display_layout);
        frameLayout.removeAllViewsInLayout();
        frameLayout.addView(glSurfaceView);
        glSurfaceView.setVisibility(View.VISIBLE);
        frameLayout.requestLayout();
    }

    private void setupStreamingModePipeline() {
        stopCurrentPipeline();
        initHands();

        if (useCameraX) {
            initCameraX();
        } else {
            initCamera2();
        }

        initGLSurfaceView();
    }

    private void stopCurrentPipeline() {
        if (cameraInput != null) {
            cameraInput.setNewFrameListener(null);
            cameraInput.close();
        }

        if (glSurfaceView != null) {
            glSurfaceView.setVisibility(View.GONE);
        }
        if (hands != null) {
            hands.close();
        }
    }


    private void initHands() {
        hands = new Hands(
                this,
                HandsOptions.builder()
                        // 视频流
                        .setStaticImageMode(false)
                        // 检测的最大手
                        .setMaxNumHands(1)
                        // 是否运行在GPU
                        .setRunOnGpu(RUN_ON_GPU)
                        .setModelComplexity(0)
                        .setMinDetectionConfidence(0.5F)
                        .setMinTrackingConfidence(0.5F)
                        .build());
        hands.setErrorListener((message, e) -> Log.e(TAG, "MediaPipe Hands error:" + message));

        hands.setResultListener(
                handsResult -> {

                    if (glSurfaceView != null) {
                        glSurfaceView.setRenderData(handsResult);
                        glSurfaceView.requestRender();
                    }

                    String msg = HandTranslate.INSTANCE.handRecognition(handsResult,mImageWidth, mImageHeight);

                    Log.d(TAG, "识别到手势: " + msg);
                });
    }

    private void startCameraX() {
        cameraInput.start(
                null,
                this,
                this,
                0,
                hands.getGlContext(),
                glSurfaceView.getWidth(),
                glSurfaceView.getHeight());
    }

    private void startCamera2() {
        camera2Input.start(this, "1");
    }

    private void  initCameraX() {
        cameraInput = new CameraInput();
        cameraInput.setNewFrameListener(textureFrame ->  {
            hands.send(textureFrame);
            mImageWidth = textureFrame.getWidth();
            mImageHeight = textureFrame.getWidth();
        });
    }



    protected final Camera2Helper.OnImageAvailableListener imageAvailableListener = new Camera2Helper.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(@Nullable ImageReader imageReader) {
            Image image = imageReader.acquireLatestImage();
            if (mImageHeight == 0) {
                mImageHeight = image.getHeight();
                mImageWidth = image.getWidth();
            }

            if (image.getPlanes() != null) {
                ByteBuffer byteBuffer = image.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[byteBuffer.remaining()];
                byteBuffer.get(bytes);

                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

                // 速度太慢，不能这样搞
//                Matrix matrix = new Matrix();
//                matrix.setScale(-1, 1);
//                matrix.postRotate(270);
//                Bitmap bitmap1 = Bitmap.createBitmap(bitmap,0, 0, bitmap.getWidth(), bitmap.getHeight(),matrix, true);


                hands.send(bitmap, image.getTimestamp());
            }
            image.close();
        }
    };

    private void initCamera2() {
        camera2Input = new Camera2Input();

        camera2Input.setOnCameraErrorListener(new Camera2Helper.OnCameraErrorListener() {
            @Override
            public void onCameraError(@Nullable Exception e) {

            }
        });

        camera2Input.setOnImageAvailableListener(imageAvailableListener);


    }
}