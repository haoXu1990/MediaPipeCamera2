// Copyright 2021 The MediaPipe Authors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.example.mediapipecamera2;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.opengl.GLES20;

import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmark;
import com.google.mediapipe.solutioncore.ResultGlRenderer;
import com.google.mediapipe.solutions.hands.Hands;
import com.google.mediapipe.solutions.hands.HandsResult;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.List;

/** A custom implementation of {@link ResultGlRenderer} to render {@link HandsResult}. */
public class HandsResultGlRenderer implements ResultGlRenderer<HandsResult> {
  private static final String TAG = "HandsResultGlRenderer";

  private static final float[] LEFT_HAND_CONNECTION_COLOR = new float[] {0.2f, 1f, 0.2f, 1f};
  private static final float[] RIGHT_HAND_CONNECTION_COLOR = new float[] {1f, 0.2f, 0.2f, 1f};
  private static final float CONNECTION_THICKNESS = 25.0f;
  private static final float[] LEFT_HAND_HOLLOW_CIRCLE_COLOR = new float[] {0.2f, 1f, 0.2f, 1f};
  private static final float[] RIGHT_HAND_HOLLOW_CIRCLE_COLOR = new float[] {1f, 0.2f, 0.2f, 1f};
  private static final float HOLLOW_CIRCLE_RADIUS = 0.01f;
  private static final float[] LEFT_HAND_LANDMARK_COLOR = new float[] {1f, 0.2f, 0.2f, 1f};
  private static final float[] RIGHT_HAND_LANDMARK_COLOR = new float[] {0.2f, 1f, 0.2f, 1f};
  private static final float LANDMARK_RADIUS = 0.008f;
  private static final int NUM_SEGMENTS = 120;
  private static final String VERTEX_SHADER =
      "uniform mat4 uProjectionMatrix;\n"
          + "attribute vec4 vPosition;\n"
          + "void main() {\n"
          + "  gl_Position = uProjectionMatrix * vPosition;\n"
          + "}";
  private static final String FRAGMENT_SHADER =
      "precision mediump float;\n"
          + "uniform vec4 uColor;\n"
          + "void main() {\n"
          + "  gl_FragColor = uColor;\n"
          + "}";
  private int program;
  private int positionHandle;
  private int projectionMatrixHandle;
  private int colorHandle;

  private int loadShader(int type, String shaderCode) {
    int shader = GLES20.glCreateShader(type);
    GLES20.glShaderSource(shader, shaderCode);
    GLES20.glCompileShader(shader);
    return shader;
  }

  @Override
  public void setupRendering() {
    program = GLES20.glCreateProgram();
    int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER);
    int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
    GLES20.glAttachShader(program, vertexShader);
    GLES20.glAttachShader(program, fragmentShader);
    GLES20.glLinkProgram(program);
    positionHandle = GLES20.glGetAttribLocation(program, "vPosition");
    projectionMatrixHandle = GLES20.glGetUniformLocation(program, "uProjectionMatrix");
    colorHandle = GLES20.glGetUniformLocation(program, "uColor");
  }

  @Override
  public void renderResult(HandsResult result, float[] projectionMatrix) {
    if (result == null) {
      return;
    }
    GLES20.glUseProgram(program);
    GLES20.glUniformMatrix4fv(projectionMatrixHandle, 1, false, projectionMatrix, 0);
    GLES20.glLineWidth(CONNECTION_THICKNESS);

    int numHands = result.multiHandLandmarks().size();
    for (int i = 0; i < numHands; ++i) {
      boolean isLeftHand = result.multiHandedness().get(i).getLabel().equals("Left");
      drawConnections(
          result.multiHandLandmarks().get(i).getLandmarkList(),
          isLeftHand ? LEFT_HAND_CONNECTION_COLOR : RIGHT_HAND_CONNECTION_COLOR);
      for (NormalizedLandmark landmark : result.multiHandLandmarks().get(i).getLandmarkList()) {
        // Draws the landmark.
        drawCircle(
            landmark.getX(),
            landmark.getY(),
            isLeftHand ? LEFT_HAND_LANDMARK_COLOR : RIGHT_HAND_LANDMARK_COLOR);
        // Draws a hollow circle around the landmark.
        drawHollowCircle(
            landmark.getX(),
            landmark.getY(),
            isLeftHand ? LEFT_HAND_HOLLOW_CIRCLE_COLOR : RIGHT_HAND_HOLLOW_CIRCLE_COLOR);
      }
    }

//    drawResult();
  }

  /**
   * Deletes the shader program.
   *
   * <p>This is only necessary if one wants to release the program while keeping the context around.
   */
  public void release() {
    GLES20.glDeleteProgram(program);
  }

  private void drawConnections(List<NormalizedLandmark> handLandmarkList, float[] colorArray) {
    GLES20.glUniform4fv(colorHandle, 1, colorArray, 0);
    for (Hands.Connection c : Hands.HAND_CONNECTIONS) {
      NormalizedLandmark start = handLandmarkList.get(c.start());
      NormalizedLandmark end = handLandmarkList.get(c.end());
      float[] vertex = {start.getX(), start.getY(), end.getX(), end.getY()};
      FloatBuffer vertexBuffer =
          ByteBuffer.allocateDirect(vertex.length * 4)
              .order(ByteOrder.nativeOrder())
              .asFloatBuffer()
              .put(vertex);
      vertexBuffer.position(0);
      GLES20.glEnableVertexAttribArray(positionHandle);
      GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer);
      GLES20.glDrawArrays(GLES20.GL_LINES, 0, 2);
    }
  }

  private void drawCircle(float x, float y, float[] colorArray) {
    GLES20.glUniform4fv(colorHandle, 1, colorArray, 0);
    int vertexCount = NUM_SEGMENTS + 2;
    float[] vertices = new float[vertexCount * 3];
    vertices[0] = x;
    vertices[1] = y;
    vertices[2] = 0;
    for (int i = 1; i < vertexCount; i++) {
      float angle = 2.0f * i * (float) Math.PI / NUM_SEGMENTS;
      int currentIndex = 3 * i;
      vertices[currentIndex] = x + (float) (LANDMARK_RADIUS * Math.cos(angle));
      vertices[currentIndex + 1] = y + (float) (LANDMARK_RADIUS * Math.sin(angle));
      vertices[currentIndex + 2] = 0;
    }
    FloatBuffer vertexBuffer =
        ByteBuffer.allocateDirect(vertices.length * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertices);
    vertexBuffer.position(0);
    GLES20.glEnableVertexAttribArray(positionHandle);
    GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer);
    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, vertexCount);
  }

  private void drawHollowCircle(float x, float y, float[] colorArray) {
    GLES20.glUniform4fv(colorHandle, 1, colorArray, 0);
    int vertexCount = NUM_SEGMENTS + 1;
    float[] vertices = new float[vertexCount * 3];
    for (int i = 0; i < vertexCount; i++) {
      float angle = 2.0f * i * (float) Math.PI / NUM_SEGMENTS;
      int currentIndex = 3 * i;
      vertices[currentIndex] = x + (float) (HOLLOW_CIRCLE_RADIUS * Math.cos(angle));
      vertices[currentIndex + 1] = y + (float) (HOLLOW_CIRCLE_RADIUS * Math.sin(angle));
      vertices[currentIndex + 2] = 0;
    }
    FloatBuffer vertexBuffer =
        ByteBuffer.allocateDirect(vertices.length * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertices);
    vertexBuffer.position(0);
    GLES20.glEnableVertexAttribArray(positionHandle);
    GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer);
    GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 0, vertexCount);
  }

  private void drawResult() {

    float[] vertexData = {
            -1f, -1f,
            1f, -1f,
            -1f, 1f,
            1f, 1f,
            //用来 加一个 图片水印 到左上角
            -1f, 0.5f,
            0f, 0.5f,
            -1f, 1f,
            0f, 1f,

//            //用来 加一个文字水印 到右下角
//            0f, -1f,
//            1f, -1f,
//            0f, -0.8f,
//            1f, -0.8f
    };



    Bitmap bitmap = createTextImage("1234", 25,"#ff0000", "#00000000", 0);
    int textTextureId = loadBitmapTexture(bitmap);
    bitmap.recycle();

    GLES20.glEnableVertexAttribArray(positionHandle);

    GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 8,64);

    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textTextureId);
    //绘制水印
    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    //解绑纹理
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

  }

  public static int loadBitmapTexture(Bitmap bitmap) {
    int[] textureIds = new int[1];
    GLES20.glGenTextures(1, textureIds, 0);
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIds[0]);
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

    ByteBuffer bitmapBuffer = ByteBuffer.allocate(bitmap.getHeight() * bitmap.getWidth() * 4);
    bitmap.copyPixelsToBuffer(bitmapBuffer);
    bitmapBuffer.flip();

    GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, bitmap.getWidth(),
            bitmap.getHeight(), 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, bitmapBuffer);
    return textureIds[0];
  }

  public static Bitmap createTextImage(String text, int textSize, String textColor, String bgColor, int padding) {

    Paint paint = new Paint();
    paint.setColor(Color.parseColor(textColor));
    paint.setTextSize(textSize);
    paint.setStyle(Paint.Style.FILL);
    paint.setAntiAlias(true);

    float width = paint.measureText(text, 0, text.length());
    float top = paint.getFontMetrics().top;
    float bottom = paint.getFontMetrics().bottom;

    Bitmap bm = Bitmap.createBitmap((int) (width + padding * 2), (int) ((bottom - top) + padding * 2), Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bm);
    canvas.drawColor(Color.parseColor(bgColor));
    canvas.drawText(text, padding, -top + padding, paint);
    return bm;
  }
}
