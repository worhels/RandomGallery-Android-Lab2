package ua.cn.stu.randomgallery.app;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.widget.Toast;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import ua.cn.stu.randomgallery.RandomGalleryClient;
import ua.cn.stu.randomgallery.RequestFailedException;

public final class ActionsHandlerService extends Service {
    public static final String ACTION_CHECK_FOR_UPDATES = "ua.cn.stu.randomgallery.action.CHECK_FOR_UPDATES";
    private static final String EXTRA_SHOW_RESULT_TOAST = "show_result_toast";
    private static final AtomicBoolean CHECK_RUNNING = new AtomicBoolean();

    private ExecutorService executor;

    public static void startCheck(Context context) {
        startCheck(context, false);
    }

    public static void startConfirmedUpdateCheck(Context context) {
        startCheck(context, true);
    }

    private static void startCheck(Context context, boolean showResultToast) {
        context.startService(new Intent(context, ActionsHandlerService.class)
                .setAction(ACTION_CHECK_FOR_UPDATES)
                .putExtra(EXTRA_SHOW_RESULT_TOAST, showResultToast));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || !ACTION_CHECK_FOR_UPDATES.equals(intent.getAction())) {
            stopSelfResult(startId);
            return START_NOT_STICKY;
        }
        if (!CHECK_RUNNING.compareAndSet(false, true)) return START_NOT_STICKY;
        boolean showResultToast = intent.getBooleanExtra(EXTRA_SHOW_RESULT_TOAST, false);
        GalleryApplication application = (GalleryApplication) getApplication();
        application.getSyncState().setChecking();
        executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                RandomGalleryClient client = application.getGalleryClient();
                boolean available = client.hasUpdates();
                application.getSyncState().setUpdateAvailable(available);
                if (available) {
                    MainThreadHandler.run(() -> {
                        if (showResultToast) {
                            Toast.makeText(getApplicationContext(), R.string.notification_updates_found,
                                    Toast.LENGTH_LONG).show();
                        }
                        NotificationHelper.showUpdateAvailable(getApplicationContext());
                    });
                }
            } catch (IOException | RequestFailedException exception) {
                application.getSyncState().setError();
            } finally {
                CHECK_RUNNING.set(false);
                MainThreadHandler.run(() -> stopSelfResult(startId));
            }
        });
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (executor != null) executor.shutdownNow();
        super.onDestroy();
    }
}
