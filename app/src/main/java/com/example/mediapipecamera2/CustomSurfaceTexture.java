package com.example.mediapipecamera2;

import android.graphics.SurfaceTexture;
public class CustomSurfaceTexture extends SurfaceTexture {

    public CustomSurfaceTexture(int textName) {
        super(textName);
        init();
    }

    private void init() {
        super.detachFromGLContext();
    }
}
