package ge.ioane.visionware;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.TangoCameraIntrinsics;
import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoCoordinateFramePair;
import com.google.atap.tangoservice.TangoErrorException;
import com.google.atap.tangoservice.TangoInvalidException;
import com.google.atap.tangoservice.TangoOutOfDateException;
import com.google.atap.tangoservice.TangoPoseData;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import clarifai2.api.ClarifaiBuilder;
import clarifai2.api.ClarifaiClient;
import clarifai2.api.request.ClarifaiRequest;
import clarifai2.dto.input.ClarifaiInput;
import clarifai2.dto.input.image.ClarifaiFileImage;
import clarifai2.dto.model.output.ClarifaiOutput;
import clarifai2.dto.prediction.Concept;
import ge.ioane.visionware.camera.ReadableTangoCameraPreview;
import ge.ioane.visionware.camera.TangoCameraScreengrabCallback;
import okhttp3.OkHttpClient;

public class MainActivity extends AppCompatActivity implements TangoCameraScreengrabCallback {

    private static final String TAG = MainActivity.class.getSimpleName();

    private static final String KEY_ADF_UUID = "ADF_UUID";

    private static final double UPDATE_INTERVAL_MS = 100.0;
    public static final int IMAGE_CAPTURE_INTERVAL = 5000;

    private ClarifaiClient mClarifai;
    private Tango mTango;
    private TangoConfig mConfig;
    private boolean mIsRelocalized;
    private boolean mPreviousLocalizationState = false;
    private ReadableTangoCameraPreview mTangoCameraPreview;
    private long mPreviousImageCapture = 0;

    private double mPreviousPoseTimeStamp;
    private double mTimeToNextUpdate = UPDATE_INTERVAL_MS;

    private TextView mLocalizationTextView;
    private TextView mStatusTextView;
    private final Object mSharedLock = new Object();

    public static void start(Context context, String adfUUID) {
        Log.d(TAG, "start() called with: context = [" + context + "], adfUUID = [" + adfUUID + "]");

        Intent starter = new Intent(context, MainActivity.class);
        starter.putExtra(KEY_ADF_UUID, adfUUID);
        context.startActivity(starter);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FrameLayout container = (FrameLayout) findViewById(R.id.container);
        mTangoCameraPreview = new ReadableTangoCameraPreview(this);
        container.addView(mTangoCameraPreview);

        mStatusTextView = (TextView) findViewById(R.id.tv_status);
        mLocalizationTextView = (TextView) findViewById(R.id.tv_localization);
    }

    private void setUpClarifai() {
        Log.d(TAG, "setUpClarifai: start");
        mClarifai = new ClarifaiBuilder("OVbR0VBLKK-lJambAJWUmiFgPkR5JuvYcCy3n9LJ", "i3aH24nnOE_HI79hR5r6VVRYO_hNVHZITGOITzI_")
                .client(new OkHttpClient()) // OPTIONAL. Allows customization of OkHttp by the user
                .buildSync();
        Log.d(TAG, "setUpClarifai: end");
    }

    @Override
    protected void onResume() {
        super.onResume();

        mTango = new Tango(this, new Runnable() {
            @Override
            public void run() {
                synchronized (MainActivity.this) {
                    mTangoCameraPreview.connectToTangoCamera(mTango, TangoCameraIntrinsics.TANGO_CAMERA_COLOR);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            onLocalizationStateChanged(false);
                        }
                    });
                    try {
                        mConfig = setTangoConfig(mTango, false, true);
                        mTango.connect(mConfig);
                        startupTango();
                    } catch (TangoOutOfDateException e) {
                        Log.e(TAG, "Tango Out of Date", e);
                    } catch (TangoErrorException e) {
                        Log.e(TAG, "Tango Error", e);
                    } catch (TangoInvalidException e) {
                        Log.e(TAG, "Tango Invalid ", e);
                    } catch (SecurityException e) {
                        // Area Learning permissions are required. If they are not available,
                        // SecurityException is thrown.
                        Log.e(TAG, "No Permissions", e);
                    }
                }
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Clear the relocalization state: we don't know where the device will be since our app
        // will be paused.
        mIsRelocalized = false;
        synchronized (this) {
            try {
                mTango.disconnect();
                mTangoCameraPreview.disconnectFromTangoCamera();
            } catch (TangoErrorException e) {
                Log.e(TAG, "Tango Error", e);
            }
        }
    }


    private TangoConfig setTangoConfig(Tango tango, boolean isLearningMode, boolean isLoadAdf) {
        // Use default configuration for Tango Service.
        TangoConfig config = tango.getConfig(TangoConfig.CONFIG_TYPE_DEFAULT);
        // Check if learning mode
        if (isLearningMode) {
            // Set learning mode to config.
            config.putBoolean(TangoConfig.KEY_BOOLEAN_LEARNINGMODE, true);

        }
        // Check for Load ADF/Constant Space relocalization mode.
        if (isLoadAdf) {
            config.putString(TangoConfig.KEY_STRING_AREADESCRIPTION, getIntent().getStringExtra(KEY_ADF_UUID));
        }
        return config;
    }


    /**
     * Set up the callback listeners for the Tango service and obtain other parameters required
     * after Tango connection.
     */
    private void startupTango() {
        // Set Tango Listeners for Poses Device wrt Start of Service, Device wrt
        // ADF and Start of Service wrt ADF.
        ArrayList<TangoCoordinateFramePair> framePairs = new ArrayList<TangoCoordinateFramePair>();
        framePairs.add(new TangoCoordinateFramePair(
                TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                TangoPoseData.COORDINATE_FRAME_DEVICE));
        framePairs.add(new TangoCoordinateFramePair(
                TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
                TangoPoseData.COORDINATE_FRAME_DEVICE));
        framePairs.add(new TangoCoordinateFramePair(
                TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
                TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE));

        mTango.connectListener(framePairs, new Tango.TangoUpdateCallback() {

            @Override
            public void onFrameAvailable(int cameraId) {
                super.onFrameAvailable(cameraId);

                if (cameraId == TangoCameraIntrinsics.TANGO_CAMERA_COLOR) {
                    mTangoCameraPreview.onFrameAvailable();

                    long currentTime = System.currentTimeMillis();
                    if (currentTime - mPreviousImageCapture > IMAGE_CAPTURE_INTERVAL) {
                        mPreviousImageCapture = currentTime;
                        // TODO
                        mTangoCameraPreview.takeSnapShot();
                    }
                }
            }

            @Override
            public void onPoseAvailable(final TangoPoseData pose) {
                super.onPoseAvailable(pose);

                // Make sure to have atomic access to Tango Data so that UI loop doesn't interfere
                // while Pose call back is updating the data.
                synchronized (mSharedLock) {
                    // Check for Device wrt ADF pose, Device wrt Start of Service pose, Start of
                    // Service wrt ADF pose (This pose determines if the device is relocalized or
                    // not).
                    if (pose.baseFrame == TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION
                            && pose.targetFrame == TangoPoseData
                            .COORDINATE_FRAME_START_OF_SERVICE) {
                        if (pose.statusCode == TangoPoseData.POSE_VALID) {
                            mIsRelocalized = true;
                        } else {
                            mIsRelocalized = false;
                        }
                    } else if (pose.baseFrame == TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION
                            && pose.targetFrame == TangoPoseData.COORDINATE_FRAME_DEVICE) {
                        if (pose.statusCode == TangoPoseData.POSE_VALID) {

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
//                                    updateStatus(Arrays.toString(pose.translation));
                                    updateStatus(RelativeCaltulator.lookAt(pose.rotation, pose.translation, new double[]{-1, 3}));
                                }
                            });
                        }
                    }
                }

                final double deltaTime = (pose.timestamp - mPreviousPoseTimeStamp) *
                        1000;
                mPreviousPoseTimeStamp = pose.timestamp;
                mTimeToNextUpdate -= deltaTime;

                if (mTimeToNextUpdate < 0.0) {
                    mTimeToNextUpdate = UPDATE_INTERVAL_MS;

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            synchronized (mSharedLock) {
                                if (mPreviousLocalizationState != mIsRelocalized) {
                                    mPreviousLocalizationState = mIsRelocalized;
                                    onLocalizationStateChanged(mIsRelocalized);
                                }
                            }
                        }
                    });
                }
            }
        });
    }

    private void onLocalizationStateChanged(boolean isLocalized) {
        if (isLocalized) {
            mLocalizationTextView.setText("Localized");
            Log.d(TAG, "Localized called");
        } else {
            mLocalizationTextView.setText("Not Localized");
            Log.d(TAG, "Not Localized");
        }
    }


    private int mCount = 0;

    private void updateStatus(String status) {
        mCount++;
        if (mCount > 4) {
            mCount = 0;
            mStatusTextView.setText(status);
        }
    }


    @Override
    public void newPhoto(String path) {
        if (mClarifai == null) {
            setUpClarifai();
        }
        Log.d(TAG, "newPhoto() called with: path = [" + path + "]");
        mClarifai.getDefaultModels()
                .generalModel()
                .predict()
                .withInputs(ClarifaiInput.forImage(ClarifaiFileImage.of(new File(path))))
                .executeAsync(new ClarifaiRequest.OnSuccess<List<ClarifaiOutput<Concept>>>() {
                    @Override
                    public void onClarifaiResponseSuccess(List<ClarifaiOutput<Concept>> clarifaiOutputs) {
                        for (ClarifaiOutput<Concept> clarifaiOutput : clarifaiOutputs) {
                            Log.d(TAG, "onClarifaiResponseSuccess: clarifaiOutputs");
                            for (Concept concept : clarifaiOutput.data()) {
                                Log.d(TAG, "onClarifaiResponseSuccess: " + concept.name() + " " + concept.value());
                            }
                        }
                    }
                });
    }
}
