package ge.ioane.visionware.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Pair;
import android.view.Menu;
import android.view.View;
import android.widget.CheckBox;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import clarifai2.api.ClarifaiBuilder;
import clarifai2.api.ClarifaiClient;
import clarifai2.dto.input.ClarifaiInput;
import clarifai2.dto.input.image.ClarifaiFileImage;
import clarifai2.dto.model.output.ClarifaiOutput;
import clarifai2.dto.prediction.Concept;
import ge.ioane.visionware.App;
import ge.ioane.visionware.R;
import ge.ioane.visionware.RelativeCaltulator;
import ge.ioane.visionware.VoiceCommandDetector;
import ge.ioane.visionware.capture.ReadableTangoCameraPreview;
import ge.ioane.visionware.capture.TangoCameraScreengrabCallback;
import ge.ioane.visionware.model.Item;
import ge.ioane.visionware.model.ItemDao;
import okhttp3.OkHttpClient;

public class MainActivity extends AppCompatActivity implements TangoCameraScreengrabCallback, VoiceCommandDetector.CommandCallbacks {

    private static final String TAG = MainActivity.class.getSimpleName();

    private static final String KEY_ADF_UUID = "ADF_UUID";

    private static final double UPDATE_INTERVAL_MS = 100.0;
    public static final int IMAGE_CAPTURE_INTERVAL = 6000;

    private TextToSpeech mTextToSpeech;
    private TangoPoseData mSnapshotPose = null;
    private TangoPoseData mCurrentPose;
    private Tango mTango;
    private TangoConfig mConfig;
    private boolean mIsRelocalized;
    private boolean mPreviousLocalizationState = false;
    private ReadableTangoCameraPreview mTangoCameraPreview;
    private long mPreviousImageCapture = 0;

    private double mPreviousPoseTimeStamp;
    private double mTimeToNextUpdate = UPDATE_INTERVAL_MS;

    private ClarifaiClient mClarifai;
    private VoiceCommandDetector mVoiceCommandDetector;
    private TextView mLocalizationTextView;
    private TextView mStatusTextView;
    private final Object mSharedLock = new Object();
    private boolean mLearnClarifai;

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
        App.getsInstance().initializeDaoSession(getIntent().getStringExtra(KEY_ADF_UUID));

        FrameLayout container = (FrameLayout) findViewById(R.id.container);
        mTangoCameraPreview = new ReadableTangoCameraPreview(this);
        container.addView(mTangoCameraPreview);

        mStatusTextView = (TextView) findViewById(R.id.tv_status);
        mLocalizationTextView = (TextView) findViewById(R.id.tv_localization);

        mVoiceCommandDetector = new VoiceCommandDetector(this, this);
        mTextToSpeech = new TextToSpeech(this, status -> Log.d(TAG, "onInit() called with: status = [" + status + "]"));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);

        CheckBox checkBox = (CheckBox) menu.findItem(R.id.menu_learn).getActionView();
        checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> mLearnClarifai = isChecked);
        checkBox.setChecked(true);
        return true;
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

        mTango = new Tango(this, () -> {
            synchronized (MainActivity.this) {
                mTangoCameraPreview.connectToTangoCamera(mTango, TangoCameraIntrinsics.TANGO_CAMERA_COLOR);

                runOnUiThread(() -> onLocalizationStateChanged(false));
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
        );
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
        ArrayList<TangoCoordinateFramePair> framePairs = new ArrayList<>();
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
                    if (currentTime - mPreviousImageCapture > IMAGE_CAPTURE_INTERVAL && mIsRelocalized) {
                        mPreviousImageCapture = currentTime;
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
                            mCurrentPose = pose;
                            runOnUiThread(() -> {
//                                    updateStatus(Arrays.toString(pose.translation));
                                if (mSnapshotPose == null) {
                                    mSnapshotPose = pose;
                                }
                                updateStatus(RelativeCaltulator.lookAtInfo(pose.rotation, pose.translation, new double[]{-1, -3}));
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

                    runOnUiThread(() -> {
                        synchronized (mSharedLock) {
                            if (mPreviousLocalizationState != mIsRelocalized) {
                                mPreviousLocalizationState = mIsRelocalized;
                                onLocalizationStateChanged(mIsRelocalized);
                            }
                        }
                    });
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mTextToSpeech.shutdown();
        mVoiceCommandDetector.onDestroy();
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
        if (!mLearnClarifai) {
            return;
        }
        TangoPoseData pose = mSnapshotPose;
        mSnapshotPose = null;

        double[] position = pose.translation;
        Log.d(TAG, "newPhoto() called with: path = [" + path + "]");
        mClarifai.getDefaultModels()
                .generalModel()
                .predict()
                .withInputs(ClarifaiInput.forImage(ClarifaiFileImage.of(new File(path))))
                .executeAsync(clarifaiOutputs -> {
                    for (ClarifaiOutput<Concept> clarifaiOutput : clarifaiOutputs) {
                        for (Concept concept : clarifaiOutput.data()) {
                            if (concept.value() > 0.94f) {
                                createItem(concept.name(), position);
                            }
                        }
                    }
                });
    }

    private void createItem(String name, double[] position) {
        Item newItem = new Item(name, position[0], position[1]);
        new Thread(() -> App.getsInstance().getDaoSession().getItemDao().insert(newItem)).start();
    }

    @Override
    public void findItemWithNameCommand(String itemName) {
        Log.d(TAG, "findItemWithNameCommand() called with: itemName = [" + itemName + "]");
        if (!mIsRelocalized) {
            speak("Sorry, still trying to localize");
            return;
        }
        new Thread(() -> {
            List<Item> items = App.getsInstance()
                    .getDaoSession()
                    .getItemDao()
                    .queryBuilder()
                    .where(ItemDao.Properties.Name.eq(itemName))
                    .build()
                    .list();

            ArrayList<Item> selectedItems = new ArrayList<>();
            Log.d(TAG, "findItemWithNameCommand: " + selectedItems);
            for (Item item : items) {
                boolean isNear = false;
                for (Item selectedItem : selectedItems) {
                    double dist = RelativeCaltulator.distance(item.getPosition(), selectedItem.getPosition());
                    if (dist < 0.2) {
                        isNear = true;
                        break;
                    }
                }
                if (!isNear) {
                    selectedItems.add(item);
                }
            }

            Collections.sort(selectedItems, (o1, o2) -> {
                double d1 = RelativeCaltulator.distance(mCurrentPose.translation, o1.getPosition());
                double d2 = RelativeCaltulator.distance(mCurrentPose.translation, o2.getPosition());
                return Double.compare(d1, d2);
            });
            runOnUiThread(() -> onItemsFound(selectedItems, itemName));
        }).start();
    }

    @Override
    public void onNoCommand() {

    }

    public void onItemsFound(List<Item> items, String name) {
        Log.d(TAG, "onItemsFound() called with: items = [" + items + "], name = [" + name + "]");
        if (items.size() == 0) {
            speak("We couldn't find any " + name);
        } else {
            speak(String.format("I found %d %s%s", items.size(), name, items.size() > 1 ? "s" : ""));

            String text = "The ";
            if (items.size() > 1) {
                text += "nearest ";
            }
            Item item = items.get(0);
            double distance = RelativeCaltulator.distance(mCurrentPose.translation, item.getPosition());
            distance = Math.round(distance - 0.1) + 0.1;

            String direction = "";
            double angle = RelativeCaltulator.getLookAngle(mCurrentPose.rotation, mCurrentPose.translation, item.getPosition());
            double absAngle = Math.abs(angle);
            if (absAngle > 145) {
                direction = "at the back";
            } else if (absAngle < 30) {
                direction = "in front";
            } else {
                if (absAngle < 60) {
                    direction = "little ";
                }
                if (angle > 0) {
                    direction += "right";
                } else {
                    direction += "left";
                }
            }
            text += String.format("%s is %.1f meter%s away %s of you", name, distance, distance > 1.5 ? "s" : "", direction);
            speak(text);
        }
    }

    @Override
    public void itemsNearbyCommand() {
        new Thread(() -> {
            List<Item> allItems = App.getsInstance().getDaoSession().getItemDao().loadAll();

            HashMap<String, Integer> itemCountMap = new HashMap<>();
            for (Item allItem : allItems) {
                if (!itemCountMap.containsKey(allItem.getName())) {
                    itemCountMap.put(allItem.getName(), 1);
                } else {
                    itemCountMap.put(allItem.getName(), itemCountMap.get(allItem.getName()) + 1);
                }
            }
            runOnUiThread(() -> {
                onNearbyItemsFound(itemCountMap);
            });
        }).start();
    }

    private void onNearbyItemsFound(HashMap<String, Integer> itemCountMap) {
        ArrayList<Pair<String, Integer>> items = new ArrayList<>();
        for (String s : itemCountMap.keySet()) {
            items.add(new Pair<>(s, itemCountMap.get(s)));
        }
        Collections.sort(items, (x1, x2) -> Integer.compare(x2.second, x1.second));

        String text = "There are ";
        for (int i = 0; i < Math.min(5, items.size()); i++) {
            Pair<String, Integer> item = items.get(i);
            text += String.format("%d %s%s", item.second, item.first, item.second > 1 ? "s" : "");
        }
        if (items.size() - 5 > 0) {
            text += String.format(" and %d more kind of items", items.size() - 5);
        }
        speak(text);
    }

    private void speak(String text) {
        mTextToSpeech.speak(text, TextToSpeech.QUEUE_ADD, null);
    }

    public void onActivateListener(View view) {
        if (!mIsRelocalized) {
            speak("Sorry, still localizing");
            return;
        }
        mVoiceCommandDetector.activateCommandRecognition();
    }
}
