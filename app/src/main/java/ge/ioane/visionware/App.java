package ge.ioane.visionware;

import android.app.Application;

import org.greenrobot.greendao.database.Database;

import ge.ioane.visionware.model.DaoMaster;
import ge.ioane.visionware.model.DaoSession;

/**
 * Created by ioane5 on 3/4/17.
 */
public class App extends Application {

    private static App sInstance;
    private DaoSession mDaoSession;

    public static App getsInstance() {
        return sInstance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
    }

    private void initializeGreenDao(String dbName) {
        DaoMaster.DevOpenHelper helper = new DaoMaster.DevOpenHelper(this, dbName);
        Database db = helper.getWritableDb();
        mDaoSession = new DaoMaster(db).newSession();
    }

    public DaoSession getDaoSession() {
        if (mDaoSession == null) {
            throw new IllegalStateException("Greendao wasn't initialized");
        }
        return mDaoSession;
    }

    public void initializeDaoSession(String uuid) {
        initializeGreenDao(uuid + "-db");
    }
}
