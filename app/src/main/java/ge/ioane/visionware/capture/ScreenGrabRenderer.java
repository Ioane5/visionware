package ge.ioane.visionware.capture;

/**
 * Created by ioane5 on 3/4/17.
 */

import android.graphics.Bitmap;
import android.opengl.GLException;
import android.opengl.GLSurfaceView;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.IntBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;


public class ScreenGrabRenderer implements GLSurfaceView.Renderer {


    TangoCameraScreengrabCallback mTangoCameraScreengrabCallback;

    GLSurfaceView.Renderer tangoCameraRenderer;
    private static final String TAG = ScreenGrabRenderer.class.getSimpleName();

    private String lastFileName = "unset";

    boolean grabNextScreen = false;

    int grabX = 0;
    int grabY = 0;
    int grabWidth = 640;
    int grabHeight = 320;

    public void setTangoCameraScreengrabCallback(TangoCameraScreengrabCallback aTangoCameraScreengrabCallback) {
        mTangoCameraScreengrabCallback = aTangoCameraScreengrabCallback;
    }

    public void grabNextScreen(int x, int y, int w, int h) {
        grabNextScreen = true;
        grabX = x;
        grabY = y;
        grabWidth = w;
        grabHeight = h;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        tangoCameraRenderer.onSurfaceCreated(gl, config);

    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        tangoCameraRenderer.onSurfaceChanged(gl, width, height);
    }

    @Override
    public void onDrawFrame(final GL10 gl) {
        tangoCameraRenderer.onDrawFrame(gl);
        if (grabNextScreen) {
            screenGrab(gl);
            grabNextScreen = false;
        }
    }


    /**
     * Creates a bitmap given a certain dimension and an OpenGL context
     * <p>
     * This code was lifted from here:
     * <p>
     * http://stackoverflow.com/questions/5514149/capture-screen-of-glsurfaceview-to-bitmap
     */
    private Bitmap createBitmapFromGLSurface(int x, int y, int w, int h, GL10 gl)
            throws OutOfMemoryError {
        int bitmapBuffer[] = new int[w * h];
        int bitmapSource[] = new int[w * h];
        IntBuffer intBuffer = IntBuffer.wrap(bitmapBuffer);
        intBuffer.position(0);

        try {
            gl.glReadPixels(x, y, w, h, GL10.GL_RGBA, GL10.GL_UNSIGNED_BYTE, intBuffer);
            int offset1, offset2;
            for (int i = 0; i < h; i++) {
                offset1 = i * w;
                offset2 = (h - i - 1) * w;
                for (int j = 0; j < w; j++) {
                    int texturePixel = bitmapBuffer[offset1 + j];
                    int blue = (texturePixel >> 16) & 0xff;
                    int red = (texturePixel << 16) & 0x00ff0000;
                    int pixel = (texturePixel & 0xff00ff00) | red | blue;
                    bitmapSource[offset2 + j] = pixel;
                }
            }
        } catch (GLException e) {
            Log.e(TAG, e.toString());
            return null;
        }

        return Bitmap.createBitmap(bitmapSource, w, h, Bitmap.Config.ARGB_8888);
    }


    /**
     * Writes a copy of the GLSurface backbuffer to storage
     */
    private void screenGrab(GL10 gl) {
        final Bitmap image = createBitmapFromGLSurface(grabX, grabY, grabWidth, grabHeight, gl);

        new Thread(new Runnable() {
            @Override
            public void run() {
                long fileprefix = System.currentTimeMillis();
                String targetPath = Environment.getExternalStorageDirectory() + "/RavenEye/Photos/";
                String imageFileName = fileprefix + ".png";
                String fullPath = "error";

                if (!(new File(targetPath)).exists()) {
                    new File(targetPath).mkdirs();
                }
                try {
                    File targetDirectory = new File(targetPath);
                    File photo = new File(targetDirectory, imageFileName);
                    FileOutputStream fos = new FileOutputStream(photo.getPath());
                    image.compress(Bitmap.CompressFormat.PNG, 100, fos);
                    fos.flush();
                    fos.close();
                    fullPath = targetPath + imageFileName;
//            Log.i(TAG, "Grabbed an image in target path:" + fullPath);

                    ///Notify the outer class(es)
                    if (mTangoCameraScreengrabCallback != null) {
                        mTangoCameraScreengrabCallback.newPhoto(fullPath);
                    } else {
                        Log.i(TAG, "Callback not set properly..");
                    }

                } catch (FileNotFoundException e) {
                    Log.e(TAG, "Exception " + e);
                    e.printStackTrace();
                } catch (IOException e) {
                    Log.e(TAG, "Exception " + e);
                    e.printStackTrace();
                }
                lastFileName = fullPath;
            }
        }).start();
    }


    /**
     * Constructor
     *
     * @param baseRenderer
     */
    public ScreenGrabRenderer(GLSurfaceView.Renderer baseRenderer) {
        tangoCameraRenderer = baseRenderer;
    }
}
