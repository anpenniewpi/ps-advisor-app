package org.fundacionparaguaya.adviserplatform.data.repositories;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.support.annotation.Nullable;
import android.util.Log;
import org.fundacionparaguaya.adviserplatform.data.remote.AuthenticationManager;
import org.fundacionparaguaya.adviserplatform.data.remote.ConnectivityWatcher;
import org.fundacionparaguaya.adviserplatform.jobs.SyncJob;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.lang.ref.WeakReference;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.fundacionparaguaya.adviserplatform.data.repositories.SyncManager.SyncState.*;


/**
 * A utility that manages the synchronization of the local databases.
 */

@Singleton
public class SyncManager implements AuthenticationManager.AuthStateChangeHandler {
    public static final String TAG = "SyncManager";
    static final String KEY_LAST_SYNC_TIME = "lastSyncTime";
    static final long LAST_SYNC_ERROR_MARGIN // a margin which will always be resynced to
            = TimeUnit.DAYS.toMillis(1); // avoid problems where models are never synced

    private FamilyRepository mFamilyRepository;
    private SurveyRepository mSurveyRepository;
    private SnapshotRepository mSnapshotRepository;
    private ImageRepository mImageRepository;

    private SharedPreferences mPreferences;
    private boolean isOnline;

    private MutableLiveData<SyncProgress> mProgress;

    @Inject
    public SyncManager(FamilyRepository familyRepository,
                SurveyRepository surveyRepository,
                SnapshotRepository snapshotRepository,
                ImageRepository imageRepository,
                SharedPreferences preferences,
                ConnectivityWatcher connectivityWatcher) {

        mFamilyRepository = familyRepository;
        mSurveyRepository = surveyRepository;
        mSnapshotRepository = snapshotRepository;
        mImageRepository = imageRepository;

        mProgress = new MutableLiveData<>();

        mPreferences = preferences;
        long lastSyncTime = mPreferences.getLong(KEY_LAST_SYNC_TIME, -1);
        mProgress.setValue(new SyncProgress(lastSyncTime != -1 ? SYNCED : NEVER, lastSyncTime));

        connectivityWatcher.status().observeForever(this::setIsOnline);
    }

    public LiveData<SyncProgress> getProgress() {
        return mProgress;
    }

    private void setIsOnline(boolean online) {
        if (online) {
            long lastSyncTime = mProgress.getValue().getLastSyncedTime();
            isOnline = true;
            updateProgress(lastSyncTime != -1 ? SYNCED : NEVER);
        } else {
            isOnline = false;
            updateProgress(ERROR_NO_INTERNET);
        }
    }

    /**
     * Synchronizes the local database with the remote one.
     * @return Whether the sync was successful.
     */
    public boolean sync(AtomicBoolean isAlive) {
        if (!isOnline) return false;

        Log.d(TAG, "sync: Synchronizing the database...");
        updateProgress(SYNCING);
        boolean result = true;

        @Nullable Date lastSync;
        SyncProgress progress = mProgress.getValue();
        if (progress != null && progress.getLastSyncedTime() != -1) {
            lastSync = new Date(progress.getLastSyncedTime() - LAST_SYNC_ERROR_MARGIN);
        } else {
            lastSync = null;
        }

        BaseRepository[] repositoriesToSync = {mFamilyRepository, mSurveyRepository, mSnapshotRepository, mImageRepository};

        for(BaseRepository repo: repositoriesToSync)
        {
            if(!isAlive.get()) return false;

            try {
                result &= repo.sync(isAlive, lastSync);
            } catch (Exception e) {
                Log.e(TAG, "sync: Error while syncing!", e);
                result = false;
            }
        }

        if (result) {
            updateProgress(SYNCED, new Date().getTime());
        }
        else {
            updateProgress(ERROR_OTHER);
        }

        Log.d(TAG, String.format("sync: Finished the synchronization %s.",
                result ? "successfully" : "with errors"));

        return result;
    }


    /**
      * Cleans all of the repositories, removing all entries. Useful for when a user logs out.
      */
    public void clean() {
        Log.d(TAG, "clean: Cleaning the database...");
        updateProgress(SYNCING);
        mFamilyRepository.clean();
        mSurveyRepository.clean();
        mSnapshotRepository.clean();
        mImageRepository.clean();
        updateProgress(NEVER, -1);
        Log.d(TAG, "clean: Finished the database clean");
    }

    public CleanTask makeCleanTask()
    {
        return new CleanTask(this);
    }

    private void updateProgress(SyncState state) {
        SyncProgress progress = mProgress.getValue();
        if (progress == null) {
            progress = new SyncProgress(state);
        } else {
            progress = new SyncProgress(state, progress.getLastSyncedTime());
        }
        mProgress.postValue(progress);
    }

    private void updateProgress(SyncState state, long lastSyncTime) {
        mProgress.postValue(new SyncProgress(state, lastSyncTime));

        SharedPreferences.Editor editor = mPreferences.edit();
        if (lastSyncTime > -1L) {
            editor.putLong(KEY_LAST_SYNC_TIME, lastSyncTime);
        } else {
            editor.remove(KEY_LAST_SYNC_TIME);
        }
        editor.apply();
    }

    @Override
    public void onAuthStateChange(AuthenticationManager.AuthenticationStatus status) {
        switch (status)
        {
            case UNAUTHENTICATED:
                makeCleanTask().execute();
                SyncJob.cancelAll();
                break;

            case AUTHENTICATED:
                SyncJob.sync();
                break;

            default:
                //not relevant to sync manager
        }
    }

    /**
     * The states that a sync can be in.
     */
    public enum SyncState {
        NEVER,
        SYNCING,
        SYNCED,
        ERROR_NO_INTERNET,
        ERROR_OTHER
    }

    /**
     * The progress of a sync.
     */
    public class SyncProgress {
        private SyncState mState;
        private long mLastSyncedTime;

        SyncProgress(SyncState syncState) {
            this(syncState, -1);
        }

        SyncProgress(SyncState syncState, long lastSyncedTime) {
            mState = syncState;
            mLastSyncedTime = lastSyncedTime;
        }

        public SyncState getSyncState() {
            return mState;
        }

        public long getLastSyncedTime() {
            return mLastSyncedTime;
        }
    }

    public static class CleanTask extends AsyncTask<Void, Void, Void>
    {
        private WeakReference<SyncManager> mSyncManager;

        CleanTask(SyncManager manager)
        {
            mSyncManager = new WeakReference<>(manager);
        }
        @Override
        protected Void doInBackground(Void... voids) {

            if(mSyncManager.get()!=null)
            {
                mSyncManager.get().clean();
            }

            return null;
        }
    }
}
