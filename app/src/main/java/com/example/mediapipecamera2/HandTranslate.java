package com.example.mediapipecamera2;

import androidx.annotation.Nullable;

import com.google.mediapipe.formats.proto.ClassificationProto;
import com.google.mediapipe.formats.proto.LandmarkProto;
import com.google.mediapipe.solutions.hands.HandsResult;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HandTranslate {

    private static final String TAG = "HandService";
    public static final HandTranslate INSTANCE;

    // 默认前置摄像头
    private volatile static int faceingID = 1;

    // 黑板
    private static final int PLAM_BLACK = 0;
    // 白板
    private static final int PLAM_WHITE = 1;


    static {
        HandTranslate recognition = new HandTranslate();
        INSTANCE = recognition;
    }

    private HandTranslate() {}

    public void setFaceingID(int id) {
        faceingID = id;
    }



    // 手势识别
    @Nullable
    public String handRecognition(HandsResult result, int width, int height) {
        // 1. 计算位置
        Map<String, ArrayList<ArrayList<Integer>>> position = findPositions(result, width, height);

        // 2. 判断手掌方向
        boolean front = palmIsPositive(position);
        if (!front) { return null; }

        // 3. 判断手指状态
        Map<String, Integer> tips_data =  fingerStraight(position.get("Right"), "Right");
        Map<String, Integer> tips_data1 =  fingerStraight(position.get("Left"), "Left");

        if (tips_data != null) {
            // 4. 数字识别
            String numberStr = normalNumber(tips_data);

            return numberStr;
        }

        if (tips_data1 != null) {
            // 4. 数字识别
            String numberStr = normalNumber(tips_data1);

            return numberStr;
        }
        return null;
    }

    // 判断手掌象限
    private int palmXXX(Map<String, ArrayList<ArrayList<Integer>>> fingerPoints) {

        ArrayList<ArrayList<Integer>> rightHand = fingerPoints.get("Right");
        if (rightHand != null) {

            ArrayList<Integer> ltp1 = rightHand.get(0);
            ArrayList<Integer> ltp2 = rightHand.get(1);
            ArrayList<Integer> ltp3 = rightHand.get(17);

            ArrayList<Integer> ltp4 = rightHand.get(9);

            if (ltp1.get(0) > ltp4.get(0)) {

            }
        }

        return -1;
    }

    // 判断手掌正反面
    private boolean palmIsPositive(Map<String, ArrayList<ArrayList<Integer>>> fingerPoints) {
        if (fingerPoints == null ) {
            return false;
        }

        ArrayList<ArrayList<Integer>> rightHand = fingerPoints.get("Right");
        if (rightHand != null) {
            ArrayList<Integer> ltp1 = rightHand.get(4);
            ArrayList<Integer> ltp2 = rightHand.get(20);

            if (ltp1.get(0) > ltp2.get(0)) {
                return true;
            }
            return false;
        }

        ArrayList<ArrayList<Integer>> leftHand = fingerPoints.get("Left");
        if (leftHand != null) {
            ArrayList<Integer> ltp1 = leftHand.get(4);
            ArrayList<Integer> ltp2 = leftHand.get(20);

            if (ltp1.get(0) > ltp2.get(0)) {
                return false;
            }
            return true;
        }
        return false;
    }


    // 根据手指状态识别数字
    @Nullable
    private String normalNumber(Map<String, Integer> fingerStraight) {
        if (fingerStraight.isEmpty()) { return null;}
        Collection<Integer> values = fingerStraight.values();
        int count = 0;
        for (Integer value : values) {
            if (1 == value) { count++; }
        }
        return String.valueOf(count);
    }

    /** 判断每个手指的状态
     * @param fingerPoints 收的关键点位置参数
     * @return 手指的状态
     */
    @Nullable
    private Map<String, Integer> fingerStraight(ArrayList<ArrayList<Integer>> fingerPoints, String handPosition) {
        if (fingerPoints == null || fingerPoints.size() != 21 ) {
            HandLogUtil.logd(TAG, "fingerStraight: " + handPosition + " " + fingerPoints);
            return null;
        }

        Map<String, Integer> tips_data = new HashMap<String, Integer>();
        int[] tips = new int[]{4, 8, 12, 16, 20};


        for (Integer tip:tips) {
            ArrayList<Integer> ltp1 = fingerPoints.get(tip);
            ArrayList<Integer> ltp2 = fingerPoints.get(tip -2);
            if (tip == 4) {
                ArrayList<Integer> ltp3 =  fingerPoints.get(tip -1);
                if (ltp1.get(0) > ltp3.get(0)) {
                    if (handPosition.equals("Right")) {
                        tips_data.put(String.valueOf(tip), 1);
                    } else {
                        tips_data.put(String.valueOf(tip), 0);
                    }

                } else {
                    if (handPosition.equals("Right")) {
                        tips_data.put(String.valueOf(tip), 0);
                    } else {
                        tips_data.put(String.valueOf(tip), 1);
                    }
                }
            } else {
                if (ltp1.get(1) > ltp2.get(1)) {
                    tips_data.put(String.valueOf(tip), 0);
                } else {
                    tips_data.put(String.valueOf(tip), 1);
                }
            }
        }
        HandLogUtil.logd(TAG, "fingerStraight: " + handPosition + " " + tips_data);
        return tips_data;
    }

    /** 计算手的关键点位置参数
     * @param result a HandsResult {@link HandsResult}
     * @param width glSurfaceView width
     * @param height glSurfaceView height
     * @return handlandmark Positions
     */
    @Nullable
    private Map<String, ArrayList<ArrayList<Integer>>> findPositions(HandsResult result, int width, int height) {
        Map<String, ArrayList<ArrayList<Integer>>> position = new HashMap<String, ArrayList<ArrayList<Integer>>>();

        if (result.multiHandLandmarks().isEmpty()) { return position; }
        int i = 0;

        for (ClassificationProto.Classification point :result.multiHandedness()) {
            float score = point.getScore();
            if (score >= 0.8) {
                // 这里可能是平台问题，后面会多一个 换行符
                String label = point.getLabel().replace("\r", "");
                List<LandmarkProto.NormalizedLandmark> handLms =  result.multiHandLandmarks().get(i).getLandmarkList();
                ArrayList<ArrayList<Integer>> positions_hand = new ArrayList<ArrayList<Integer>>(20);
                for (int j = 0; j < handLms.size(); j++) {
                    LandmarkProto.NormalizedLandmark lm = handLms.get(j);
                    int x = (int) (lm.getX() * width);
                    int y = (int) (lm.getY() * height);
                    ArrayList<Integer> positions_finger = new ArrayList<Integer>(2);
                    positions_finger.add(x);
                    positions_finger.add(y);
                    positions_hand.add(positions_finger);
                }
                position.put(label,positions_hand);
                HandLogUtil.logd(TAG, "findPositions: " + position);
            }
            i++;
        }

        if (faceingID == 1) {
            // 前置摄像头 左右手数据交换
            Map<String, ArrayList<ArrayList<Integer>>> reversePosition = new HashMap<String, ArrayList<ArrayList<Integer>>>();
            if (position.containsKey("Left")) {
                reversePosition.put("Right", position.get("Left") );
            }
            if (position.containsKey("Right")) {
                reversePosition.put("Left", position.get("Right") );
            }
            return reversePosition;
        }

        return position;
    }

}
