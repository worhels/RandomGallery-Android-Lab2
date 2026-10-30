package ua.cn.stu.randomgallery.app;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import ua.cn.stu.randomgallery.RandomGalleryClient;
import ua.cn.stu.randomgallery.RequestFailedException;

public final class SyncService extends JobService {
    public static final int JOB_ID = 1402;
    private static final String TAG = "RandomGallerySync";
    private static volatile boolean cancelledByUser;

    private final Object lock = new Object();
    private ExecutorService executor;
    private Future<?> future;
    private JobParameters activeParameters;
    private boolean running;

    public static int scheduleUpdate(@NonNull Context context) {
        GalleryApplication application = (GalleryApplication) context.getApplicationContext();
        SyncState.Snapshot state = application.getSyncState().getSnapshot();
        if (state.status == SyncState.Status.RUNNING) return JobScheduler.RESULT_SUCCESS;

        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) {
            application.getSyncState().setError();
            return JobScheduler.RESULT_FAILURE;
        }
        JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(context, SyncService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                .setPersisted(false)
                .build();
        int result = scheduler.schedule(job);
        if (result == JobScheduler.RESULT_SUCCESS) {
            NetworkStatus network = NetworkStatus.read(context);
            cancelledByUser = false;
            application.getSyncState().setScheduled(network.hasInternet, network.unmetered);
            NotificationHelper.cancelUpdatePrompt(context);
        } else {
            application.getSyncState().setError();
        }
        return result;
    }

    public static void cancelScheduledUpdate(Context context) {
        cancelledByUser = true;
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler != null) scheduler.cancel(JOB_ID);
        GalleryApplication application = (GalleryApplication) context.getApplicationContext();
        application.getSyncState().setCancelled();
        NotificationHelper.cancelSyncNotification(context);
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        Log.d(TAG, "onStartJob jobId=" + params.getJobId());
        synchronized (lock) {
            if (running) return false;
            running = true;
            activeParameters = params;
            cancelledByUser = false;
            GalleryApplication application = (GalleryApplication) getApplication();
            application.getSyncState().setRunning();
            Log.d(TAG, "gallery sync started");
            NotificationHelper.showSyncProgress(this, 0);
            executor = Executors.newSingleThreadExecutor();
            future = executor.submit(() -> runSync(application, params));
        }
        return true;
    }

    private void runSync(GalleryApplication application, JobParameters params) {
        boolean success = false;
        try {
            RandomGalleryClient client = application.getGalleryClient();
            success = client.syncGallery(progress -> {
                Log.d(TAG, "gallery sync progress=" + progress + "%");
                application.getSyncState().setProgress(progress);
                NotificationHelper.showSyncProgress(this, progress);
            });
        } catch (IOException | RequestFailedException exception) {
            Log.e(TAG, "gallery sync failed", exception);
            application.getSyncState().setError();
        }
        boolean result = success;
        MainThreadHandler.run(() -> finishJob(application, params, result));
    }

    private void finishJob(GalleryApplication application, JobParameters params, boolean success) {
        synchronized (lock) {
            if (!running || activeParameters != params) return;
            running = false;
            activeParameters = null;
            future = null;
            if (success) application.getSyncState().setSuccess();
            else application.getSyncState().setError();
            Log.d(TAG, success ? "gallery sync completed successfully" : "gallery sync completed with error");
            NotificationHelper.cancelSyncNotification(this);
            jobFinished(params, false);
            stopSelf();
            if (executor != null) {
                executor.shutdown();
                executor = null;
            }
        }
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Log.d(TAG, "onStopJob jobId=" + params.getJobId());
        synchronized (lock) {
            if (activeParameters == params) {
                running = false;
                activeParameters = null;
                if (future != null) future.cancel(true);
                future = null;
                if (executor != null) {
                    executor.shutdownNow();
                    executor = null;
                }
            }
        }
        NotificationHelper.cancelSyncNotification(this);
        if (cancelledByUser) {
            ((GalleryApplication) getApplication()).getSyncState().setCancelled();
            return false;
        }
        NetworkStatus network = NetworkStatus.read(this);
        ((GalleryApplication) getApplication()).getSyncState()
                .setScheduled(network.hasInternet, network.unmetered);
        return true;
    }

    @Override
    public void onNetworkChanged(JobParameters params) {
        NetworkStatus network = NetworkStatus.read(this);
        ((GalleryApplication) getApplication()).getSyncState()
                .updateNetworkStatus(network.hasInternet, network.unmetered);
    }
}
