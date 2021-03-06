package ge.ioane.visionware.capture;

import android.opengl.GLSurfaceView;
import android.util.Log;

import com.google.atap.tangoservice.TangoCameraPreview;

import ge.ioane.visionware.activities.MainActivity;

/**
 * Created by ioane5 on 3/4/17.
 */
public class ReadableTangoCameraPreview extends TangoCameraPreview implements TangoCameraScreengrabCallback {

    MainActivity mainActivity;
    private static final String TAG = ReadableTangoCameraPreview.class.getSimpleName();

    //An intercept renderer
    ScreenGrabRenderer screenGrabRenderer;

    private boolean takeSnapShot = false;

    @Override
    public void setRenderer(GLSurfaceView.Renderer renderer) {
        //Create our "man in the middle"
        screenGrabRenderer = new ScreenGrabRenderer(renderer);

        //Set it's call back
        screenGrabRenderer.setTangoCameraScreengrabCallback(this);

        //Tell the TangoCameraPreview class to use this intermediate renderer
        super.setRenderer(screenGrabRenderer);
        Log.i(TAG, "Intercepted the renderer!!!");
    }


    /**
     * Set a trigger for snapshot.  Call this from main activity
     * in response to a use input
     */
    public void takeSnapShot() {
        takeSnapShot = true;
    }

    @Override
    public void onFrameAvailable() {
        super.onFrameAvailable();
        if (takeSnapShot) {
            //screenGrabWithRoot();
            screenGrabRenderer.grabNextScreen(0, 0, this.getWidth(), this.getHeight());
            takeSnapShot = false;
        }
    }

    public ReadableTangoCameraPreview(MainActivity context) {
        super(context);
        mainActivity = context;

    }

    public void newPhoto(String aNewPhotoPath) {
        mainActivity.newPhoto(aNewPhotoPath);

    }

}
