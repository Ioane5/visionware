package ge.ioane.visionware;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.google.atap.tangoservice.Tango;

import java.util.ArrayList;

public class AdfListActivity extends AppCompatActivity {

    private static final String TAG = AdfListActivity.class.getSimpleName();

    // Permission request action.
    public static final int REQUEST_CODE_TANGO_PERMISSION = 0;
    private Tango mTango;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_adf_list);

        if (!Tango.hasPermission(this, Tango.PERMISSIONTYPE_ADF_LOAD_SAVE)) {
            startActivityForResult(Tango.getRequestPermissionIntent(Tango.PERMISSIONTYPE_ADF_LOAD_SAVE), REQUEST_CODE_TANGO_PERMISSION);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (hasAdfPermission()) {
            mTango = new Tango(this, new Runnable() {
                @Override
                public void run() {
                    synchronized (AdfListActivity.this) {
                        final ArrayList<String> fullUuidList = mTango.listAreaDescriptions();
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                MainActivity.start(AdfListActivity.this, fullUuidList.get(fullUuidList.size() - 1));
                                finish();
                            }
                        });
                    }
                }
            });
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        synchronized (this) {
            if (mTango != null) {
                mTango.disconnect();
            }
        }
    }

    private boolean hasAdfPermission() {
        return Tango.hasPermission(this, Tango.PERMISSIONTYPE_ADF_LOAD_SAVE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_TANGO_PERMISSION) {
            if (resultCode != RESULT_OK) {
                finish();
            }
        }
    }
}
