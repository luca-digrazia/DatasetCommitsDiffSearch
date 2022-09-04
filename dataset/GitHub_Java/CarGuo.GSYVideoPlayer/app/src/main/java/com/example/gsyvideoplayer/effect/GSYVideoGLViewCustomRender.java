package com.example.gsyvideoplayer.effect;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.opengl.Matrix;

import com.shuyu.gsyvideoplayer.R;
import com.shuyu.gsyvideoplayer.render.GSYVideoGLViewSimpleRender;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;


/**
 * 自定义渲染功能
 */
@SuppressLint("ViewConstructor")
public class GSYVideoGLViewCustomRender extends GSYVideoGLViewSimpleRender {

    private Bitmap mBitmap, mBitmap2;

    private int mTexturesBitmap[] = new int[1];

    //水印圖
    private BitmapEffect bitmapEffect = new BitmapEffect();

    public GSYVideoGLViewCustomRender() {
        super();
    }

    @Override
    protected void bindDrawFrameTexture() {
        super.bindDrawFrameTexture();
        int mFilterInputTextureUniform2 = GLES20.glGetUniformLocation(mProgram, "sTexture2");
        GLES20.glActiveTexture(GLES20.GL_TEXTURE3);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTexturesBitmap[0]);
        GLES20.glUniform1i(mFilterInputTextureUniform2, mTexturesBitmap[0]);
    }

    @Override
    public void onSurfaceChanged(GL10 glUnused, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 glUnused) {
        super.onDrawFrame(glUnused);
        float[] transform = new float[16];
        Matrix.setRotateM(transform, 0, 360 * 60 / 100, 0.0f, 0.0f, 1.0f);
        Matrix.scaleM(transform, 0, 50f / mSurfaceView.getWidth(), 50f / mSurfaceView.getWidth(), 1);
        GLES20.glUniformMatrix4fv(muMVPMatrixHandle, 1, false, transform, 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glFinish();
    }

    @Override
    public void onSurfaceCreated(GL10 glUnused, EGLConfig config) {
        super.onSurfaceCreated(glUnused, config);
        mBitmap = BitmapFactory.decodeResource(mSurfaceView.getResources(), R.drawable.unlock);
        mBitmap2 = BitmapFactory.decodeResource(mSurfaceView.getResources(), R.drawable.video_enlarge);
        GLES20.glGenTextures(1, mTexturesBitmap, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTexturesBitmap[0]);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, mBitmap, 0);
        mBitmap.recycle();
    }

    @Override
    protected String getVertexShader() {
        return super.getVertexShader();
    }

    @Override
    protected String getFragmentShader() {
        return bitmapEffect.getShader(mSurfaceView);
    }

    @Override
    public void releaseAll() {
        super.releaseAll();

    }
}


