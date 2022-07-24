package com.example.mediapipecamera2;

import android.util.Log;


public class HandLogUtil {
    private static final String TAG = "HandService";
    private static boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    private static boolean IS_OPEN = true;
    public static final HandLogUtil INSTANCE;

    static {
        HandLogUtil recognition = new HandLogUtil();
        INSTANCE = recognition;
    }

    public static void logd(String TAG, String text) {
        HandLogUtil.INSTANCE.d(TAG, text);
    }

    public static void loge(String TAG, String text) {
        HandLogUtil.INSTANCE.e(TAG, text);
    }

    private HandLogUtil() {}

    private boolean isAllow() {
        return DEBUG || IS_OPEN;
    }

    private void e(String TAG, String text) {
        if (isAllow()) {
            Log.e(TAG, text);
        }
    }

    private void d(String TAG, String text) {
        if (isAllow()) {
            Log.d(TAG, text);
        }
    }

}
