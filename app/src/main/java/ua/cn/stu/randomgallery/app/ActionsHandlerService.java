package ua.cn.stu.randomgallery.app;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.PersistableBundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import ua.cn.stu.randomgallery.RandomGalleryClient;
import ua.cn.stu.randomgallery.RequestFailedException;

public final class ActionsHandlerService extends JobService {
    public static final int UPDATE_CHECK_JOB_ID = 1403;
    public static final String ACTION_CHECK_FOR_UPDATES = "ua.cn.stu.randomgallery.action.CHECK_FOR_UPDATES";
    private static final String TAG = "GalleryUpdateCheck";
    private static final String EXTRA_SHOW_RESULT_TOAST = "show_result_toast";
    private static final AtomicBoolean CHECK_RUNNING = new AtomicBoolean();
    private static final AtomicBoolean CANCELLED_BY_USER = new AtomicBoolean();

    private final Object lock = new Object();
    private ExecutorService executor;
    private Future<?> future;
    private JobParameters activeParameters;

    public static int startCheck(@NonNull Context context) {
        return scheduleCheck(context, false);
    }

    public static int startConfirmedUpdateCheck(@NonNull Context context) {
        return scheduleCheck(context, true);
    }

    public static void cancelPendingCheck(@NonNull Context context) {
        CANCELLED_BY_USER.set(true);
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler != null) scheduler.cancel(UPDATE_CHECK_JOB_ID);
    }

    private static int scheduleCheck(Context context, boolean showResultToast) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) return JobScheduler.RESULT_FAILURE;
        PersistableBundle extras = new PersistableBundle();
        extras.putBoolean(EXTRA_SHOW_RESULT_TOAST, showResultToast);
        JobInfo job = new JobInfo.Builder(UPDATE_CHECK_JOB_ID,
                new ComponentName(context, ActionsHandlerService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setExtras(extras)
                .setPersisted(false)
                .build();
        int result = scheduler.schedule(job);
        if (result == JobScheduler.RESULT_SUCCESS) {
            CANCELLED_BY_USER.set(false);
            ((GalleryApplication) context.getApplicationContext()).getSyncState().setChecking();
            NotificationHelper.cancelUpdatePrompt(context);
        } else {
            ((GalleryApplication) context.getApplicationContext()).getSyncState().setError();
        }
        return result;
    }

    @Override
    public boolean onStartJob(@NonNull JobParameters params) {
        if (!CHECK_RUNNING.compareAndSet(false, true)) {
            jobFinished(params, false);
            return false;
        }
        synchronized (lock) {
            activeParameters = params;
            GalleryApplication application = (GalleryApplication) getApplication();
            application.getSyncState().setChecking();
            Log.d(TAG, "update check started");
            boolean showResultToast = params.getExtras().getBoolean(EXTRA_SHOW_RESULT_TOAST, false);
            executor = Executors.newSingleThreadExecutor();
            future = executor.submit(() -> checkForUpdates(application, params, showResultToast));
        }
        return true;
    }

    private void checkForUpdates(GalleryApplication application, JobParameters params,
                                 boolean showResultToast) {
        boolean available = false;
        boolean failed = false;
        try {
            RandomGalleryClient client = application.getGalleryClient();
            available = client.hasUpdates();
            Log.d(TAG, "update check completed; updatesAvailable=" + available);
        } catch (IOException | RequestFailedException exception) {
            failed = true;
            Log.e(TAG, "update check failed", exception);
        }
        boolean resultAvailable = available;
        boolean resultFailed = failed;
        MainThreadHandler.run(() -> finishCheck(application, params, resultAvailable,
                resultFailed, showResultToast));
    }

    private void finishCheck(GalleryApplication application, JobParameters params,
                             boolean available, boolean failed, boolean showResultToast) {
        synchronized (lock) {
            if (activeParameters != params) return;
            activeParameters = null;
            future = null;
            if (failed) {
                application.getSyncState().setError();
            } else {
                application.getSyncState().setUpdateAvailable(available);
                if (available) {
                    if (showResultToast) {
                        Toast.makeText(getApplicationContext(), R.string.notification_updates_found,
                                Toast.LENGTH_LONG).show();
                    }
                    NotificationHelper.showUpdateAvailable(getApplicationContext());
                }
            }
            jobFinished(params, false);
            CHECK_RUNNING.set(false);
            shutdownExecutor(false);
        }
    }

    @Override
    public boolean onStopJob(@NonNull JobParameters params) {
        Log.d(TAG, "update check stopped");
        synchronized (lock) {
            if (activeParameters == params) {
                activeParameters = null;
                if (future != null) future.cancel(true);
                future = null;
                shutdownExecutor(true);
            }
        }
        CHECK_RUNNING.set(false);
        if (CANCELLED_BY_USER.get()) {
            ((GalleryApplication) getApplication()).getSyncState().setCancelled();
            return false;
        }
        return true;
    }

    private void shutdownExecutor(boolean interrupt) {
        if (executor == null) return;
        if (interrupt) executor.shutdownNow();
        else executor.shutdown();
        executor = null;
    }

}
